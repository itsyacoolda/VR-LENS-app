package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * Interactive Onboarding & Calibration Wizard for new VR users.
 * Guides beginners step-by-step through:
 * 1. Headset & Experience Selection
 * 2. Eye Comfort & Double Vision Calibration
 * 3. Screen Size / Zoom Tuning
 * 4. Interactive Trackpad & Gesture Training
 * 5. Ready-to-Play Launch
 *
 * Keeps complex toggles hidden until setup is finished.
 */
@Composable
fun VrOnboardingWizard(
    settings: VrSettings,
    onStepChanged: (Int) -> Unit,
    onComplete: () -> Unit,
    onStartVr: () -> Unit,
    onUpdateSettings: (VrSettings) -> Unit,
    onPresetSelected: (String) -> Unit,
    onAdjustScale: (Float) -> Unit,
    onAdjustSplit: (Float) -> Unit,
    onMoveCursor: (Float, Float) -> Unit,
    onPulseCursorClick: () -> Unit,
    onRecenterCursor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStep = settings.onboardingStep.coerceIn(0, 4)

    // System Back button handling: Navigate to previous step, or if at step 0 return directly to Dashboard
    BackHandler {
        if (currentStep > 0) {
            onStepChanged(currentStep - 1)
        } else {
            onComplete()
        }
    }

    // Interactive Trackpad training milestone checks
    var trainedMove by remember { mutableStateOf(false) }
    var trainedClick by remember { mutableStateOf(false) }
    var trainedRecenter by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vr_onboarding_wizard_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1424)),
        border = BorderStroke(2.dp, VrCyanNeon),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // =========================================================================
            // RECOVERY BANNER: HELPS USERS WHO CLICKED BACK BY ACCIDENT
            // =========================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .testTag("accidental_back_recovery_banner"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14243B)),
                border = BorderStroke(1.dp, VrGreenNeon.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💾", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Clicked back to start by accident?",
                                color = VrGreenNeon,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Your settings are safely saved! Active: ${settings.presetName}",
                                color = VrTextPrimary,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = VrGreenNeon),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚡ Return to App",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Header: Wizard Title & Step Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(VrCyanNeon.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${currentStep + 1}",
                            color = VrCyanNeon,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "VR SETUP WIZARD",
                            color = VrCyanNeon,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = when (currentStep) {
                                0 -> "Step 1 of 4 • Select Your Headset"
                                1 -> "Step 2 of 4 • Eye Alignment & Comfort"
                                2 -> "Step 3 of 4 • Screen Size & Zoom"
                                3 -> "Step 4 of 4 • Trackpad Training"
                                else -> "All Set! Ready for VR"
                            },
                            color = VrTextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Return to Dashboard pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onComplete() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit to Dashboard",
                        tint = VrCyanNeon,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Exit to App",
                        color = VrCyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Linear Step Progress
            LinearProgressIndicator(
                progress = { ((currentStep + 1) / 5f).coerceIn(0.1f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = VrCyanNeon,
                trackColor = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // STEP CONTENT WITH ANIMATED TRANSITIONS
            // =========================================================================
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "onboarding_step_content"
            ) { step ->
                when (step) {
                    0 -> OnboardingStepHeadset(
                        settings = settings,
                        onPresetSelected = onPresetSelected,
                        onUpdateSettings = onUpdateSettings,
                        onComplete = onComplete
                    )
                    1 -> OnboardingStepEyeComfort(
                        settings = settings,
                        onAdjustSplit = onAdjustSplit,
                        onUpdateSettings = onUpdateSettings
                    )
                    2 -> OnboardingStepScreenSize(
                        settings = settings,
                        onAdjustScale = onAdjustScale,
                        onUpdateSettings = onUpdateSettings
                    )
                    3 -> OnboardingStepTrackpadTraining(
                        settings = settings,
                        trainedMove = trainedMove,
                        trainedClick = trainedClick,
                        trainedRecenter = trainedRecenter,
                        onMoveCursor = { dx, dy ->
                            trainedMove = true
                            onMoveCursor(dx, dy)
                        },
                        onPulseCursorClick = {
                            trainedClick = true
                            onPulseCursorClick()
                        },
                        onRecenterCursor = {
                            trainedRecenter = true
                            onRecenterCursor()
                        }
                    )
                    else -> OnboardingStepFinished(
                        settings = settings,
                        onStartVr = onStartVr,
                        onComplete = onComplete
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // NAVIGATION BUTTONS: PREVIOUS / NEXT / FINISH
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    Button(
                        onClick = { onStepChanged(currentStep - 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("◀ Back", color = VrTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("✕ Exit to App", color = VrCyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14243B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Keep Saved Setup", color = VrGreenNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (currentStep < 4) {
                    Button(
                        onClick = { onStepChanged(currentStep + 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = VrCyanNeon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (currentStep) {
                                0 -> "Next: Eye Comfort ➔"
                                1 -> "Next: Screen Size ➔"
                                2 -> "Next: Trackpad Training ➔"
                                else -> "Finish Calibration ➔"
                            },
                            color = Color.Black,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// STEP 0: SELECT HEADSET
// =============================================================================
@Composable
private fun OnboardingStepHeadset(
    settings: VrSettings,
    onPresetSelected: (String) -> Unit,
    onUpdateSettings: (VrSettings) -> Unit,
    onComplete: () -> Unit
) {
    Column {
        // Helpful Tooltip Bubble
        TooltipBubble(
            tip = "Choose your VR headset below. We'll automatically apply the right lens curve and optical separation so you get a clear, immersive view right away."
        )

        Spacer(modifier = Modifier.height(12.dp))

        val options = listOf(
            Triple(
                VrSettings.PRESET_CINEMA,
                "🎬 Cinema (IMAX Curved Screen)",
                "Theater aspect ratio (1.18x) with gentle 40% wrap curvature & ambient bounce glow. Ideal for movies."
            ),
            Triple(
                VrSettings.PRESET_FLAT,
                "📐 Flat (Zero Distortion)",
                "Completely flat 16:9 display without optical warping. Razor-sharp for reading, Chrome & Android UI."
            ),
            Triple(
                VrSettings.PRESET_CURVED,
                "🌊 Curved (Ultra-Wide 3D)",
                "Deep panoramic 58% wrap-around screen with expansive 1.25x aspect ratio for maximum immersion."
            ),
            Triple(
                VrSettings.PRESET_GEAR_VR_S23,
                "🎮 Gear VR (Galaxy S23+)",
                "Custom tuned for Samsung phones & Gear VR USB-C headset with side trackpad and buttons."
            ),
            Triple(
                "AR_MODE",
                "🌐 Augmented Reality & Hand AI",
                "See your real room with floating SBS window and hands-free camera gestures."
            )
        )

        options.forEach { (key, title, desc) ->
            val isSelected = if (key == "AR_MODE") settings.arPassthroughEnabled
            else settings.presetName == key && !settings.arPassthroughEnabled

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF142436) else Color(0xFF131A29))
                    .border(
                        width = if (isSelected) 1.8.dp else 1.dp,
                        color = if (isSelected) VrCyanNeon else VrCardBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (key == "AR_MODE") {
                            onUpdateSettings(
                                settings.copy(
                                    arPassthroughEnabled = true,
                                    handTrackingEnabled = true,
                                    showCameraRadarPip = false
                                )
                            )
                        } else {
                            onUpdateSettings(settings.copy(arPassthroughEnabled = false))
                            onPresetSelected(key)
                        }
                    }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) VrCyanNeon else Color(0xFF334155))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            color = if (isSelected) VrCyanNeon else VrTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = desc,
                            color = VrTextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct return to dashboard button
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, VrCyanNeon.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = VrCyanNeon,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Keep Current Settings (${settings.presetName}) & Go to App",
                color = VrCyanNeon,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =============================================================================
// STEP 1: EYE COMFORT & DOUBLE VISION
// =============================================================================
@Composable
private fun OnboardingStepEyeComfort(
    settings: VrSettings,
    onAdjustSplit: (Float) -> Unit,
    onUpdateSettings: (VrSettings) -> Unit
) {
    Column {
        TooltipBubble(
            tip = "Every person's eyes have a unique distance between them (IPD). If you see double images, slide left or right until the two circles merge into a single clear 3D image!"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Visual Alignment Target
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF070B14))
                .border(1.dp, VrVioletNeon.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 [ Left Eye ]",
                    color = VrCyanNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width((24 + (settings.ipdOffset * 200)).dp.coerceAtLeast(8.dp)))
                Text(
                    text = "[ Right Eye ] 🎯",
                    color = VrVioletNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Eye Separation Offset",
                color = VrTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "◀ Left",
                    color = VrVioletNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clickable { onAdjustSplit(-0.01f) }
                        .padding(horizontal = 6.dp)
                )
                Text(
                    text = "${(settings.ipdOffset * 100).toInt()}",
                    color = VrVioletNeon,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Right ▶",
                    color = VrVioletNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
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
            colors = SliderDefaults.colors(thumbColor = VrVioletNeon, activeTrackColor = VrVioletNeon)
        )

        Button(
            onClick = { onUpdateSettings(settings.copy(ipdOffset = 0.01f)) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Reset to Recommended Default (0.01)", color = VrTextSecondary, fontSize = 11.sp)
        }
    }
}

// =============================================================================
// STEP 2: SCREEN SIZE & ZOOM
// =============================================================================
@Composable
private fun OnboardingStepScreenSize(
    settings: VrSettings,
    onAdjustScale: (Float) -> Unit,
    onUpdateSettings: (VrSettings) -> Unit
) {
    Column {
        TooltipBubble(
            tip = "Tune how large the screen appears. Increase size for an IMAX theater sensation, or decrease if the corners of the app are blocked by your headset's nose bridge."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Size Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sizes = listOf(
                "Compact (88%)" to 0.88f,
                "Standard (95%)" to 0.95f,
                "IMAX Giant (105%)" to 1.05f
            )
            sizes.forEach { (label, scaleVal) ->
                val isSelected = kotlin.math.abs(settings.scale - scaleVal) < 0.04f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) VrCyanNeon else Color(0xFF1E293B))
                        .clickable { onUpdateSettings(settings.copy(scale = scaleVal)) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else VrTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Screen Zoom Factor",
                color = VrTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
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
                    fontWeight = FontWeight.ExtraBold,
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
            colors = SliderDefaults.colors(thumbColor = VrCyanNeon, activeTrackColor = VrCyanNeon)
        )
    }
}

// =============================================================================
// STEP 3: TRACKPAD TRAINING (INTERACTIVE)
// =============================================================================
@Composable
private fun OnboardingStepTrackpadTraining(
    settings: VrSettings,
    trainedMove: Boolean,
    trainedClick: Boolean,
    trainedRecenter: Boolean,
    onMoveCursor: (Float, Float) -> Unit,
    onPulseCursorClick: () -> Unit,
    onRecenterCursor: () -> Unit
) {
    Column {
        TooltipBubble(
            tip = "Try out the Gear VR trackpad! Slide your thumb in the box below to aim your 3D reticle, tap once to click, and double-tap to snap the reticle back to center."
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Milestones Checklist
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MilestonePill(
                completed = trainedMove,
                label = "1. Slide to Move"
            )
            MilestonePill(
                completed = trainedClick,
                label = "2. Tap to Click"
            )
            MilestonePill(
                completed = trainedRecenter,
                label = "3. Double-Tap Recenter"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Test Sandbox Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF070B14))
                .border(
                    1.dp,
                    if (settings.isCursorClicking) VrGreenNeon else VrCyanNeon.copy(alpha = 0.7f),
                    RoundedCornerShape(14.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onPulseCursorClick() },
                        onDoubleTap = { onRecenterCursor() }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val w = size.width.coerceAtLeast(1).toFloat()
                        val h = size.height.coerceAtLeast(1).toFloat()
                        onMoveCursor(dragAmount.x / (w * 0.5f), dragAmount.y / (h * 0.5f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (settings.isCursorClicking) "⚡ CLICK TRIGGERED!" else "✨ TOUCHPAD PRACTICE SANDBOX",
                    color = if (settings.isCursorClicking) VrGreenNeon else VrCyanNeon,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "3D Reticle at (${(settings.cursorX * 100).toInt()}%, ${(settings.cursorY * 100).toInt()}%)",
                    color = VrTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// =============================================================================
// STEP 4: FINISHED & READY
// =============================================================================
@Composable
private fun OnboardingStepFinished(
    settings: VrSettings,
    onStartVr: () -> Unit,
    onComplete: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(VrGreenNeon.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = VrGreenNeon,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Calibration Complete!",
            color = VrGreenNeon,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Your headset is calibrated for ${settings.presetName}.\nAll complex technical toggles are safely organized in the clean Home view.",
            color = VrTextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                onComplete()
                onStartVr()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VrGreenNeon),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🚀 Launch VR SBS Now", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Explore Home Dashboard", color = VrTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TooltipBubble(tip: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101B2E))
            .border(1.dp, VrCyanNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Tip",
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tip,
                color = VrTextPrimary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun MilestonePill(
    completed: Boolean,
    label: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (completed) Color(0xFF142B20) else Color(0xFF1E293B))
            .border(
                1.dp,
                if (completed) VrGreenNeon else Color(0xFF334155),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (completed) Icons.Default.Check else Icons.Default.TouchApp,
                contentDescription = null,
                tint = if (completed) VrGreenNeon else VrTextSecondary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (completed) VrGreenNeon else VrTextSecondary,
                fontSize = 9.5.sp,
                fontWeight = if (completed) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
