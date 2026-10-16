package com.example.ui.components

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VrSettings
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Composable UI component with a Switch to enable or disable AI-powered hand gesture tracking mode,
 * strictly respecting the runtime Camera permission state via Accompanist Permissions.
 * Also provides:
 * - Simultaneous Multi-Gesture Control (Swipe Left/Right/Up/Down, Pinch, Open Palm, Fist, Pointing)
 * - 2-Second Center Dot Focus Auto-Clicker (Dwell Click)
 * - Gear VR Side Buttons (Volume Up, Volume Down, Home, Back)
 * - Hands-Free Voice Control functions (respecting RECORD_AUDIO permission)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalPermissionsApi::class)
@Composable
fun AiHandGestureControlDeck(
    settings: VrSettings,
    onUpdateSettings: (VrSettings) -> Unit,
    onTriggerHandGesture: (String, String) -> Unit,
    onTriggerGearVrSideButton: (String) -> Unit,
    onTriggerVoiceCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingEnableHandTracking by remember { mutableStateOf(false) }
    var pendingEnableArMode by remember { mutableStateOf(false) }
    var pendingEnableVoice by remember { mutableStateOf(false) }

    // Runtime Camera permission state managed via Accompanist Permissions
    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    ) { granted ->
        if (granted) {
            if (pendingEnableHandTracking || pendingEnableArMode) {
                onUpdateSettings(
                    settings.copy(
                        handTrackingEnabled = true,
                        arPassthroughEnabled = if (pendingEnableArMode) true else settings.arPassthroughEnabled,
                        showCameraRadarPip = false
                    )
                )
            }
            Toast.makeText(context, "Camera Permission Granted: AI Hand Gesture Tracking Active!", Toast.LENGTH_SHORT).show()
        } else {
            onUpdateSettings(
                settings.copy(
                    handTrackingEnabled = false,
                    arPassthroughEnabled = false
                )
            )
            Toast.makeText(context, "Camera permission is required to enable AI Hand Gesture Tracking.", Toast.LENGTH_LONG).show()
        }
        pendingEnableHandTracking = false
        pendingEnableArMode = false
    }

    // Runtime Microphone permission state for Voice Control
    val audioPermissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    ) { granted ->
        if (granted && pendingEnableVoice) {
            onUpdateSettings(settings.copy(voiceControlEnabled = true))
            Toast.makeText(context, "Voice Control Active! Speak commands like 'Click', 'Swipe Left', 'Back'", Toast.LENGTH_SHORT).show()
        } else if (!granted) {
            onUpdateSettings(settings.copy(voiceControlEnabled = false))
            Toast.makeText(context, "Microphone permission is required for live voice recognition.", Toast.LENGTH_SHORT).show()
        }
        pendingEnableVoice = false
    }

    // Ensure handTrackingEnabled and arPassthroughEnabled strictly respect cameraPermissionState
    val isCameraGranted = cameraPermissionState.status.isGranted
    LaunchedEffect(isCameraGranted) {
        if (!isCameraGranted && (settings.handTrackingEnabled || settings.arPassthroughEnabled)) {
            onUpdateSettings(
                settings.copy(
                    handTrackingEnabled = false,
                    arPassthroughEnabled = false
                )
            )
        }
    }

    val effectiveHandTrackingEnabled = settings.handTrackingEnabled && isCameraGranted

    Column(modifier = modifier.fillMaxWidth()) {
        // =========================================================================
        // CARD 1: AI-POWERED HAND GESTURE TRACKING + 2S CENTER DOT CLICKER + AR
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131129)),
            border = BorderStroke(
                width = if (effectiveHandTrackingEnabled || settings.arPassthroughEnabled) 2.dp else 1.dp,
                color = if (effectiveHandTrackingEnabled || settings.arPassthroughEnabled) VrGreenNeon else VrVioletNeon
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header + Primary Switch to Enable/Disable AI Hand Gesture Tracking Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x33FF9100))
                                    .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "IN DEVELOPMENT • VISION PRO AI",
                                    color = Color(0xFFFF9100),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.7.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "AI Hand Gesture Tracking Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = VrTextPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (effectiveHandTrackingEnabled) {
                                "Camera Permission Verified • Wide Screen + Trackpad Tap Click Active"
                            } else if (!isCameraGranted) {
                                "Requires Camera Permission to Track Hand Movements"
                            } else {
                                "Toggle Switch ON to track hand gestures via camera (keeps wide screen)"
                            },
                            color = if (effectiveHandTrackingEnabled) VrGreenNeon else VrTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Dedicated Switch to Enable/Disable AI Hand Gesture Tracking (respects camera permission)
                    Switch(
                        checked = effectiveHandTrackingEnabled,
                        onCheckedChange = { shouldEnable ->
                            if (shouldEnable) {
                                if (isCameraGranted) {
                                    onUpdateSettings(
                                        settings.copy(
                                            handTrackingEnabled = true,
                                            allGesturesSimultaneous = true
                                        )
                                    )
                                } else {
                                    pendingEnableHandTracking = true
                                    cameraPermissionState.launchPermissionRequest()
                                }
                            } else {
                                onUpdateSettings(settings.copy(handTrackingEnabled = false))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrGreenNeon,
                            uncheckedTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.testTag("ai_hand_gesture_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Accompanist Runtime Camera Permission Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isCameraGranted) Color(0xFF0A2419) else Color(0xFF281808)
                        )
                        .border(
                            1.dp,
                            if (isCameraGranted) VrGreenNeon.copy(alpha = 0.6f) else Color(0xFFFF9100),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCameraGranted) {
                                "📷 Camera Permission: Granted (Ready for Hand AI & AR)"
                            } else if (cameraPermissionState.status.shouldShowRationale) {
                                "📷 Camera Permission Needed to Detect Hand Movements"
                            } else {
                                "📷 Runtime Camera Permission Required for Hand Tracking"
                            },
                            color = if (isCameraGranted) VrGreenNeon else Color(0xFFFF9100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isCameraGranted) {
                        Button(
                            onClick = {
                                pendingEnableHandTracking = true
                                cameraPermissionState.launchPermissionRequest()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("grant_camera_permission_button")
                        ) {
                            Text(
                                text = "Allow Cam",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // -----------------------------------------------------------------
                // TRACKPAD TAP-TO-CLICK CONTROLLER SECTION
                // -----------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0C1829))
                        .border(
                            1.dp,
                            if (settings.isCursorClicking) VrGreenNeon else VrCyanNeon.copy(alpha = 0.55f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Adjust,
                                    contentDescription = null,
                                    tint = if (settings.isCursorClicking) VrGreenNeon else VrCyanNeon,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "🎯 Trackpad Tap-to-Click Mode",
                                        color = VrGreenNeon,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = "Slide on Trackpad to aim 3D Dot • Tap Trackpad to trigger click ripple & tap!",
                                        color = VrTextSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (settings.isCursorClicking) VrGreenNeon else Color(0xFF162C3D))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (settings.isCursorClicking) "CLICKING!" else "TRACKPAD TAP",
                                    color = if (settings.isCursorClicking) Color.Black else VrCyanNeon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (settings.isCursorClicking) {
                                    "⚡ Trackpad Click Ripple Active at (${(settings.cursorX * 100).toInt()}%, ${(settings.cursorY * 100).toInt()}%)"
                                } else {
                                    "3D Cursor Position: (${(settings.cursorX * 100).toInt()}%, ${(settings.cursorY * 100).toInt()}%) • Tap Trackpad to Click"
                                },
                                color = if (settings.isCursorClicking) VrGreenNeon else VrCyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onTriggerHandGesture(
                                    "PINCH_CLICK",
                                    "🎯 Trackpad Tap Click ➔ Clicked at Center Dot!"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("test_center_dot_dwell_click_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.isCursorClicking) VrGreenNeon else Color(0xFF162C3D)
                            ),
                            border = BorderStroke(1.dp, VrGreenNeon),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "🎯 TAP TRACKPAD TO CLICK AT CURSOR NOW",
                                color = if (settings.isCursorClicking) Color.Black else VrGreenNeon,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selectable Button 1: Use Vision Pro Hand Gestures (In-Dev)
                Button(
                    onClick = {
                        if (!isCameraGranted) {
                            pendingEnableHandTracking = true
                            cameraPermissionState.launchPermissionRequest()
                        } else {
                            val newEnabled = !settings.handTrackingEnabled
                            onUpdateSettings(
                                settings.copy(
                                    handTrackingEnabled = newEnabled,
                                    allGesturesSimultaneous = true
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("select_vision_pro_hand_gestures_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (effectiveHandTrackingEnabled) VrGreenNeon else VrVioletNeon
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (effectiveHandTrackingEnabled) {
                            "🖐️ VISION PRO HAND GESTURES: ACTIVE (ALL AT ONCE)"
                        } else {
                            "🖐️ USE VISION PRO HAND GESTURES (IN-DEV)"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Selectable Button 2: Augmented Reality (AR) Camera Passthrough Mode
                Button(
                    onClick = {
                        if (!isCameraGranted) {
                            pendingEnableArMode = true
                            cameraPermissionState.launchPermissionRequest()
                        } else {
                            val newAr = !settings.arPassthroughEnabled
                            onUpdateSettings(
                                settings.copy(
                                    arPassthroughEnabled = newAr,
                                    handTrackingEnabled = if (newAr) true else settings.handTrackingEnabled,
                                    showCameraRadarPip = false
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("select_ar_passthrough_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings.arPassthroughEnabled && isCameraGranted) VrCyanNeon else Color(0xFF1E293B)
                    ),
                    border = BorderStroke(1.dp, VrCyanNeon),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (settings.arPassthroughEnabled && isCameraGranted) {
                            "🌐 AUGMENTED REALITY (AR) CAMERA MODE: ON"
                        } else {
                            "🌐 ENABLE AUGMENTED REALITY (AR) + HAND CAM"
                        },
                        color = if (settings.arPassthroughEnabled && isCameraGranted) Color.Black else VrCyanNeon,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Use All Gestures At Once Toggle + Live Telemetry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use All Hand Gestures Simultaneously",
                            color = VrTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Swipe L/R/U/D + 2s Center Dot Click + Pinch + Palm Pause + Fist Back all active at once",
                            color = VrTextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                    Switch(
                        checked = settings.allGesturesSimultaneous,
                        onCheckedChange = { allOn ->
                            onUpdateSettings(settings.copy(allGesturesSimultaneous = allOn))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrCyanNeon
                        ),
                        modifier = Modifier.testTag("all_gestures_simultaneous_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live AI Vision & Hand Gesture Telemetry Status Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, VrCyanNeon.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "LIVE AI VISION & MULTI-GESTURE TELEMETRY",
                            color = VrCyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = settings.lastDetectedGesture,
                            color = VrGreenNeon,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // All Simultaneous Gestures Action Bar
                Text(
                    text = "ALL SIMULTANEOUS GESTURES (ACTIVE & TESTABLE AT ONCE):",
                    color = VrTextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val gestureTests = listOf(
                        Triple("SWIPE_LEFT", "👈 Swipe Left (Slide Left)", "👈 Hand Swipe Left ➔ Slid App Left"),
                        Triple("SWIPE_RIGHT", "👉 Swipe Right (Slide Right)", "👉 Hand Swipe Right ➔ Slid App Right"),
                        Triple("SWIPE_UP", "👆 Swipe Up (Scroll Up)", "👆 Hand Swipe Up ➔ Scrolled App Up"),
                        Triple("SWIPE_DOWN", "👇 Swipe Down (Scroll Down)", "👇 Hand Swipe Down ➔ Scrolled App Down"),
                        Triple("DWELL_FOCUS_DEMO", "🎯 2s Center Dot Focus Click", "🎯 Center Dot Focusing 2.0s ➔ Auto-Click!"),
                        Triple("PINCH_TAP", "🤏 Pinch Fingers (Instant Click)", "🤏 Vision Pro Pinch ➔ Clicked Center Dot"),
                        Triple("OPEN_PALM", "✋ Open Palm (Play/Pause)", "✋ Open Palm Gesture ➔ Toggled Play/Pause"),
                        Triple("FIST_BACK", "✊ Closed Fist (Go Back)", "✊ Closed Fist Gesture ➔ Navigated Back")
                    )
                    gestureTests.forEach { (code, btnLabel, statusMsg) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, VrVioletNeon.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                .clickable {
                                    onTriggerHandGesture(code, statusMsg)
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = btnLabel,
                                color = VrTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AR Floating App Window Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AR Floating App Window Opacity",
                        color = VrTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(settings.arAppOpacity * 100).toInt()}%",
                        color = VrCyanNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.arAppOpacity,
                    onValueChange = { opacity ->
                        onUpdateSettings(settings.copy(arAppOpacity = opacity))
                    },
                    valueRange = 0.40f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = VrCyanNeon,
                        activeTrackColor = VrCyanNeon
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // CARD 2: GEAR VR SIDE BUTTONS (VOLUME UP / DOWN, HOME, BACK)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
            border = BorderStroke(1.dp, VrCyanNeon.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = VrCyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "GEAR VR SIDE BUTTONS (VOL+, VOL-, HOME, BACK)",
                                style = MaterialTheme.typography.labelMedium,
                                color = VrCyanNeon,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.7.sp
                            )
                            Text(
                                text = "Uses physical headset Volume rockers, Home & Back buttons",
                                color = VrTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = settings.gearVrSideButtonsEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings(settings.copy(gearVrSideButtonsEnabled = enabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrCyanNeon
                        ),
                        modifier = Modifier.testTag("gear_vr_side_buttons_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Volume Rocker Mode Selector
                Text(
                    text = "SIDE VOLUME BUTTONS FUNCTION IN VR:",
                    color = VrTextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        0 to "🎯 Vol+ Click / Vol- Scroll",
                        1 to "🔍 Screen Zoom +/-",
                        2 to "🔊 Media Volume"
                    )
                    modes.forEach { (modeIdx, modeTitle) ->
                        val selected = settings.volumeButtonsMode == modeIdx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) VrCyanNeon else Color(0xFF1E293B))
                                .border(1.dp, VrCyanNeon.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable {
                                    onUpdateSettings(settings.copy(volumeButtonsMode = modeIdx))
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeTitle,
                                color = if (selected) Color.Black else VrTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Gear VR Side Buttons Deck (Volume +, Volume -, Home, Back)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onTriggerGearVrSideButton("VOLUME_UP") },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("gear_vr_vol_up_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF182A42)),
                        border = BorderStroke(1.dp, VrGreenNeon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔊 Vol +", color = VrGreenNeon, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                    Button(
                        onClick = { onTriggerGearVrSideButton("VOLUME_DOWN") },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("gear_vr_vol_down_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF182A42)),
                        border = BorderStroke(1.dp, VrCyanNeon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔉 Vol -", color = VrCyanNeon, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                    Button(
                        onClick = { onTriggerGearVrSideButton("HOME") },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("gear_vr_home_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF231B42)),
                        border = BorderStroke(1.dp, VrVioletNeon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🏠 Home", color = VrVioletNeon, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                    Button(
                        onClick = { onTriggerGearVrSideButton("BACK") },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("gear_vr_back_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1B17)),
                        border = BorderStroke(1.dp, Color(0xFFFF9100)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔙 Back", color = Color(0xFFFF9100), fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hardware Side Button Telemetry Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF080E18))
                        .border(1.dp, VrCyanNeon.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = settings.lastHardwareButtonEvent,
                        color = VrGreenNeon,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // CARD 3: HANDS-FREE VR VOICE CONTROL ENGINE
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111C24)),
            border = BorderStroke(
                width = if (settings.voiceControlEnabled) 2.dp else 1.dp,
                color = if (settings.voiceControlEnabled) VrGreenNeon else VrCyanNeon.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (settings.voiceControlEnabled) VrGreenNeon else VrCyanNeon,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "HANDS-FREE VR VOICE CONTROL",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (settings.voiceControlEnabled) VrGreenNeon else VrCyanNeon,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.7.sp
                            )
                            Text(
                                text = "Say \"Click\", \"Swipe Left\", \"Scroll Down\", \"Back\", \"Home\", \"AR Mode\"",
                                color = VrTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = settings.voiceControlEnabled && audioPermissionState.status.isGranted,
                        onCheckedChange = { enableVoice ->
                            if (enableVoice) {
                                if (audioPermissionState.status.isGranted) {
                                    onUpdateSettings(settings.copy(voiceControlEnabled = true))
                                } else {
                                    pendingEnableVoice = true
                                    audioPermissionState.launchPermissionRequest()
                                }
                            } else {
                                onUpdateSettings(settings.copy(voiceControlEnabled = false))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrGreenNeon
                        ),
                        modifier = Modifier.testTag("voice_control_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Voice Command Status Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF081018))
                        .border(1.dp, VrGreenNeon.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = settings.lastVoiceCommand,
                        color = VrGreenNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "VOICE COMMAND FUNCTIONS (SAY OR TAP TO EXECUTE):",
                    color = VrTextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val voiceCommands = listOf(
                        "Click" to "🎙️ \"Click\" (Tap Dot)",
                        "Swipe Left" to "🎙️ \"Swipe Left\"",
                        "Swipe Right" to "🎙️ \"Swipe Right\"",
                        "Scroll Down" to "🎙️ \"Scroll Down\"",
                        "Scroll Up" to "🎙️ \"Scroll Up\"",
                        "Back" to "🎙️ \"Back\"",
                        "Home" to "🎙️ \"Home\"",
                        "Volume Up" to "🎙️ \"Volume Up\"",
                        "Volume Down" to "🎙️ \"Volume Down\"",
                        "Zoom In" to "🎙️ \"Zoom In\"",
                        "Zoom Out" to "🎙️ \"Zoom Out\"",
                        "AR Mode" to "🎙️ \"AR Mode\"",
                        "Play / Pause" to "🎙️ \"Play / Pause\"",
                        "Recenter" to "🎙️ \"Recenter\""
                    )
                    voiceCommands.forEach { (phrase, chipText) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1B2B38))
                                .border(1.dp, VrCyanNeon.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable {
                                    onTriggerVoiceCommand(phrase)
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = chipText,
                                color = VrTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
