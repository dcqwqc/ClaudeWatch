package io.qwqc.claudewatch.data.terminal

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * A compact VT100 / xterm-256color terminal emulator: enough of the protocol to
 * render tmux and the Claude Code TUI faithfully (alt screen, scroll regions,
 * SGR colors incl. 256/truecolor, line/char insert-delete, cursor save/restore).
 *
 * It is deliberately a pragmatic subset, not a complete VT implementation —
 * extend the CSI dispatch if you hit a sequence it doesn't handle.
 *
 * Control bytes are matched by code (0x1B etc.) on purpose, to avoid embedding
 * raw control characters in source. Not thread-safe; drive [feed] and
 * [snapshot] from a single owner thread.
 */
class TerminalEmulator(cols: Int, rows: Int) {

    var cols = cols; private set
    var rows = rows; private set

    private class Cell {
        var ch: Char = ' '
        var fg: Int = DEFAULT      // 0 sentinel = "use default fg"
        var bg: Int = DEFAULT
        var bold: Boolean = false
        var inverse: Boolean = false
        fun reset(curBg: Int) { ch = ' '; fg = DEFAULT; bg = curBg; bold = false; inverse = false }
    }

    private var primary = grid(rows, cols)
    private var alt = grid(rows, cols)
    private var buffer = primary
    private var altActive = false

    private var row = 0
    private var col = 0
    private var savedRow = 0
    private var savedCol = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    // current pen
    private var curFg = DEFAULT
    private var curBg = DEFAULT
    private var curBold = false
    private var curInverse = false

    var cursorVisible = true; private set

    // Mouse reporting requested by the app. tmux turns these on when `mouse on`;
    // the UI forwards bezel scrolls as wheel events only while [mouseTracking] is
    // set, so they never leak into a plain shell as text. [mouseSgr] selects the
    // coordinate encoding (SGR 1006 vs. legacy X10).
    var mouseTracking = false; private set
    var mouseSgr = false; private set

    // ---- UTF-8 streaming decode ----
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    // ---- parser state ----
    private enum class State { GROUND, ESC, CSI, OSC, OSC_ESC, CHARSET }
    private var state = State.GROUND
    private val csi = StringBuilder()

    private fun grid(r: Int, c: Int) = Array(r) { Array(c) { Cell() } }

    fun feed(data: ByteArray, len: Int) {
        val combined = ByteArray(pending.size + len)
        System.arraycopy(pending, 0, combined, 0, pending.size)
        System.arraycopy(data, 0, combined, pending.size, len)
        val inBuf = ByteBuffer.wrap(combined)
        val outBuf = CharBuffer.allocate(combined.size + 4)
        decoder.reset()
        decoder.decode(inBuf, outBuf, false)
        val remaining = inBuf.remaining()
        pending = combined.copyOfRange(combined.size - remaining, combined.size)
        outBuf.flip()
        while (outBuf.hasRemaining()) process(outBuf.get())
    }

    private fun process(c: Char) {
        when (state) {
            State.GROUND -> ground(c)
            State.ESC -> esc(c)
            State.CSI -> csi(c)
            State.OSC -> if (c.code == BEL) state = State.GROUND else if (c.code == ESC) state = State.OSC_ESC
            State.OSC_ESC -> state = if (c == '\\') State.GROUND else State.OSC
            State.CHARSET -> state = State.GROUND
        }
    }

    private fun ground(c: Char) {
        when (c.code) {
            ESC -> state = State.ESC
            0x0A, 0x0B, 0x0C -> lineFeed()      // LF, VT, FF
            0x0D -> col = 0                      // CR
            0x08 -> if (col > 0) col--           // BS
            0x09 -> col = minOf(cols - 1, (col / 8 + 1) * 8) // TAB
            BEL -> {}                            // bell
            else -> if (c.code >= 0x20) putChar(c)
        }
    }

