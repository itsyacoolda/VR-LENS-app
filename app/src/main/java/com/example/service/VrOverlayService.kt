package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
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
import android.util.Log
import android.view.Gravity
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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VrPreferencesRepository
import com.example.graphics.VrSbsRenderer
import com.example.model.VrSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class VrOverlayService : Service() {

    private val tag = "VrOverlayService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var glSurfaceView: GLSurfaceView? = null
    private var sbsRenderer: VrSbsRenderer? = null
    private var surface: Surface? = null

    // Floating Interactive Controls
    private var floatingHudView: View? = null
    private var floatingOpticsModal: View? = null
    private var isHudMinimized = false

    private lateinit var prefsRepo: VrPreferencesRepository
    private var currentSettings: VrSettings = VrSettings()

    private var vibrator: Vibrator? = null

    // Touch gesture detectors for when Pass-Through is OFF
    private var scaleDetector: ScaleGestureDetector? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        prefsRepo = VrPreferencesRepository.getInstance(applicationContext)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        currentSettings = prefsRepo.getSettings()

        scope.launch {
            prefsRepo.settingsFlow.collect { newSettings ->
                currentSettings = newSettings
                sbsRenderer?.settings = newSettings
                glSurfaceView?.requestRender()
                updateTouchPassThroughFlag()
                updateHudViews()
                _liveSettings.value = newSettings
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
            ACTION_RECENTER -> {
                prefsRepo.updateSettings(
                    currentSettings.copy(
                        ipdOffset = 0.02f,
                        scale = 0.85f,
                        yOffset = 0.0f
                    )
                )
                vibrateBriefly(30)
            }
        }

        return START_NOT_STICKY
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

        val toggleTouchIntent = PendingIntent.getService(
            this, 2,
            Intent(this, VrOverlayService::class.java).apply { action = ACTION_TOGGLE_TOUCH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exitIntent = PendingIntent.getService(
            this, 3,
            Intent(this, VrOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseLabel = if (currentSettings.isPaused) "Play" else "Pause"

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VR SBS Overlay Active")
            .setContentText("Controls: Play/Pause, Resize, Split available on headset seam")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_play, pauseLabel, playPauseIntent)
            .addAction(android.R.drawable.ic_menu_rotate, "Touch Mode", toggleTouchIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop VR", exitIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
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

    private fun setupOverlayViews() {
        // 1. Fullscreen GLSurfaceView for SBS rendering
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setZOrderOnTop(false)
        }

        sbsRenderer = VrSbsRenderer(glSurfaceView!!) { surfaceTexture ->
            mainHandler.post {
                setupVirtualDisplay(surfaceTexture)
            }
        }.apply {
            settings = currentSettings
        }

        glSurfaceView?.setRenderer(sbsRenderer)
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        setupGestureDetectors()
        glSurfaceView?.setOnTouchListener { _, event ->
            if (!currentSettings.touchPassThrough) {
                scaleDetector?.onTouchEvent(event)
                handleManualTouchAdjustment(event)
                true
            } else {
                false
            }
        }

        val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_SECURE // Prevents feedback mirror loop in screen capture!

        val initialFlags = if (currentSettings.touchPassThrough) {
            overlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            overlayFlags
        }

        val surfaceParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
        }

        try {
            windowManager.addView(glSurfaceView, surfaceParams)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add GLSurfaceView to WindowManager", e)
            stopSelf()
            return
        }

        // 2. Setup Unobtrusive Floating VR Seam Controller (along nose bridge)
        setupInteractiveControlsHud()
    }

    private fun updateTouchPassThroughFlag() {
        glSurfaceView?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_SECURE

            params.flags = if (currentSettings.touchPassThrough) {
                overlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                overlayFlags
            }

            try {
                windowManager.updateViewLayout(view, params)
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
        val windowMetrics = windowManager.defaultDisplay
        val size = Point()
        windowMetrics.getRealSize(size)
        val screenWidth = size.x
        val screenHeight = size.y
        val densityDpi = resources.displayMetrics.densityDpi

        surfaceTexture.setDefaultBufferSize(screenWidth, screenHeight)
        surface = Surface(surfaceTexture)

        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "VrSbsVirtualDisplay",
                screenWidth,
                screenHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
            Log.i(tag, "VirtualDisplay created: ${screenWidth}x${screenHeight} at ${densityDpi}dpi")
        } catch (e: Exception) {
            Log.e(tag, "Error creating VirtualDisplay", e)
        }
    }

    /**
     * Creates the sleek, non-obstructing interactive control HUD aligned with the headset seam.
     */
    private fun setupInteractiveControlsHud() {
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
            setPadding(18, 10, 18, 10)
            gravity = Gravity.CENTER_VERTICAL
        }

        // 1. Play / Pause Button
        val playPauseBtn = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(
                if (currentSettings.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            )
            setColorFilter(Color.parseColor("#00E5FF"))
            setPadding(12, 8, 12, 8)
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
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(14, 6, 10, 6)
            setOnClickListener {
                prefsRepo.adjustScale(-0.05f)
                vibrateBriefly(20)
            }
        }

        val sizeIndicator = TextView(this).apply {
            id = View.generateViewId()
            text = "${(currentSettings.scale * 100).toInt()}%"
            textSize = 11f
            setTextColor(Color.parseColor("#80F2FF"))
            paint.isFakeBoldText = true
            setPadding(4, 0, 4, 0)
        }

        val resizeUpBtn = TextView(this).apply {
            text = "＋"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(10, 6, 14, 6)
            setOnClickListener {
                prefsRepo.adjustScale(+0.05f)
                vibrateBriefly(20)
            }
        }

        // Vertical divider line
        val divider1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, 36).apply {
                setMargins(6, 0, 6, 0)
            }
            setBackgroundColor(Color.parseColor("#26334D"))
        }

        // 3. Adjust SBS Split Section: [◀] Split [▶]
        val splitInBtn = TextView(this).apply {
            text = "◀"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(12, 8, 6, 8)
            setOnClickListener {
                prefsRepo.adjustIpd(-0.01f)
                vibrateBriefly(20)
            }
        }

        val splitIndicator = TextView(this).apply {
            id = View.generateViewId()
            text = "IPD ${(currentSettings.ipdOffset * 100).toInt()}"
            textSize = 11f
            setTextColor(Color.parseColor("#B388FF"))
            paint.isFakeBoldText = true
            setPadding(4, 0, 4, 0)
        }

        val splitOutBtn = TextView(this).apply {
            text = "▶"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(6, 8, 12, 8)
            setOnClickListener {
                prefsRepo.adjustIpd(+0.01f)
                vibrateBriefly(20)
            }
        }

        // Vertical divider line
        val divider2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, 36).apply {
                setMargins(6, 0, 6, 0)
            }
            setBackgroundColor(Color.parseColor("#26334D"))
        }

        // 4. Touch Pass-Through Mode toggle
        val touchModeBtn = TextView(this).apply {
            id = View.generateViewId()
            text = if (currentSettings.touchPassThrough) "TOUCH PASS" else "VR CONTROLS"
            textSize = 10f
            paint.isFakeBoldText = true
            setTextColor(if (currentSettings.touchPassThrough) Color.parseColor("#00E676") else Color.parseColor("#FF9100"))
            setPadding(12, 6, 12, 6)
            setOnClickListener {
                val newMode = !currentSettings.touchPassThrough
                prefsRepo.setTouchPassThrough(newMode)
                vibrateBriefly(40)
            }
        }

        // 5. Lens Distortion & Optics Quick Modal Button
        val lensOpticsBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Color.parseColor("#00E5FF"))
            setPadding(10, 8, 10, 8)
            setOnClickListener {
                toggleInVrOpticsModal()
            }
        }

        // 6. Minimize to tiny dot button (so user has zero obstruction)
        val minimizeBtn = TextView(this).apply {
            text = "▲"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(10, 8, 10, 8)
            setOnClickListener {
                collapseHud()
            }
        }

        // 7. Stop VR
        val stopVrBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#FF5252"))
            setPadding(10, 8, 6, 8)
            setOnClickListener {
                vibrateBriefly(50)
                stopSelf()
            }
        }

        // Vertical divider line
        val divider3 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, 36).apply {
                setMargins(6, 0, 6, 0)
            }
            setBackgroundColor(Color.parseColor("#26334D"))
        }

        // Direct Touch Peek Button: instantly suspends overlay so touches work on underlying app
        val touchPeekBtn = TextView(this).apply {
            text = "👆 TOUCH APP"
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
                prefsRepo.setTouchPeek(true)
                vibrateBriefly(40)
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
        expandedLayout.addView(touchModeBtn)
        expandedLayout.addView(divider3)
        expandedLayout.addView(touchPeekBtn)
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
            text = "👆 SCREEN TOUCH ACTIVE"
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

        val devSettingsBtn = TextView(this).apply {
            text = "⚙️ Dev Fix"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(12, 6, 6, 6)
            setOnClickListener {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val generalIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(generalIntent)
                    } catch (_: Exception) {}
                }
            }
        }

        touchPeekBar.addView(peekTitle)
        touchPeekBar.addView(returnToVrBtn)
        touchPeekBar.addView(devSettingsBtn)

        root.addView(expandedLayout)
        root.addView(collapsedDot)
        root.addView(touchPeekBar)

        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 20
        }

        floatingHudView = root
        try {
            windowManager.addView(root, hudParams)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add floating controls HUD", e)
        }

        updateHudViews()
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

        // Update Touch mode
        val touchText = expanded.getChildAt(9) as? TextView
        if (currentSettings.touchPassThrough) {
            touchText?.text = "TOUCH PASS"
            touchText?.setTextColor(Color.parseColor("#00E676"))
        } else {
            touchText?.text = "VR CONTROLS"
            touchText?.setTextColor(Color.parseColor("#FF9100"))
        }
    }

    /**
     * Toggles in-VR Lens Distortion and Optical Tuning Modal with Focal Length and Coefficients.
     */
    private fun toggleInVrOpticsModal() {
        if (floatingOpticsModal != null) {
            try {
                windowManager.removeView(floatingOpticsModal)
            } catch (_: Exception) {}
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

        // 1. Focal Length Slider (30mm to 65mm)
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        floatingOpticsModal = container
        try {
            windowManager.addView(container, modalParams)
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
        _isOverlayRunning.value = false

        try {
            floatingOpticsModal?.let { windowManager.removeView(it) }
            floatingOpticsModal = null
        } catch (_: Exception) {}

        try {
            floatingHudView?.let { windowManager.removeView(it) }
            floatingHudView = null
        } catch (_: Exception) {}

        try {
            glSurfaceView?.let { windowManager.removeView(it) }
            glSurfaceView = null
        } catch (_: Exception) {}

        sbsRenderer?.release()
        sbsRenderer = null

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
        const val ACTION_RECENTER = "com.example.action.RECENTER"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        private val _liveSettings = MutableStateFlow(VrSettings())
        val liveSettings: StateFlow<VrSettings> = _liveSettings.asStateFlow()
    }
}
