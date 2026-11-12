package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PaperRed,
    onPrimary = Color.White,
    primaryContainer = PaperOnRedContainer,
    onPrimaryContainer = PaperRedContainer,
    secondary = PaperDarkRed,
    onSecondary = Color.White,
    background = PaperBackgroundDark,
    onBackground = PaperTextPrimaryDark,
    surface = PaperSurfaceDark,
    onSurface = PaperTextPrimaryDark,
    surfaceVariant = Color(0xFF28282E),
    onSurfaceVariant = PaperTextSecondaryDark,
    outline = PaperBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = PaperRed,
    onPrimary = Color.White,
    primaryContainer = PaperLightRed,
    onPrimaryContainer = PaperOnRedContainer,
    secondary = PaperDarkRed,
    onSecondary = Color.White,
    background = PaperBackgroundLight,
    onBackground = PaperTextPrimaryLight,
    surface = PaperSurfaceLight,
    onSurface = PaperTextPrimaryLight,
    surfaceVariant = Color(0xFFF0F0F3),
    onSurfaceVariant = PaperTextSecondaryLight,
    outline = PaperBorderLight
)

@Composable
fun PaperFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
