package io.qwqc.claudewatch.data.terminal

import io.qwqc.claudewatch.data.ssh.ShellChannel
import io.qwqc.claudewatch.data.ssh.SshManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns one live PTY terminal: opens a shell over SSH, attaches the tmux session,
 * pumps bytes through a [TerminalEmulator], and publishes [snapshot] frames.
 *
 * The emulator is single-threaded by contract, so all feed/snapshot/resize calls
 * are serialized behind [lock].
 */
class TerminalController(private val ssh: SshManager) {

    sealed interface Status {
        data object Disconnected : Status
        data object Connecting : Status
        data object Connected : Status
        data class Error(val message: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _snapshot = MutableStateFlow(TerminalSnapshot.Empty)
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Disconnected)
    val status: StateFlow<Status> = _status.asStateFlow()

    var activeSessionName: String? = null
        private set

    private var channel: ShellChannel? = null
    private var emulator: TerminalEmulator? = null
    private var readerJob: Job? = null

    fun connect(cols: Int, rows: Int, tmuxSession: String) {
        if ((_status.value == Status.Connected || _status.value == Status.Connecting) && activeSessionName == tmuxSession) return
        if (_status.value != Status.Disconnected) {
            disconnect()
        }
        activeSessionName = tmuxSession
        _status.value = Status.Connecting
        readerJob = scope.launch {
            var retryCount = 0
            // Once we've attached successfully, the host/key/config are known-good,
            // so later drops are transient and we retry indefinitely. Before the
            // first success they may signal a real misconfig, so we give up quickly
            // and surface the error (with Retry/Settings) instead of spinning forever.
            var everConnected = false
            while (isActive) {
                try {
                    val emu = TerminalEmulator(cols, rows)
                    emulator = emu
                    val ch = ssh.openShell(cols, rows)
                    channel = ch
                    _status.value = Status.Connected
                    everConnected = true
                    retryCount = 0 // reset on successful connect
                    
                    // Attach (or create) the Claude tmux session. `new-session -A`
                    // attaches if it exists, else creates it. We also force
                    // `mouse on` (so tmux reports wheel events to us) and
                    // `alternate-scroll off` (so the wheel scrolls the pane's
                    // scroll-back via copy-mode instead of being translated into
                    // arrow keys for the foreground TUI). See scroll() below.
                    ch.write("exec tmux new-session -A -s $tmuxSession \\; set -g mouse on \\; set -g alternate-scroll off\n")
                    readLoop(ch, emu)
                    
                    // If readLoop exits normally (e.g. EOF), check if we should retry or if it's a clean exit.
                    if (!isActive) break
                    if (_status.value == Status.Connected) {
                        // Connection dropped unexpectedly
                        throw Exception("Connection lost")
                    } else {
                        // Probably manual disconnect or handled error
                        break
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Transient network/DNS drops are expected on a watch: the radio
                    // powers down whenever we're backgrounded (e.g. while the system
                    // keyboard activity is in front), which kills the socket and makes
                    // DNS fail. Because tmux holds the session server-side, we can keep
                    // retrying and re-attach losslessly once the network returns —
                    // instead of dumping the user into a scary "SSH error" overlay.
                    if (isActive && isRecoverable(e) && (everConnected || retryCount < 3)) {
                        retryCount++
                        _status.value = Status.Connecting
                        // Exponential backoff (0.5s,1,2,4,8…) capped at 8s so
                        // reconnecting while offline stays gentle on the battery.
                        val delayMs = (500L * (1L shl (retryCount - 1).coerceAtMost(4))).coerceAtMost(8000L)
                        kotlinx.coroutines.delay(delayMs)
                        continue
                    }
                    _status.value = Status.Error(e.message ?: "Connection failed")
                    break
                } finally {
                    runCatching { channel?.close() }
                    channel = null
                }
            }
        }
    }

    /**
     * True for errors we expect to recover from on their own — network/DNS drops
     * caused by the watch suspending its radio in the background. Auth/config
     * failures (e.g. JSch "Auth fail") are NOT recoverable and surface immediately
     * so the user isn't left staring at a spinner forever.
     */
    private fun isRecoverable(e: Throwable): Boolean {
        if (e is java.net.UnknownHostException ||
            e is java.net.SocketException ||        // incl. ConnectException, NoRouteToHostException
            e is java.net.SocketTimeoutException ||
            e is java.io.InterruptedIOException
        ) return true
        // JSch wraps most transport failures in a generic JSchException; match on text.
        val msg = (e.message ?: "").lowercase()
        return "connection lost" in msg ||
            "timeout" in msg ||
            "session is down" in msg ||
            "connection is closed by foreign host" in msg ||
            "connection refused" in msg ||
            "network is unreachable" in msg ||
            "no route to host" in msg ||
            "broken pipe" in msg ||
            "socket" in msg
    }

    private fun readLoop(ch: ShellChannel, emu: TerminalEmulator) {
        val buf = ByteArray(16384)
        var lastEmit = 0L
        while (scope.isActive && ch.isConnected) {
            val n = try { ch.read(buf) } catch (e: Exception) { -1 }
            if (n < 0) break
            if (n == 0) continue
            synchronized(lock) { emu.feed(buf, n) }
            // Render when the burst has drained (nothing more buffered) or the
            // frame budget elapsed. Coalescing a flood keeps us from rebuilding
            // the snapshot per chunk; the drain check means the LAST line of a
            // reply shows immediately instead of waiting for the next output.
            val drained = try { ch.available() == 0 } catch (e: Exception) { true }
            val now = System.currentTimeMillis()
            if (drained || now - lastEmit >= FRAME_MS) {
                synchronized(lock) { _snapshot.value = emu.snapshot() }
                lastEmit = now
            }
        }
        // flush final frame
        synchronized(lock) { _snapshot.value = emu.snapshot() }
    }

    /** Send literal text (e.g. a voice prompt) to the session. */
    fun sendText(text: String) {
        scope.launch { runCatching { channel?.write(text) } }
    }

    /** Send raw bytes (control sequences: Enter, Esc, Ctrl-C, arrows…). */
    fun sendBytes(bytes: ByteArray) {
        scope.launch { runCatching { channel?.write(bytes) } }
    }

    /**
     * Forward one mouse-wheel notch so the bezel scrolls tmux's pane scroll-back.
     * With `mouse on`, wheel-up makes tmux enter copy-mode and scroll back; with
     * `alternate-scroll off`, it does so even when a full-screen TUI is running.
     *
     * We only emit when the app has actually enabled mouse reporting, so the bytes
     * never leak into a plain shell as visible garbage.
     */
    fun scroll(up: Boolean) {
        scope.launch {
            val bytes = synchronized(lock) {
                val emu = emulator
                if (emu == null || !emu.mouseTracking) null
                else mouseWheelBytes(up, emu.cols, emu.rows, emu.mouseSgr)
            } ?: return@launch
            runCatching { channel?.write(bytes) }
        }
    }

    /**
     * Build a single wheel event. Points at the middle of the pane (1-based); for
     * a single full-screen pane any in-bounds cell routes the wheel to it.
     */
    private fun mouseWheelBytes(up: Boolean, cols: Int, rows: Int, sgr: Boolean): ByteArray {
        val col = (cols / 2).coerceIn(1, 223)
        val row = (rows / 2).coerceIn(1, 223)
        val btn = if (up) 64 else 65 // 64 = wheel-up, 65 = wheel-down
        return if (sgr) {
            // SGR (1006):  ESC [ < btn ; col ; row M
            byteArrayOf(0x1B, 0x5B) + "<$btn;$col;${row}M".toByteArray(Charsets.US_ASCII)
        } else {
            // Legacy X10:  ESC [ M  Cb Cx Cy  — each value offset by 32.
            byteArrayOf(
                0x1B, '['.code.toByte(), 'M'.code.toByte(),
                (32 + btn).toByte(), (32 + col).toByte(), (32 + row).toByte(),
            )
        }
    }

    fun resize(cols: Int, rows: Int) {
        scope.launch {
            synchronized(lock) {
                emulator?.let { emu ->
                    emu.resize(cols, rows)
                    _snapshot.value = emu.snapshot()
                }
            }
            runCatching { channel?.resize(cols, rows) }
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        runCatching { channel?.close() }
        channel = null
        emulator = null
        activeSessionName = null
        _status.value = Status.Disconnected
        _snapshot.value = TerminalSnapshot.Empty
    }

    private companion object {
        /** Render cap during continuous output (~30 fps). Interactive updates and
         *  the end of each burst still render immediately (see readLoop). */
        const val FRAME_MS = 33L
    }
}
