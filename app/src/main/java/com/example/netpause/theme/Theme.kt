package com.example.netpause.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StillwaterColors = lightColorScheme(
    primary = Magenta,
    onPrimary = Color.White,
    primaryContainer = Pink,
    onPrimaryContainer = Plum,
    secondary = MutedPlum,
    onSecondary = Color.White,
    secondaryContainer = Pink,
    onSecondaryContainer = Plum,
    background = Paper,
    onBackground = Plum,
    surface = Paper,
    onSurface = Plum,
    surfaceVariant = Pink,
    onSurfaceVariant = MutedPlum,
    surfaceContainer = Pink,
    surfaceContainerLow = Pink,
    surfaceContainerHigh = Pink,
    surfaceContainerHighest = Pink,
    outline = MutedPlum,
    outlineVariant = Edge,
    error = Magenta,
    scrim = Plum,
)

@Composable
fun NetPauseTheme(content: @Composable () -> Unit) {
    // The selected prototype has a fixed light palette, independent of wallpaper colors.
    MaterialTheme(colorScheme = StillwaterColors, typography = Typography, content = content)
}
