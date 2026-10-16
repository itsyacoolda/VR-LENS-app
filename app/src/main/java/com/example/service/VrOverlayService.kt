package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VrPreferencesRepository
import com.example.graphics.VrFrameHolder
import com.example.graphics.VrSbsRenderer
import com.example.model.VrSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

class VrOverlayService : Service() {

    private val tag = "VrOverlayService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var defaultWindowManager: WindowManager
    private var activeWindowManager: WindowManager? = null
    private var isUsingTrustedAccessibilityOverlay = false

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var glSurfaceView: GLSurfaceView? = null
    private var sbsRenderer: VrSbsRenderer? = null
    private var surface: Surface? = null
    private var projectionImageReader: ImageReader? = null
    @Volatile
    private var isShutterSamplingExternalApp = false
    @Volatile
    private var isReadyForCleanExternalCapture = false

    // Floating Interactive Controls & Gear VR Touchpad UI
    private var floatingHudView: View? = null
    private var sideTouchpadView: View? = null
    private var floatingOpticsModal: View? = null
    private var isHudMinimized = false
    private var isSideTouchpadMinimized = false

    private lateinit var prefsRepo: VrPreferencesRepository
    private var currentSettings: VrSettings = VrSettings()

    private var vibrator: Vibrator? = null
    private var touchpadDriver: GearVrTouchpadDriver? = null
    private var cameraHandController: VrArCameraHandController? = null
    private var voiceControlManager: VrVoiceControlManager? = null
    private var headTracker: VrHeadTracker? = null

    // 2-Second Center Dot Dwell-Click tracking in overlay service
    private var dwellAnchorX = 0.5f
    private var dwellAnchorY = 0.5f
    private var dwellStartTime = 0L
    private var hasDwellClickedSpot = true

    // Touch gesture detectors for when Pass-Through is OFF
    private var scaleDetector: ScaleGestureDetector? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        defaultWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        prefsRepo = VrPreferencesRepository.getInstance(applicationContext)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        currentSettings = prefsRepo.getSettings()

        touchpadDriver = GearVrTouchpadDriver(applicationContext) { durationMs ->
            vibrateBriefly(durationMs)
        }.also { it.start() }

        headTracker = VrHeadTracker(applicationContext) { yaw, pitch, roll ->
            sbsRenderer?.updateHeadPose(yaw, pitch, roll)
            glSurfaceView?.requestRender()
        }.also {
            if (currentSettings.headTrackingEnabled) {
                it.start()
            }
        }

        cameraHandController = VrArCameraHandController(
            context = applicationContext,
            onHapticFeedback = { durationMs -> vibrateBriefly(durationMs) },
            onPlayPauseToggle = { togglePlayPauseContent() }
        )

        voiceControlManager = VrVoiceControlManager(
            context = applicationContext,
            onHapticFeedback = { durationMs -> vibrateBriefly(durationMs) },
            onClickCenterDot = { triggerTouchpadClickAtCursor() },
            onPlayPauseToggle = { togglePlayPauseContent() }
        )

        VrFrameHolder.onFrameUpdated = {
            glSurfaceView?.requestRender()
        }

        scope.launch {
            prefsRepo.settingsFlow.collect { newSettings ->
                val trackingChanged = newSettings.headTrackingEnabled != currentSettings.headTrackingEnabled
                currentSettings = newSettings
                sbsRenderer?.settings = newSettings
                if (trackingChanged) {
                    if (newSettings.headTrackingEnabled) {
                        headTracker?.start()
                    } else {
                        headTracker?.stop()
                        sbsRenderer?.updateHeadPose(0f, 0f, 0f)
                    }
                }
                glSurfaceView?.requestRender()
                cameraHandController?.updateStateFromSettings()
                voiceControlManager?.updateFromSettings()
                updateTouchPassThroughFlag()
                updateHudViews()
                updateSideTouchpadViews()
                _liveSettings.value = newSettings
            }
        }

        // Ensure dwell progress is always cleared so clicking is purely triggered by Trackpad tap
        if (currentSettings.dwellProgress > 0f) {
            prefsRepo.setDwellProgress(0f)
        }

        // Periodic clean snapshot refresh when viewing external apps (outside MainActivity)
        scope.launch {
            var wasMainActivityVisible = VrFrameHolder.isMainActivityVisible
            var lastExternalRefreshTime = 0L
            while (isActive) {
                delay(200L)
                val isMainVis = VrFrameHolder.isMainActivityVisible
                if (_isOverlayRunning.value &&
                    !isMainVis &&
                    !currentSettings.isPaused &&
                    !currentSettings.isTouchPeekMode
                ) {
                    val now = SystemClock.uptimeMillis()
                    val justLeftMainActivity = wasMainActivityVisible && !isMainVis
                    if (justLeftMainActivity || (now - lastExternalRefreshTime >= 1100L)) {
                        lastExternalRefreshTime = now
                        requestCleanExternalAppFrame()
                    }
                }
                wasMainActivityVisible = isMainVis
            }
        }

