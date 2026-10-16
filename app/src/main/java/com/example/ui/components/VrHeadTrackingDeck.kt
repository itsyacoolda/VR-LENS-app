package com.example.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VrSettings
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Control Deck for VR Head Tracking.
 * Displays live 3DoF telemetry (Yaw, Pitch, Roll), active hardware sensor fusion state,
 * intuitive Mode Selection (World-Locked, Gaze Aiming, Panoramic), and precision tuning.
 */
@Composable
fun VrHeadTrackingDeck(
    settings: VrSettings,
    onUpdateSettings: (VrSettings) -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }

    val yawDeg = (settings.headYaw * 180f / PI.toFloat()).toInt()
    val pitchDeg = (settings.headPitch * 180f / PI.toFloat()).toInt()
    val rollDeg = (settings.headRoll * 180f / PI.toFloat()).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("head_tracking_deck_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1526)),
        border = BorderStroke(
            1.dp,
            if (settings.headTrackingEnabled) VrCyanNeon.copy(alpha = 0.8f) else VrCardBorder
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Icon, Title, and Master Switch
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
                            .clip(CircleShape)
                            .background(if (settings.headTrackingEnabled) Color(0x2200E5FF) else Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Head Tracking Gyroscope",
                            tint = if (settings.headTrackingEnabled) VrCyanNeon else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "VR HEAD TRACKING",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.headTrackingEnabled) VrCyanNeon else VrTextPrimary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (settings.headTrackingEnabled) Color(0x2600E676) else Color(0xFF26334D))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (settings.headTrackingEnabled) "3DoF ACTIVE" else "OFF",
                                    color = if (settings.headTrackingEnabled) VrGreenNeon else Color.Gray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = settings.headTrackingSensorName,
                            color = VrTextSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1
                        )
                    }
                }

                Switch(
                    checked = settings.headTrackingEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(settings.copy(headTrackingEnabled = enabled))
                    },
                    modifier = Modifier.testTag("head_tracking_master_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = VrCyanNeon,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            AnimatedVisibility(
                visible = settings.headTrackingEnabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Live 3D Gyroscope Telemetry Canvas (Radar & Horizon)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF070C16))
                            .border(1.dp, Color(0xFF16253C), RoundedCornerShape(12.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                            val w = size.width
                            val h = size.height
                            val cx = w / 2f
                            val cy = h / 2f

                            // Background concentric orientation rings
                            drawCircle(
                                color = Color(0x1F00E5FF),
                                radius = 40.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 1f)
                            )
                            drawCircle(
                                color = Color(0x1400E5FF),
                                radius = 24.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 1f)
                            )

                            // Crosshair axes
                            drawLine(
                                color = Color(0x2294A3B8),
                                start = Offset(cx - 50.dp.toPx(), cy),
                                end = Offset(cx + 50.dp.toPx(), cy),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color(0x2294A3B8),
                                start = Offset(cx, cy - 35.dp.toPx()),
                                end = Offset(cx, cy + 35.dp.toPx()),
                                strokeWidth = 1f
                            )

                            // Tilted artificial horizon line reflecting Roll & Pitch
                            val rollRad = settings.headRoll
                            val pitchOffsetPx = (settings.headPitch * 35.dp.toPx()).coerceIn(-30.dp.toPx(), 30.dp.toPx())
                            val horizonLen = 35.dp.toPx()
                            val cosR = cos(rollRad)
                            val sinR = sin(rollRad)

                            val hx1 = cx - horizonLen * cosR
                            val hy1 = cy + pitchOffsetPx - horizonLen * sinR
                            val hx2 = cx + horizonLen * cosR
                            val hy2 = cy + pitchOffsetPx + horizonLen * sinR

                            drawLine(
                                color = VrVioletNeon.copy(alpha = 0.75f),
                                start = Offset(hx1, hy1),
                                end = Offset(hx2, hy2),
                                strokeWidth = 2f
                            )

                            // Head Gaze Indicator Dot reflecting Yaw & Pitch
                            val gazeX = (cx + settings.headYaw * 45.dp.toPx()).coerceIn(cx - 40.dp.toPx(), cx + 40.dp.toPx())
                            val gazeY = (cy - settings.headPitch * 35.dp.toPx()).coerceIn(cy - 30.dp.toPx(), cy + 30.dp.toPx())

                            drawCircle(
                                color = VrCyanNeon,
                                radius = 5.dp.toPx(),
                                center = Offset(gazeX, gazeY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = Offset(gazeX, gazeY)
                            )
                        }

                        // Live Telemetry badges overlay
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "YAW: ${yawDeg}°",
                                color = VrCyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PITCH: ${pitchDeg}°",
                                color = VrVioletNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ROLL: ${rollDeg}°",
                                color = VrGreenNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary Recenter Button
                    Button(
                        onClick = onRecenter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("head_tracking_recenter_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132A3E)),
                        border = BorderStroke(1.dp, VrCyanNeon.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Recenter View",
                            tint = VrCyanNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🎯 Recenter Forward View",
                            color = VrCyanNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Selection Chips
                    Text(
                        text = "TRACKING VIEW MODE",
                        color = VrVioletNeon,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            Triple(VrSettings.HEAD_TRACKING_WORLD_LOCKED, "🌐 World-Locked", "Virtual Screen"),
                            Triple(VrSettings.HEAD_TRACKING_GAZE_AIM, "🎯 Gaze Aim", "Head Pointer"),
                            Triple(VrSettings.HEAD_TRACKING_CINEMA_CANVAS, "🎬 Panoramic", "360 Canvas")
                        )

                        modes.forEach { (modeVal, title, sub) ->
                            val isSelected = settings.headTrackingMode == modeVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF16324D) else VrCardSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) VrCyanNeon else VrCardBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onUpdateSettings(settings.copy(headTrackingMode = modeVal))
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) VrCyanNeon else VrTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = sub,
                                        color = VrTextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sensitivity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = VrCyanNeon, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Head Movement Sensitivity",
                                color = VrTextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${"%.1f".format(settings.headTrackingSensitivity)}x",
                            color = VrCyanNeon,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = settings.headTrackingSensitivity,
                        onValueChange = { sens ->
                            onUpdateSettings(settings.copy(headTrackingSensitivity = sens))
                        },
                        valueRange = 0.3f..2.5f,
                        modifier = Modifier.testTag("head_tracking_sensitivity_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = VrCyanNeon,
                            activeTrackColor = VrCyanNeon,
                            inactiveTrackColor = Color(0xFF1E293B)
                        )
                    )

                    // Toggle Advanced Tuning
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedSettings = !showAdvancedSettings }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = VrTextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Advanced Comfort & Inversion",
                                color = VrTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = if (showAdvancedSettings) "▲ Hide" else "▼ Show",
                            color = VrCyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Smoothing / Damping
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Comfort Damping Filter",
                                    color = VrTextPrimary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (settings.headTrackingSmoothing < 0.15f) "Ultra-Fast (Direct)"
                                    else if (settings.headTrackingSmoothing > 0.35f) "Smooth (Cinematic)"
                                    else "Balanced",
                                    color = VrVioletNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = settings.headTrackingSmoothing,
                                onValueChange = { smooth ->
                                    onUpdateSettings(settings.copy(headTrackingSmoothing = smooth))
                                },
                                valueRange = 0.08f..0.50f,
                                colors = SliderDefaults.colors(
                                    thumbColor = VrVioletNeon,
                                    activeTrackColor = VrVioletNeon,
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )

                            // Axis Inversions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (settings.headTrackingInvertYaw) Color(0xFF1A2A38) else VrCardSurface)
                                        .border(1.dp, if (settings.headTrackingInvertYaw) VrCyanNeon else VrCardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            onUpdateSettings(settings.copy(headTrackingInvertYaw = !settings.headTrackingInvertYaw))
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Invert Yaw (Left/Right)",
                                        color = if (settings.headTrackingInvertYaw) VrCyanNeon else VrTextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (settings.headTrackingInvertYaw) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (settings.headTrackingInvertPitch) Color(0xFF1A2A38) else VrCardSurface)
                                        .border(1.dp, if (settings.headTrackingInvertPitch) VrCyanNeon else VrCardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            onUpdateSettings(settings.copy(headTrackingInvertPitch = !settings.headTrackingInvertPitch))
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Invert Pitch (Up/Down)",
                                        color = if (settings.headTrackingInvertPitch) VrCyanNeon else VrTextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (settings.headTrackingInvertPitch) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
