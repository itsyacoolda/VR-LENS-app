package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VrSettings
import com.example.ui.components.LiveSbsPreview
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrDeepSpace
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: VrSettings,
    onSettingsChanged: (VrSettings) -> Unit,
    onPresetSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val presets = listOf(
        VrSettings.PRESET_GEAR_VR_S23,
        VrSettings.PRESET_CARDBOARD,
        VrSettings.PRESET_DAYDREAM,
        VrSettings.PRESET_CINEMA_FLAT,
        VrSettings.PRESET_CURVED_3D
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VR Optics & SBS Tuning",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VrTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VrCyanNeon
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onPresetSelected(VrSettings.PRESET_GEAR_VR_S23)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            tint = VrCyanNeon
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VrDeepSpace)
            )
        },
        containerColor = VrDeepSpace
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Live Simulation Box
            LiveSbsPreview(settings = settings)

            Spacer(modifier = Modifier.height(16.dp))

            // Presets row
            Text(
                text = "HEADSET PRESETS",
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
                            .padding(horizontal = 12.dp, vertical = 8.dp)
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

            Spacer(modifier = Modifier.height(20.dp))

            // Optical Alignment Card
            SettingGroupCard(title = "OPTICAL ALIGNMENT (IPD & FOV)", icon = Icons.Default.FilterCenterFocus) {
                // IPD
                SliderItem(
                    label = "SBS Split / Interpupillary Distance (IPD Shift)",
                    valueText = "${(settings.ipdOffset * 100).toInt()}",
                    value = settings.ipdOffset,
                    range = -0.15f..0.15f,
                    onValueChange = { onSettingsChanged(settings.copy(ipdOffset = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )

                // Scale / FOV
                SliderItem(
                    label = "Overlay Window Resize / Zoom (Scale)",
                    valueText = "${(settings.scale * 100).toInt()}%",
                    value = settings.scale,
                    range = 0.50f..1.25f,
                    onValueChange = { onSettingsChanged(settings.copy(scale = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )

                // Central Split Gap
                SliderItem(
                    label = "Central Split Gap Width (Nose Seam Spacer)",
                    valueText = "${(settings.splitGapWidth * 100).toInt()}%",
                    value = settings.splitGapWidth,
                    range = 0.0f..0.10f,
                    onValueChange = { onSettingsChanged(settings.copy(splitGapWidth = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )

                // Vertical Y Alignment
                SliderItem(
                    label = "Vertical Optical Centering",
                    valueText = "${(settings.yOffset * 100).toInt()}",
                    value = settings.yOffset,
                    range = -0.20f..0.20f,
                    onValueChange = { onSettingsChanged(settings.copy(yOffset = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lens Curvature & Distortion Card
            SettingGroupCard(title = "LENS DISTORTION CORRECTION COEFFICIENTS", icon = Icons.Default.Tune) {
                // Focal Length Parameter
                SliderItem(
                    label = "Headset Lens Focal Length (Gear VR = 42mm)",
                    valueText = "${settings.focalLength.toInt()} mm",
                    value = settings.focalLength,
                    range = 25.0f..70.0f,
                    onValueChange = { onSettingsChanged(settings.copy(focalLength = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )

                // Distortion K1
                SliderItem(
                    label = "Primary Radial Distortion (K1 Curvature)",
                    valueText = String.format("%.2f", settings.barrelK1),
                    value = settings.barrelK1,
                    range = 0.0f..0.35f,
                    onValueChange = { onSettingsChanged(settings.copy(barrelK1 = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )

                // Distortion K2
                SliderItem(
                    label = "Secondary Edge Distortion (K2 Warp)",
                    valueText = String.format("%.3f", settings.barrelK2),
                    value = settings.barrelK2,
                    range = 0.0f..0.15f,
                    onValueChange = { onSettingsChanged(settings.copy(barrelK2 = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )

                // Distortion K3
                SliderItem(
                    label = "Higher-Order Lens Correction (K3)",
                    valueText = String.format("%.3f", settings.barrelK3),
                    value = settings.barrelK3,
                    range = 0.0f..0.05f,
                    onValueChange = { onSettingsChanged(settings.copy(barrelK3 = it, presetName = VrSettings.PRESET_CUSTOM)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chromatic Aberration Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chromatic Aberration Lens Compensation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VrTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Counteracts color separation (red/blue fringing) on fresnel lenses",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.chromaticAberration,
                        onCheckedChange = { onSettingsChanged(settings.copy(chromaticAberration = it, presetName = VrSettings.PRESET_CUSTOM)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrVioletNeon,
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mask Type Selector
                Text(
                    text = "Perimeter Lens Mask",
                    style = MaterialTheme.typography.bodySmall,
                    color = VrTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val maskOptions = listOf("None", "Circular VR Lens", "Soft Vignette")
                    maskOptions.forEachIndexed { index, name ->
                        val selected = settings.maskType == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) VrVioletNeon else Color(0xFF0F172A))
                                .border(1.dp, if (selected) VrVioletNeon else VrCardBorder, RoundedCornerShape(10.dp))
                                .clickable { onSettingsChanged(settings.copy(maskType = index, presetName = VrSettings.PRESET_CUSTOM)) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = if (selected) Color.Black else VrTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Screen & Controls Card
            SettingGroupCard(title = "BEHAVIOR & 3D DEPTH", icon = Icons.Default.TouchApp) {
                // Touch pass-through toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Touch Pass-Through Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VrTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Touches pass directly to underlying app while VR SBS overlay is active",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.touchPassThrough,
                        onCheckedChange = { onSettingsChanged(settings.copy(touchPassThrough = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrGreenNeon,
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Calibration Grid toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Alignment Crosshair Grid",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VrTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Displays center crosshairs for lens alignment in headset",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.showCalibrationGrid,
                        onCheckedChange = { onSettingsChanged(settings.copy(showCalibrationGrid = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = VrCyanNeon,
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3D Parallax Depth
                SliderItem(
                    label = "Stereoscopic 3D Depth Parallax",
                    valueText = "${(settings.parallax3D * 1000).toInt()}",
                    value = settings.parallax3D,
                    range = 0.0f..0.03f,
                    onValueChange = { onSettingsChanged(settings.copy(parallax3D = it)) }
                )

                // Brightness
                SliderItem(
                    label = "Screen Brightness Multiplier",
                    valueText = "${(settings.brightness * 100).toInt()}%",
                    value = settings.brightness,
                    range = 0.5f..1.3f,
                    onValueChange = { onSettingsChanged(settings.copy(brightness = it)) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingGroupCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VrCardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VrCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VrCyanNeon,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = VrCyanNeon,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SliderItem(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = VrTextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = VrVioletNeon,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = VrCyanNeon,
                activeTrackColor = VrCyanNeon,
                inactiveTrackColor = Color(0xFF334155)
            )
        )
    }
}
