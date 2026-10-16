package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.graphics.VrFrameHolder
import com.example.model.VrSettings
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanGlow
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon

@Composable
fun LiveSbsPreview(
    settings: VrSettings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VrCardSurface)
            .border(1.dp, VrCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = VrCyanNeon,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live SBS Optical Simulation",
                    style = MaterialTheme.typography.titleSmall,
                    color = VrTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "${(settings.scale * 100).toInt()}% • IPD ${(settings.ipdOffset * 100).toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = VrVioletNeon,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dual eye simulation canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Eye Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    EyeSimulationView(
                        eyeLabel = "LEFT EYE",
                        isLeft = true,
                        settings = settings
                    )
                }

                // Center physical nose separator bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0F172A), VrCyanNeon, Color(0xFF0F172A))
                            )
                        )
                )

                // Right Eye Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    EyeSimulationView(
                        eyeLabel = "RIGHT EYE",
                        isLeft = false,
                        settings = settings
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (settings.headTrackingEnabled) {
                    val yawDeg = (settings.headYaw * 180f / Math.PI.toFloat()).toInt()
                    val pitchDeg = (settings.headPitch * 180f / Math.PI.toFloat()).toInt()
                    "🧭 Head Track: ${yawDeg}° / ${pitchDeg}°"
                } else if (settings.cinemaScreenEnabled) {
                    "🎬 Curved Cinema (${(settings.cinemaCurvature * 100).toInt()}%)"
                } else {
                    "Lens: ${if (settings.barrelK1 > 0.001f) "Curved Barrel (Gear VR)" else "Flat Screen"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (settings.headTrackingEnabled) VrCyanNeon else if (settings.cinemaScreenEnabled) VrVioletNeon else VrTextSecondary,
                fontWeight = if (settings.headTrackingEnabled) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 11.sp
            )
            Text(
                text = "Trackpad: ${
                    when (settings.touchpadMode) {
                        VrSettings.TOUCHPAD_MODE_POINTER -> "🎯 Aim & Tap"
                        VrSettings.TOUCHPAD_MODE_SCROLL -> "📜 Smooth Scroll"
                        VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> "🔍 Zoom/IPD"
                        else -> "🎯 Aim"
                    }
                }",
                style = MaterialTheme.typography.bodySmall,
                color = when (settings.touchpadMode) {
                    VrSettings.TOUCHPAD_MODE_SCROLL -> VrVioletNeon
                    VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> VrGreenNeon
                    else -> VrCyanNeon
                },
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun EyeSimulationView(
    eyeLabel: String,
    isLeft: Boolean,
    settings: VrSettings
) {
    val frameVersion by VrFrameHolder.frameVersionFlow.collectAsState()
    val liveImageBitmap = remember(frameVersion) {
        val bmp = VrFrameHolder.latestBitmap
        if (bmp != null && !bmp.isRecycled) {
            try {
                bmp.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val ipdShift = if (isLeft) -(settings.ipdOffset * 25f) else (settings.ipdOffset * 25f)
    val scale = settings.scale.coerceIn(0.5f, 1.2f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val headX = if (settings.headTrackingEnabled && settings.headTrackingMode != VrSettings.HEAD_TRACKING_GAZE_AIM) {
            (settings.headYaw * 30f * settings.headTrackingSensitivity).coerceIn(-w * 0.35f, w * 0.35f)
        } else 0f
        val headY = if (settings.headTrackingEnabled && settings.headTrackingMode != VrSettings.HEAD_TRACKING_GAZE_AIM) {
            (settings.headPitch * 25f * settings.headTrackingSensitivity).coerceIn(-h * 0.3f, h * 0.3f)
        } else 0f
        val centerX = w / 2f + ipdShift + headX
        val centerY = h / 2f - (settings.yOffset * 30f) + headY

        // Draw wide widescreen cinema screen in VR space (never tall or narrow)
        val clampedScale = scale.coerceIn(0.78f, 1.12f)
        val screenW = (w * 0.94f) * clampedScale
        val screenH = (screenW * 0.62f).coerceAtMost(h * 0.82f)

        val left = centerX - screenW / 2f
        val top = centerY - screenH / 2f

        // If Augmented Reality (AR) Passthrough is enabled, draw simulated real-world camera room background
        if (settings.arPassthroughEnabled) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A2F23), Color(0xFF0F2027), Color(0xFF203A43))
                ),
                topLeft = Offset.Zero,
                size = Size(w, h)
            )
            // Subtle spatial AR room grid lines
            drawLine(
                color = Color(0x3300E676),
                start = Offset(0f, h * 0.72f),
                end = Offset(w, h * 0.72f),
                strokeWidth = 1f
            )
        }

        // Screen content background & Live Captured App Mirroring
        val appAlpha = if (settings.arPassthroughEnabled) settings.arAppOpacity else 1.0f

        // Dynamic Theater Ambilight Glow when Curved Cinema Screen Mode is active
        if (settings.cinemaScreenEnabled && settings.cinemaAmbientGlow && !settings.arPassthroughEnabled) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x4400E5FF),
                        Color(0x227C4DFF),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = (screenW * 0.72f).coerceAtLeast(10f)
                ),
                topLeft = Offset(left - 14f, top - 14f),
                size = Size(screenW + 28f, screenH + 28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
            )
        }

        drawRoundRect(
            color = Color(0xFF1E293B).copy(alpha = appAlpha),
            topLeft = Offset(left, top),
            size = Size(screenW, screenH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )

        if (liveImageBitmap != null) {
            drawImage(
                image = liveImageBitmap,
                dstOffset = IntOffset((left + 2f).toInt(), (top + 2f).toInt()),
                dstSize = IntSize((screenW - 4f).toInt().coerceAtLeast(1), (screenH - 4f).toInt().coerceAtLeast(1)),
                alpha = appAlpha
            )
            drawRoundRect(
                color = Color(0xAA00E5FF),
                topLeft = Offset(left, top),
                size = Size(screenW, screenH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                style = Stroke(width = 1.5f)
            )
        } else {
            // Simulated app video / content gradient fallback before first frame
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F172A).copy(alpha = appAlpha),
                        Color(0xFF1E1B4B).copy(alpha = appAlpha),
                        Color(0xFF0369A1).copy(alpha = appAlpha)
                    ),
                    start = Offset(left, top),
                    end = Offset(left + screenW, top + screenH)
                ),
                topLeft = Offset(left + 2, top + 2),
                size = Size(screenW - 4, screenH - 4),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )

            // Simulated UI header bar
            drawRect(
                color = Color(0x66000000),
                topLeft = Offset(left + 2, top + 2),
                size = Size(screenW - 4, screenH * 0.18f)
            )

            // Simulated play button / center content
            val playRadius = 12f * scale
            drawCircle(
                color = Color(0x9900E5FF),
                radius = playRadius,
                center = Offset(centerX, centerY)
            )
        }

        // Concave Curved Cinema Screen Top & Bottom Perspective Arcs
        if (settings.cinemaScreenEnabled && settings.cinemaCurvature > 0.01f) {
            val curveDip = (screenH * 0.18f * settings.cinemaCurvature).coerceIn(2f, 22f)
            val topCurveMask = Path().apply {
                moveTo(left - 2f, top - 2f)
                lineTo(left + screenW + 2f, top - 2f)
                lineTo(left + screenW + 2f, top)
                quadraticTo(centerX, top + curveDip, left - 2f, top)
                close()
            }
            val bottomCurveMask = Path().apply {
                moveTo(left - 2f, top + screenH + 2f)
                lineTo(left + screenW + 2f, top + screenH + 2f)
                lineTo(left + screenW + 2f, top + screenH)
                quadraticTo(centerX, top + screenH - curveDip, left - 2f, top + screenH)
                close()
            }
            val maskBg = if (settings.arPassthroughEnabled) Color(0xFF142822) else Color.Black
            drawPath(path = topCurveMask, color = maskBg)
            drawPath(path = bottomCurveMask, color = maskBg)

            val curvedBorderPath = Path().apply {
                moveTo(left, top)
                quadraticTo(centerX, top + curveDip, left + screenW, top)
                lineTo(left + screenW, top + screenH)
                quadraticTo(centerX, top + screenH - curveDip, left, top + screenH)
                close()
            }
            drawPath(
                path = curvedBorderPath,
                color = Color(0xDD00E5FF),
                style = Stroke(width = 1.8f)
            )
        }

        // Vision Pro spatial glass border in AR mode
        if (settings.arPassthroughEnabled) {
            drawRoundRect(
                color = Color(0xCC00E5FF),
                topLeft = Offset(left, top),
                size = Size(screenW, screenH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                style = Stroke(width = 2f)
            )
        }

        // Draw 3D Stereoscopic VR Cursor Reticle + Trackpad Click Ripple Effect
        if (settings.gearVrTouchpadEnabled || settings.handTrackingEnabled) {
            val cursorPxX = left + (settings.cursorX * screenW)
            val cursorPxY = top + (settings.cursorY * screenH)
            val cursorColor = if (settings.isCursorClicking) Color(0xFF00E676) else Color(0xFF00E5FF)
            val outerR = if (settings.isCursorClicking) 12f else 7.5f

            // Trackpad Click Ripple Burst when user taps the trackpad
            if (settings.isCursorClicking) {
                drawCircle(
                    color = Color(0x6600E676),
                    radius = outerR + 6f,
                    center = Offset(cursorPxX, cursorPxY),
                    style = Stroke(width = 3.5f)
                )
            }

            drawCircle(
                color = Color(0xCC000000),
                radius = outerR + 2f,
                center = Offset(cursorPxX, cursorPxY),
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = cursorColor,
                radius = outerR,
                center = Offset(cursorPxX, cursorPxY),
                style = Stroke(width = 2.2f)
            )
            // Center Dot Clicker
            drawCircle(
                color = if (settings.isCursorClicking) Color.White else Color(0xFF00E676),
                radius = if (settings.isCursorClicking) 5.0f else 3.0f,
                center = Offset(cursorPxX, cursorPxY)
            )
        }

        // Barrel distortion curved simulation outline if active
        if (settings.barrelK1 > 0.001f) {
            val barrelPath = Path().apply {
                val bulge = 6f * (settings.barrelK1 / 0.12f)
                moveTo(left, top)
                quadraticTo(centerX, top - bulge, left + screenW, top)
                quadraticTo(left + screenW + bulge, centerY, left + screenW, top + screenH)
                quadraticTo(centerX, top + screenH + bulge, left, top + screenH)
                quadraticTo(left - bulge, centerY, left, top)
                close()
            }
            drawPath(
                path = barrelPath,
                color = Color(0x44B388FF),
                style = Stroke(width = 2f)
            )
        }

        // Circular lens boundary mask
        if (settings.maskType == 1) {
            val lensRadius = (minOf(w, h) / 2f) * 0.96f
            drawCircle(
                color = Color.Black,
                radius = lensRadius + 20f,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = 40f)
            )
            drawCircle(
                color = Color(0x3300E5FF),
                radius = lensRadius,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = 1.5f)
            )
        }

        // Alignment crosshair if enabled
        if (settings.showCalibrationGrid) {
            drawLine(
                color = Color(0x9900E5FF),
                start = Offset(w / 2f - 15f, h / 2f),
                end = Offset(w / 2f + 15f, h / 2f),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color(0x9900E5FF),
                start = Offset(w / 2f, h / 2f - 15f),
                end = Offset(w / 2f, h / 2f + 15f),
                strokeWidth = 1.5f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = eyeLabel,
            color = Color(0x88FFFFFF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
