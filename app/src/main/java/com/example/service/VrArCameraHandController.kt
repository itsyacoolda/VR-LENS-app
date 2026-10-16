package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import com.example.data.VrPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Dual-purpose Camera2 Controller for:
 * 1. Augmented Reality (AR) Stereoscopic Passthrough (streams Rear Camera to OpenGL ES SurfaceTexture
 *    so the real world is visible in 3D SBS behind any floating app).
 * 2. Apple Vision Pro style Hand Movement & Gemini AI Vision Gesture Detection (detects hand swipes
 *    left/right/up/down and pinch-to-click to control the underlying app hands-free).
 */
class VrArCameraHandController(
    private val context: Context,
    private val onHapticFeedback: (Long) -> Unit,
    private val onPlayPauseToggle: () -> Unit
) {
    private val tag = "VrArCameraHandCtrl"
    private val prefsRepo = VrPreferencesRepository.getInstance(context)
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var glCameraSurface: Surface? = null
    private var currentSurfaceTexture: SurfaceTexture? = null

    private var isCameraRunning = false

    // Optical hand motion & swipe tracking state
    private var prevLumaFrame: ByteArray? = null
    private var smoothedHandX = 0.5f
    private var smoothedHandY = 0.5f
    private var historyStartX = 0.5f
    private var historyStartY = 0.5f
    private var historyStartTime = 0L
    private var lastGestureTriggerTime = 0L
    private var prevActivePixelCount = 0

    // Gemini AI Vision throttling state
    @Volatile
    private var isAiVisionRequestInFlight = false
    private var lastAiVisionTimestamp = 0L

    fun setGlCameraSurfaceTexture(surfaceTexture: SurfaceTexture?) {
        if (currentSurfaceTexture === surfaceTexture) return
        currentSurfaceTexture = surfaceTexture
        surfaceTexture?.setDefaultBufferSize(1280, 720)
        glCameraSurface?.release()
        glCameraSurface = surfaceTexture?.let { Surface(it) }
        if (isCameraRunning) {
            restartCameraSession()
        }
    }

    fun updateStateFromSettings() {
        val settings = prefsRepo.getSettings()
        val shouldRunCamera = settings.handTrackingEnabled || settings.arPassthroughEnabled
        if (shouldRunCamera && !isCameraRunning) {
            startCamera()
        } else if (!shouldRunCamera && isCameraRunning) {
            stopCamera()
        }
    }

    @SuppressLint("MissingPermission")
    fun startCamera() {
        if (isCameraRunning) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            prefsRepo.setLastDetectedGesture("⚠️ Grant Camera Permission for AR & Hand AI")
            _isCameraActive.value = false
            return
        }

        isCameraRunning = true
        if (cameraThread == null) {
            cameraThread = HandlerThread("VrArCameraThread").apply { start() }
            cameraHandler = Handler(cameraThread!!.looper)
        }

        try {
            val backCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (backCameraId == null) {
                prefsRepo.setLastDetectedGesture("No Rear Camera Found")
                isCameraRunning = false
                return
            }

            imageReader?.close()
            imageReader = ImageReader.newInstance(ANALYSIS_WIDTH, ANALYSIS_HEIGHT, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val image = try {
                        reader.acquireLatestImage()
                    } catch (_: Exception) {
                        null
                    }
                    if (image != null) {
                        try {
                            processCameraFrame(image)
                        } catch (e: Exception) {
                            Log.w(tag, "Frame analysis error: ${e.message}")
                        } finally {
                            image.close()
                        }
                    }
                }, cameraHandler)
            }

            cameraManager.openCamera(backCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    _isCameraActive.value = true
                    prefsRepo.setLastDetectedGesture("👁️ Vision Pro Hand & AR Camera Online")
                    createCaptureSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    _isCameraActive.value = false
                    isCameraRunning = false
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    _isCameraActive.value = false
                    isCameraRunning = false
                    Log.e(tag, "Camera error: $error")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to open camera: ${e.message}")
            isCameraRunning = false
            _isCameraActive.value = false
        }
    }

    private fun restartCameraSession() {
        val dev = cameraDevice ?: return
        createCaptureSession(dev)
    }

    private fun createCaptureSession(camera: CameraDevice) {
        try {
            val surfaces = mutableListOf<Surface>()
            glCameraSurface?.let { if (it.isValid) surfaces.add(it) }
            imageReader?.surface?.let { if (it.isValid) surfaces.add(it) }

            if (surfaces.isEmpty()) return

            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                surfaces.forEach { addTarget(it) }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                        } catch (e: Exception) {
                            Log.e(tag, "setRepeatingRequest error: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(tag, "CameraCaptureSession configuration failed")
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Error creating capture session: ${e.message}")
        }
    }

    /**
     * Real-time dual-pipeline hand tracking:
     * 1. Instant 30fps Optical Hand Motion & Centroid Swipe Detector (zero latency for hand swiping left/right/up/down).
     * 2. Periodic Gemini AI Vision (`gemini-3.5-flash`) frame classification for Apple Vision Pro gestures.
     */
    private fun processCameraFrame(image: Image) {
        val settings = prefsRepo.getSettings()
        if (!settings.handTrackingEnabled) return

        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val w = image.width
        val h = image.height

        val currentLuma = ByteArray(w * h)
        var offset = 0
        for (row in 0 until h) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(currentLuma, offset, w)
            offset += w
        }

        val prev = prevLumaFrame
        prevLumaFrame = currentLuma

        val now = SystemClock.uptimeMillis()

        if (prev != null && prev.size == currentLuma.size) {
            var sumX = 0f
            var sumY = 0f
            var totalWeight = 0f
            var activePixels = 0

            // Compute optical motion centroid across frame (subsampled by 2 for speed)
            var y = 4
            while (y < h - 4) {
                var x = 4
                while (x < w - 4) {
                    val idx = y * w + x
                    val diff = abs((currentLuma[idx].toInt() and 0xFF) - (prev[idx].toInt() and 0xFF))
                    if (diff > 24) {
                        val weight = diff.toFloat()
                        // Mirror X so moving hand left/right in front of rear camera matches user's visual left/right
                        val normX = 1.0f - (x.toFloat() / w.toFloat())
                        val normY = y.toFloat() / h.toFloat()
                        sumX += normX * weight
                        sumY += normY * weight
                        totalWeight += weight
                        activePixels++
                    }
                    x += 2
                }
                y += 2
            }

            if (activePixels > 38 && totalWeight > 0f) {
                val rawHandX = (sumX / totalWeight).coerceIn(0.05f, 0.95f)
                val rawHandY = (sumY / totalWeight).coerceIn(0.05f, 0.95f)

                smoothedHandX = smoothedHandX * 0.55f + rawHandX * 0.45f
                smoothedHandY = smoothedHandY * 0.55f + rawHandY * 0.45f

                // Update 3D Stereoscopic VR Cursor to follow hand in real time!
                prefsRepo.setCursorPosition(smoothedHandX, smoothedHandY)
                _liveHandPosition.value = Pair(smoothedHandX, smoothedHandY)

                if (historyStartTime == 0L || (now - historyStartTime) > 520L) {
                    historyStartX = smoothedHandX
                    historyStartY = smoothedHandY
                    historyStartTime = now
                } else {
                    val dt = now - historyStartTime
                    val dx = smoothedHandX - historyStartX
                    val dy = smoothedHandY - historyStartY

                    if (dt >= 90L && (now - lastGestureTriggerTime) > 850L) {
                        if (abs(dx) > 0.24f && abs(dx) > abs(dy) * 1.35f) {
                            lastGestureTriggerTime = now
                            historyStartTime = 0L
                            if (dx < 0f) {
                                executeDetectedGesture(
                                    gesture = "SWIPE_LEFT",
                                    label = "👈 Hand Swipe Left ➔ Slid App Left"
                                )
                            } else {
                                executeDetectedGesture(
                                    gesture = "SWIPE_RIGHT",
                                    label = "👉 Hand Swipe Right ➔ Slid App Right"
                                )
                            }
                        } else if (abs(dy) > 0.24f && abs(dy) > abs(dx) * 1.35f) {
                            lastGestureTriggerTime = now
                            historyStartTime = 0L
                            if (dy < 0f) {
                                executeDetectedGesture(
                                    gesture = "SWIPE_UP",
                                    label = "👆 Hand Swipe Up ➔ Scrolled App Up"
                                )
                            } else {
                                executeDetectedGesture(
                                    gesture = "SWIPE_DOWN",
                                    label = "👇 Hand Swipe Down ➔ Scrolled App Down"
                                )
                            }
                        }
                    }
                }
            }
            prevActivePixelCount = activePixels
        }

        // Trigger Gemini AI Vision hand analysis every ~2.2 seconds when enabled
        if (settings.aiVisionGesturesEnabled &&
            !isAiVisionRequestInFlight &&
            (now - lastAiVisionTimestamp) > 2200L &&
            GeminiHandVisionService.isApiKeyConfigured()
        ) {
            isAiVisionRequestInFlight = true
            lastAiVisionTimestamp = now
            val grayscaleBitmap = lumaToBitmap(currentLuma, w, h)
            scope.launch {
                try {
                    val result = GeminiHandVisionService.analyzeHandGestureFrame(grayscaleBitmap)
                    if (result.gesture != "NONE") {
                        prefsRepo.setCursorPosition(result.handX, result.handY)
                        _liveHandPosition.value = Pair(result.handX, result.handY)
                        if (result.gesture != "POINT_MOVE" &&
                            (SystemClock.uptimeMillis() - lastGestureTriggerTime) > 900L
                        ) {
                            lastGestureTriggerTime = SystemClock.uptimeMillis()
                            executeDetectedGesture(
                                gesture = result.gesture,
                                label = "🤖 AI Vision: ${result.actionLabel}"
                            )
                        } else if (result.gesture == "POINT_MOVE") {
                            prefsRepo.setLastDetectedGesture("🤖 AI Pointing (${(result.handX * 100).toInt()}%, ${(result.handY * 100).toInt()}%)")
                        }
                    }
                } finally {
                    isAiVisionRequestInFlight = false
                }
            }
        }
    }

    private fun lumaToBitmap(luma: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in luma.indices) {
            val v = luma[i].toInt() and 0xFF
            pixels[i] = Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Executes the detected Apple Vision Pro hand gesture on the underlying app
     * via VrAccessibilityService and provides tactile + HUD feedback.
     */
    fun executeDetectedGesture(gesture: String, label: String) {
        prefsRepo.setLastDetectedGesture(label)
        _lastGestureEvent.value = label
        onHapticFeedback(35L)

        val a11y = VrAccessibilityService.instance
        when (gesture.uppercase()) {
            "PINCH_TAP" -> {
                a11y?.dispatchTapAtCursor()
            }
            "SWIPE_LEFT" -> {
                a11y?.dispatchDirectionalSlide("SWIPE_LEFT")
            }
            "SWIPE_RIGHT" -> {
                a11y?.dispatchDirectionalSlide("SWIPE_RIGHT")
            }
            "SWIPE_UP" -> {
                a11y?.dispatchDirectionalSlide("SWIPE_UP")
            }
            "SWIPE_DOWN" -> {
                a11y?.dispatchDirectionalSlide("SWIPE_DOWN")
            }
            "OPEN_PALM" -> {
                onPlayPauseToggle()
            }
            "FIST_BACK" -> {
                a11y?.triggerBackButton()
            }
        }
    }

    fun stopCamera() {
        isCameraRunning = false
        _isCameraActive.value = false
        try {
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
    }

    fun release() {
        stopCamera()
        glCameraSurface?.release()
        glCameraSurface = null
        try {
            cameraThread?.quitSafely()
        } catch (_: Exception) {}
        cameraThread = null
        cameraHandler = null
        scope.cancel()
    }

    companion object {
        private const val ANALYSIS_WIDTH = 240
        private const val ANALYSIS_HEIGHT = 180

        private val _isCameraActive = MutableStateFlow(false)
        val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

        private val _liveHandPosition = MutableStateFlow(Pair(0.5f, 0.5f))
        val liveHandPosition: StateFlow<Pair<Float, Float>> = _liveHandPosition.asStateFlow()

        private val _lastGestureEvent = MutableStateFlow("Vision Pro Hand AI Ready")
        val lastGestureEvent: StateFlow<String> = _lastGestureEvent.asStateFlow()
    }
}
