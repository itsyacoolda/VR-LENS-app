package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.data.VrPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trusted Accessibility Service that:
 * 1. Provides a TYPE_ACCESSIBILITY_OVERLAY window context so Android 12+ (One UI / S23+ / stock phones)
 *    NEVER blocks touches with "Untrusted Touch" restrictions — zero Developer Options required.
 * 2. Dispatches real, OS-trusted taps, swipes, scrolls, and Back navigation from the Gear VR Touchpad
 *    and 3D Stereoscopic VR Cursor to any underlying app.
 */
class VrAccessibilityService : AccessibilityService() {

    private val tag = "VrAccessibilityService"
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefsRepo: VrPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        prefsRepo = VrPreferencesRepository.getInstance(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.i(tag, "VrAccessibilityService connected - Trusted Overlay & Gear VR Touchpad active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No window content inspection needed; privacy-preserving gesture & overlay host only.
    }

    override fun onInterrupt() {
        Log.w(tag, "VrAccessibilityService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!VrOverlayService.isOverlayRunning.value) {
            return super.onKeyEvent(event)
        }
        val settings = prefsRepo.getSettings()

        // Handle Gear VR Side Buttons (Volume Up, Volume Down, Home, Back)
        if (settings.gearVrSideButtonsEnabled) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        return handleVolumeUpButton(settings.volumeButtonsMode)
                    }
                    return settings.volumeButtonsMode != 2
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        return handleVolumeDownButton(settings.volumeButtonsMode)
                    }
                    return settings.volumeButtonsMode != 2
                }
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_META_LEFT -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        triggerHomeButton()
                    }
                    return true
                }
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        triggerBackButton()
                    }
                    return true
                }
            }
        }

        if (!settings.gearVrTouchpadEnabled) {
            return super.onKeyEvent(event)
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    dispatchTapAtCursor()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (settings.touchpadSwipeScrolls) {
                        dispatchScrollAtCursor(0f, -0.25f)
                    } else {
                        prefsRepo.moveCursorByDelta(0f, -0.06f)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (settings.touchpadSwipeScrolls) {
                        dispatchScrollAtCursor(0f, 0.25f)
                    } else {
                        prefsRepo.moveCursorByDelta(0f, 0.06f)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (settings.touchpadSwipeScrolls) {
                        dispatchScrollAtCursor(-0.25f, 0f)
                    } else {
                        prefsRepo.moveCursorByDelta(-0.06f, 0f)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (settings.touchpadSwipeScrolls) {
                        dispatchScrollAtCursor(0.25f, 0f)
                    } else {
                        prefsRepo.moveCursorByDelta(0.06f, 0f)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    fun getTrustedWindowManager(): WindowManager {
        return getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun getScreenDimensions(): Point {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(size)
        return size
    }

    /**
     * Dispatches a trusted tap gesture at the current 3D VR Cursor coordinates (cursorX, cursorY).
     */
    fun dispatchTapAtCursor(onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val current = prefsRepo.getSettings()
        val screen = getScreenDimensions()
        val targetX = (current.cursorX * screen.x).coerceIn(10f, (screen.x - 10).toFloat())
        val targetY = (current.cursorY * screen.y).coerceIn(10f, (screen.y - 10).toFloat())

        prefsRepo.setCursorClicking(true)
        mainHandler.postDelayed({ prefsRepo.setCursorClicking(false) }, 160L)

        dispatchTapAtPixel(targetX, targetY, onComplete)
    }

    /**
     * Dispatches a trusted tap at exact screen pixel coordinates.
     */
    fun dispatchTapAtPixel(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            VrOverlayService.instance?.temporarilyEnsurePassThroughForGesture()
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 55L)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    onComplete?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    onComplete?.invoke()
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to dispatch tap: ${e.message}")
            onComplete?.invoke()
        }
    }

    /**
     * Dispatches a smooth scroll/swipe gesture in the underlying app centered at the 3D VR Cursor.
     */
    fun dispatchScrollAtCursor(dxNorm: Float, dyNorm: Float, onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val current = prefsRepo.getSettings()
        val screen = getScreenDimensions()
        val startX = (current.cursorX * screen.x).coerceIn(50f, (screen.x - 50).toFloat())
        val startY = (current.cursorY * screen.y).coerceIn(50f, (screen.y - 50).toFloat())
        val endX = (startX - dxNorm * screen.x * 0.6f).coerceIn(20f, (screen.x - 20).toFloat())
        val endY = (startY - dyNorm * screen.y * 0.6f).coerceIn(20f, (screen.y - 20).toFloat())

        dispatchSwipeAtPixels(startX, startY, endX, endY, 200L, onComplete)
    }

    fun dispatchSwipeAtPixels(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 200L,
        onComplete: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            VrOverlayService.instance?.temporarilyEnsurePassThroughForGesture()
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(50L, 600L))
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    onComplete?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    onComplete?.invoke()
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to dispatch swipe: ${e.message}")
            onComplete?.invoke()
        }
    }

    fun triggerBackButton() {
        prefsRepo.setLastHardwareButtonEvent("🔙 Gear VR Back Button ➔ Navigated Back")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun triggerHomeButton() {
        prefsRepo.setLastHardwareButtonEvent("🏠 Gear VR Home Button ➔ Returned Home")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun handleVolumeUpButton(mode: Int = prefsRepo.getSettings().volumeButtonsMode): Boolean {
        return when (mode) {
            0 -> {
                prefsRepo.setLastHardwareButtonEvent("🔊 Gear VR Vol+ ➔ Clicked at Center Dot!")
                dispatchTapAtCursor()
                true
            }
            1 -> {
                prefsRepo.adjustScale(+0.04f)
                prefsRepo.setLastHardwareButtonEvent("🔊 Gear VR Vol+ ➔ Spatial Zoom In (+4%)")
                true
            }
            else -> {
                prefsRepo.setLastHardwareButtonEvent("🔊 Gear VR Vol+ ➔ Media Volume Up")
                false
            }
        }
    }

    fun handleVolumeDownButton(mode: Int = prefsRepo.getSettings().volumeButtonsMode): Boolean {
        return when (mode) {
            0 -> {
                prefsRepo.setLastHardwareButtonEvent("🔉 Gear VR Vol- ➔ Scrolled App Down")
                dispatchDirectionalSlide("SWIPE_DOWN")
                true
            }
            1 -> {
                prefsRepo.adjustScale(-0.04f)
                prefsRepo.setLastHardwareButtonEvent("🔉 Gear VR Vol- ➔ Spatial Zoom Out (-4%)")
                true
            }
            else -> {
                prefsRepo.setLastHardwareButtonEvent("🔉 Gear VR Vol- ➔ Media Volume Down")
                false
            }
        }
    }

    /**
     * Dispatches a full-screen directional slide in the underlying app when a Vision Pro
     * camera hand swipe gesture is detected (e.g., SWIPE_LEFT -> Slide Left).
     */
    fun dispatchDirectionalSlide(direction: String, onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val screen = getScreenDimensions()
        val w = screen.x.toFloat()
        val h = screen.y.toFloat()
        val centerX = w * 0.5f
        val centerY = h * 0.5f

        when (direction.uppercase()) {
            "LEFT", "SWIPE_LEFT" -> {
                // Hand swiping left -> slide left across the underlying app
                dispatchSwipeAtPixels(
                    startX = w * 0.80f,
                    startY = centerY,
                    endX = w * 0.20f,
                    endY = centerY,
                    durationMs = 220L,
                    onComplete = onComplete
                )
            }
            "RIGHT", "SWIPE_RIGHT" -> {
                // Hand swiping right -> slide right across the underlying app
                dispatchSwipeAtPixels(
                    startX = w * 0.20f,
                    startY = centerY,
                    endX = w * 0.80f,
                    endY = centerY,
                    durationMs = 220L,
                    onComplete = onComplete
                )
            }
            "UP", "SWIPE_UP" -> {
                // Hand swiping up -> slide/scroll up in the underlying app
                dispatchSwipeAtPixels(
                    startX = centerX,
                    startY = h * 0.78f,
                    endX = centerX,
                    endY = h * 0.22f,
                    durationMs = 240L,
                    onComplete = onComplete
                )
            }
            "DOWN", "SWIPE_DOWN" -> {
                // Hand swiping down -> slide/scroll down in the underlying app
                dispatchSwipeAtPixels(
                    startX = centerX,
                    startY = h * 0.22f,
                    endX = centerX,
                    endY = h * 0.78f,
                    durationMs = 240L,
                    onComplete = onComplete
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
    }

    companion object {
        @Volatile
        var instance: VrAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (_isConnected.value && instance != null) return true
            val expectedComponentName = ComponentName(context, VrAccessibilityService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServicesSetting.split(':').any {
                ComponentName.unflattenFromString(it) == expectedComponentName
            }
        }
    }
}
