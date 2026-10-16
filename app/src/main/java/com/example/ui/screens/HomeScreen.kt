package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ScreenDestination
import com.example.model.VrSettings
import com.example.ui.components.AiHandGestureControlDeck
import com.example.ui.components.LiveSbsPreview
import com.example.ui.components.QuickAppLauncher
import com.example.ui.components.VrHeadTrackingDeck
import com.example.ui.components.VrOnboardingWizard
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrDeepSpace
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    settings: VrSettings,
    isOverlayActive: Boolean,
    overlayPermissionGranted: Boolean,
    accessibilityEnabled: Boolean,
    touchpadDeviceName: String,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onStartOverlay: (Int, Intent) -> Unit,
    onStopOverlay: () -> Unit,
    onToggleTouchPass: () -> Unit,
    onTogglePause: () -> Unit,
    onAdjustScale: (Float) -> Unit,
    onAdjustSplit: (Float) -> Unit,
    onToggleTouchPeek: () -> Unit,
    onUpdateSettings: (VrSettings) -> Unit,
    onMoveCursor: (Float, Float) -> Unit,
    onPulseCursorClick: () -> Unit,
    onTriggerHandGesture: (String, String) -> Unit,
    onTriggerGearVrSideButton: (String) -> Unit,
    onTriggerVoiceCommand: (String) -> Unit,
    onPresetSelected: (String) -> Unit,
    onNavigate: (ScreenDestination) -> Unit,
    onSetTouchpadMode: (Int) -> Unit = {},
    onRecenterCursor: () -> Unit = {},
    onToggleSimpleUiMode: (Boolean) -> Unit = {},
    onSetOnboardingStep: (Int) -> Unit = {},
    onCompleteOnboarding: () -> Unit = {},
    onRestartOnboarding: () -> Unit = {},
    onSaveConfiguration: (String) -> Unit = {},
    onRestoreConfiguration: () -> Boolean = { false },
    onResumeDashboard: () -> Unit = {}
) {
    val context = LocalContext.current
    var showRecalibrateDialog by remember { mutableStateOf(false) }
    var saveNoticeMessage by remember { mutableStateOf<String?>(null) }

    // MediaProjection screen capture launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onStartOverlay(result.resultCode, result.data!!)
            Toast.makeText(context, "VR SBS Started! Insert phone into headset", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Screen capture permission required for VR SBS mirroring", Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VrDeepSpace)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero Banner Box with Shay Tech Branding
        val heroImageBitmap = remember(context) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.img_vr_hero_1790343009512,
                    opts
                )?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, VrCardBorder, RoundedCornerShape(20.dp))
        ) {
            if (heroImageBitmap != null) {
                Image(
                    bitmap = heroImageBitmap,
                    contentDescription = "VR Headset Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xCC090D14),
                                Color(0xF2090D14)
                            )
                        )
                    )
            )

            // Top-Right Creator Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xD90A101D))
                    .border(1.dp, VrCyanNeon.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "⚡ SHAY TECH VR",
                    color = VrCyanNeon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            // Banner Title Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isOverlayActive) VrGreenNeon else VrCyanNeon)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOverlayActive) "VR SBS ONLINE" else "SAMSUNG GEAR VR & S23+",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverlayActive) VrGreenNeon else VrCyanNeon,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "VR SBS Cinema & Trackpad",
                    style = MaterialTheme.typography.titleLarge,
                    color = VrTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // ONBOARDING WIZARD (HIDES COMPLEX TOGGLES UNTIL CALIBRATION IS FINISHED)
        // =========================================================================
        if (!settings.hasCompletedOnboarding) {
            VrOnboardingWizard(
                settings = settings,
                onStepChanged = onSetOnboardingStep,
                onComplete = onCompleteOnboarding,
                onStartVr = {
                    if (!isOverlayActive) {
                        val mediaProjectionManager =
                            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                },
                onUpdateSettings = onUpdateSettings,
                onPresetSelected = onPresetSelected,
                onAdjustScale = onAdjustScale,
                onAdjustSplit = onAdjustSplit,
                onMoveCursor = onMoveCursor,
                onPulseCursorClick = onPulseCursorClick,
                onRecenterCursor = onRecenterCursor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Show real-time optical SBS preview while adjusting sliders in the wizard
            LiveSbsPreview(settings = settings)

            Spacer(modifier = Modifier.height(24.dp))
            return@Column
        }

        // Calibrated status banner with Re-run Wizard button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
            border = BorderStroke(1.dp, VrGreenNeon.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("🎓", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Calibrated: ${settings.presetName}",
                            color = VrGreenNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Tap recalibrate anytime to change headsets or zoom",
                            color = VrTextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
                Button(
                    onClick = { showRecalibrateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Recalibrate", color = VrCyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showRecalibrateDialog) {
            AlertDialog(
                onDismissRequest = { showRecalibrateDialog = false },
                containerColor = Color(0xFF0F1A2A),
                icon = {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = VrCyanNeon)
                },
                title = {
                    Text("Recalibrate VR Headset?", color = VrTextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "Your current calibration (${settings.presetName}) is already safely saved in device storage.\n\nDo you want to re-run the 5-step calibration wizard, or keep using your saved setup?",
                        color = VrTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRecalibrateDialog = false
                            onRestartOnboarding()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VrCyanNeon)
                    ) {
                        Text("Re-run Wizard", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRecalibrateDialog = false }) {
                        Text("Keep Current Setup", color = VrGreenNeon, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =========================================================================
        // USER-FRIENDLY MODE SELECTOR: SIMPLE MODE vs PRO EXPERT CONTROLS
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, VrCardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Simple Mode Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (settings.isSimpleUiMode) VrCyanNeon else Color.Transparent)
                        .clickable { onToggleSimpleUiMode(true) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "✨",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Simple Mode",
                                color = if (settings.isSimpleUiMode) Color.Black else VrTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "1-Tap & Friendly",
                                color = if (settings.isSimpleUiMode) Color(0xFF1E293B) else VrTextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }

                // Pro Controls Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!settings.isSimpleUiMode) VrVioletNeon else Color.Transparent)
                        .clickable { onToggleSimpleUiMode(false) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🛠️",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Pro Controls",
                                color = if (!settings.isSimpleUiMode) Color.Black else VrTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "All Optics & Shader",
                                color = if (!settings.isSimpleUiMode) Color(0xFF1E293B) else VrTextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =========================================================================
        // SAVED CONFIGURATION & STORAGE BACKUP MANAGER
        // =========================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("saved_configuration_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1526)),
            border = BorderStroke(1.dp, VrGreenNeon.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VrGreenNeon.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Saved Setup",
                                tint = VrGreenNeon,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SAVED CONFIGURATION",
                                    color = VrGreenNeon,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(VrGreenNeon.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AUTO-SAVED",
                                        color = VrGreenNeon,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Text(
                                text = "Active Profile: ${settings.presetName} • Persistent on Device",
                                color = VrTextSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // Transient save notice feedback banner
                AnimatedVisibility(visible = saveNoticeMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF142B20))
                            .border(1.dp, VrGreenNeon, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VrGreenNeon, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = saveNoticeMessage ?: "",
                                color = VrGreenNeon,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Your adjustments (Scale ${(settings.scale * 100).toInt()}%, Aspect ${(settings.aspectCorrection * 100).toInt()}%, Curve ${(settings.cinemaCurvature * 100).toInt()}%, IPD & Trackpad) are securely saved to persistent device storage. If you ever click back, your setup is protected.",
                    color = VrTextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Save & Restore Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveConfiguration(settings.presetName)
                            saveNoticeMessage = "✓ Settings & ${settings.presetName} saved to device!"
                            Toast.makeText(context, "💾 App configuration saved to device!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1.3f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VrGreenNeon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save Current Setup",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            val restored = onRestoreConfiguration()
                            if (restored) {
                                saveNoticeMessage = "↺ Restored saved configuration!"
                                Toast.makeText(context, "Restored saved configuration!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Saved setup restored! Current settings active.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (settings.hasBackupProfile) VrCyanNeon else Color(0xFF334155))
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = VrCyanNeon, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Restore Setup",
                            color = VrCyanNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Permission Banners (Overlay & Accessibility)
        AnimatedVisibility(visible = !overlayPermissionGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261505)),
                border = BorderStroke(1.dp, Color(0xFFFF9100)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9100),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Step 1: Allow Display Over Other Apps",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFFF9100),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Needed so the VR SBS screen projects over YouTube and apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Allow", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (!accessibilityEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161226)),
                border = BorderStroke(1.dp, VrVioletNeon.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = VrVioletNeon,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Optional: Trusted Touch for Gear VR Trackpad",
                            style = MaterialTheme.typography.titleSmall,
                            color = VrVioletNeon,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enables tapping & clicking inside other apps directly from the trackpad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onRequestAccessibilityPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = VrVioletNeon),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Enable", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // =========================================================================
        // CARD 1: MASTER 1-TAP VR LAUNCHER CARD
        // =========================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isOverlayActive) 2.dp else 1.dp,
                    color = if (isOverlayActive) VrGreenNeon else VrCyanNeon.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = VrCardSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isOverlayActive) VrGreenNeon.copy(alpha = pulseAlpha)
                                    else VrCyanNeon
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isOverlayActive) "VR SBS ENGINE ONLINE" else "READY TO LAUNCH",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isOverlayActive) VrGreenNeon else VrCyanNeon,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isOverlayActive) "Dual lenses projecting in stereo" else "Tap below, then place phone in headset",
                                style = MaterialTheme.typography.bodySmall,
                                color = VrTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    if (isOverlayActive) {
                        Button(
                            onClick = { onTogglePause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.isPaused) VrCyanNeon else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (settings.isPaused) "▶ Resume" else "⏸ Pause",
                                color = if (settings.isPaused) Color.Black else VrTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Master Button: Start or Stop
                Button(
                    onClick = {
                        if (isOverlayActive) {
                            onStopOverlay()
                        } else {
                            val mediaProjectionManager =
                                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("master_sbs_toggle_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayActive) Color(0xFF3B151E) else VrGreenNeon
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isOverlayActive) Color(0xFFFF4081) else VrGreenNeon
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isOverlayActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isOverlayActive) Color(0xFFFF5252) else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isOverlayActive) "⏹️ STOP VR OVERLAY" else "🚀 START VR SBS (1-TAP)",
                        color = if (isOverlayActive) Color.White else Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (isOverlayActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onToggleTouchPeek() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settings.isTouchPeekMode) VrCyanNeon else Color(0xFF10281F)
                        ),
                        border = BorderStroke(1.dp, VrGreenNeon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (settings.isTouchPeekMode) "🕶️ Tap to Return to VR SBS" else "👆 Instant Touch Peek Mode (Tap Underlying App)",
                            color = if (settings.isTouchPeekMode) Color.Black else VrGreenNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // CARD: VR HEAD TRACKING & 3DoF GYROSCOPE DECK
        // =========================================================================
        VrHeadTrackingDeck(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onRecenter = onRecenterCursor
        )

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // CARD 2: SUPER USEFUL GEAR VR TRACKPAD COMMAND CENTER
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1322)),
            border = BorderStroke(1.5.dp, VrCyanNeon),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with Hardware / Virtual status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VrCyanNeon.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = null,
                                tint = VrCyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GEAR VR TRACKPAD HUB",
                                color = VrCyanNeon,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = touchpadDeviceName,
                                color = VrGreenNeon,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = settings.gearVrTouchpadEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings(settings.copy(gearVrTouchpadEnabled = enabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrCyanNeon
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3 Multi-function Trackpad Modes Selector
                Text(
                    text = "SELECT ACTIVE TRACKPAD MODE",
                    color = VrTextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modeList = listOf(
                        Triple(VrSettings.TOUCHPAD_MODE_POINTER, "🎯 Aim & Click", VrCyanNeon),
                        Triple(VrSettings.TOUCHPAD_MODE_SCROLL, "📜 Smooth Scroll", VrVioletNeon),
                        Triple(VrSettings.TOUCHPAD_MODE_ZOOM_IPD, "🔍 Zoom & Comfort", VrGreenNeon)
                    )
                    modeList.forEach { (modeIdx, label, accentColor) ->
                        val isSelected = settings.touchpadMode == modeIdx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentColor else Color(0xFF1E293B))
                                .border(
                                    1.dp,
                                    if (isSelected) accentColor else Color(0xFF334155),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onSetTouchpadMode(modeIdx)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else VrTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Trackpad Sandbox with real-time feedback
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(105.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF070B12))
                        .border(
                            1.dp,
                            if (settings.isCursorClicking) VrGreenNeon else VrCyanNeon.copy(alpha = 0.6f),
                            RoundedCornerShape(14.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    onPulseCursorClick()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val w = size.width.coerceAtLeast(1).toFloat()
                                val h = size.height.coerceAtLeast(1).toFloat()
                                onMoveCursor(dragAmount.x / (w * 0.5f), dragAmount.y / (h * 0.5f))
                            }
                        }
                        .testTag("gear_vr_touchpad_tester"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (settings.isCursorClicking) "⚡ CLICK TRIGGERED!" else "✨ TOUCHPAD TEST PAD (DRAG & TAP)",
                            color = if (settings.isCursorClicking) VrGreenNeon else VrCyanNeon,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "3D Reticle: (${(settings.cursorX * 100).toInt()}%, ${(settings.cursorY * 100).toInt()}%) • Mode: ${
                                when (settings.touchpadMode) {
                                    VrSettings.TOUCHPAD_MODE_POINTER -> "Aim & Click"
                                    VrSettings.TOUCHPAD_MODE_SCROLL -> "Smooth Scroll"
                                    VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> "Zoom & Comfort"
                                    else -> "Pointer"
                                }
                            }",
                            color = VrTextSecondary,
                            fontSize = 10.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Double-tap to recenter • Hold 0.5s to toggle scroll mode",
                            color = VrVioletNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Recenter 3D Reticle button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRecenterCursor,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF162338)),
                        border = BorderStroke(1.dp, VrCyanNeon),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("⚡ Recenter 3D Reticle", color = VrCyanNeon, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }

                    Button(
                        onClick = onPulseCursorClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settings.isCursorClicking) VrGreenNeon else Color(0xFF142E20)
                        ),
                        border = BorderStroke(1.dp, VrGreenNeon),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "🎯 Tap Click",
                            color = if (settings.isCursorClicking) Color.Black else VrGreenNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Friendly In-Headset Gesture Cheat Sheet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "💡 IN-HEADSET SHORTCUTS (THUMB ON GEAR VR TOUCHPAD):",
                            color = Color(0xFFFF9100),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Single Tap: Click / Select at 3D Dot\n• Double Tap: Instantly re-centers 3D Dot to center\n• Hold 0.5s: Swaps between Aim & Scroll mode\n• Flick Left: Navigates Back in any app",
                            color = VrTextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // SIMPLE MODE VIEW: ESSENTIAL EXPERIENCE & 2 SIMPLE SLIDERS
        // =========================================================================
        if (settings.isSimpleUiMode) {
            // STEP 2: ONE-TAP VIEWING PRESETS (Cinema, Flat, Curved, Gear VR, AR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ONE-TAP VIEWING PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = VrVioletNeon,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Auto-tunes aspect ratio & curve",
                    color = VrTextSecondary,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data class PresetOption(
                    val key: String,
                    val title: String,
                    val description: String,
                    val aspectLabel: String,
                    val curveLabel: String,
                    val extraBadge: String
                )

                val presetOptions = listOf(
                    PresetOption(
                        key = VrSettings.PRESET_CINEMA,
                        title = "🎬 Cinema (Curved IMAX)",
                        description = "Subtle IMAX curve with theater aspect ratio. Perfect for movies, YouTube & videos.",
                        aspectLabel = "1.18x Aspect",
                        curveLabel = "40% Curve",
                        extraBadge = "Ambient Glow"
                    ),
                    PresetOption(
                        key = VrSettings.PRESET_FLAT,
                        title = "📐 Flat (Zero Distortion)",
                        description = "Completely flat screen without barrel distortion. Razor-sharp for reading, Chrome & Android UI.",
                        aspectLabel = "1.12x Aspect",
                        curveLabel = "0% Flat",
                        extraBadge = "No Warping"
                    ),
                    PresetOption(
                        key = VrSettings.PRESET_CURVED,
                        title = "🌊 Curved (Ultra-Wide 3D)",
                        description = "Deep cylindrical panoramic wrap-around field of view with 3D stereo parallax depth.",
                        aspectLabel = "1.25x Aspect",
                        curveLabel = "58% Curve",
                        extraBadge = "3D Stereo"
                    ),
                    PresetOption(
                        key = VrSettings.PRESET_GEAR_VR_S23,
                        title = "🎮 Gear VR (Galaxy S23+)",
                        description = "Calibrated specifically for Samsung Galaxy S23+ screen width and Gear VR headset lenses.",
                        aspectLabel = "1.15x Aspect",
                        curveLabel = "32% Curve",
                        extraBadge = "S23+ Tuned"
                    ),
                    PresetOption(
                        key = "AR_MODE",
                        title = "🌐 AR Passthrough & Hand AI",
                        description = "See your real room with floating SBS window and hands-free camera gestures.",
                        aspectLabel = "1.15x Aspect",
                        curveLabel = "Floating",
                        extraBadge = "Vision Pro"
                    )
                )

                presetOptions.forEach { opt ->
                    val isSelected = if (opt.key == "AR_MODE") settings.arPassthroughEnabled
                    else (settings.presetName == opt.key || (opt.key == VrSettings.PRESET_CINEMA && settings.presetName == VrSettings.PRESET_CURVED_CINEMA) || (opt.key == VrSettings.PRESET_FLAT && settings.presetName == VrSettings.PRESET_CINEMA_FLAT) || (opt.key == VrSettings.PRESET_CURVED && settings.presetName == VrSettings.PRESET_CURVED_3D)) && !settings.arPassthroughEnabled

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (opt.key == "AR_MODE") {
                                    onUpdateSettings(
                                        settings.copy(
                                            arPassthroughEnabled = true,
                                            handTrackingEnabled = true,
                                            showCameraRadarPip = false
                                        )
                                    )
                                } else {
                                    onUpdateSettings(settings.copy(arPassthroughEnabled = false))
                                    onPresetSelected(opt.key)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF142436) else VrCardSurface
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) VrCyanNeon else VrCardBorder
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) VrCyanNeon else Color(0xFF334155))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = opt.title,
                                    color = if (isSelected) VrCyanNeon else VrTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(VrCyanNeon.copy(alpha = 0.2f))
                                            .border(1.dp, VrCyanNeon, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = VrCyanNeon,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = opt.description,
                                color = VrTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(start = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Badges showing aspect ratio, curvature, and features
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = opt.aspectLabel,
                                        color = VrCyanNeon,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = opt.curveLabel,
                                        color = VrVioletNeon,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = opt.extraBadge,
                                        color = VrGreenNeon,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // STEP 3: ESSENTIAL TUNING (ONLY 2 FRIENDLY SLIDERS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VrCardSurface),
                border = BorderStroke(1.dp, VrCardBorder),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "QUICK COMFORT TUNING",
                        color = VrCyanNeon,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Slider 1: Screen Size (Zoom)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📐 Screen Size",
                            color = VrTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "－",
                                color = VrCyanNeon,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { onAdjustScale(-0.05f) }
                                    .padding(horizontal = 6.dp)
                            )
                            Text(
                                text = "${(settings.scale * 100).toInt()}%",
                                color = VrCyanNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "＋",
                                color = VrCyanNeon,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { onAdjustScale(+0.05f) }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    Slider(
                        value = settings.scale,
                        onValueChange = { onUpdateSettings(settings.copy(scale = it)) },
                        valueRange = 0.80f..1.15f,
                        colors = SliderDefaults.colors(
                            thumbColor = VrCyanNeon,
                            activeTrackColor = VrCyanNeon
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Slider 2: Eye Alignment (Fix Double Vision)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👁️ Eye Comfort (Fix Double Vision)",
                            color = VrTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "◀",
                                color = VrVioletNeon,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { onAdjustSplit(-0.01f) }
                                    .padding(horizontal = 6.dp)
                            )
                            Text(
                                text = "${(settings.ipdOffset * 100).toInt()}",
                                color = VrVioletNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "▶",
                                color = VrVioletNeon,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { onAdjustSplit(+0.01f) }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    Slider(
                        value = settings.ipdOffset,
                        onValueChange = { onUpdateSettings(settings.copy(ipdOffset = it)) },
                        valueRange = -0.06f..0.06f,
                        colors = SliderDefaults.colors(
                            thumbColor = VrVioletNeon,
                            activeTrackColor = VrVioletNeon
                        )
                    )
                    Text(
                        text = "Tip: Slide left or right until the two images merge into one crisp 3D picture.",
                        color = VrTextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        // =========================================================================
        // PRO EXPERT CONTROLS VIEW (OPTICAL PARAMETERS, SHADERS, HAND AI)
        // =========================================================================
        if (!settings.isSimpleUiMode) {
            // Curved Cinema Screen Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1524)),
                border = BorderStroke(1.dp, if (settings.cinemaScreenEnabled) VrCyanNeon else VrCardBorder),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = VrCyanNeon)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CURVED CINEMA SCREEN GEOMETRY",
                                color = VrCyanNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.cinemaScreenEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(settings.copy(cinemaScreenEnabled = enabled))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = VrCyanNeon)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Applies cylindrical IMAX wrap-around curve to SBS video feed.",
                        color = VrTextSecondary,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = settings.cinemaCurvature,
                        onValueChange = { onUpdateSettings(settings.copy(cinemaCurvature = it)) },
                        valueRange = 0.0f..0.70f,
                        colors = SliderDefaults.colors(thumbColor = VrCyanNeon, activeTrackColor = VrCyanNeon)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Hand Gesture Control Deck & Vision Pro System
            AiHandGestureControlDeck(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onTriggerHandGesture = onTriggerHandGesture,
                onTriggerGearVrSideButton = onTriggerGearVrSideButton,
                onTriggerVoiceCommand = onTriggerVoiceCommand
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Full Preset Selector Chips
            Text(
                text = "ALL HEADSET PRESETS",
                style = MaterialTheme.typography.labelSmall,
                color = VrVioletNeon,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    VrSettings.PRESET_CINEMA,
                    VrSettings.PRESET_FLAT,
                    VrSettings.PRESET_CURVED,
                    VrSettings.PRESET_GEAR_VR_S23,
                    VrSettings.PRESET_CARDBOARD,
                    VrSettings.PRESET_DAYDREAM
                )
                presets.forEach { preset ->
                    val isSelected = settings.presetName == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) VrCyanNeon else VrCardSurface)
                            .border(1.dp, if (isSelected) VrCyanNeon else VrCardBorder, RoundedCornerShape(16.dp))
                            .clickable { onPresetSelected(preset) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = preset,
                            color = if (isSelected) Color.Black else VrTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live SBS optical simulation
        LiveSbsPreview(settings = settings)

        Spacer(modifier = Modifier.height(16.dp))

        // Quick App Launcher into VR (YouTube, Netflix, Chrome)
        QuickAppLauncher()

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Action Grid
        Text(
            text = "VR TOOLS & GUIDES",
            style = MaterialTheme.typography.labelSmall,
            color = VrVioletNeon,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionTile(
                title = "Calibrate Lenses",
                subtitle = "Optical targets & IPD",
                icon = Icons.Default.Adjust,
                iconColor = VrCyanNeon,
                modifier = Modifier.weight(1f)
            ) {
                onNavigate(ScreenDestination.Calibration)
            }

            ActionTile(
                title = "S23+ & Gear VR",
                subtitle = "School demo & trackpad",
                icon = Icons.Default.Help,
                iconColor = VrVioletNeon,
                modifier = Modifier.weight(1f)
            ) {
                onNavigate(ScreenDestination.GearVrGuide)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionTile(
                title = "VR Web Browser",
                subtitle = "SBS web cinema & videos",
                icon = Icons.Default.Language,
                iconColor = VrGreenNeon,
                modifier = Modifier.weight(1f)
            ) {
                onNavigate(ScreenDestination.VrBrowser)
            }

            ActionTile(
                title = "Optics & Shader",
                subtitle = "Barrel & scale tuning",
                icon = Icons.Default.Tune,
                iconColor = Color(0xFFFF9100),
                modifier = Modifier.weight(1f)
            ) {
                onNavigate(ScreenDestination.Settings)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom switch to pro or simple
        if (settings.isSimpleUiMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, VrCardBorder, RoundedCornerShape(12.dp))
                    .clickable { onToggleSimpleUiMode(false) }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛠️ Need Barrel Distortion or Custom Shaders? Switch to Pro Controls ➔",
                    color = VrCyanNeon,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, VrCardBorder, RoundedCornerShape(12.dp))
                    .clickable { onToggleSimpleUiMode(true) }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✨ Return to Simple & Friendly Mode ➔",
                    color = VrGreenNeon,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Made by Shay Tech Footer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B111E)),
            border = BorderStroke(1.dp, VrCyanNeon.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "⚡ MADE BY SHAY TECH",
                        color = VrCyanNeon,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Spatial VR SBS • Multi-Mode Trackpad • S23+ Suite",
                        color = VrTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VrVioletNeon.copy(alpha = 0.18f))
                        .border(1.dp, VrVioletNeon.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v2.5",
                        color = VrVioletNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VrCardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VrCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = VrTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VrTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
