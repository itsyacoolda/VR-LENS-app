package com.example.model

/**
 * Configuration settings for SBS (Side-by-Side) VR projection.
 */
data class VrSettings(
    // Horizontal eye separation adjustment (-0.25f to +0.25f)
    val ipdOffset: Float = 0.0f,
    // Screen zoom / scale factor inside VR space (0.5f to 1.3f)
    val scale: Float = 0.82f,
    // Vertical optical alignment (-0.3f to +0.3f)
    val yOffset: Float = 0.0f,
    // Aspect ratio stretch/compression correction (0.6f to 1.5f)
    val aspectCorrection: Float = 1.0f,
    // Headset lens focal length in millimeters (25mm to 75mm, typical Gear VR is ~42mm)
    val focalLength: Float = 42.0f,
    // Brown-Conrady Barrel Distortion K1 (radial quadratic curvature)
    val barrelK1: Float = 0.12f,
    // Brown-Conrady Barrel Distortion K2 (radial quartic curvature)
    val barrelK2: Float = 0.04f,
    // Brown-Conrady Barrel Distortion K3 (radial sextic edge curvature)
    val barrelK3: Float = 0.01f,
    // Central split gap width between left and right eyes (0.0f to 0.12f)
    val splitGapWidth: Float = 0.0f,
    // Optical mask type: 0 = Rectangular cinema, 1 = Circular VR lens, 2 = Soft cinema vignette
    val maskType: Int = 1,
    // Screen brightness multiplier (0.4f to 1.3f)
    val brightness: Float = 1.0f,
    // Touch pass-through mode: if true, touches pass directly to the underlying app
    val touchPassThrough: Boolean = true,
    // Pseudo-stereoscopic 3D parallax depth offset (0.0f to 0.04f)
    val parallax3D: Float = 0.005f,
    // Whether to display calibration alignment crosshairs
    val showCalibrationGrid: Boolean = false,
    // Whether VR projection frame capture is paused / frozen
    val isPaused: Boolean = false,
    // Whether touch peek mode is active (temporarily hides overlay to allow touching underlying app directly)
    val isTouchPeekMode: Boolean = false,
    // Chromatic aberration lens fringe compensation
    val chromaticAberration: Boolean = false,
    // Selected preset name
    val presetName: String = PRESET_GEAR_VR_S23
) {
    companion object {
        const val PRESET_GEAR_VR_S23 = "Gear VR (Galaxy S23+)"
        const val PRESET_CARDBOARD = "Google Cardboard"
        const val PRESET_DAYDREAM = "Daydream View"
        const val PRESET_CINEMA_FLAT = "IMAX Flat Cinema"
        const val PRESET_CURVED_3D = "Curved 3D Theater"
        const val PRESET_CUSTOM = "Custom"

        fun getPreset(name: String): VrSettings {
            return when (name) {
                PRESET_GEAR_VR_S23 -> VrSettings(
                    ipdOffset = 0.02f,
                    scale = 0.85f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.0f,
                    focalLength = 42.0f,
                    barrelK1 = 0.12f,
                    barrelK2 = 0.04f,
                    barrelK3 = 0.01f,
                    splitGapWidth = 0.01f,
                    maskType = 1,
                    brightness = 1.0f,
                    touchPassThrough = true,
                    parallax3D = 0.006f,
                    isPaused = false,
                    chromaticAberration = false,
                    presetName = PRESET_GEAR_VR_S23
                )
                PRESET_CARDBOARD -> VrSettings(
                    ipdOffset = 0.0f,
                    scale = 0.78f,
                    yOffset = 0.0f,
                    aspectCorrection = 0.95f,
                    focalLength = 37.0f,
                    barrelK1 = 0.18f,
                    barrelK2 = 0.06f,
                    barrelK3 = 0.02f,
                    splitGapWidth = 0.0f,
                    maskType = 1,
                    brightness = 1.0f,
                    touchPassThrough = true,
                    parallax3D = 0.004f,
                    isPaused = false,
                    chromaticAberration = false,
                    presetName = PRESET_CARDBOARD
                )
                PRESET_DAYDREAM -> VrSettings(
                    ipdOffset = 0.01f,
                    scale = 0.88f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.0f,
                    focalLength = 45.0f,
                    barrelK1 = 0.08f,
                    barrelK2 = 0.02f,
                    barrelK3 = 0.005f,
                    splitGapWidth = 0.005f,
                    maskType = 1,
                    brightness = 1.05f,
                    touchPassThrough = true,
                    parallax3D = 0.008f,
                    isPaused = false,
                    chromaticAberration = false,
                    presetName = PRESET_DAYDREAM
                )
                PRESET_CINEMA_FLAT -> VrSettings(
                    ipdOffset = 0.0f,
                    scale = 0.92f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.0f,
                    focalLength = 50.0f,
                    barrelK1 = 0.0f,
                    barrelK2 = 0.0f,
                    barrelK3 = 0.0f,
                    splitGapWidth = 0.0f,
                    maskType = 2,
                    brightness = 1.0f,
                    touchPassThrough = true,
                    parallax3D = 0.0f,
                    isPaused = false,
                    chromaticAberration = false,
                    presetName = PRESET_CINEMA_FLAT
                )
                PRESET_CURVED_3D -> VrSettings(
                    ipdOffset = 0.03f,
                    scale = 0.90f,
                    yOffset = 0.0f,
                    aspectCorrection = 1.05f,
                    focalLength = 40.0f,
                    barrelK1 = 0.15f,
                    barrelK2 = 0.05f,
                    barrelK3 = 0.02f,
                    splitGapWidth = 0.02f,
                    maskType = 0,
                    brightness = 1.1f,
                    touchPassThrough = true,
                    parallax3D = 0.012f,
                    isPaused = false,
                    chromaticAberration = true,
                    presetName = PRESET_CURVED_3D
                )
                else -> VrSettings(presetName = PRESET_CUSTOM)
            }
        }
    }
}
