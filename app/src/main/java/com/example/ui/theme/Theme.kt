package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VrDarkColorScheme = darkColorScheme(
    primary = VrCyanNeon,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = VrVioletNeon,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3B1E78),
    onSecondaryContainer = Color(0xFFEADBFF),
    tertiary = VrGreenNeon,
    onTertiary = Color.Black,
    background = VrDeepSpace,
    onBackground = VrTextPrimary,
    surface = VrSurfaceDark,
    onSurface = VrTextPrimary,
    surfaceVariant = VrCardSurface,
    onSurfaceVariant = VrTextSecondary,
    outline = VrCardBorder,
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For VR applications, always provide the deep-space optics dark theme
    MaterialTheme(
        colorScheme = VrDarkColorScheme,
        typography = Typography,
        content = content
    )
}
