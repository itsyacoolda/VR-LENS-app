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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VrSettings
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanGlow
import com.example.ui.theme.VrCyanNeon
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Lens: ${if (settings.barrelK1 > 0.001f) "Curved Barrel (Gear VR)" else "Flat Cinema"}",
                style = MaterialTheme.typography.bodySmall,
                color = VrTextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = "Mask: ${when(settings.maskType) { 1 -> "Circular VR"; 2 -> "Soft Vignette"; else -> "Rectangle" }}",
                style = MaterialTheme.typography.bodySmall,
                color = VrTextSecondary,
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
    val ipdShift = if (isLeft) -(settings.ipdOffset * 25f) else (settings.ipdOffset * 25f)
    val scale = settings.scale.coerceIn(0.5f, 1.2f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f + ipdShift
        val centerY = h / 2f - (settings.yOffset * 30f)

        // Draw simulated phone screen in VR space
        val screenW = (w * 0.88f) * scale
        val screenH = (h * 0.85f) * scale

        val left = centerX - screenW / 2f
        val top = centerY - screenH / 2f

        // Simulated screen content background
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(left, top),
            size = Size(screenW, screenH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )

        // Simulated app video / content gradient
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0369A1)),
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
