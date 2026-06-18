package io.qwqc.claudewatch.data.terminal

/**
 * A run of consecutive cells sharing one style. Colors are concrete ARGB ints
 * (defaults already resolved + inverse already applied), so the renderer is dumb.
 */
data class TermSpan(
    val text: String,
    val fg: Int,
    val bg: Int,
    val bold: Boolean,
)

/** An immutable view of the terminal grid, safe to hand to Compose. */
data class TerminalSnapshot(
    val rows: List<List<TermSpan>>,
    val cols: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val mouseTracking: Boolean,
    val mouseSgr: Boolean,
) {
    companion object {
        val Empty = TerminalSnapshot(emptyList(), 0, 0, 0, false, false, false)
    }
}
