package io.qwqc.claudewatch.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Claude-inspired palette: warm orange on near-black, with cream text.
 * These are original brand-adjacent colors, not copied Anthropic assets.
 */
object ClaudePalette {
    val Orange = Color(0xFFD97757)        // primary "Claude clay" orange
    val OrangeBright = Color(0xFFE8956F)  // highlight / pressed
    val OrangeDeep = Color(0xFFB85C3E)    // variant / shadow
    val Amber = Color(0xFFE6A86B)         // secondary accent (weekly ring)

    val Black = Color(0xFF000000)
    val Ink = Color(0xFF0C0C0C)           // app background
    val Surface = Color(0xFF1A1512)       // cards / sheets (warm dark)
    val SurfaceHi = Color(0xFF2A211B)     // raised surface

    val Cream = Color(0xFFF5E9DC)         // primary text
    val Sand = Color(0xFFC9BBAD)          // secondary text
    val Muted = Color(0xFF8A7F74)         // tertiary / hints

    val Green = Color(0xFF7FB069)         // "ok" / under limit
    val Red = Color(0xFFE5564E)           // error / over limit

    // Terminal default fg/bg (used when a cell has no explicit color)
    val TermBg = Color(0xFF0C0C0C)
    val TermFg = Color(0xFFE8E0D5)
}
