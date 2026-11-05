package com.example.telecomhand.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MonochromeScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1C1C1C),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFF8C8C8C),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF1F1F1F),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF242424)
)

@Composable
fun TelecomHandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonochromeScheme, typography = Typography, content = content)
}
