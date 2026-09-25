package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VrSettings
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrDeepSpace
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon

@Composable
fun CalibrationScreen(
    currentSettings: VrSettings,
    onSaveSettings: (VrSettings) -> Unit,
    onNavigateBack: () -> Unit
) {
    var ipdOffset by remember { mutableFloatStateOf(currentSettings.ipdOffset) }
    var scale by remember { mutableFloatStateOf(currentSettings.scale) }
    var yOffset by remember { mutableFloatStateOf(currentSettings.yOffset) }
    var showHelp by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 1.25f)
                    ipdOffset = (ipdOffset + (pan.x / 1500f)).coerceIn(-0.15f, 0.15f)
                    yOffset = (yOffset - (pan.y / 2000f)).coerceIn(-0.25f, 0.25f)
                }
            }
    ) {
        // Dual Eye Calibration Targets
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye Target
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                CalibrationEyeTarget(
                    label = "LEFT EYE",
                    isLeft = true,
                    ipdOffset = ipdOffset,
                    scale = scale,
                    yOffset = yOffset
                )
            }

            // Center Nose Divider Line
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F172A), VrCyanNeon, Color(0xFF0F172A))
                        )
                    )
            )

            // Right Eye Target
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                CalibrationEyeTarget(
                    label = "RIGHT EYE",
                    isLeft = false,
                    ipdOffset = ipdOffset,
                    scale = scale,
                    yOffset = yOffset
                )
            }
        }

        // Top bar with exit and save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x990D1117))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = VrCyanNeon
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        ipdOffset = 0.02f
                        scale = 0.85f
                        yOffset = 0.0f
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x990D1117))
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        onSaveSettings(
                            currentSettings.copy(
                                ipdOffset = ipdOffset,
                                scale = scale,
                                yOffset = yOffset
                            )
                        )
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VrCyanNeon),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply & Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Floating HUD indicator at bottom center
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xDD0F172A))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pinch to Zoom FOV • Drag Left/Right to Adjust IPD",
                style = MaterialTheme.typography.bodySmall,
                color = VrCyanNeon,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "IPD: ${(ipdOffset * 100).toInt()} • Scale: ${(scale * 100).toInt()}% • Alignment Y: ${(yOffset * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = VrVioletNeon,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CalibrationEyeTarget(
    label: String,
    isLeft: Boolean,
    ipdOffset: Float,
    scale: Float,
    yOffset: Float
) {
    val ipdShift = if (isLeft) -(ipdOffset * 120f) else (ipdOffset * 120f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f + ipdShift
        val cy = h / 2f - (yOffset * 150f)

        // Concentric optical circles
        val maxRadius = (minOf(w, h) / 2.2f) * scale
        val ringCount = 5

        for (i in 1..ringCount) {
            val r = maxRadius * (i.toFloat() / ringCount)
            drawCircle(
                color = if (i == ringCount) Color(0xFF00E5FF) else Color(0x3300E5FF),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = if (i == ringCount) 2f else 1f)
            )
        }

        // Crosshairs
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(cx - maxRadius * 1.15f, cy),
            end = Offset(cx + maxRadius * 1.15f, cy),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(cx, cy - maxRadius * 1.15f),
            end = Offset(cx, cy + maxRadius * 1.15f),
            strokeWidth = 2f
        )

        // Inner focal dot
        drawCircle(
            color = Color(0xFF00E676),
            radius = 6f,
            center = Offset(cx, cy)
        )

        // 10-degree tick marks
        val tickRadius = maxRadius * 0.7f
        for (angle in 0 until 360 step 30) {
            val rad = Math.toRadians(angle.toDouble())
            val x1 = cx + (tickRadius - 10f) * Math.cos(rad).toFloat()
            val y1 = cy + (tickRadius - 10f) * Math.sin(rad).toFloat()
            val x2 = cx + (tickRadius + 10f) * Math.cos(rad).toFloat()
            val y2 = cy + (tickRadius + 10f) * Math.sin(rad).toFloat()
            drawLine(
                color = Color(0x66B388FF),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 1.5f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 70.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = label,
            color = Color(0x9900E5FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}