    private fun esc(c: Char) {
        when (c) {
            '[' -> { csi.setLength(0); state = State.CSI }
            ']' -> state = State.OSC
            '(', ')', '*', '+' -> state = State.CHARSET
            '7' -> { saveCursor(); state = State.GROUND }
            '8' -> { restoreCursor(); state = State.GROUND }
            'D' -> { lineFeed(); state = State.GROUND }           // IND
            'E' -> { lineFeed(); col = 0; state = State.GROUND }  // NEL
            'M' -> { reverseIndex(); state = State.GROUND }       // RI
            'c' -> { hardReset(); state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun csi(c: Char) {
        if (c.code in 0x40..0x7E) {
            dispatchCsi(c)
            state = State.GROUND
        } else {
            csi.append(c)
        }
    }

    // ---------------- CSI dispatch ----------------

    private fun dispatchCsi(final: Char) {
        val raw = csi.toString()
        val private = raw.startsWith("?")
        val body = if (private) raw.substring(1) else raw
        val params = body.split(';').map { it.toIntOrNull() }
        fun p(i: Int, def: Int) = params.getOrNull(i)?.takeIf { it >= 0 } ?: def
        val n = p(0, 1).coerceAtLeast(1)

        when (final) {
            'A' -> row = maxOf(scrollTop, row - n)
            'B' -> row = minOf(scrollBottom, row + n)
            'C' -> col = minOf(cols - 1, col + n)
            'D' -> col = maxOf(0, col - n)
            'E' -> { row = minOf(scrollBottom, row + n); col = 0 }
            'F' -> { row = maxOf(scrollTop, row - n); col = 0 }
            'G', '`' -> col = (p(0, 1) - 1).coerceIn(0, cols - 1)
            'd' -> row = (p(0, 1) - 1).coerceIn(0, rows - 1)
            'H', 'f' -> { row = (p(0, 1) - 1).coerceIn(0, rows - 1); col = (p(1, 1) - 1).coerceIn(0, cols - 1) }
            'J' -> eraseDisplay(p(0, 0))
            'K' -> eraseLine(p(0, 0))
            'm' -> applySgr(params)
            'r' -> {
                scrollTop = (p(0, 1) - 1).coerceIn(0, rows - 1)
                scrollBottom = (p(1, rows) - 1).coerceIn(scrollTop, rows - 1)
                row = scrollTop; col = 0
            }
            'L' -> insertLines(n)
            'M' -> deleteLines(n)
            'P' -> deleteChars(n)
            '@' -> insertChars(n)
            'X' -> eraseChars(n)
            'S' -> scrollUp(n)
            'T' -> scrollDown(n)
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h' -> if (private) setMode(params, true)
            'l' -> if (private) setMode(params, false)
            else -> {} // unhandled: ignore
        }
    }

    private fun setMode(params: List<Int?>, enable: Boolean) {
        for (m in params) when (m) {
            25 -> cursorVisible = enable
            47, 1047, 1049 -> switchAltScreen(enable)
            // Mouse tracking: 1000 normal, 1002 button-event, 1003 any-event.
            1000, 1002, 1003 -> mouseTracking = enable
            1006 -> mouseSgr = enable // SGR extended-coordinate encoding
            else -> {} // 2004 bracketed paste, etc. -> ignore
        }
    }

    private fun switchAltScreen(toAlt: Boolean) {
        if (toAlt == altActive) return
        altActive = toAlt
        if (toAlt) {
            clearGrid(alt)
            buffer = alt
            row = 0; col = 0
        } else {
            buffer = primary
        }
        scrollTop = 0; scrollBottom = rows - 1
    }

    // ---------------- SGR ----------------

    private fun applySgr(params: List<Int?>) {
        if (params.isEmpty() || (params.size == 1 && params[0] == null)) { sgrReset(); return }
        var i = 0
        while (i < params.size) {
            when (val code = params[i] ?: 0) {
                0 -> sgrReset()
                1 -> curBold = true
                22 -> curBold = false
                7 -> curInverse = true
                27 -> curInverse = false
                in 30..37 -> curFg = ANSI16[code - 30]
                39 -> curFg = DEFAULT
                in 40..47 -> curBg = ANSI16[code - 40]
                49 -> curBg = DEFAULT
                in 90..97 -> curFg = ANSI16[8 + code - 90]
                in 100..107 -> curBg = ANSI16[8 + code - 100]
                38 -> i = readExtendedColor(params, i) { curFg = it }
                48 -> i = readExtendedColor(params, i) { curBg = it }
                else -> {}
            }
            i++
        }
    }

    private inline fun readExtendedColor(params: List<Int?>, start: Int, set: (Int) -> Unit): Int {
        return when (params.getOrNull(start + 1)) {
            5 -> { set(color256(params.getOrNull(start + 2) ?: 0)); start + 2 }
            2 -> {
                val r = params.getOrNull(start + 2) ?: 0
                val g = params.getOrNull(start + 3) ?: 0
                val b = params.getOrNull(start + 4) ?: 0
                set((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
                start + 4
            }
            else -> start
        }
    }

    private fun sgrReset() { curFg = DEFAULT; curBg = DEFAULT; curBold = false; curInverse = false }

    // ---------------- buffer ops ----------------

    private fun putChar(c: Char) {
        if (col >= cols) { col = 0; lineFeed() }
        val cell = buffer[row][col]
        cell.ch = c; cell.fg = curFg; cell.bg = curBg; cell.bold = curBold; cell.inverse = curInverse
        col++
    }

    private fun lineFeed() {
        if (row == scrollBottom) scrollUp(1) else if (row < rows - 1) row++
    }

    private fun reverseIndex() {
        if (row == scrollTop) scrollDown(1) else if (row > 0) row--
    }

    private fun scrollUp(n: Int) {
        // No local scroll-back: tmux owns the history (reached via copy-mode from
        // the bezel), so a line that scrolls off the top is simply dropped.
        repeat(n.coerceAtMost(rows)) {
            for (r in scrollTop until scrollBottom) buffer[r] = buffer[r + 1]
            buffer[scrollBottom] = blankRow()
        }
    }

    private fun scrollDown(n: Int) {
        repeat(n.coerceAtMost(rows)) {
            for (r in scrollBottom downTo scrollTop + 1) buffer[r] = buffer[r - 1]
            buffer[scrollTop] = blankRow()
        }
    }

    private fun insertLines(n: Int) {
        if (row < scrollTop || row > scrollBottom) return
        repeat(n.coerceAtMost(scrollBottom - row + 1)) {
            for (r in scrollBottom downTo row + 1) buffer[r] = buffer[r - 1]
            buffer[row] = blankRow()
        }
    }

    private fun deleteLines(n: Int) {
        if (row < scrollTop || row > scrollBottom) return
        repeat(n.coerceAtMost(scrollBottom - row + 1)) {
            for (r in row until scrollBottom) buffer[r] = buffer[r + 1]
            buffer[scrollBottom] = blankRow()
        }
    }

    private fun deleteChars(n: Int) {
        val line = buffer[row]
        val count = n.coerceAtMost(cols - col)
        for (c in col until cols) {
            val src = c + count
            if (src < cols) copyCell(line[src], line[c]) else line[c].reset(curBg)
        }
    }

    private fun insertChars(n: Int) {
        val line = buffer[row]
        val count = n.coerceAtMost(cols - col)
        for (c in cols - 1 downTo col) {
            val src = c - count
            if (src >= col) copyCell(line[src], line[c]) else line[c].reset(curBg)
        }
    }

    private fun eraseChars(n: Int) {
        val end = (col + n).coerceAtMost(cols)
        for (c in col until end) buffer[row][c].reset(curBg)
    }

    private fun eraseLine(mode: Int) {
        val line = buffer[row]
        when (mode) {
            0 -> for (c in col until cols) line[c].reset(curBg)
            1 -> for (c in 0..col.coerceAtMost(cols - 1)) line[c].reset(curBg)
            2 -> for (c in 0 until cols) line[c].reset(curBg)
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { eraseLine(0); for (r in row + 1 until rows) clearRow(r) }
            1 -> { eraseLine(1); for (r in 0 until row) clearRow(r) }
            else -> for (r in 0 until rows) clearRow(r)
        }
    }

    private fun clearRow(r: Int) { for (c in 0 until cols) buffer[r][c].reset(curBg) }
    private fun clearGrid(g: Array<Array<Cell>>) { for (r in g.indices) for (c in g[r].indices) g[r][c].reset(DEFAULT) }
    private fun blankRow(): Array<Cell> = Array(cols) { Cell().also { it.bg = curBg } }
    private fun copyCell(from: Cell, to: Cell) { to.ch = from.ch; to.fg = from.fg; to.bg = from.bg; to.bold = from.bold; to.inverse = from.inverse }

    private fun saveCursor() { savedRow = row; savedCol = col }
    private fun restoreCursor() { row = savedRow.coerceIn(0, rows - 1); col = savedCol.coerceIn(0, cols - 1) }

    private fun hardReset() {
        clearGrid(primary); clearGrid(alt)
        row = 0; col = 0; scrollTop = 0; scrollBottom = rows - 1
        sgrReset(); cursorVisible = true; altActive = false; buffer = primary
        mouseTracking = false; mouseSgr = false
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        cols = newCols; rows = newRows
        primary = grid(rows, cols); alt = grid(rows, cols)
        buffer = if (altActive) alt else primary
        row = row.coerceIn(0, rows - 1); col = col.coerceIn(0, cols - 1)
        scrollTop = 0; scrollBottom = rows - 1
    }

    // ---------------- snapshot ----------------

    fun snapshot(): TerminalSnapshot {
        // Render only the visible screen: tmux owns the scroll-back (reached via
        // copy-mode from the bezel), so each frame is O(visible rows), not
        // O(scroll-back). This is the main thing keeping rendering cheap.
        val out = ArrayList<List<TermSpan>>(rows)

        fun processLine(line: Array<Cell>) {
            val spans = ArrayList<TermSpan>()
            val sb = StringBuilder()
            var sFg = 0; var sBg = 0; var sBold = false
            var started = false
            for (c in 0 until cols) {
                if (c >= line.size) break // Handle lines saved before a resize widened the terminal
                val cell = line[c]
                var fg = if (cell.fg == DEFAULT) DEF_FG else cell.fg
                var bg = if (cell.bg == DEFAULT) DEF_BG else cell.bg
                if (cell.inverse) { val t = fg; fg = bg; bg = t }
                if (!started) {
                    sFg = fg; sBg = bg; sBold = cell.bold; started = true
                } else if (fg != sFg || bg != sBg || cell.bold != sBold) {
                    spans.add(TermSpan(sb.toString(), sFg, sBg, sBold))
                    sb.setLength(0); sFg = fg; sBg = bg; sBold = cell.bold
                }
                sb.append(cell.ch)
            }
            if (sb.isNotEmpty()) spans.add(TermSpan(sb.toString(), sFg, sBg, sBold))
            out.add(spans)
        }

        for (r in 0 until rows) {
            processLine(buffer[r])
        }
        
        return TerminalSnapshot(out, cols, row, col, cursorVisible, mouseTracking, mouseSgr)
    }

    companion object {
        private const val ESC = 0x1B
        private const val BEL = 0x07

        private const val DEFAULT = 0 // transparent sentinel = "use default"
        val DEF_FG = 0xFFE8E0D5.toInt()
        val DEF_BG = 0xFF0C0C0C.toInt()

        private val ANSI16 = intArrayOf(
            0xFF000000.toInt(), 0xFFCD3131.toInt(), 0xFF0DBC79.toInt(), 0xFFE5E510.toInt(),
            0xFF2472C8.toInt(), 0xFFBC3FBC.toInt(), 0xFF11A8CD.toInt(), 0xFFE5E5E5.toInt(),
            0xFF666666.toInt(), 0xFFF14C4C.toInt(), 0xFF23D18B.toInt(), 0xFFF5F543.toInt(),
            0xFF3B8EEA.toInt(), 0xFFD670D6.toInt(), 0xFF29B8DB.toInt(), 0xFFFFFFFF.toInt(),
        )

        private fun color256(i: Int): Int = when {
            i in 0..15 -> ANSI16[i]
            i in 16..231 -> {
                val n = i - 16
                val r = n / 36; val g = (n / 6) % 6; val b = n % 6
                fun ch(v: Int) = if (v == 0) 0 else 55 + v * 40
                (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
            }
            else -> { val v = 8 + (i - 232) * 10; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        }
    }
}
