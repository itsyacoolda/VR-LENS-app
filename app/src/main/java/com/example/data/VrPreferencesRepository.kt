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
            .putString(KEY_PRESET_NAME, newSettings.presetName)
            .apply()

        _settingsFlow.value = newSettings
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

    fun applyPreset(presetName: String) {
        val preset = VrSettings.getPreset(presetName)
        updateSettings(preset)
    }

    private fun loadSettings(): VrSettings {
        return VrSettings(
            ipdOffset = prefs.getFloat(KEY_IPD_OFFSET, 0.02f),
            scale = prefs.getFloat(KEY_SCALE, 0.85f),
            yOffset = prefs.getFloat(KEY_Y_OFFSET, 0.0f),
            aspectCorrection = prefs.getFloat(KEY_ASPECT, 1.0f),
            focalLength = prefs.getFloat(KEY_FOCAL_LENGTH, 42.0f),
            barrelK1 = prefs.getFloat(KEY_BARREL_K1, 0.12f),
            barrelK2 = prefs.getFloat(KEY_BARREL_K2, 0.04f),
            barrelK3 = prefs.getFloat(KEY_BARREL_K3, 0.01f),
            splitGapWidth = prefs.getFloat(KEY_SPLIT_GAP, 0.01f),
            maskType = prefs.getInt(KEY_MASK_TYPE, 1),
            brightness = prefs.getFloat(KEY_BRIGHTNESS, 1.0f),
            touchPassThrough = prefs.getBoolean(KEY_TOUCH_PASS_THROUGH, true),
            parallax3D = prefs.getFloat(KEY_PARALLAX_3D, 0.006f),
            showCalibrationGrid = prefs.getBoolean(KEY_SHOW_GRID, false),
            isPaused = prefs.getBoolean(KEY_IS_PAUSED, false),
            chromaticAberration = prefs.getBoolean(KEY_CHROMATIC, false),
            presetName = prefs.getString(KEY_PRESET_NAME, VrSettings.PRESET_GEAR_VR_S23) ?: VrSettings.PRESET_GEAR_VR_S23
        )
    }

    companion object {
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
        private const val KEY_PRESET_NAME = "preset_name"

        @Volatile
        private var INSTANCE: VrPreferencesRepository? = null

        fun getInstance(context: Context): VrPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VrPreferencesRepository(context).also { INSTANCE = it }
            }
        }
    }
}
