package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.VrSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VrPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("vr_sbs_preferences", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<VrSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): VrSettings = _settingsFlow.value

    fun updateSettings(newSettings: VrSettings) {
        prefs.edit()
            .putFloat(KEY_IPD_OFFSET, newSettings.ipdOffset)
            .putFloat(KEY_SCALE, newSettings.scale)
            .putFloat(KEY_Y_OFFSET, newSettings.yOffset)
            .putFloat(KEY_ASPECT, newSettings.aspectCorrection)
            .putFloat(KEY_FOCAL_LENGTH, newSettings.focalLength)
            .putFloat(KEY_BARREL_K1, newSettings.barrelK1)
            .putFloat(KEY_BARREL_K2, newSettings.barrelK2)
            .putFloat(KEY_BARREL_K3, newSettings.barrelK3)
            .putFloat(KEY_SPLIT_GAP, newSettings.splitGapWidth)
            .putInt(KEY_MASK_TYPE, newSettings.maskType)
            .putFloat(KEY_BRIGHTNESS, newSettings.brightness)
            .putBoolean(KEY_TOUCH_PASS_THROUGH, newSettings.touchPassThrough)
            .putFloat(KEY_PARALLAX_3D, newSettings.parallax3D)
            .putBoolean(KEY_SHOW_GRID, newSettings.showCalibrationGrid)
            .putBoolean(KEY_IS_PAUSED, newSettings.isPaused)
            .putBoolean(KEY_CHROMATIC, newSettings.chromaticAberration)
            .putBoolean(KEY_GEAR_VR_TOUCHPAD, newSettings.gearVrTouchpadEnabled)
            .putFloat(KEY_TOUCHPAD_SENSITIVITY, newSettings.touchpadSensitivity)
            .putBoolean(KEY_TOUCHPAD_SWIPE_SCROLLS, newSettings.touchpadSwipeScrolls)
            .putInt(KEY_TOUCHPAD_MODE, newSettings.touchpadMode)
            .putBoolean(KEY_IS_SIMPLE_UI_MODE, newSettings.isSimpleUiMode)
            .putBoolean(KEY_HAS_COMPLETED_ONBOARDING, newSettings.hasCompletedOnboarding)
            .putBoolean(KEY_HAND_TRACKING, newSettings.handTrackingEnabled)
            .putBoolean(KEY_AI_VISION_GESTURES, newSettings.aiVisionGesturesEnabled)
            .putBoolean(KEY_AR_PASSTHROUGH, newSettings.arPassthroughEnabled)
            .putFloat(KEY_AR_APP_OPACITY, newSettings.arAppOpacity)
            .putBoolean(KEY_SHOW_CAMERA_PIP, newSettings.showCameraRadarPip)
            .putBoolean(KEY_ALL_GESTURES_SIMULTANEOUS, newSettings.allGesturesSimultaneous)
            .putBoolean(KEY_DWELL_CLICK, newSettings.dwellClickEnabled)
            .putLong(KEY_DWELL_TIME_MS, newSettings.dwellTimeMs)
            .putBoolean(KEY_SIDE_BUTTONS_ENABLED, newSettings.gearVrSideButtonsEnabled)
            .putInt(KEY_VOLUME_BUTTONS_MODE, newSettings.volumeButtonsMode)
            .putBoolean(KEY_VOICE_CONTROL_ENABLED, newSettings.voiceControlEnabled)
            .putBoolean(KEY_CINEMA_SCREEN_ENABLED, newSettings.cinemaScreenEnabled)
            .putFloat(KEY_CINEMA_CURVATURE, newSettings.cinemaCurvature)
            .putBoolean(KEY_CINEMA_AMBIENT_GLOW, newSettings.cinemaAmbientGlow)
            .putString(KEY_PRESET_NAME, newSettings.presetName)
            .putInt(KEY_ONBOARDING_STEP, newSettings.onboardingStep)
            .putLong(KEY_LAST_SAVED_TIMESTAMP, newSettings.lastSavedTimestamp)
            .putString(KEY_SAVED_PROFILE_NAME, newSettings.savedProfileName)
            .putBoolean(KEY_HAS_BACKUP_PROFILE, newSettings.hasBackupProfile)
            .putBoolean(KEY_HEAD_TRACKING_ENABLED, newSettings.headTrackingEnabled)
            .putFloat(KEY_HEAD_TRACKING_SENSITIVITY, newSettings.headTrackingSensitivity)
            .putFloat(KEY_HEAD_TRACKING_SMOOTHING, newSettings.headTrackingSmoothing)
            .putInt(KEY_HEAD_TRACKING_MODE, newSettings.headTrackingMode)
            .putBoolean(KEY_HEAD_TRACKING_INVERT_PITCH, newSettings.headTrackingInvertPitch)
            .putBoolean(KEY_HEAD_TRACKING_INVERT_YAW, newSettings.headTrackingInvertYaw)
            .apply()

        _settingsFlow.value = newSettings
    }

    fun setDwellProgress(progress: Float) {
        val current = _settingsFlow.value
        val clamped = progress.coerceIn(0f, 1f)
        if (kotlin.math.abs(current.dwellProgress - clamped) >= 0.01f || clamped == 0f || clamped == 1f) {
            _settingsFlow.value = current.copy(dwellProgress = clamped)
        }
    }

    fun setLastHardwareButtonEvent(eventText: String) {
        val current = _settingsFlow.value
        if (current.lastHardwareButtonEvent != eventText) {
            _settingsFlow.value = current.copy(lastHardwareButtonEvent = eventText)
        }
    }

    fun setVoiceStatus(isListening: Boolean? = null, commandText: String? = null) {
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            isVoiceListening = isListening ?: current.isVoiceListening,
            lastVoiceCommand = commandText ?: current.lastVoiceCommand
        )
    }

    fun setHandTrackingEnabled(enabled: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(handTrackingEnabled = enabled, showCameraRadarPip = false))
    }

    fun setArPassthroughEnabled(enabled: Boolean) {
        val current = _settingsFlow.value
        updateSettings(
            current.copy(
                arPassthroughEnabled = enabled
            )
        )
    }

    fun setLastDetectedGesture(gestureText: String) {
        val current = _settingsFlow.value
        if (current.lastDetectedGesture != gestureText) {
            _settingsFlow.value = current.copy(lastDetectedGesture = gestureText)
        }
    }

    fun moveCursorByDelta(dxNorm: Float, dyNorm: Float) {
        val current = _settingsFlow.value
        val sens = current.touchpadSensitivity
        val newX = (current.cursorX + dxNorm * sens).coerceIn(0.05f, 0.95f)
        val newY = (current.cursorY + dyNorm * sens).coerceIn(0.05f, 0.95f)
        _settingsFlow.value = current.copy(cursorX = newX, cursorY = newY)
    }

    fun setCursorPosition(xNorm: Float, yNorm: Float) {
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            cursorX = xNorm.coerceIn(0.05f, 0.95f),
            cursorY = yNorm.coerceIn(0.05f, 0.95f)
        )
    }

    fun setCursorClicking(clicking: Boolean) {
        val current = _settingsFlow.value
        if (current.isCursorClicking != clicking) {
            _settingsFlow.value = current.copy(isCursorClicking = clicking)
        }
    }

    fun toggleGearVrTouchpad() {
        val current = _settingsFlow.value
        updateSettings(current.copy(gearVrTouchpadEnabled = !current.gearVrTouchpadEnabled))
    }

    fun setTouchpadMode(mode: Int) {
        val current = _settingsFlow.value
        val isScroll = (mode == VrSettings.TOUCHPAD_MODE_SCROLL)
        val notice = when (mode) {
            VrSettings.TOUCHPAD_MODE_POINTER -> "🎯 Aim & Click Mode Active"
            VrSettings.TOUCHPAD_MODE_SCROLL -> "📜 Fluid Kinetic Scroll Mode Active"
            VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> "🔍 Headset Zoom & Eye Comfort Mode"
            else -> "🎯 Trackpad Active"
        }
        updateSettings(
            current.copy(
                touchpadMode = mode,
                touchpadSwipeScrolls = isScroll,
                trackpadHudNotice = notice,
                lastDetectedGesture = notice
            )
        )
    }

    fun cycleTouchpadMode() {
        val current = _settingsFlow.value
        val nextMode = (current.touchpadMode + 1) % 3
        setTouchpadMode(nextMode)
    }

    fun setSimpleUiMode(isSimple: Boolean) {
        val current = _settingsFlow.value
        if (current.isSimpleUiMode != isSimple) {
            updateSettings(current.copy(isSimpleUiMode = isSimple))
        }
    }

    fun setCompletedOnboarding(completed: Boolean) {
        val current = _settingsFlow.value
        updateSettings(
            current.copy(
                hasCompletedOnboarding = completed,
                onboardingStep = if (completed) 4 else current.onboardingStep,
                isSimpleUiMode = true
            )
        )
    }

    fun setOnboardingStep(step: Int) {
        val current = _settingsFlow.value
        val clamped = step.coerceIn(0, 4)
        prefs.edit().putInt(KEY_ONBOARDING_STEP, clamped).apply()
        _settingsFlow.value = current.copy(onboardingStep = clamped)
    }

    fun restartOnboarding() {
        val current = _settingsFlow.value
        updateSettings(
            current.copy(
                hasCompletedOnboarding = false,
                onboardingStep = 0,
                isSimpleUiMode = true
            )
        )
    }

    fun resumeDashboard() {
        val current = _settingsFlow.value
        updateSettings(
            current.copy(
                hasCompletedOnboarding = true,
                isSimpleUiMode = true
            )
        )
    }

    fun saveConfigurationSnapshot(profileName: String = "My Calibrated VR Setup"): VrSettings {
        val current = _settingsFlow.value
        val now = System.currentTimeMillis()
        prefs.edit()
            .putFloat(KEY_BACKUP_IPD, current.ipdOffset)
            .putFloat(KEY_BACKUP_SCALE, current.scale)
            .putFloat(KEY_BACKUP_ASPECT, current.aspectCorrection)
            .putFloat(KEY_BACKUP_Y_OFFSET, current.yOffset)
            .putFloat(KEY_BACKUP_CURVATURE, current.cinemaCurvature)
            .putBoolean(KEY_BACKUP_CINEMA_ENABLED, current.cinemaScreenEnabled)
            .putBoolean(KEY_BACKUP_AMBIENT_GLOW, current.cinemaAmbientGlow)
            .putString(KEY_BACKUP_PRESET_NAME, current.presetName)
            .putFloat(KEY_BACKUP_TOUCHPAD_SENSITIVITY, current.touchpadSensitivity)
            .putInt(KEY_BACKUP_TOUCHPAD_MODE, current.touchpadMode)
            .putString(KEY_BACKUP_PROFILE_TITLE, profileName)
            .putLong(KEY_BACKUP_TIMESTAMP, now)
            .putBoolean(KEY_HAS_BACKUP_PROFILE, true)
            .apply()

        val updated = current.copy(
            lastSavedTimestamp = now,
            savedProfileName = profileName,
            hasBackupProfile = true,
            hasCompletedOnboarding = true
        )
        updateSettings(updated)
        return updated
    }

    fun restoreConfigurationSnapshot(): Boolean {
        val hasBackup = prefs.getBoolean(KEY_HAS_BACKUP_PROFILE, false)
        if (!hasBackup) return false
        val current = _settingsFlow.value
        val restored = current.copy(
            ipdOffset = prefs.getFloat(KEY_BACKUP_IPD, current.ipdOffset),
            scale = prefs.getFloat(KEY_BACKUP_SCALE, current.scale),
            aspectCorrection = prefs.getFloat(KEY_BACKUP_ASPECT, current.aspectCorrection),
            yOffset = prefs.getFloat(KEY_BACKUP_Y_OFFSET, current.yOffset),
            cinemaCurvature = prefs.getFloat(KEY_BACKUP_CURVATURE, current.cinemaCurvature),
            cinemaScreenEnabled = prefs.getBoolean(KEY_BACKUP_CINEMA_ENABLED, current.cinemaScreenEnabled),
            cinemaAmbientGlow = prefs.getBoolean(KEY_BACKUP_AMBIENT_GLOW, current.cinemaAmbientGlow),
            presetName = prefs.getString(KEY_BACKUP_PRESET_NAME, current.presetName) ?: current.presetName,
            touchpadSensitivity = prefs.getFloat(KEY_BACKUP_TOUCHPAD_SENSITIVITY, current.touchpadSensitivity),
            touchpadMode = prefs.getInt(KEY_BACKUP_TOUCHPAD_MODE, current.touchpadMode),
            savedProfileName = prefs.getString(KEY_BACKUP_PROFILE_TITLE, "My Calibrated VR Setup") ?: "My Calibrated VR Setup",
            hasBackupProfile = true,
            hasCompletedOnboarding = true,
            isSimpleUiMode = true,
            lastSavedTimestamp = prefs.getLong(KEY_BACKUP_TIMESTAMP, System.currentTimeMillis())
        )
        updateSettings(restored)
        return true
    }

    fun recenterCursor() {
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            cursorX = 0.5f,
            cursorY = 0.5f,
            headYaw = 0f,
            headPitch = 0f,
            headRoll = 0f,
            trackpadHudNotice = "⚡ Recenter: 3D Reticle & Head View Centered",
            lastDetectedGesture = "⚡ View & 3D Reticle Re-centered to Center"
        )
    }

    fun setHeadTrackingEnabled(enabled: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(headTrackingEnabled = enabled))
    }

    fun setHeadTrackingSensitivity(sensitivity: Float) {
        val current = _settingsFlow.value
        val clamped = sensitivity.coerceIn(0.2f, 3.0f)
        updateSettings(current.copy(headTrackingSensitivity = clamped))
    }

    fun setHeadTrackingSmoothing(smoothing: Float) {
        val current = _settingsFlow.value
        val clamped = smoothing.coerceIn(0.05f, 0.65f)
        updateSettings(current.copy(headTrackingSmoothing = clamped))
    }

    fun setHeadTrackingMode(mode: Int) {
        val current = _settingsFlow.value
        updateSettings(current.copy(headTrackingMode = mode))
    }

    fun setHeadTrackingInvertPitch(invert: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(headTrackingInvertPitch = invert))
    }

    fun setHeadTrackingInvertYaw(invert: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(headTrackingInvertYaw = invert))
    }

    fun updateHeadOrientation(yaw: Float, pitch: Float, roll: Float, sensorName: String) {
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            headYaw = yaw,
            headPitch = pitch,
            headRoll = roll,
            headTrackingSensorName = sensorName
        )
    }

    fun setTrackpadHudNotice(notice: String) {
        val current = _settingsFlow.value
        if (current.trackpadHudNotice != notice) {
            _settingsFlow.value = current.copy(trackpadHudNotice = notice)
        }
    }

    fun togglePause() {
        val current = _settingsFlow.value
        updateSettings(current.copy(isPaused = !current.isPaused))
    }

    fun toggleTouchPeek() {
        val current = _settingsFlow.value
        updateSettings(current.copy(isTouchPeekMode = !current.isTouchPeekMode))
    }

    fun setTouchPeek(enabled: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(isTouchPeekMode = enabled))
    }

    fun adjustScale(delta: Float) {
        val current = _settingsFlow.value
        val newScale = (current.scale + delta).coerceIn(0.50f, 1.30f)
        updateSettings(current.copy(scale = newScale, presetName = VrSettings.PRESET_CUSTOM))
    }

    fun adjustIpd(delta: Float) {
        val current = _settingsFlow.value
        val newIpd = (current.ipdOffset + delta).coerceIn(-0.15f, 0.15f)
        updateSettings(current.copy(ipdOffset = newIpd, presetName = VrSettings.PRESET_CUSTOM))
    }

    fun adjustSplitGap(delta: Float) {
        val current = _settingsFlow.value
        val newGap = (current.splitGapWidth + delta).coerceIn(0.0f, 0.12f)
        updateSettings(current.copy(splitGapWidth = newGap, presetName = VrSettings.PRESET_CUSTOM))
    }

    fun setTouchPassThrough(enabled: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(touchPassThrough = enabled))
    }

    fun setCinemaScreenEnabled(enabled: Boolean) {
        val current = _settingsFlow.value
        val curvature = if (enabled && current.cinemaCurvature < 0.05f) 0.32f else current.cinemaCurvature
        updateSettings(
            current.copy(
                cinemaScreenEnabled = enabled,
                cinemaCurvature = curvature
            )
        )
    }

    fun setCinemaCurvature(curvature: Float) {
        val current = _settingsFlow.value
        updateSettings(
            current.copy(
                cinemaScreenEnabled = curvature > 0.01f,
                cinemaCurvature = curvature.coerceIn(0.0f, 0.80f),
                presetName = VrSettings.PRESET_CUSTOM
            )
        )
    }

    fun applyPreset(presetName: String) {
        val current = _settingsFlow.value
        val preset = VrSettings.getPreset(presetName).copy(
            gearVrTouchpadEnabled = current.gearVrTouchpadEnabled,
            touchpadSensitivity = current.touchpadSensitivity,
            touchpadSwipeScrolls = current.touchpadSwipeScrolls,
            touchpadMode = current.touchpadMode,
            isSimpleUiMode = current.isSimpleUiMode,
            hasCompletedOnboarding = current.hasCompletedOnboarding,
            onboardingStep = current.onboardingStep,
            dwellClickEnabled = current.dwellClickEnabled,
            gearVrSideButtonsEnabled = current.gearVrSideButtonsEnabled,
            volumeButtonsMode = current.volumeButtonsMode
        )
        updateSettings(preset)
    }

    private fun loadSettings(): VrSettings {
        val migratedWideV2 = prefs.getBoolean(KEY_WIDE_TRACKPAD_MIGRATED_V2, false)
        if (!migratedWideV2) {
            prefs.edit()
                .putBoolean(KEY_WIDE_TRACKPAD_MIGRATED_V2, true)
                .putFloat(KEY_SCALE, 0.95f)
                .putFloat(KEY_ASPECT, 1.15f)
                .putInt(KEY_MASK_TYPE, 0)
                .putBoolean(KEY_SHOW_CAMERA_PIP, false)
                .putBoolean(KEY_DWELL_CLICK, false)
                .apply()
        }
        return VrSettings(
            ipdOffset = prefs.getFloat(KEY_IPD_OFFSET, 0.01f),
            scale = prefs.getFloat(KEY_SCALE, 0.95f).coerceAtLeast(0.88f),
            yOffset = prefs.getFloat(KEY_Y_OFFSET, 0.0f),
            aspectCorrection = prefs.getFloat(KEY_ASPECT, 1.15f).coerceAtLeast(1.10f),
            focalLength = prefs.getFloat(KEY_FOCAL_LENGTH, 42.0f),
            barrelK1 = prefs.getFloat(KEY_BARREL_K1, 0.08f),
            barrelK2 = prefs.getFloat(KEY_BARREL_K2, 0.02f),
            barrelK3 = prefs.getFloat(KEY_BARREL_K3, 0.005f),
            splitGapWidth = prefs.getFloat(KEY_SPLIT_GAP, 0.01f),
            maskType = prefs.getInt(KEY_MASK_TYPE, 0),
            brightness = prefs.getFloat(KEY_BRIGHTNESS, 1.0f),
            touchPassThrough = prefs.getBoolean(KEY_TOUCH_PASS_THROUGH, true),
            parallax3D = prefs.getFloat(KEY_PARALLAX_3D, 0.006f),
            showCalibrationGrid = prefs.getBoolean(KEY_SHOW_GRID, false),
            isPaused = prefs.getBoolean(KEY_IS_PAUSED, false),
            chromaticAberration = prefs.getBoolean(KEY_CHROMATIC, false),
            gearVrTouchpadEnabled = prefs.getBoolean(KEY_GEAR_VR_TOUCHPAD, true),
            touchpadSensitivity = prefs.getFloat(KEY_TOUCHPAD_SENSITIVITY, 1.4f),
            touchpadSwipeScrolls = prefs.getBoolean(KEY_TOUCHPAD_SWIPE_SCROLLS, false),
            touchpadMode = prefs.getInt(KEY_TOUCHPAD_MODE, VrSettings.TOUCHPAD_MODE_POINTER),
            isSimpleUiMode = prefs.getBoolean(KEY_IS_SIMPLE_UI_MODE, true),
            hasCompletedOnboarding = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false),
            handTrackingEnabled = prefs.getBoolean(KEY_HAND_TRACKING, false),
            aiVisionGesturesEnabled = prefs.getBoolean(KEY_AI_VISION_GESTURES, true),
            arPassthroughEnabled = prefs.getBoolean(KEY_AR_PASSTHROUGH, false),
            arAppOpacity = prefs.getFloat(KEY_AR_APP_OPACITY, 0.90f),
            showCameraRadarPip = false,
            allGesturesSimultaneous = prefs.getBoolean(KEY_ALL_GESTURES_SIMULTANEOUS, true),
            dwellClickEnabled = prefs.getBoolean(KEY_DWELL_CLICK, false),
            dwellTimeMs = prefs.getLong(KEY_DWELL_TIME_MS, 2000L),
            gearVrSideButtonsEnabled = prefs.getBoolean(KEY_SIDE_BUTTONS_ENABLED, true),
            volumeButtonsMode = prefs.getInt(KEY_VOLUME_BUTTONS_MODE, 0),
            voiceControlEnabled = prefs.getBoolean(KEY_VOICE_CONTROL_ENABLED, false),
            cinemaScreenEnabled = prefs.getBoolean(KEY_CINEMA_SCREEN_ENABLED, true),
            cinemaCurvature = prefs.getFloat(KEY_CINEMA_CURVATURE, 0.32f),
            cinemaAmbientGlow = prefs.getBoolean(KEY_CINEMA_AMBIENT_GLOW, true),
            presetName = prefs.getString(KEY_PRESET_NAME, VrSettings.PRESET_GEAR_VR_S23) ?: VrSettings.PRESET_GEAR_VR_S23,
            onboardingStep = prefs.getInt(KEY_ONBOARDING_STEP, 0),
            lastSavedTimestamp = prefs.getLong(KEY_LAST_SAVED_TIMESTAMP, 0L),
            savedProfileName = prefs.getString(KEY_SAVED_PROFILE_NAME, "") ?: "",
            hasBackupProfile = prefs.getBoolean(KEY_HAS_BACKUP_PROFILE, false),
            headTrackingEnabled = prefs.getBoolean(KEY_HEAD_TRACKING_ENABLED, true),
            headTrackingSensitivity = prefs.getFloat(KEY_HEAD_TRACKING_SENSITIVITY, 1.0f),
            headTrackingSmoothing = prefs.getFloat(KEY_HEAD_TRACKING_SMOOTHING, 0.22f),
            headTrackingMode = prefs.getInt(KEY_HEAD_TRACKING_MODE, VrSettings.HEAD_TRACKING_WORLD_LOCKED),
            headTrackingInvertPitch = prefs.getBoolean(KEY_HEAD_TRACKING_INVERT_PITCH, false),
            headTrackingInvertYaw = prefs.getBoolean(KEY_HEAD_TRACKING_INVERT_YAW, false)
        )
    }

    companion object {
        private const val KEY_WIDE_TRACKPAD_MIGRATED_V2 = "wide_trackpad_migrated_v2"
        private const val KEY_HEAD_TRACKING_ENABLED = "head_tracking_enabled"
        private const val KEY_HEAD_TRACKING_SENSITIVITY = "head_tracking_sensitivity"
        private const val KEY_HEAD_TRACKING_SMOOTHING = "head_tracking_smoothing"
        private const val KEY_HEAD_TRACKING_MODE = "head_tracking_mode"
        private const val KEY_HEAD_TRACKING_INVERT_PITCH = "head_tracking_invert_pitch"
        private const val KEY_HEAD_TRACKING_INVERT_YAW = "head_tracking_invert_yaw"
        private const val KEY_IPD_OFFSET = "ipd_offset"
        private const val KEY_SCALE = "scale"
        private const val KEY_Y_OFFSET = "y_offset"
        private const val KEY_ASPECT = "aspect"
        private const val KEY_FOCAL_LENGTH = "focal_length"
        private const val KEY_BARREL_K1 = "barrel_k1"
        private const val KEY_BARREL_K2 = "barrel_k2"
        private const val KEY_BARREL_K3 = "barrel_k3"
        private const val KEY_SPLIT_GAP = "split_gap"
        private const val KEY_MASK_TYPE = "mask_type"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_TOUCH_PASS_THROUGH = "touch_pass_through"
        private const val KEY_PARALLAX_3D = "parallax_3d"
        private const val KEY_SHOW_GRID = "show_grid"
        private const val KEY_IS_PAUSED = "is_paused"
        private const val KEY_CHROMATIC = "chromatic"
        private const val KEY_GEAR_VR_TOUCHPAD = "gear_vr_touchpad"
        private const val KEY_TOUCHPAD_SENSITIVITY = "touchpad_sensitivity"
        private const val KEY_TOUCHPAD_SWIPE_SCROLLS = "touchpad_swipe_scrolls"
        private const val KEY_TOUCHPAD_MODE = "touchpad_mode"
        private const val KEY_IS_SIMPLE_UI_MODE = "is_simple_ui_mode"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_ONBOARDING_STEP = "onboarding_step"
        private const val KEY_LAST_SAVED_TIMESTAMP = "last_saved_timestamp"
        private const val KEY_SAVED_PROFILE_NAME = "saved_profile_name"
        private const val KEY_HAS_BACKUP_PROFILE = "has_backup_profile"
        private const val KEY_HAND_TRACKING = "hand_tracking_enabled"
        private const val KEY_AI_VISION_GESTURES = "ai_vision_gestures"
        private const val KEY_AR_PASSTHROUGH = "ar_passthrough_enabled"
        private const val KEY_AR_APP_OPACITY = "ar_app_opacity"
        private const val KEY_SHOW_CAMERA_PIP = "show_camera_pip"
        private const val KEY_ALL_GESTURES_SIMULTANEOUS = "all_gestures_simultaneous"
        private const val KEY_DWELL_CLICK = "dwell_click_enabled"
        private const val KEY_DWELL_TIME_MS = "dwell_time_ms"
        private const val KEY_SIDE_BUTTONS_ENABLED = "side_buttons_enabled"
        private const val KEY_VOLUME_BUTTONS_MODE = "volume_buttons_mode"
        private const val KEY_VOICE_CONTROL_ENABLED = "voice_control_enabled"
        private const val KEY_CINEMA_SCREEN_ENABLED = "cinema_screen_enabled"
        private const val KEY_CINEMA_CURVATURE = "cinema_curvature"
        private const val KEY_CINEMA_AMBIENT_GLOW = "cinema_ambient_glow"
        private const val KEY_PRESET_NAME = "preset_name"

        // Backup slot keys
        private const val KEY_BACKUP_IPD = "backup_ipd"
        private const val KEY_BACKUP_SCALE = "backup_scale"
        private const val KEY_BACKUP_ASPECT = "backup_aspect"
        private const val KEY_BACKUP_Y_OFFSET = "backup_y_offset"
        private const val KEY_BACKUP_CURVATURE = "backup_curvature"
        private const val KEY_BACKUP_CINEMA_ENABLED = "backup_cinema_enabled"
        private const val KEY_BACKUP_AMBIENT_GLOW = "backup_ambient_glow"
        private const val KEY_BACKUP_PRESET_NAME = "backup_preset_name"
        private const val KEY_BACKUP_TOUCHPAD_SENSITIVITY = "backup_touchpad_sensitivity"
        private const val KEY_BACKUP_TOUCHPAD_MODE = "backup_touchpad_mode"
        private const val KEY_BACKUP_PROFILE_TITLE = "backup_profile_title"
        private const val KEY_BACKUP_TIMESTAMP = "backup_timestamp"

        @Volatile
        private var INSTANCE: VrPreferencesRepository? = null

        fun getInstance(context: Context): VrPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VrPreferencesRepository(context).also { INSTANCE = it }
            }
        }
    }
}
