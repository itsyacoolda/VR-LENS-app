package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VrPreferencesRepository
import com.example.model.VrSettings
import com.example.service.GearVrTouchpadDriver
import com.example.service.VrAccessibilityService
import com.example.service.VrOverlayService
import com.example.service.VrVoiceControlManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

sealed class ScreenDestination {
    data object Home : ScreenDestination()
    data object Settings : ScreenDestination()
    data object Calibration : ScreenDestination()
    data object GearVrGuide : ScreenDestination()
    data object VrBrowser : ScreenDestination()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = VrPreferencesRepository.getInstance(application)

    val settings: StateFlow<VrSettings> = prefsRepo.settingsFlow

    val isOverlayActive: StateFlow<Boolean> = VrOverlayService.isOverlayRunning

    val touchpadDeviceName: StateFlow<String> = GearVrTouchpadDriver.detectedDeviceName

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _overlayPermissionGranted = MutableStateFlow(checkOverlayPermission())
    val overlayPermissionGranted: StateFlow<Boolean> = _overlayPermissionGranted.asStateFlow()

    private val _accessibilityEnabled = MutableStateFlow(
        VrAccessibilityService.isAccessibilityServiceEnabled(application)
    )
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    private val voiceControlManager = VrVoiceControlManager(
        context = application,
        onHapticFeedback = {},
        onClickCenterDot = { pulseCursorClick() },
        onPlayPauseToggle = { togglePause(application) }
    )

    // 2-Second Center Dot Dwell-Click state
    private var dwellAnchorX = 0.5f
    private var dwellAnchorY = 0.5f
    private var dwellStartTime = 0L
    private var hasDwellClickedCurrentSpot = true

    init {
        viewModelScope.launch {
            VrAccessibilityService.isConnected.collect { connected ->
                _accessibilityEnabled.value =
                    connected || VrAccessibilityService.isAccessibilityServiceEnabled(getApplication())
            }
        }

        // Observe settings to sync Voice Control
        viewModelScope.launch {
            prefsRepo.settingsFlow.collect { current ->
                voiceControlManager.updateFromSettings()
                if (current.dwellProgress > 0f) {
                    prefsRepo.setDwellProgress(0f)
                }
            }
        }
    }

    fun navigateTo(destination: ScreenDestination) {
        _currentScreen.value = destination
    }

    fun checkPermissions() {
        _overlayPermissionGranted.value = checkOverlayPermission()
        _accessibilityEnabled.value =
            VrAccessibilityService.isAccessibilityServiceEnabled(getApplication())
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(getApplication())
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun requestAccessibilityPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    fun startVrOverlay(resultCode: Int, data: Intent, context: Context) {
        val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
            action = VrOverlayService.ACTION_START
            putExtra(VrOverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(VrOverlayService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopVrOverlay(context: Context) {
        val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
            action = VrOverlayService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun toggleTouchPassThrough(context: Context) {
        if (isOverlayActive.value) {
            val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
                action = VrOverlayService.ACTION_TOGGLE_TOUCH
            }
            context.startService(serviceIntent)
        } else {
            prefsRepo.setTouchPassThrough(!prefsRepo.getSettings().touchPassThrough)
        }
    }

    fun togglePause(context: Context) {
        if (isOverlayActive.value) {
            val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
                action = VrOverlayService.ACTION_TOGGLE_PAUSE
            }
            context.startService(serviceIntent)
        } else {
            prefsRepo.togglePause()
        }
    }

    fun adjustScale(delta: Float, context: Context) {
        if (isOverlayActive.value) {
            val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
                action = if (delta > 0) VrOverlayService.ACTION_RESIZE_UP else VrOverlayService.ACTION_RESIZE_DOWN
            }
            context.startService(serviceIntent)
        } else {
            prefsRepo.adjustScale(delta)
        }
    }

    fun adjustSplit(delta: Float, context: Context) {
        if (isOverlayActive.value) {
            val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
                action = if (delta > 0) VrOverlayService.ACTION_SPLIT_OUT else VrOverlayService.ACTION_SPLIT_IN
            }
            context.startService(serviceIntent)
        } else {
            prefsRepo.adjustIpd(delta)
        }
    }

    fun toggleTouchPeek(context: Context) {
        if (isOverlayActive.value) {
            val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
                action = VrOverlayService.ACTION_TOGGLE_TOUCH_PEEK
            }
            context.startService(serviceIntent)
        } else {
            prefsRepo.toggleTouchPeek()
        }
    }

