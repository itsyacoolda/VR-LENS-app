package com.example.model

/**
 * Configuration settings for SBS (Side-by-Side) VR projection.
 */
data class VrSettings(
    // Horizontal eye separation adjustment (-0.25f to +0.25f)
    val ipdOffset: Float = 0.01f,
    // Screen zoom / scale factor inside VR space (0.5f to 1.3f)
    val scale: Float = 0.95f,
    // Vertical optical alignment (-0.3f to +0.3f)
    val yOffset: Float = 0.0f,
    // Aspect ratio stretch/compression correction (0.6f to 1.5f) - >1.0 ensures wide cinema aspect
    val aspectCorrection: Float = 1.15f,
    // Headset lens focal length in millimeters (25mm to 75mm, typical Gear VR is ~42mm)
    val focalLength: Float = 42.0f,
    // Brown-Conrady Barrel Distortion K1 (radial quadratic curvature)
    val barrelK1: Float = 0.08f,
    // Brown-Conrady Barrel Distortion K2 (radial quartic curvature)
    val barrelK2: Float = 0.02f,
    // Brown-Conrady Barrel Distortion K3 (radial sextic edge curvature)
    val barrelK3: Float = 0.005f,
    // Central split gap width between left and right eyes (0.0f to 0.12f)
    val splitGapWidth: Float = 0.01f,
    // Optical mask type: 0 = Wide Full Screen, 1 = Circular VR lens, 2 = Soft cinema vignette
    val maskType: Int = 0,
    // Screen brightness multiplier (0.4f to 1.3f)
    val brightness: Float = 1.0f,
    // Touch pass-through mode: if true, touches pass directly to the underlying app
    val touchPassThrough: Boolean = true,
    // Pseudo-stereoscopic 3D parallax depth offset (0.0f to 0.04f)
    val parallax3D: Float = 0.006f,
    // Whether to display calibration alignment crosshairs
    val showCalibrationGrid: Boolean = false,
    // Whether VR projection frame capture is paused / frozen
    val isPaused: Boolean = false,
    // Whether touch peek mode is active (temporarily hides overlay to allow touching underlying app directly)
    val isTouchPeekMode: Boolean = false,
    // Chromatic aberration lens fringe compensation
    val chromaticAberration: Boolean = false,
    // Whether the Gear VR Touchpad & Stereoscopic 3D VR Cursor is active
    val gearVrTouchpadEnabled: Boolean = true,
    // Gear VR Touchpad pointer speed sensitivity (0.5f to 3.0f)
    val touchpadSensitivity: Float = 1.4f,
    // Normalized 3D VR Cursor X coordinate (0.0f = left, 1.0f = right)
    val cursorX: Float = 0.5f,
    // Normalized 3D VR Cursor Y coordinate (0.0f = top, 1.0f = bottom)
    val cursorY: Float = 0.5f,
    // Visual pulse state when Gear VR touchpad click is triggered
    val isCursorClicking: Boolean = false,
    // If true, swiping on touchpad directly scrolls the underlying app; if false, moves the 3D cursor
    val touchpadSwipeScrolls: Boolean = false,
    // Multi-function Trackpad mode: 0 = Pointer (Aim & Click), 1 = Fluid Scroll, 2 = In-Headset Zoom & IPD
    val touchpadMode: Int = TOUCHPAD_MODE_POINTER,
    // Live transient HUD feedback message for trackpad actions (e.g. "🎯 Pointer Mode", "⚡ Recenter")
    val trackpadHudNotice: String = "",
    // Beginner-friendly Simple Mode vs Pro Controls (default true to eliminate steep learning curve)
    val isSimpleUiMode: Boolean = true,
    // Interactive Onboarding Wizard status: hides complex toggles until initial setup/calibration is complete
    val hasCompletedOnboarding: Boolean = false,
    // Current step in the interactive onboarding wizard (0..4)
    val onboardingStep: Int = 0,
    // IN-DEV: Apple Vision Pro style Camera Hand Movement & Gesture Tracking
    val handTrackingEnabled: Boolean = false,
    // Whether Gemini AI Vision (gemini-3.5-flash) is active alongside optical hand motion tracking
    val aiVisionGesturesEnabled: Boolean = true,
    // Augmented Reality (AR) Mixed-Reality Passthrough mode (live rear camera behind floating SBS app)
    val arPassthroughEnabled: Boolean = false,
    // Opacity of the floating app window in AR Passthrough mode (0.35f to 1.0f)
    val arAppOpacity: Float = 0.90f,
    // Disabled by default so turning on Hand Tracking never places a rectangular PIP box over the screen
    val showCameraRadarPip: Boolean = false,
    // Live telemetry string of the most recently detected hand gesture & executed movement
    val lastDetectedGesture: String = "Trackpad Tap-to-Click Ready • Swipe to Move 3D Cursor, Tap to Click",
    // Whether hand swipe gestures run simultaneously
    val allGesturesSimultaneous: Boolean = true,
    // Center Dot 2-Second Focus Auto-Clicker (OFF by default so clicking is purely via Trackpad tap)
    val dwellClickEnabled: Boolean = false,
    // Focus duration in milliseconds before the center dot clicks if enabled (default 2000ms = 2 seconds)
    val dwellTimeMs: Long = 2000L,
    // Live 0.0f..1.0f progress as the center dot focuses on a target
    val dwellProgress: Float = 0.0f,
    // Gear VR Physical Side Buttons (Volume Up, Volume Down, Home, Back)
    val gearVrSideButtonsEnabled: Boolean = true,
    // Volume button behavior in VR: 0 = Vol+ Clicks Center Dot & Vol- Scrolls Down, 1 = Spatial Screen Zoom In/Out, 2 = Audio Volume + HUD
    val volumeButtonsMode: Int = 0,
    // Live telemetry of the most recent Gear VR side button press (Vol+, Vol-, Home, Back)
    val lastHardwareButtonEvent: String = "Side Buttons Ready (Vol+ / Vol- / Home / Back)",
    // Hands-free Voice Control mode
    val voiceControlEnabled: Boolean = false,
    // Whether the SpeechRecognizer is actively listening for voice commands
    val isVoiceListening: Boolean = false,
    // Most recent recognized voice command & action
    val lastVoiceCommand: String = "Voice Ready: Say \"Click\", \"Swipe Left\", \"Home\", \"Back\"",
    // Curved Cinema Screen Display Mode: applies concave cylindrical theater screen curvature to SBS video feed
    val cinemaScreenEnabled: Boolean = true,
    // Cylindrical horizontal cinema screen curvature intensity (0.0f = flat, 0.32f = subtle IMAX curve, 0.80f = deep dome)
    val cinemaCurvature: Float = 0.32f,
    // Ambient theater bounce glow around the curved cinema screen edges
    val cinemaAmbientGlow: Boolean = true,
    // Selected preset name
    val presetName: String = PRESET_GEAR_VR_S23,
    // Epoch timestamp when settings were explicitly saved
    val lastSavedTimestamp: Long = 0L,
    // Name of user-saved custom profile slot, if any
    val savedProfileName: String = "",
    // Whether a user snapshot backup exists on device storage
    val hasBackupProfile: Boolean = false,
    // Head Tracking using phone's Gyroscope + Accelerometer
    val headTrackingEnabled: Boolean = true,
    // Head tracking sensitivity multiplier (0.3f to 2.5f, default 1.0f)
    val headTrackingSensitivity: Float = 1.0f,
    // Head tracking smoothing & comfort factor (0.05f to 0.50f, default 0.22f)
    val headTrackingSmoothing: Float = 0.22f,
    // Head tracking mode: 0 = World-Locked Floating Screen, 1 = Gaze Pointer Reticle, 2 = Panoramic Cinema Canvas
    val headTrackingMode: Int = HEAD_TRACKING_WORLD_LOCKED,
    // Invert Pitch axis (looking up moves viewport down)
    val headTrackingInvertPitch: Boolean = false,
    // Invert Yaw axis (turning head right moves viewport left)
    val headTrackingInvertYaw: Boolean = false,
    // Live head tracking Euler orientation angles in radians for SBS projection & preview
    val headYaw: Float = 0f,
    val headPitch: Float = 0f,
    val headRoll: Float = 0f,
    // Active sensor label (e.g. "Game Rotation Vector (Gyro+Accel)")
    val headTrackingSensorName: String = "Game Rotation Vector (Gyro+Accel)"
) {
    companion object {
        const val HEAD_TRACKING_WORLD_LOCKED = 0
        const val HEAD_TRACKING_GAZE_AIM = 1
        const val HEAD_TRACKING_CINEMA_CANVAS = 2

        const val TOUCHPAD_MODE_POINTER = 0
        const val TOUCHPAD_MODE_SCROLL = 1
        const val TOUCHPAD_MODE_ZOOM_IPD = 2

        const val PRESET_GEAR_VR_S23 = "Gear VR (Galaxy S23+)"
        const val PRESET_CINEMA = "Cinema"
        const val PRESET_FLAT = "Flat"
        const val PRESET_CURVED = "Curved"
        const val PRESET_CURVED_CINEMA = "Curved Cinema Screen"
        const val PRESET_CARDBOARD = "Google Cardboard"
        const val PRESET_DAYDREAM = "Daydream View"
        const val PRESET_CINEMA_FLAT = "IMAX Flat Cinema"
        const val PRESET_CURVED_3D = "Curved 3D Theater"
        const val PRESET_CUSTOM = "Custom"

        fun getPreset(name: String): VrSettings {
            return when (name) {
                PRESET_CINEMA, PRESET_CURVED_CINEMA -> VrSettings(
                    ipdOffset = 0.01f,
                    scale = 0.96f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.18f,
                    focalLength = 44.0f,
                    barrelK1 = 0.06f,
                    barrelK2 = 0.015f,
                    barrelK3 = 0.005f,
                    splitGapWidth = 0.01f,
                    maskType = 0,
                    brightness = 1.05f,
                    touchPassThrough = true,
                    parallax3D = 0.010f,
                    isPaused = false,
                    chromaticAberration = false,
                    cinemaScreenEnabled = true,
                    cinemaCurvature = 0.40f,
                    cinemaAmbientGlow = true,
                    presetName = PRESET_CINEMA
                )
                PRESET_FLAT, PRESET_CINEMA_FLAT -> VrSettings(
                    ipdOffset = 0.0f,
                    scale = 0.96f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.12f,
                    focalLength = 50.0f,
                    barrelK1 = 0.0f,
                    barrelK2 = 0.0f,
                    barrelK3 = 0.0f,
                    splitGapWidth = 0.0f,
                    maskType = 0,
                    brightness = 1.0f,
                    touchPassThrough = true,
                    parallax3D = 0.0f,
                    isPaused = false,
                    chromaticAberration = false,
                    cinemaScreenEnabled = false,
                    cinemaCurvature = 0.0f,
                    cinemaAmbientGlow = false,
                    presetName = PRESET_FLAT
                )
                PRESET_CURVED, PRESET_CURVED_3D -> VrSettings(
                    ipdOffset = 0.02f,
                    scale = 0.96f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.25f,
                    focalLength = 40.0f,
                    barrelK1 = 0.08f,
                    barrelK2 = 0.02f,
                    barrelK3 = 0.005f,
                    splitGapWidth = 0.01f,
                    maskType = 0,
                    brightness = 1.10f,
                    touchPassThrough = true,
                    parallax3D = 0.014f,
                    isPaused = false,
                    chromaticAberration = true,
                    cinemaScreenEnabled = true,
                    cinemaCurvature = 0.58f,
                    cinemaAmbientGlow = true,
                    presetName = PRESET_CURVED
                )
                PRESET_GEAR_VR_S23, "Gear VR" -> VrSettings(
                    ipdOffset = 0.01f,
                    scale = 0.95f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.15f,
                    focalLength = 42.0f,
                    barrelK1 = 0.08f,
                    barrelK2 = 0.02f,
                    barrelK3 = 0.005f,
                    splitGapWidth = 0.01f,
                    maskType = 0,
                    brightness = 1.0f,
                    touchPassThrough = true,
                    parallax3D = 0.006f,
                    isPaused = false,
                    chromaticAberration = false,
                    cinemaScreenEnabled = true,
                    cinemaCurvature = 0.32f,
                    cinemaAmbientGlow = true,
                    presetName = PRESET_GEAR_VR_S23
                )
                PRESET_CURVED_CINEMA -> VrSettings(
                    ipdOffset = 0.01f,
                    scale = 0.96f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.18f,
                    focalLength = 44.0f,
                    barrelK1 = 0.06f,
                    barrelK2 = 0.015f,
                    barrelK3 = 0.005f,
                    splitGapWidth = 0.01f,
                    maskType = 0,
                    brightness = 1.05f,
                    touchPassThrough = true,
                    parallax3D = 0.010f,
                    isPaused = false,
                    chromaticAberration = false,
                    cinemaScreenEnabled = true,
                    cinemaCurvature = 0.42f,
                    cinemaAmbientGlow = true,
                    presetName = PRESET_CURVED_CINEMA
                )
                PRESET_CARDBOARD -> VrSettings(
                    ipdOffset = 0.0f,
                    scale = 0.92f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.12f,
                    focalLength = 37.0f,
                    barrelK1 = 0.12f,
                    barrelK2 = 0.04f,
                    barrelK3 = 0.01f,
                    splitGapWidth = 0.0f,
                    maskType = 0,
                    brightness = 1.0f,
                    touchPassThrough = true,
                    parallax3D = 0.004f,
                    isPaused = false,
                    chromaticAberration = false,
                    cinemaScreenEnabled = false,
                    cinemaCurvature = 0.25f,
                    cinemaAmbientGlow = true,
                    presetName = PRESET_CARDBOARD
                )
                PRESET_DAYDREAM -> VrSettings(
                    ipdOffset = 0.01f,
                    scale = 0.95f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.15f,
                    focalLength = 45.0f,
                    barrelK1 = 0.06f,
                    barrelK2 = 0.015f,
                    barrelK3 = 0.005f,
                    splitGapWidth = 0.005f,
                    maskType = 0,
                    brightness = 1.05f,
                    touchPassThrough = true,
                    parallax3D = 0.008f,
                    isPaused = false,
                    chromaticAberration = false,
                    cinemaScreenEnabled = true,
                    cinemaCurvature = 0.28f,
                    cinemaAmbientGlow = true,
                    presetName = PRESET_DAYDREAM
                )
                PRESET_CINEMA_FLAT -> VrSettings(
                    ipdOffset = 0.0f,
                    scale = 0.98f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.20f,
                    focalLength = 50.0f,
                    barrelK1 = 0.0f,
                    barrelK2 = 0.0f,
                    barrelK3 = 0.0f,
                    splitGapWidth = 0.0f,
                    maskType = 0,
                    brightness = 1.0f,
                    touchPassThrough = true,
                    parallax3D = 0.0f,
                    isPaused = false,
                    chromaticAberration = false,
                    cinemaScreenEnabled = false,
                    cinemaCurvature = 0.0f,
                    cinemaAmbientGlow = false,
                    presetName = PRESET_CINEMA_FLAT
                )
                PRESET_CURVED_3D -> VrSettings(
                    ipdOffset = 0.02f,
                    scale = 0.96f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.18f,
                    focalLength = 40.0f,
                    barrelK1 = 0.10f,
                    barrelK2 = 0.03f,
                    barrelK3 = 0.01f,
                    splitGapWidth = 0.01f,
                    maskType = 0,
                    brightness = 1.1f,
                    touchPassThrough = true,
                    parallax3D = 0.012f,
                    isPaused = false,
                    chromaticAberration = true,
                    cinemaScreenEnabled = true,
                    cinemaCurvature = 0.50f,
                    cinemaAmbientGlow = true,
                    presetName = PRESET_CURVED_3D
                )
                else -> VrSettings(presetName = PRESET_CUSTOM)
            }
        }
    }
}
