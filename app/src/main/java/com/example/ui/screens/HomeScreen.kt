package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ScreenDestination
import com.example.model.VrSettings
import com.example.ui.components.LiveSbsPreview
import com.example.ui.components.QuickAppLauncher
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanGlow
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrDeepSpace
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrRedAlert
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    settings: VrSettings,
    isOverlayActive: Boolean,
    overlayPermissionGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: (Int, Intent) -> Unit,
    onStopOverlay: () -> Unit,
    onToggleTouchPass: () -> Unit,
    onTogglePause: () -> Unit,
    onAdjustScale: (Float) -> Unit,
    onAdjustSplit: (Float) -> Unit,
    onToggleTouchPeek: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onNavigate: (ScreenDestination) -> Unit
) {
    val context = LocalContext.current

    // Launcher for Notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission handled
    }

    // Launcher for MediaProjection screen capture intent
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onStartOverlay(result.resultCode, result.data!!)
            Toast.makeText(context, "VR SBS Overlay Activated! Insert phone into Gear VR", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Screen capture permission required for VR SBS mirroring", Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VrDeepSpace)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero Banner Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, VrCardBorder, RoundedCornerShape(20.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_vr_hero_1790343009512),
                contentDescription = "VR Headset Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient scrim
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

            // Banner Title Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
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
                        text = if (isOverlayActive) "VR SBS ENGINE ONLINE" else "GEAR VR & MOBILE SBS SUITE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverlayActive) VrGreenNeon else VrCyanNeon,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "VR Lens SBS Overlay",
                    style = MaterialTheme.typography.titleLarge,
                    color = VrTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Overlay Permission Alert Card if not granted
        AnimatedVisibility(visible = !overlayPermissionGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261505)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9100)),
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
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Over Other Apps Permission",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFFF9100),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Required so the SBS VR view can project over games, YouTube, and any app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Master Launch / Stop VR Card
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
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isOverlayActive) "VR OVERLAY IS RUNNING" else "VR SBS SYSTEM READY",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isOverlayActive) VrGreenNeon else VrCyanNeon,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isOverlayActive) "Projecting screen in dual SBS VR" else "Mirror any app into dual SBS lenses",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOverlayActive) VrGreenNeon.copy(alpha = 0.15f) else VrCyanNeon.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOverlayActive) Icons.Default.Visibility else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isOverlayActive) VrGreenNeon else VrCyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big Master Button
                Button(
                    onClick = {
                        if (isOverlayActive) {
                            onStopOverlay()
                        } else {
                            if (!overlayPermissionGranted) {
                                onRequestOverlayPermission()
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .scale(if (isOverlayActive) pulseScale else 1.0f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayActive) VrRedAlert else VrCyanNeon
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (isOverlayActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOverlayActive) "STOP VR SBS OVERLAY" else "START SBS VR OVERLAY",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Quick Controls Card (Play/Pause, Resize Window, SBS Split)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B101B))
                        .border(1.dp, Color(0xFF26334D), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "LIVE OVERLAY INTERACTIVE CONTROLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = VrCyanNeon,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play / Pause Toggle Button
                        Button(
                            onClick = { onTogglePause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.isPaused) VrCyanNeon else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (settings.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Play/Pause",
                                tint = if (settings.isPaused) Color.Black else VrCyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (settings.isPaused) "Resume" else "Pause",
                                color = if (settings.isPaused) Color.Black else VrTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Resize Window Controls: [-] 85% [+]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "－",
                                color = VrTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { onAdjustScale(-0.05f) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Text(
                                text = "Size: ${(settings.scale * 100).toInt()}%",
                                color = VrCyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "＋",
                                color = VrTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { onAdjustScale(+0.05f) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // SBS Split Controls: [◀] Split [▶]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "◀",
                                color = VrTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { onAdjustSplit(-0.01f) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Text(
                                text = "Split: ${(settings.ipdOffset * 100).toInt()}",
                                color = VrVioletNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "▶",
                                color = VrTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { onAdjustSplit(+0.01f) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 In headset: A floating controller sits on the center nose divider with Play/Pause, Resize, Split, and Lens Tuning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VrTextSecondary,
                        fontSize = 10.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Touch pass-through fast toggle inside card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (settings.touchPassThrough) VrGreenNeon else Color(0xFFFF9100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (settings.touchPassThrough) "Touch Pass: Enabled" else "Touch Pass: Off (VR Adjust)",
                                color = VrTextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (settings.touchPassThrough) "Taps go directly to underlying app" else "Taps adjust IPD & scale in VR",
                                color = VrTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = settings.touchPassThrough,
                        onCheckedChange = { onToggleTouchPass() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrGreenNeon,
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                // If overlay is active, show the Instant Touch Peek button right here as well!
                if (isOverlayActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onToggleTouchPeek() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settings.isTouchPeekMode) VrCyanNeon else Color(0xFF10281F)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VrGreenNeon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (settings.isTouchPeekMode) Color.Black else VrGreenNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (settings.isTouchPeekMode) "🕶️ Tap to Return to VR SBS" else "👆 Touch Screen Now (Peek Mode)",
                            color = if (settings.isTouchPeekMode) Color.Black else VrGreenNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TOUCH INTERACTION & TROUBLESHOOTING CARD (Specifically addressing why touches are blocked on Galaxy S23+)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1826)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = VrGreenNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TOUCH SCREEN INTERACTION GUIDE",
                        style = MaterialTheme.typography.labelMedium,
                        color = VrGreenNeon,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Why doesn't touch work through the overlay?\n" +
                            "On Android 12+ (including Galaxy S23+), the system blocks 'untrusted touches' when an overlay is visible on top of an app to prevent tapjacking. Here are the 2 ways to touch and interact freely:",
                    style = MaterialTheme.typography.bodySmall,
                    color = VrTextSecondary,
                    lineHeight = 18.sp,
                    fontSize = 11.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Solution 1 Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A121E))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Method 1: Instant Touch Peek (No setup needed)",
                            color = VrCyanNeon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the '👆 TOUCH APP' button on the floating controller or notification bar anytime you want to touch or type. The overlay instantly hides so you can tap 100% normally, then tap 'Return to VR' to resume!",
                            color = VrTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Solution 2 Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A121E))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Method 2: Disable 'Block Untrusted Touches' in Developer Options",
                            color = VrVioletNeon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "To allow direct touches to pass through the overlay without hiding it: In Developer options, turn OFF 'Block untrusted touches'.",
                            color = VrTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onOpenDevSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = VrVioletNeon),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Open Developer Options", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Preset Selector Chips
        Text(
            text = "SELECT HEADSET PRESET",
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
                VrSettings.PRESET_GEAR_VR_S23,
                VrSettings.PRESET_CARDBOARD,
                VrSettings.PRESET_DAYDREAM,
                VrSettings.PRESET_CINEMA_FLAT,
                VrSettings.PRESET_CURVED_3D
            )
            presets.forEach { preset ->
                val isSelected = settings.presetName == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) VrCyanNeon else VrCardSurface)
                        .border(
                            1.dp,
                            if (isSelected) VrCyanNeon else VrCardBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onPresetSelected(preset) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = preset,
                        color = if (isSelected) Color.Black else VrTextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live SBS optical simulation
        LiveSbsPreview(settings = settings)

        Spacer(modifier = Modifier.height(16.dp))

        // Quick App Launcher into VR
        QuickAppLauncher()

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Action Grid
        Text(
            text = "VR TOOLS & CONFIGURATION",
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
                subtitle = "Hardware setup guide",
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
        border = androidx.compose.foundation.BorderStroke(1.dp, VrCardBorder)
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
