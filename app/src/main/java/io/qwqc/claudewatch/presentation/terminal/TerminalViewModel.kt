package io.qwqc.claudewatch.presentation.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.qwqc.claudewatch.Graph
import io.qwqc.claudewatch.data.terminal.TerminalController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TerminalViewModel : ViewModel() {
    private val controller = Graph.terminalController
    private val store = Graph.settingsStore

    val snapshot = controller.snapshot
    val status = controller.status

    /** True if SSH isn't configured yet, so the screen can route to Settings. */
    private val _needsConfig = MutableStateFlow(false)
    val needsConfig = _needsConfig.asStateFlow()

    /** Display title (the profile name), shown above the terminal. */
    private val _title = MutableStateFlow("Claude")
    val title = _title.asStateFlow()

    private var session: String? = null
    private var pendingSize: Pair<Int, Int>? = null

    /** Resolve the profile's tmux session + title before any connect. */
    fun bind(profileId: String) {
        viewModelScope.launch {
            val profile = store.currentTerminals().firstOrNull { it.id == profileId }
            val s = store.current()
            _title.value = profile?.name ?: "Claude"
            session = profile?.tmuxSession ?: s.tmuxSession
            // If the view already reported its size, connect now.
            pendingSize?.let { (c, r) -> maybeConnect(c, r) }
        }
    }

    /** Called once the view knows how many cols/rows fit; connects or resizes. */
    fun onSize(cols: Int, rows: Int) {
        pendingSize = cols to rows
        maybeConnect(cols, rows)
    }

    private fun maybeConnect(cols: Int, rows: Int) {
        val sess = session ?: return // not bound yet; bind() will retry
        viewModelScope.launch {
            val s = store.current()
            if (!s.isConfigured) { _needsConfig.value = true; return@launch }
            _needsConfig.value = false

            if (controller.activeSessionName == sess) {
                when (controller.status.value) {
                    is TerminalController.Status.Connected -> controller.resize(cols, rows)
                    is TerminalController.Status.Connecting -> Unit
                    else -> controller.connect(cols, rows, sess)
                }
            } else {
                controller.connect(cols, rows, sess)
            }

            // If we just started connecting (or were already connected), ensure
            // the server has our FCM token. This waits until we are Connected
            // to avoid opening two SSH sessions at once.
            launch {
                controller.status.collect { st ->
                    if (st is TerminalController.Status.Connected) {
                        store.fcmToken.first()?.let { token ->
                            io.qwqc.claudewatch.fcm.PushTokenRegistrar(Graph.sshManager, store).registerIfPossible(token)
                        }
                    }
                }
            }
        }
    }

    fun retry() {
        controller.disconnect()
        pendingSize?.let { (c, r) -> maybeConnect(c, r) }
    }

    /**
     * The text currently sitting in Claude Code's input box, read off the live
     * screen so the keyboard dialog can pre-fill it for editing.
     */
    fun currentInput(): String {
        return try {
            val snap = snapshot.value
            val rows = snap.rows
            if (rows.isEmpty()) return ""

            fun extract(line: String): String? {
                // Claude Code's input line is "❯ <text>" (the prompt is the first
                // non-blank glyph; no side borders). Require that so we don't latch
                // onto a '❯'/'>' that appears mid-output.
                var i = 0
                while (i < line.length && line[i] == ' ') i++
                if (i >= line.length || (line[i] != PROMPT && line[i] != '>')) return null
                var s = i + 1
                while (s < line.length && (line[s].code == 0x20 || line[s].code == 0xA0)) s++
                return line.substring(s).trimEnd()
            }

            // Only check the last few visible rows to keep the UI thread snappy.
            val count = 10
            val start = (rows.size - 1).coerceAtLeast(0)
            val end = (rows.size - count).coerceAtLeast(0)
            for (i in start downTo end) {
                val lineText = rows[i].joinToString("") { it.text }
                extract(lineText)?.let { return it }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    /** True once a bezel scroll has put tmux into copy-mode; cleared when we send
     *  real input (so we leave copy-mode first). */
    private var inCopyMode = false
    private var scrollAccumulator = 0f

    /**
     * Drive tmux's scroll-back from the rotary bezel by forwarding real
     * mouse-wheel notches (see [TerminalController.scroll]). With `mouse on` +
     * `alternate-scroll off` server-side, wheel-up makes tmux enter copy-mode and
     * scroll its pane history; wheel-down scrolls back toward the live bottom.
     * Every [STEP] pixels of accumulated bezel motion = one notch.
     */
    fun onScroll(pixels: Float) {
        scrollAccumulator += pixels
        while (scrollAccumulator <= -STEP) {   // bezel up → back into history
            scrollAccumulator += STEP
            inCopyMode = true
            controller.scroll(up = true)
        }
        while (scrollAccumulator >= STEP) {     // bezel down → toward the live bottom
            scrollAccumulator -= STEP
            controller.scroll(up = false)
        }
    }

    /** Manual fallback (the "scr" key): enter tmux copy-mode for keyboard scrolling. */
    fun enterCopyMode() {
        controller.sendBytes(byteArrayOf(0x02, 0x5B)) // Ctrl-b, [
        inCopyMode = true
    }

    /** Leave copy-mode before sending real input, else it'd be read as copy commands. */
    private fun exitCopyMode() {
        if (inCopyMode) {
            controller.sendBytes(byteArrayOf(0x71)) // 'q' cancels tmux copy-mode
            inCopyMode = false
        }
    }

    /**
     * Type [text] at the cursor in Claude's input box (no Enter). This INSERTS —
     * it does not clear the line — so moving the cursor with the arrows and then
     * typing/​deleting (⌫) behaves like a normal terminal.
     */
    fun typeText(text: String) {
        exitCopyMode()
        controller.sendText(text)
    }

    /** Submit whatever is in the input box. */
    fun pressEnter() {
        exitCopyMode()
        controller.sendBytes(byteArrayOf(0x0D))
    }

    fun sendBytes(bytes: ByteArray) {
        exitCopyMode()
        controller.sendBytes(bytes)
    }

    override fun onCleared() {
        // Do not tear down the SSH PTY so it stays alive while navigating.
    }

    private companion object {
        const val PROMPT = '❯' // Claude Code's input prompt glyph: ❯
        const val STEP = 40f   // bezel pixels per forwarded wheel notch
    }
}
