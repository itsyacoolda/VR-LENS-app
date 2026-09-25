package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VrPreferencesRepository
import com.example.model.VrSettings
import com.example.service.VrOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _overlayPermissionGranted = MutableStateFlow(checkOverlayPermission())
    val overlayPermissionGranted: StateFlow<Boolean> = _overlayPermissionGranted.asStateFlow()

    fun navigateTo(destination: ScreenDestination) {
        _currentScreen.value = destination
    }

    fun checkPermissions() {
        _overlayPermissionGranted.value = checkOverlayPermission()
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
        val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
            action = VrOverlayService.ACTION_TOGGLE_TOUCH
        }
        context.startService(serviceIntent)
    }

    fun togglePause(context: Context) {
        val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
            action = VrOverlayService.ACTION_TOGGLE_PAUSE
        }
        context.startService(serviceIntent)
    }

    fun adjustScale(delta: Float, context: Context) {
        val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
            action = if (delta > 0) VrOverlayService.ACTION_RESIZE_UP else VrOverlayService.ACTION_RESIZE_DOWN
        }
        context.startService(serviceIntent)
    }

    fun adjustSplit(delta: Float, context: Context) {
        val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
            action = if (delta > 0) VrOverlayService.ACTION_SPLIT_OUT else VrOverlayService.ACTION_SPLIT_IN
        }
        context.startService(serviceIntent)
    }

    fun toggleTouchPeek(context: Context) {
        val serviceIntent = Intent(context, VrOverlayService::class.java).apply {
            action = VrOverlayService.ACTION_TOGGLE_TOUCH_PEEK
        }
        context.startService(serviceIntent)
    }

    fun openDeveloperSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val generalIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(generalIntent)
            } catch (_: Exception) {}
        }
    }

    fun updateSettings(newSettings: VrSettings) {
        prefsRepo.updateSettings(newSettings)
    }

    fun applyPreset(presetName: String) {
        prefsRepo.applyPreset(presetName)
    }
}
