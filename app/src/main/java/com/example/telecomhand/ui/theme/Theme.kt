package com.example.telecomhand.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val InkScheme = darkColorScheme(
    primary = Raspberry,
    onPrimary = Color.White,
    primaryContainer = RaspberryDeep,
    onPrimaryContainer = Color.White,
    secondary = Lagoon,
    onSecondary = Ink,
    secondaryContainer = Surface3,
    onSecondaryContainer = TextPrimary,
    tertiary = Amber,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Color(0xFF3B3F4D),
    outlineVariant = Hairline,
    error = Danger
)

@Composable
fun TelecomHandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkScheme, typography = Typography, content = content)
}