    fun moveCursorByDelta(dxNorm: Float, dyNorm: Float) {
        prefsRepo.moveCursorByDelta(dxNorm, dyNorm)
    }

    fun pulseCursorClick() {
        val s = prefsRepo.getSettings()
        prefsRepo.setCursorClicking(true)
        prefsRepo.setLastDetectedGesture(
            "🎯 Trackpad Clicked at (${(s.cursorX * 100).toInt()}%, ${(s.cursorY * 100).toInt()}%)"
        )
        viewModelScope.launch {
            kotlinx.coroutines.delay(220L)
            prefsRepo.setCursorClicking(false)
        }
        val a11y = VrAccessibilityService.instance
        if (a11y != null && isOverlayActive.value) {
            a11y.dispatchTapAtCursor()
        } else {
            MainActivity.dispatchInAppTouchAtNormalized(s.cursorX, s.cursorY)
        }
    }

    fun triggerHandGestureAction(gesture: String, label: String) {
        prefsRepo.setLastDetectedGesture(label)
        val a11y = VrAccessibilityService.instance
        when (gesture.uppercase()) {
            "SWIPE_LEFT" -> {
                prefsRepo.moveCursorByDelta(-0.18f, 0f)
                a11y?.dispatchDirectionalSlide("SWIPE_LEFT")
            }
            "SWIPE_RIGHT" -> {
                prefsRepo.moveCursorByDelta(+0.18f, 0f)
                a11y?.dispatchDirectionalSlide("SWIPE_RIGHT")
            }
            "SWIPE_UP" -> {
                prefsRepo.moveCursorByDelta(0f, -0.18f)
                a11y?.dispatchDirectionalSlide("SWIPE_UP")
            }
            "SWIPE_DOWN" -> {
                prefsRepo.moveCursorByDelta(0f, +0.18f)
                a11y?.dispatchDirectionalSlide("SWIPE_DOWN")
            }
            "PINCH_TAP" -> {
                pulseCursorClick()
            }
            "OPEN_PALM" -> {
                togglePause(getApplication())
            }
            "FIST_BACK" -> {
                VrAccessibilityService.instance?.triggerBackButton()
            }
            "DWELL_FOCUS_DEMO" -> {
                // Move center dot slightly to a new target so the 2-second focus timer arms and auto-clicks!
                val current = prefsRepo.getSettings()
                val nextX = if (current.cursorX < 0.6f) current.cursorX + 0.08f else current.cursorX - 0.08f
                val nextY = if (current.cursorY < 0.6f) current.cursorY + 0.06f else current.cursorY - 0.06f
                prefsRepo.setCursorPosition(nextX, nextY)
            }
        }
    }

