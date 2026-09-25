package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CalibrationScreen
import com.example.ui.screens.GearVrGuideScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VrBrowserScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrDeepSpace
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary

class MainActivity : ComponentActivity() {

    private var viewModelInstance: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val vm: MainViewModel = viewModel()
                viewModelInstance = vm
                MainContent(viewModel = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModelInstance?.checkPermissions()
    }
}

@Composable
fun MainContent(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isOverlayActive by viewModel.isOverlayActive.collectAsStateWithLifecycle()
    val overlayPermissionGranted by viewModel.overlayPermissionGranted.collectAsStateWithLifecycle()

    val showBottomNav = currentScreen is ScreenDestination.Home ||
            currentScreen is ScreenDestination.Settings ||
            currentScreen is ScreenDestination.GearVrGuide ||
            currentScreen is ScreenDestination.VrBrowser

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VrDeepSpace,
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = VrCardSurface,
                    tonalElevation = 4.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen is ScreenDestination.Home,
                        onClick = { viewModel.navigateTo(ScreenDestination.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("VR Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = VrCyanNeon,
                            indicatorColor = VrCyanNeon,
                            unselectedIconColor = VrTextSecondary,
                            unselectedTextColor = VrTextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen is ScreenDestination.Settings,
                        onClick = { viewModel.navigateTo(ScreenDestination.Settings) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Tuning") },
                        label = { Text("Tuning") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = VrCyanNeon,
                            indicatorColor = VrCyanNeon,
                            unselectedIconColor = VrTextSecondary,
                            unselectedTextColor = VrTextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen is ScreenDestination.GearVrGuide,
                        onClick = { viewModel.navigateTo(ScreenDestination.GearVrGuide) },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Guide") },
                        label = { Text("S23+ Guide") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = VrCyanNeon,
                            indicatorColor = VrCyanNeon,
                            unselectedIconColor = VrTextSecondary,
                            unselectedTextColor = VrTextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen is ScreenDestination.VrBrowser,
                        onClick = { viewModel.navigateTo(ScreenDestination.VrBrowser) },
                        icon = { Icon(Icons.Default.Language, contentDescription = "Browser") },
                        label = { Text("Cinema") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = VrCyanNeon,
                            indicatorColor = VrCyanNeon,
                            unselectedIconColor = VrTextSecondary,
                            unselectedTextColor = VrTextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                is ScreenDestination.Home -> {
                    HomeScreen(
                        settings = settings,
                        isOverlayActive = isOverlayActive,
                        overlayPermissionGranted = overlayPermissionGranted,
                        onRequestOverlayPermission = {
                            viewModel.requestOverlayPermission(viewModel.getApplication())
                        },
                        onStartOverlay = { resultCode, data ->
                            viewModel.startVrOverlay(resultCode, data, viewModel.getApplication())
                        },
                        onStopOverlay = {
                            viewModel.stopVrOverlay(viewModel.getApplication())
                        },
                        onToggleTouchPass = {
                            viewModel.toggleTouchPassThrough(viewModel.getApplication())
                        },
                        onTogglePause = {
                            viewModel.togglePause(viewModel.getApplication())
                        },
                        onAdjustScale = { delta ->
                            viewModel.adjustScale(delta, viewModel.getApplication())
                        },
                        onAdjustSplit = { delta ->
                            viewModel.adjustSplit(delta, viewModel.getApplication())
                        },
                        onToggleTouchPeek = {
                            viewModel.toggleTouchPeek(viewModel.getApplication())
                        },
                        onOpenDevSettings = {
                            viewModel.openDeveloperSettings(context)
                        },
                        onPresetSelected = { presetName ->
                            viewModel.applyPreset(presetName)
                        },
                        onNavigate = { destination ->
                            viewModel.navigateTo(destination)
                        }
                    )
                }
                is ScreenDestination.Settings -> {
                    BackHandler { viewModel.navigateTo(ScreenDestination.Home) }
                    SettingsScreen(
                        settings = settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        onPresetSelected = { viewModel.applyPreset(it) },
                        onNavigateBack = { viewModel.navigateTo(ScreenDestination.Home) }
                    )
                }
                is ScreenDestination.Calibration -> {
                    BackHandler { viewModel.navigateTo(ScreenDestination.Home) }
                    CalibrationScreen(
                        currentSettings = settings,
                        onSaveSettings = { viewModel.updateSettings(it) },
                        onNavigateBack = { viewModel.navigateTo(ScreenDestination.Home) }
                    )
                }
                is ScreenDestination.GearVrGuide -> {
                    BackHandler { viewModel.navigateTo(ScreenDestination.Home) }
                    GearVrGuideScreen(
                        onNavigateBack = { viewModel.navigateTo(ScreenDestination.Home) }
                    )
                }
                is ScreenDestination.VrBrowser -> {
                    BackHandler { viewModel.navigateTo(ScreenDestination.Home) }
                    VrBrowserScreen(
                        onNavigateBack = { viewModel.navigateTo(ScreenDestination.Home) }
                    )
                }
            }
        }
    }
}
