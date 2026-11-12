package com.jellyflix.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val JellyflixColors = darkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB20710),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFB3B3B3),
    onSecondary = Color(0xFF141414),
    background = Color(0xFF141414),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFE5E5E5),
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color(0xFFB3B3B3),
)

@Composable
fun JellyflixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JellyflixColors,
        typography = JellyflixTypography,
        shapes = JellyflixShapes,
        content = content,
    )
}
