package com.example.telecomhand.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CD8FF),
    secondary = Color(0xFF7967FF),
    tertiary = Color(0xFF50E3A4),
    background = Color(0xFF070B13),
    surface = Color(0xFF111824),
    surfaceVariant = Color(0xFF1B2638),
    onPrimary = Color(0xFF001F29),
    onBackground = Color(0xFFF3F7FF),
    onSurface = Color(0xFFF3F7FF),
    onSurfaceVariant = Color(0xFF91A1B7)
)

@Composable
fun TelecomHandTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
