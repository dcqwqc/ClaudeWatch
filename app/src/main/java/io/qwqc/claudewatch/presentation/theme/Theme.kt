package io.qwqc.claudewatch.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val ClaudeColors = Colors(
    primary = ClaudePalette.Orange,
    primaryVariant = ClaudePalette.OrangeDeep,
    secondary = ClaudePalette.Amber,
    secondaryVariant = ClaudePalette.OrangeBright,
    background = ClaudePalette.Ink,
    surface = ClaudePalette.Surface,
    error = ClaudePalette.Red,
    onPrimary = ClaudePalette.Black,
    onSecondary = ClaudePalette.Black,
    onBackground = ClaudePalette.Cream,
    onSurface = ClaudePalette.Cream,
    onSurfaceVariant = ClaudePalette.Sand,
    onError = ClaudePalette.Black,
)

@Composable
fun ClaudeWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = ClaudeColors,
        typography = ClaudeTypography,
        content = content,
    )
}