        // Automatically upgrade overlay window to TYPE_ACCESSIBILITY_OVERLAY if user enables Accessibility Service while running
        scope.launch {
            VrAccessibilityService.isConnected.collect { connected ->
                if (_isOverlayRunning.value && connected && !isUsingTrustedAccessibilityOverlay) {
                    Log.i(tag, "Upgrading VR Overlay to Trusted TYPE_ACCESSIBILITY_OVERLAY")
                    reattachOverlayWindowsWithTrustedContext()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != 0 && data != null) {
                    startForegroundNotification()
                    initMediaProjectionAndViews(resultCode, data)
                } else {
                    Log.e(tag, "Missing MediaProjection resultCode or data")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_TOGGLE_PAUSE -> {
                togglePlayPauseContent()
            }
            ACTION_RESIZE_UP -> {
                prefsRepo.adjustScale(+0.05f)
                vibrateBriefly(25)
            }
            ACTION_RESIZE_DOWN -> {
                prefsRepo.adjustScale(-0.05f)
                vibrateBriefly(25)
            }
            ACTION_SPLIT_IN -> {
                prefsRepo.adjustIpd(-0.01f)
                vibrateBriefly(25)
            }
            ACTION_SPLIT_OUT -> {
                prefsRepo.adjustIpd(+0.01f)
                vibrateBriefly(25)
            }
            ACTION_TOGGLE_TOUCH -> {
                val newPassThrough = !currentSettings.touchPassThrough
                prefsRepo.setTouchPassThrough(newPassThrough)
                vibrateBriefly(40)
            }
            ACTION_TOGGLE_TOUCH_PEEK -> {
                prefsRepo.toggleTouchPeek()
                vibrateBriefly(40)
            }
            ACTION_CLICK_CURSOR -> {
                triggerTouchpadClickAtCursor()
            }
            ACTION_RECENTER -> {
                recenterHeadPose()
            }
        }

        return START_NOT_STICKY
    }

    fun recenterHeadPose() {
        headTracker?.recenter()
        prefsRepo.recenterCursor()
        vibrateBriefly(30)
    }

    private fun resolveOverlayWindowManagerAndType(): Pair<WindowManager, Int> {
        val a11yService = VrAccessibilityService.instance
        return if (a11yService != null) {
            isUsingTrustedAccessibilityOverlay = true
            Pair(
                a11yService.getTrustedWindowManager(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            )
        } else {
            isUsingTrustedAccessibilityOverlay = false
            Pair(
                defaultWindowManager,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            )
        }
    }

    private fun reattachOverlayWindowsWithTrustedContext() {
        val wm = activeWindowManager ?: defaultWindowManager
        try {
            floatingOpticsModal?.let { wm.removeView(it) }
            floatingOpticsModal = null
        } catch (_: Exception) {}
        try {
            sideTouchpadView?.let { wm.removeView(it) }
            sideTouchpadView = null
        } catch (_: Exception) {}
        try {
            floatingHudView?.let { wm.removeView(it) }
            floatingHudView = null
        } catch (_: Exception) {}

        val (newWm, newType) = resolveOverlayWindowManagerAndType()
        activeWindowManager = newWm

        glSurfaceView?.let { glView ->
            try {
                wm.removeView(glView)
            } catch (_: Exception) {}
            val surfaceParams = createGlSurfaceLayoutParams(newType)
            try {
                newWm.addView(glView, surfaceParams)
            } catch (e: Exception) {
                Log.e(tag, "Error re-adding GLSurfaceView with trusted type: ${e.message}")
            }
        }

        setupInteractiveControlsHud(newWm, newType)
        setupSideGearVrTouchpad(newWm, newType)
    }

    fun triggerTouchpadClickAtCursor() {
        vibrateBriefly(30)
        val s = prefsRepo.getSettings()
        prefsRepo.setCursorClicking(true)
        prefsRepo.setLastDetectedGesture(
            "🎯 Trackpad Clicked at (${(s.cursorX * 100).toInt()}%, ${(s.cursorY * 100).toInt()}%)"
        )
        mainHandler.postDelayed({
            prefsRepo.setCursorClicking(false)
        }, 220L)

        val a11y = VrAccessibilityService.instance
        if (a11y != null) {
            // Ensure glSurfaceView has FLAG_NOT_TOUCHABLE during gesture injection
            temporarilyEnsurePassThroughForGesture()
            a11y.dispatchTapAtCursor()
            if (!VrFrameHolder.isMainActivityVisible) {
                mainHandler.postDelayed({
                    requestCleanExternalAppFrame()
                }, 320L)
            }
        } else {
            MainActivity.dispatchInAppTouchAtNormalized(s.cursorX, s.cursorY)
        }
    }

    fun temporarilyEnsurePassThroughForGesture() {
        val view = glSurfaceView ?: return
        val wm = activeWindowManager ?: defaultWindowManager
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val hasNotTouchable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        if (!hasNotTouchable) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                wm.updateViewLayout(view, params)
            } catch (_: Exception) {}
            mainHandler.postDelayed({
                updateTouchPassThroughFlag()
            }, 150L)
        }
    }

