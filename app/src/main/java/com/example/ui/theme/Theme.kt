package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CinemaBlack,
    primaryContainer = CinemaSurfaceVariant,
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = CinemaBlack,
    secondaryContainer = CinemaSurfaceVariant,
    onSecondaryContainer = NeonPurple,
    tertiary = NeonAmber,
    onTertiary = CinemaBlack,
    background = CinemaBlack,
    onBackground = TextPrimary,
    surface = CinemaDark,
    onSurface = TextPrimary,
    surfaceVariant = CinemaSurface,
    onSurfaceVariant = TextSecondary,
    outline = CinemaCardBorder,
    error = NeonRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