    fun triggerGearVrSideButton(button: String) {
        val a11y = VrAccessibilityService.instance
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = prefsRepo.getSettings().volumeButtonsMode

        when (button.uppercase()) {
            "VOLUME_UP" -> {
                if (a11y != null) {
                    a11y.handleVolumeUpButton(mode)
                } else {
                    when (mode) {
                        0 -> {
                            prefsRepo.setLastHardwareButtonEvent("🔊 Gear VR Vol+ ➔ Clicked at Center Dot!")
                            pulseCursorClick()
                        }
                        1 -> {
                            prefsRepo.adjustScale(+0.04f)
                            prefsRepo.setLastHardwareButtonEvent("🔊 Gear VR Vol+ ➔ Spatial Zoom In (+4%)")
                        }
                        else -> {
                            audioManager?.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_RAISE,
                                AudioManager.FLAG_SHOW_UI
                            )
                            prefsRepo.setLastHardwareButtonEvent("🔊 Gear VR Vol+ ➔ Media Volume Up")
                        }
                    }
                }
            }
            "VOLUME_DOWN" -> {
                if (a11y != null) {
                    a11y.handleVolumeDownButton(mode)
                } else {
                    when (mode) {
                        0 -> {
                            prefsRepo.setLastHardwareButtonEvent("🔉 Gear VR Vol- ➔ Scrolled App Down")
                            prefsRepo.moveCursorByDelta(0f, +0.15f)
                        }
                        1 -> {
                            prefsRepo.adjustScale(-0.04f)
                            prefsRepo.setLastHardwareButtonEvent("🔉 Gear VR Vol- ➔ Spatial Zoom Out (-4%)")
                        }
                        else -> {
                            audioManager?.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_LOWER,
                                AudioManager.FLAG_SHOW_UI
                            )
                            prefsRepo.setLastHardwareButtonEvent("🔉 Gear VR Vol- ➔ Media Volume Down")
                        }
                    }
                }
            }
            "HOME" -> {
                if (a11y != null) {
                    a11y.triggerHomeButton()
                } else {
                    prefsRepo.setLastHardwareButtonEvent("🏠 Gear VR Home Button ➔ Returned Home")
                }
            }
            "BACK" -> {
                if (a11y != null) {
                    a11y.triggerBackButton()
                } else {
                    prefsRepo.setLastHardwareButtonEvent("🔙 Gear VR Back Button ➔ Navigated Back")
                }
            }
        }
    }

    fun triggerVoiceCommand(phrase: String) {
        voiceControlManager.executeVoiceCommand(phrase)
    }

    override fun onCleared() {
        super.onCleared()
        voiceControlManager.release()
    }

    fun updateSettings(newSettings: VrSettings) {
        prefsRepo.updateSettings(newSettings)
    }

    fun applyPreset(presetName: String) {
        prefsRepo.applyPreset(presetName)
    }

    fun setTouchpadMode(mode: Int) {
        prefsRepo.setTouchpadMode(mode)
    }

    fun cycleTouchpadMode() {
        prefsRepo.cycleTouchpadMode()
    }

    fun setSimpleUiMode(isSimple: Boolean) {
        prefsRepo.setSimpleUiMode(isSimple)
    }

    fun recenterCursor() {
        prefsRepo.recenterCursor()
    }

    fun setCompletedOnboarding(completed: Boolean) {
        prefsRepo.setCompletedOnboarding(completed)
    }

    fun setOnboardingStep(step: Int) {
        prefsRepo.setOnboardingStep(step)
    }

    fun restartOnboarding() {
        prefsRepo.restartOnboarding()
    }

    fun resumeDashboard() {
        prefsRepo.resumeDashboard()
    }

    fun saveConfiguration(profileName: String = "My Calibrated VR Setup") {
        prefsRepo.saveConfigurationSnapshot(profileName)
    }

    fun restoreSavedConfiguration(): Boolean {
        return prefsRepo.restoreConfigurationSnapshot()
    }

    fun setHeadTrackingEnabled(enabled: Boolean) {
        prefsRepo.setHeadTrackingEnabled(enabled)
    }

    fun setHeadTrackingSensitivity(sensitivity: Float) {
        prefsRepo.setHeadTrackingSensitivity(sensitivity)
    }

    fun setHeadTrackingSmoothing(smoothing: Float) {
        prefsRepo.setHeadTrackingSmoothing(smoothing)
    }

    fun setHeadTrackingMode(mode: Int) {
        prefsRepo.setHeadTrackingMode(mode)
    }

    fun setHeadTrackingInvertPitch(invert: Boolean) {
        prefsRepo.setHeadTrackingInvertPitch(invert)
    }

    fun setHeadTrackingInvertYaw(invert: Boolean) {
        prefsRepo.setHeadTrackingInvertYaw(invert)
    }

    fun recenterHeadTracking() {
        prefsRepo.recenterCursor()
        VrOverlayService.instance?.recenterHeadPose()
    }
}
