package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrDeepSpace
import com.example.ui.theme.VrGreenNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearVrGuideScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Gear VR & S23+ Setup Guide",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VrDeepSpace
                )
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
            // Hero info callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F1B2E))
                    .border(1.dp, VrCyanNeon.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = VrCyanNeon,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Optimized for Galaxy S23+ & Gear VR",
                            style = MaterialTheme.typography.titleSmall,
                            color = VrCyanNeon,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your Samsung Galaxy S23+ has a 6.6-inch Dynamic AMOLED 2X screen with 120Hz refresh rate. When paired with Gear VR lenses, this app transforms any 2D app into an immersive giant cinema VR experience!",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "STEP-BY-STEP VR INSTRUCTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = VrVioletNeon,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideStepCard(
                stepNumber = "1",
                icon = Icons.Default.Usb,
                iconColor = VrCyanNeon,
                title = "Hardware Fitting & USB Connector",
                description = "The Galaxy S23+ fits securely in the Gear VR (SM-R324 / SM-R325). Make sure the USB-C connector arm is set to position 'B' (larger phone size). If you do not want the USB to engage, you can leave the connector flipped up or slide the phone right under the spring clamp as optics-only."
            )

            GuideStepCard(
                stepNumber = "2",
                icon = Icons.Default.Speed,
                iconColor = VrGreenNeon,
                title = "120Hz Motion Smoothness on S23+",
                description = "For the smoothest head movement and zero blur in VR:\n• Open S23+ Settings -> Display -> Motion Smoothness -> Select 'Adaptive' (120Hz).\n• Turn off Auto-Brightness to maintain maximum contrast in the dark headset."
            )

            GuideStepCard(
                stepNumber = "3",
                icon = Icons.Default.Tune,
                iconColor = VrVioletNeon,
                title = "Calibrate IPD & Lens Distortion",
                description = "• Put on the headset and adjust the Gear VR's top focus wheel to sharpen the center optics.\n• Use the 'Calibrate Lenses' screen in this app to align the left and right crosshairs until your eyes see a single crisp 3D focal image without double vision."
            )

            GuideStepCard(
                stepNumber = "4",
                icon = Icons.Default.Gamepad,
                iconColor = Color(0xFFFF9100),
                title = "Gear VR Touchpad & Trusted Touch (Zero Dev Options)",
                description = "Works on any unmodified school or teacher phone without touching Developer Options:\n\n" +
                        "• Enable Trusted Touch: Tap 'Enable Trusted Touch & Gear VR Pad' on the Home screen and toggle ON 'VR Lens SBS Touch & Gear VR Touchpad' in Accessibility. This upgrades the overlay to a trusted OS layer (TYPE_ACCESSIBILITY_OVERLAY) so Android 12+ never blocks touches as 'untrusted'!\n" +
                        "• Physical Gear VR Touchpad: When plugged into the Gear VR USB-C port, swipe on the headset's side touchpad to move the 3D Stereoscopic Cursor across both eyes, tap to click, and press the Gear VR Back button to go back.\n" +
                        "• Right-Edge Thumb Trackpad: Even if the USB-C connector is flipped up or the phone has a case, use the floating 'GEAR PAD' on the right edge of the screen to move the 3D cursor, click, scroll, or navigate Back!"
            )

            GuideStepCard(
                stepNumber = "5",
                icon = Icons.Default.CheckCircle,
                iconColor = VrCyanNeon,
                title = "Floating Quick Seam Controller",
                description = "A slim control pill sits along the center divider between the lenses. Tap it anytime to toggle between Touch Pass-Through (underlying app), 🌐 AR Camera mode, 🖐️ Hand AI gestures, or to stop VR."
            )

            GuideStepCard(
                stepNumber = "6",
                icon = Icons.Default.Visibility,
                iconColor = VrGreenNeon,
                title = "Apple Vision Pro Hand AI & AR Mode (In-Dev)",
                description = "Use your S23+ rear camera for spatial hand gestures & Mixed-Reality AR while running any app:\n" +
                        "• Selectable In-Dev Feature: Tap 'USE VISION PRO HAND GESTURES (IN-DEV)' or 'ENABLE AUGMENTED REALITY (AR) + HAND CAM' on the Home screen (or tap 🌐 AR / 🖐️ HAND on the center VR seam HUD).\n" +
                        "• Hand Gestures & Gemini AI Vision: Wave your hand Left in front of the camera to slide Left in the active app, wave Right to slide Right, wave Up/Down to scroll, or Pinch thumb + index finger to click at the 3D Cursor.\n" +
                        "• Augmented Reality (AR) Passthrough: Projects the live outward camera into both VR lenses with any app floating as a translucent spatial window over your real room!"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = VrCardSurface
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VrCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f))
                    .border(1.dp, iconColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    color = iconColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VrTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VrTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