    private fun promptEnableTrustedTouchAccessibility() {
        Toast.makeText(
            this,
            "Turn ON 'VR Lens SBS Touch & Gear VR Touchpad' in Accessibility to enable Trusted Touch without Developer Options!",
            Toast.LENGTH_LONG
        ).show()
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    fun togglePlayPauseContent() {
        prefsRepo.togglePause()
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val eventTime = SystemClock.uptimeMillis()
            val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
            val up = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
            audioManager?.dispatchMediaKeyEvent(down)
            audioManager?.dispatchMediaKeyEvent(up)
        } catch (_: Exception) {}
        vibrateBriefly(45)
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VrOverlayService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clickCursorIntent = PendingIntent.getService(
            this, 2,
            Intent(this, VrOverlayService::class.java).apply { action = ACTION_CLICK_CURSOR },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exitIntent = PendingIntent.getService(
            this, 3,
            Intent(this, VrOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseLabel = if (currentSettings.isPaused) "Play" else "Pause"

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VR SBS & Gear VR Touchpad Active")
            .setContentText("Trusted Touch & 3D Stereoscopic VR Cursor Ready")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_play, pauseLabel, playPauseIntent)
            .addAction(android.R.drawable.ic_menu_mylocation, "VR Click", clickCursorIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop VR", exitIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasCameraPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasCameraPerm) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                fgType
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isOverlayRunning.value = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VR SBS Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status and interactive controls for VR SBS overlay."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun initMediaProjectionAndViews(resultCode: Int, data: Intent) {
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(tag, "MediaProjection stopped by system")
                    stopSelf()
                }
            }, mainHandler)

            setupOverlayViews()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start MediaProjection: ${e.message}", e)
            stopSelf()
        }
    }

    private fun createGlSurfaceLayoutParams(windowType: Int): WindowManager.LayoutParams {
        val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val initialFlags = if (currentSettings.touchPassThrough) {
            overlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            overlayFlags
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
        }
    }

    private fun setupOverlayViews() {
        val (wm, windowType) = resolveOverlayWindowManagerAndType()
        activeWindowManager = wm

        // 1. Fullscreen GLSurfaceView for SBS rendering
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setZOrderOnTop(false)
        }

        sbsRenderer = VrSbsRenderer(
            glSurfaceView = glSurfaceView!!,
            onCameraSurfaceTextureReady = { camSurfaceTexture ->
                mainHandler.post {
                    cameraHandController?.setGlCameraSurfaceTexture(camSurfaceTexture)
                    cameraHandController?.updateStateFromSettings()
                }
            },
            onSurfaceTextureReady = { surfaceTexture ->
                mainHandler.post {
                    setupVirtualDisplay(surfaceTexture)
                }
            }
        ).apply {
            settings = currentSettings
        }

        glSurfaceView?.setRenderer(sbsRenderer)
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        setupGestureDetectors()
        glSurfaceView?.setOnGenericMotionListener { v, event ->
            touchpadDriver?.handleTouchpadMotionEvent(event, v.width, v.height) ?: false
        }
        glSurfaceView?.setOnTouchListener { v, event ->
            val isFromExternalTouchpad =
                (event.source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD ||
                        (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            if (isFromExternalTouchpad && currentSettings.gearVrTouchpadEnabled) {
                touchpadDriver?.handleTouchpadMotionEvent(event, v.width, v.height) ?: true
            } else if (!currentSettings.touchPassThrough) {
                scaleDetector?.onTouchEvent(event)
                handleManualTouchAdjustment(event)
                true
            } else {
                false
            }
        }

        val surfaceParams = createGlSurfaceLayoutParams(windowType)

        try {
            wm.addView(glSurfaceView, surfaceParams)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add GLSurfaceView to WindowManager", e)
            stopSelf()
            return
        }

        // 2. Setup Unobtrusive Floating VR Seam Controller (along nose bridge)
        setupInteractiveControlsHud(wm, windowType)

        // 3. Setup Right-Edge Gear VR Touchpad & 3D Cursor Controller
        setupSideGearVrTouchpad(wm, windowType)
    }

    private fun updateTouchPassThroughFlag() {
        val wm = activeWindowManager ?: defaultWindowManager
        glSurfaceView?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

            params.flags = if (currentSettings.touchPassThrough) {
                overlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                overlayFlags
            }

            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e(tag, "Error updating overlay touch flags: ${e.message}")
            }
        }
    }

    private fun setupGestureDetectors() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newScale = (currentSettings.scale * scaleFactor).coerceIn(0.5f, 1.3f)
                prefsRepo.updateSettings(currentSettings.copy(scale = newScale))
                return true
            }
        })
    }

    private fun handleManualTouchAdjustment(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = event.x - initialTouchX
                    val dy = event.y - initialTouchY
                    if (abs(dx) > 15 || abs(dy) > 15) {
                        isDragging = true
                        val ipdDelta = (dx / 2000f)
                        val newIpd = (currentSettings.ipdOffset + ipdDelta).coerceIn(-0.15f, 0.15f)

                        val yDelta = (dy / 2500f)
                        val newY = (currentSettings.yOffset - yDelta).coerceIn(-0.25f, 0.25f)

                        prefsRepo.updateSettings(currentSettings.copy(ipdOffset = newIpd, yOffset = newY))
                        initialTouchX = event.x
                        initialTouchY = event.y
                    }
                }
            }
        }
        return true
    }

    private fun setupVirtualDisplay(surfaceTexture: SurfaceTexture) {
        val windowMetrics = defaultWindowManager.defaultDisplay
        val size = Point()
        @Suppress("DEPRECATION")
        windowMetrics.getRealSize(size)
        val captureW = (size.x / 2).coerceIn(320, 720)
        val captureH = (size.y / 2).coerceIn(480, 1440)
        val densityDpi = resources.displayMetrics.densityDpi

        surfaceTexture.setDefaultBufferSize(captureW, captureH)

        try {
            projectionImageReader?.close()
            projectionImageReader = ImageReader.newInstance(
                captureW,
                captureH,
                PixelFormat.RGBA_8888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = try {
                        reader.acquireLatestImage()
                    } catch (_: Exception) {
                        null
                    } ?: return@setOnImageAvailableListener

                    try {
                        // When MainActivity is visible, MainActivity feeds its own Window directly
                        // into VrFrameHolder via PixelCopy/Canvas with zero overlay interference.
                        // When viewing external apps, only accept frames when the overlay is hidden
                        // (either Touch Peek mode or during the verified clean shutter window).
                        val canAcceptExternalFrame = !VrFrameHolder.isMainActivityVisible &&
                                (currentSettings.isTouchPeekMode || isReadyForCleanExternalCapture)

                        if (canAcceptExternalFrame) {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride
                            val rowPadding = rowStride - pixelStride * captureW
                            val bmpWidth = captureW + rowPadding / pixelStride

                            // Verify left-edge vertical center marker (x = 2, y = captureH / 2) is NOT present
                            val markerY = captureH / 2
                            val markerOffset = (markerY * rowStride) + (2 * pixelStride)
                            var isFeedbackFrame = false
                            if (markerOffset >= 0 && markerOffset + 3 < buffer.limit()) {
                                val r = buffer.get(markerOffset).toInt() and 0xFF
                                val g = buffer.get(markerOffset + 1).toInt() and 0xFF
                                val b = buffer.get(markerOffset + 2).toInt() and 0xFF
                                // Matches vec4(0.051, 0.949, 0.741, 1.0) -> rgb(13, 242, 189)
                                if (r in 0..45 && g in 200..255 && b in 145..225) {
                                    isFeedbackFrame = true
                                }
                            }

                            if (!isFeedbackFrame || currentSettings.isTouchPeekMode) {
                                buffer.position(0)
                                val rawBmp = Bitmap.createBitmap(bmpWidth, captureH, Bitmap.Config.ARGB_8888)
                                rawBmp.copyPixelsFromBuffer(buffer)
                                val cropped = if (bmpWidth != captureW) {
                                    Bitmap.createBitmap(rawBmp, 0, 0, captureW, captureH).also {
                                        rawBmp.recycle()
                                    }
                                } else {
                                    rawBmp
                                }

                                isReadyForCleanExternalCapture = false
                                VrFrameHolder.updateFromCleanProjection(cropped)
                                glSurfaceView?.requestRender()

                                if (isShutterSamplingExternalApp) {
                                    mainHandler.post {
                                        restoreOverlayAlphaAfterShutter()
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        image.close()
                    }
                }, mainHandler)
            }

            surface = projectionImageReader?.surface

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "VrSbsVirtualDisplay",
                captureW,
                captureH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
            Log.i(tag, "VirtualDisplay created: ${captureW}x${captureH} at ${densityDpi}dpi")
        } catch (e: Exception) {
            Log.e(tag, "Error creating VirtualDisplay", e)
        }
    }

    private fun restoreOverlayAlphaAfterShutter() {
        val targetAlpha = if (currentSettings.isTouchPeekMode) 0.0f else 1.0f
        glSurfaceView?.alpha = targetAlpha
        floatingHudView?.alpha = 1.0f
        sideTouchpadView?.alpha = 1.0f
        isReadyForCleanExternalCapture = false
        isShutterSamplingExternalApp = false
    }

    fun requestCleanExternalAppFrame() {
        val view = glSurfaceView ?: return
        if (isShutterSamplingExternalApp || VrFrameHolder.isMainActivityVisible) return
        isShutterSamplingExternalApp = true
        mainHandler.post {
            try {
                projectionImageReader?.acquireLatestImage()?.close()
            } catch (_: Exception) {}
            isReadyForCleanExternalCapture = true
            view.alpha = 0.0f
            floatingHudView?.alpha = 0.0f
            sideTouchpadView?.alpha = 0.0f
            // Safety timeout to restore overlay alpha even if external screen is static
            mainHandler.postDelayed({
                if (isShutterSamplingExternalApp) {
                    restoreOverlayAlphaAfterShutter()
                }
            }, 95L)
        }
    }

    /**
     * Creates the sleek, non-obstructing interactive control HUD aligned with the headset seam.
     */
    private fun setupInteractiveControlsHud(wm: WindowManager, windowType: Int) {
        val root = FrameLayout(this)

        val pillBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 48f
            setColor(Color.parseColor("#E60B0F17"))
            setStroke(2, Color.parseColor("#3300E5FF"))
        }

        // Expanded Control Bar
        val expandedLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            background = pillBackground
            setPadding(16, 8, 16, 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        // 1. Play / Pause Button
        val playPauseBtn = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(
                if (currentSettings.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            )
            setColorFilter(Color.parseColor("#00E5FF"))
            setPadding(10, 8, 10, 8)
            setOnClickListener {
                togglePlayPauseContent()
                setImageResource(
                    if (currentSettings.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
                )
            }
        }

        // 2. Resize Overlay Window Section: [-] Size% [+]
        val resizeDownBtn = TextView(this).apply {
            text = "－"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(12, 6, 8, 6)
            setOnClickListener {
                prefsRepo.adjustScale(-0.05f)
                vibrateBriefly(20)
            }
        }

        val sizeIndicator = TextView(this).apply {
            id = View.generateViewId()
            text = "${(currentSettings.scale * 100).toInt()}%"
            textSize = 10.5f
            setTextColor(Color.parseColor("#80F2FF"))
            paint.isFakeBoldText = true
            setPadding(4, 0, 4, 0)
        }

        val resizeUpBtn = TextView(this).apply {
            text = "＋"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(8, 6, 12, 6)
            setOnClickListener {
                prefsRepo.adjustScale(+0.05f)
                vibrateBriefly(20)
            }
        }

        val divider1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, 34).apply {
                setMargins(4, 0, 4, 0)
            }
            setBackgroundColor(Color.parseColor("#26334D"))
        }

        // 3. Adjust SBS Split Section: [◀] Split [▶]
        val splitInBtn = TextView(this).apply {
            text = "◀"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(10, 8, 6, 8)
            setOnClickListener {
                prefsRepo.adjustIpd(-0.01f)
                vibrateBriefly(20)
            }
        }

        val splitIndicator = TextView(this).apply {
            id = View.generateViewId()
            text = "IPD ${(currentSettings.ipdOffset * 100).toInt()}"
            textSize = 10.5f
            setTextColor(Color.parseColor("#B388FF"))
            paint.isFakeBoldText = true
            setPadding(4, 0, 4, 0)
        }

        val splitOutBtn = TextView(this).apply {
            text = "▶"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(6, 8, 10, 8)
            setOnClickListener {
                prefsRepo.adjustIpd(+0.01f)
                vibrateBriefly(20)
            }
        }

        val divider2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, 34).apply {
                setMargins(4, 0, 4, 0)
            }
            setBackgroundColor(Color.parseColor("#26334D"))
        }

        // 4. Touchpad Cursor Click Button
        val cursorClickBtn = TextView(this).apply {
            id = View.generateViewId()
            text = "🎯 CLICK"
            textSize = 10f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#00E676"))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#2600E676"))
                setStroke(1, Color.parseColor("#00E676"))
            }
            background = btnBg
            setPadding(10, 6, 10, 6)
            setOnClickListener {
                triggerTouchpadClickAtCursor()
            }
        }

        val divider3 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, 34).apply {
                setMargins(4, 0, 4, 0)
            }
            setBackgroundColor(Color.parseColor("#26334D"))
        }

        // 5. Direct Touch Peek Button
        val touchPeekBtn = TextView(this).apply {
            text = "👆 PEEK"
            textSize = 10f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(8, 6, 8, 6)
            setOnClickListener {
                prefsRepo.setTouchPeek(true)
                vibrateBriefly(40)
            }
        }

        // 5b. Augmented Reality (AR) Camera Passthrough Toggle Button
        val arToggleBtn = TextView(this).apply {
            text = if (currentSettings.arPassthroughEnabled) "🌐 AR ON" else "🌐 AR"
            textSize = 10f
            paint.isFakeBoldText = true
            setTextColor(if (currentSettings.arPassthroughEnabled) Color.parseColor("#00E676") else Color.parseColor("#B388FF"))
            setPadding(8, 6, 8, 6)
            setOnClickListener {
                prefsRepo.setArPassthroughEnabled(!currentSettings.arPassthroughEnabled)
                vibrateBriefly(35)
            }
        }

        // 5c. Apple Vision Pro Hand Gesture Tracking (In-Dev) Toggle Button
        val handAiBtn = TextView(this).apply {
            text = if (currentSettings.handTrackingEnabled) "🖐️ HAND ON" else "🖐️ HAND"
            textSize = 10f
            paint.isFakeBoldText = true
            setTextColor(if (currentSettings.handTrackingEnabled) Color.parseColor("#00E676") else Color.parseColor("#80F2FF"))
            setPadding(8, 6, 8, 6)
            setOnClickListener {
                prefsRepo.setHandTrackingEnabled(!currentSettings.handTrackingEnabled)
                vibrateBriefly(35)
            }
        }

        // 5d. Curved Cinema Screen Display Mode Toggle Button
        val cinemaBtn = TextView(this).apply {
            text = if (currentSettings.cinemaScreenEnabled) "🎬 CURVED" else "🎬 FLAT"
            textSize = 10f
            paint.isFakeBoldText = true
            setTextColor(if (currentSettings.cinemaScreenEnabled) Color.parseColor("#00E5FF") else Color.parseColor("#94A3B8"))
            setPadding(8, 6, 8, 6)
            setOnClickListener {
                prefsRepo.setCinemaScreenEnabled(!currentSettings.cinemaScreenEnabled)
                vibrateBriefly(35)
            }
        }

        // 6. Lens Distortion & Optics Quick Modal Button
        val lensOpticsBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Color.parseColor("#00E5FF"))
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                toggleInVrOpticsModal()
            }
        }

        // 7. Minimize to tiny dot button
        val minimizeBtn = TextView(this).apply {
            text = "▲"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                collapseHud()
            }
        }

        // 8. Stop VR
        val stopVrBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#FF5252"))
            setPadding(8, 8, 6, 8)
            setOnClickListener {
                vibrateBriefly(50)
                stopSelf()
            }
        }

        expandedLayout.addView(playPauseBtn)
        expandedLayout.addView(resizeDownBtn)
        expandedLayout.addView(sizeIndicator)
        expandedLayout.addView(resizeUpBtn)
        expandedLayout.addView(divider1)
        expandedLayout.addView(splitInBtn)
        expandedLayout.addView(splitIndicator)
        expandedLayout.addView(splitOutBtn)
        expandedLayout.addView(divider2)
        expandedLayout.addView(cursorClickBtn)
        expandedLayout.addView(divider3)
        expandedLayout.addView(touchPeekBtn)
        expandedLayout.addView(arToggleBtn)
        expandedLayout.addView(handAiBtn)
        expandedLayout.addView(cinemaBtn)
        expandedLayout.addView(lensOpticsBtn)
        expandedLayout.addView(minimizeBtn)
        expandedLayout.addView(stopVrBtn)

        // Collapsed Minimal Floating Dot (unobtrusive 24dp dot along nose seam)
        val collapsedDot = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 30f
                setColor(Color.parseColor("#CC0D1117"))
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            background = dotBg
            setPadding(16, 8, 16, 8)
            gravity = Gravity.CENTER

            val dotText = TextView(context).apply {
                text = "VR SBS ▼"
                textSize = 10f
                setTextColor(Color.parseColor("#00E5FF"))
                paint.isFakeBoldText = true
            }
            addView(dotText)

            setOnClickListener {
                expandHud()
            }
        }

        // Floating Touch Peek Bar (visible when screen is in touch interaction mode)
        val touchPeekBar = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            visibility = if (currentSettings.isTouchPeekMode) View.VISIBLE else View.GONE
            val barBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(Color.parseColor("#FA0B0F17"))
                setStroke(2, Color.parseColor("#00E676"))
            }
            background = barBg
            setPadding(18, 10, 18, 10)
            gravity = Gravity.CENTER_VERTICAL
        }

        val peekTitle = TextView(this).apply {
            text = "👆 DIRECT SCREEN TOUCH"
            textSize = 11f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#00E676"))
            setPadding(6, 4, 14, 4)
        }

        val returnToVrBtn = TextView(this).apply {
            text = "🕶️ RETURN TO VR"
            textSize = 12f
            paint.isFakeBoldText = true
            setTextColor(Color.BLACK)
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.parseColor("#00E5FF"))
            }
            background = btnBg
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                prefsRepo.setTouchPeek(false)
                vibrateBriefly(40)
            }
        }

        touchPeekBar.addView(peekTitle)
        touchPeekBar.addView(returnToVrBtn)

        root.addView(expandedLayout)
        root.addView(collapsedDot)
        root.addView(touchPeekBar)

        root.setOnGenericMotionListener { v, event ->
            touchpadDriver?.handleTouchpadMotionEvent(event, v.width, v.height) ?: false
        }

        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 20
        }

        floatingHudView = root
        try {
            wm.addView(root, hudParams)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add floating controls HUD", e)
        }

        updateHudViews()
    }

    /**
     * Creates a sleek, right-edge Gear VR Touchpad & 3D Stereoscopic Cursor controller.
     * Works with both the physical Gear VR touchpad (USB-C / HID) AND right-thumb swipe/tap gestures
     * so the student and teacher can effortlessly point, click, scroll, and go Back in VR!
     */
    private fun setupSideGearVrTouchpad(wm: WindowManager, windowType: Int) {
        val root = FrameLayout(this)

        val padBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(Color.parseColor("#D90B0F17"))
            setStroke(2, Color.parseColor("#4D00E5FF"))
        }

        val expandedPad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = padBg
            setPadding(12, 12, 12, 12)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val padTitle = TextView(this).apply {
            text = "GEAR PAD"
            textSize = 9f
            setTextColor(Color.parseColor("#00E5FF"))
            paint.isFakeBoldText = true
            setPadding(4, 0, 8, 0)
        }

        val minPadBtn = TextView(this).apply {
            text = "▶"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(6, 2, 4, 2)
            setOnClickListener {
                isSideTouchpadMinimized = true
                updateSideTouchpadViews()
                vibrateBriefly(20)
            }
        }

        headerRow.addView(padTitle)
        headerRow.addView(minPadBtn)
        expandedPad.addView(headerRow)

        // Interactive Trackpad Surface for moving the 3D Stereoscopic VR Cursor & Tapping
        val trackpadSurface = FrameLayout(this).apply {
            val surfaceBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#1A00E5FF"))
                setStroke(2, Color.parseColor("#6600E676"))
            }
            background = surfaceBg
            layoutParams = LinearLayout.LayoutParams(180, 135).apply {
                setMargins(0, 6, 0, 6)
            }

            val hintLabel = TextView(context).apply {
                text = "SWIPE CURSOR\nTAP TO CLICK"
                textSize = 8.5f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#CCFFFFFF"))
                paint.isFakeBoldText = true
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            addView(hintLabel)

            setOnGenericMotionListener { v, event ->
                touchpadDriver?.handleTouchpadMotionEvent(event, v.width, v.height) ?: false
            }

            setOnTouchListener { v, event ->
                TemporarilyEnsureGestureReady()
                touchpadDriver?.handleTouchpadMotionEvent(event, v.width, v.height) ?: true
            }
        }
        expandedPad.addView(trackpadSurface)

        // Mode Toggle: Pointer (Aim) vs Scroll vs Zoom/IPD Mode
        val modeToggleBtn = TextView(this).apply {
            id = View.generateViewId()
            val mode = currentSettings.touchpadMode
            text = when (mode) {
                VrSettings.TOUCHPAD_MODE_POINTER -> "🎯 AIM & TAP"
                VrSettings.TOUCHPAD_MODE_SCROLL -> "📜 SCROLL"
                VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> "🔍 ZOOM/IPD"
                else -> "🎯 AIM"
            }
            textSize = 9f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            val modeBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f
                setColor(
                    when (mode) {
                        VrSettings.TOUCHPAD_MODE_POINTER -> Color.parseColor("#00E5FF")
                        VrSettings.TOUCHPAD_MODE_SCROLL -> Color.parseColor("#B388FF")
                        VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> Color.parseColor("#00E676")
                        else -> Color.parseColor("#00E5FF")
                    }
                )
            }
            background = modeBg
            setPadding(8, 7, 8, 7)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 2, 0, 5)
            }
            setOnClickListener {
                prefsRepo.cycleTouchpadMode()
                vibrateBriefly(30)
            }
        }
        expandedPad.addView(modeToggleBtn)

        // Gear VR Side Buttons Row 1: HOME + BACK
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 4)
            }
        }

        val homeBtn = TextView(this).apply {
            text = "🏠 HOME"
            textSize = 8.5f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#2A1B4E"))
            }
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 3, 0)
            }
            setOnClickListener {
                val a11y = VrAccessibilityService.instance
                if (a11y != null) {
                    vibrateBriefly(30)
                    a11y.triggerHomeButton()
                } else {
                    promptEnableTrustedTouchAccessibility()
                }
            }
        }

        val backBtn = TextView(this).apply {
            text = "🔙 BACK"
            textSize = 8.5f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#26334D"))
            }
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(3, 0, 0, 0)
            }
            setOnClickListener {
                val a11y = VrAccessibilityService.instance
                if (a11y != null) {
                    vibrateBriefly(30)
                    a11y.triggerBackButton()
                } else {
                    promptEnableTrustedTouchAccessibility()
                }
            }
        }
        navRow.addView(homeBtn)
        navRow.addView(backBtn)
        expandedPad.addView(navRow)

        // Gear VR Side Buttons Row 2: VOL+ & VOL-
        val volRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val volUpBtn = TextView(this).apply {
            text = "🔊 VOL+"
            textSize = 8.5f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00E676"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#142834"))
            }
            setPadding(8, 7, 8, 7)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 3, 0)
            }
            setOnClickListener {
                vibrateBriefly(25)
                val a11y = VrAccessibilityService.instance
                if (a11y != null) {
                    a11y.handleVolumeUpButton()
                } else {
                    prefsRepo.adjustScale(+0.04f)
                }
            }
        }

        val volDownBtn = TextView(this).apply {
            text = "🔉 VOL-"
            textSize = 8.5f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#142834"))
            }
            setPadding(8, 7, 8, 7)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(3, 0, 0, 0)
            }
            setOnClickListener {
                vibrateBriefly(25)
                val a11y = VrAccessibilityService.instance
                if (a11y != null) {
                    a11y.handleVolumeDownButton()
                } else {
                    prefsRepo.adjustScale(-0.04f)
                }
            }
        }
        volRow.addView(volUpBtn)
        volRow.addView(volDownBtn)
        expandedPad.addView(volRow)

        val recenterPadBtn = TextView(this).apply {
            text = "🎯 RECENTER VIEW"
            textSize = 8.5f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#102236"))
                setStroke(1, Color.parseColor("#3300E5FF"))
            }
            setPadding(8, 7, 8, 7)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 6, 0, 0)
            }
            setOnClickListener {
                recenterHeadPose()
            }
        }
        expandedPad.addView(recenterPadBtn)

        // Collapsed Right-Edge Pill Tab
        val collapsedTab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val tabBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.parseColor("#CC0D1117"))
                setStroke(2, Color.parseColor("#00E676"))
            }
            background = tabBg
            setPadding(12, 18, 12, 18)
            gravity = Gravity.CENTER

            val tabText = TextView(context).apply {
                text = "◀\nP\nA\nD"
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#00E676"))
                paint.isFakeBoldText = true
            }
            addView(tabText)
            setOnClickListener {
                isSideTouchpadMinimized = false
                updateSideTouchpadViews()
                vibrateBriefly(25)
            }
        }

        root.addView(expandedPad)
        root.addView(collapsedTab)

        val sideParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 12
        }

        sideTouchpadView = root
        try {
            wm.addView(root, sideParams)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add side Gear VR touchpad view", e)
        }

        updateSideTouchpadViews()
    }

    private fun TemporarilyEnsureGestureReady() {
        temporarilyEnsurePassThroughForGesture()
    }

    private fun updateSideTouchpadViews() {
        val root = sideTouchpadView as? FrameLayout ?: return
        val expanded = root.getChildAt(0) as? LinearLayout ?: return
        val collapsed = root.getChildAt(1) as? LinearLayout ?: return

        if (currentSettings.isTouchPeekMode || !currentSettings.gearVrTouchpadEnabled) {
            root.visibility = View.GONE
            return
        }
        root.visibility = View.VISIBLE

        if (isSideTouchpadMinimized) {
            expanded.visibility = View.GONE
            collapsed.visibility = View.VISIBLE
        } else {
            expanded.visibility = View.VISIBLE
            collapsed.visibility = View.GONE
        }

        val modeToggleBtn = expanded.getChildAt(2) as? TextView
        modeToggleBtn?.text = if (currentSettings.touchpadSwipeScrolls) "📜 SCROLL" else "🎯 CURSOR"
        (modeToggleBtn?.background as? GradientDrawable)?.setColor(
            if (currentSettings.touchpadSwipeScrolls) Color.parseColor("#B388FF")
            else Color.parseColor("#00E5FF")
        )
    }

    private fun collapseHud() {
        val root = floatingHudView as? FrameLayout ?: return
        val expanded = root.getChildAt(0)
        val collapsed = root.getChildAt(1)
        expanded.visibility = View.GONE
        collapsed.visibility = View.VISIBLE
        isHudMinimized = true
        vibrateBriefly(25)
    }

    private fun expandHud() {
        val root = floatingHudView as? FrameLayout ?: return
        val expanded = root.getChildAt(0)
        val collapsed = root.getChildAt(1)
        expanded.visibility = View.VISIBLE
        collapsed.visibility = View.GONE
        isHudMinimized = false
        vibrateBriefly(25)
    }

    private fun updateHudViews() {
        val root = floatingHudView as? FrameLayout ?: return
        val expanded = root.getChildAt(0) as? LinearLayout ?: return
        val collapsed = root.getChildAt(1) as? LinearLayout ?: return
        val touchPeek = root.getChildAt(2) as? LinearLayout ?: return

        if (currentSettings.isTouchPeekMode) {
            glSurfaceView?.visibility = View.GONE
            expanded.visibility = View.GONE
            collapsed.visibility = View.GONE
            touchPeek.visibility = View.VISIBLE
        } else {
            glSurfaceView?.visibility = View.VISIBLE
            glSurfaceView?.requestRender()
            touchPeek.visibility = View.GONE
            if (isHudMinimized) {
                expanded.visibility = View.GONE
                collapsed.visibility = View.VISIBLE
            } else {
                expanded.visibility = View.VISIBLE
                collapsed.visibility = View.GONE
            }
        }

        // Update Play/Pause icon
        val playPauseBtn = expanded.getChildAt(0) as? ImageView
        playPauseBtn?.setImageResource(
            if (currentSettings.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        )

        // Update Scale %
        val scaleText = expanded.getChildAt(2) as? TextView
        scaleText?.text = "${(currentSettings.scale * 100).toInt()}%"

        // Update Split / IPD
        val splitText = expanded.getChildAt(6) as? TextView
        splitText?.text = "IPD ${(currentSettings.ipdOffset * 100).toInt()}"

        // Update AR Toggle & Hand AI Toggle labels
        val arBtn = expanded.getChildAt(11) as? TextView
        arBtn?.text = if (currentSettings.arPassthroughEnabled) "🌐 AR ON" else "🌐 AR"
        arBtn?.setTextColor(
            if (currentSettings.arPassthroughEnabled) Color.parseColor("#00E676")
            else Color.parseColor("#B388FF")
        )

        val handBtn = expanded.getChildAt(12) as? TextView
        handBtn?.text = if (currentSettings.handTrackingEnabled) "🖐️ HAND ON" else "🖐️ HAND"
        handBtn?.setTextColor(
            if (currentSettings.handTrackingEnabled) Color.parseColor("#00E676")
            else Color.parseColor("#80F2FF")
        )

        val cinemaBtn = expanded.getChildAt(13) as? TextView
        cinemaBtn?.text = if (currentSettings.cinemaScreenEnabled) "🎬 CURVED" else "🎬 FLAT"
        cinemaBtn?.setTextColor(
            if (currentSettings.cinemaScreenEnabled) Color.parseColor("#00E5FF")
            else Color.parseColor("#94A3B8")
        )
    }

    /**
     * Toggles in-VR Lens Distortion and Optical Tuning Modal with Focal Length and Coefficients.
     */
    private fun toggleInVrOpticsModal() {
        val (wm, windowType) = resolveOverlayWindowManagerAndType()
        if (floatingOpticsModal != null) {
            try {
                wm.removeView(floatingOpticsModal)
            } catch (_: Exception) {
                try {
                    defaultWindowManager.removeView(floatingOpticsModal)
                } catch (_: Exception) {}
            }
            floatingOpticsModal = null
            return
        }

        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(Color.parseColor("#F50D1117"))
            setStroke(2, Color.parseColor("#00E5FF"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(42, 30, 42, 30)
            elevation = 30f
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Lens Distortion & Optics Tuning"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 15f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeIcon = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 0, 4, 16)
            setOnClickListener { toggleInVrOpticsModal() }
        }

        headerRow.addView(title)
        headerRow.addView(closeIcon)
        container.addView(headerRow)

        // 1. Focal Length Slider (25mm to 65mm)
        val focalLabel = TextView(this).apply {
            text = "Focal Length: ${currentSettings.focalLength.toInt()} mm (Gear VR = 42mm)"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, 8, 0, 0)
        }
        val focalSeek = SeekBar(this).apply {
            max = 40
            progress = (currentSettings.focalLength - 25f).toInt().coerceIn(0, 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val f = 25f + progress
                        focalLabel.text = "Focal Length: ${f.toInt()} mm (Gear VR = 42mm)"
                        prefsRepo.updateSettings(currentSettings.copy(focalLength = f, presetName = VrSettings.PRESET_CUSTOM))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(focalLabel)
        container.addView(focalSeek)

        // 2. Distortion Coefficient K1
        val k1Label = TextView(this).apply {
            text = "Curvature (Distortion K1): ${String.format("%.2f", currentSettings.barrelK1)}"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, 8, 0, 0)
        }
        val k1Seek = SeekBar(this).apply {
            max = 35
            progress = (currentSettings.barrelK1 * 100).toInt().coerceIn(0, 35)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val k1 = progress / 100f
                        k1Label.text = "Curvature (Distortion K1): ${String.format("%.2f", k1)}"
                        prefsRepo.updateSettings(currentSettings.copy(barrelK1 = k1, presetName = VrSettings.PRESET_CUSTOM))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(k1Label)
        container.addView(k1Seek)

        // 3. Distortion Coefficient K2
        val k2Label = TextView(this).apply {
            text = "Edge Warp (Distortion K2): ${String.format("%.3f", currentSettings.barrelK2)}"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, 8, 0, 0)
        }
        val k2Seek = SeekBar(this).apply {
            max = 20
            progress = (currentSettings.barrelK2 * 200).toInt().coerceIn(0, 20)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val k2 = progress / 200f
                        k2Label.text = "Edge Warp (Distortion K2): ${String.format("%.3f", k2)}"
                        prefsRepo.updateSettings(currentSettings.copy(barrelK2 = k2, presetName = VrSettings.PRESET_CUSTOM))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(k2Label)
        container.addView(k2Seek)

        // 4. Central Split Gap Width
        val gapLabel = TextView(this).apply {
            text = "Central Divider Split Gap: ${(currentSettings.splitGapWidth * 100).toInt()}%"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, 8, 0, 0)
        }
        val gapSeek = SeekBar(this).apply {
            max = 10
            progress = (currentSettings.splitGapWidth * 100).toInt().coerceIn(0, 10)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val gap = progress / 100f
                        gapLabel.text = "Central Divider Split Gap: ${progress}%"
                        prefsRepo.updateSettings(currentSettings.copy(splitGapWidth = gap, presetName = VrSettings.PRESET_CUSTOM))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(gapLabel)
        container.addView(gapSeek)

        // 5. Curved Cinema Screen Curvature
        val cinemaCurveLabel = TextView(this).apply {
            text = "Curved Cinema Screen Arc: ${if (currentSettings.cinemaScreenEnabled) "${(currentSettings.cinemaCurvature * 100).toInt()}%" else "Flat (0%)"}"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
            setPadding(0, 8, 0, 0)
        }
        val cinemaCurveSeek = SeekBar(this).apply {
            max = 80
            progress = if (currentSettings.cinemaScreenEnabled) (currentSettings.cinemaCurvature * 100).toInt().coerceIn(0, 80) else 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val curve = progress / 100f
                        cinemaCurveLabel.text = "Curved Cinema Screen Arc: ${if (progress > 1) "${progress}%" else "Flat (0%)"}"
                        prefsRepo.setCinemaCurvature(curve)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(cinemaCurveLabel)
        container.addView(cinemaCurveSeek)

        // Done button
        val doneBtn = TextView(this).apply {
            text = "Done & Apply"
            setTextColor(Color.BLACK)
            textSize = 13f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#00E5FF"))
            }
            background = btnBg
            setPadding(20, 14, 20, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
            setOnClickListener {
                toggleInVrOpticsModal()
            }
        }
        container.addView(doneBtn)

        val modalParams = WindowManager.LayoutParams(
            720,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        floatingOpticsModal = container
        try {
            wm.addView(container, modalParams)
        } catch (e: Exception) {
            Log.e(tag, "Failed to show optics modal", e)
        }
    }

    private fun vibrateBriefly(ms: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isOverlayRunning.value = false
        VrFrameHolder.onFrameUpdated = null
        voiceControlManager?.release()
        voiceControlManager = null
        cameraHandController?.release()
        cameraHandController = null
        touchpadDriver?.stop()
        touchpadDriver = null
        headTracker?.stop()
        headTracker = null

        val wm = activeWindowManager ?: defaultWindowManager

        try {
            floatingOpticsModal?.let { wm.removeView(it) }
            floatingOpticsModal = null
        } catch (_: Exception) {}

        try {
            sideTouchpadView?.let { wm.removeView(it) }
            sideTouchpadView = null
        } catch (_: Exception) {}

        try {
            floatingHudView?.let { wm.removeView(it) }
            floatingHudView = null
        } catch (_: Exception) {}

        try {
            glSurfaceView?.let { wm.removeView(it) }
            glSurfaceView = null
        } catch (_: Exception) {}

        sbsRenderer?.release()
        sbsRenderer = null

        try {
            projectionImageReader?.close()
        } catch (_: Exception) {}
        projectionImageReader = null

        surface?.release()
        surface = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        scope.cancel()
        Log.i(tag, "VrOverlayService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var instance: VrOverlayService? = null
            private set

        const val CHANNEL_ID = "vr_sbs_overlay_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.action.START_VR"
        const val ACTION_STOP = "com.example.action.STOP_VR"
        const val ACTION_TOGGLE_PAUSE = "com.example.action.TOGGLE_PAUSE"
        const val ACTION_RESIZE_UP = "com.example.action.RESIZE_UP"
        const val ACTION_RESIZE_DOWN = "com.example.action.RESIZE_DOWN"
        const val ACTION_SPLIT_IN = "com.example.action.SPLIT_IN"
        const val ACTION_SPLIT_OUT = "com.example.action.SPLIT_OUT"
        const val ACTION_TOGGLE_TOUCH = "com.example.action.TOGGLE_TOUCH"
        const val ACTION_TOGGLE_TOUCH_PEEK = "com.example.action.TOGGLE_TOUCH_PEEK"
        const val ACTION_CLICK_CURSOR = "com.example.action.CLICK_CURSOR"
        const val ACTION_RECENTER = "com.example.action.RECENTER"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        private val _liveSettings = MutableStateFlow(VrSettings())
        val liveSettings: StateFlow<VrSettings> = _liveSettings.asStateFlow()
    }
}
