package io.qwqc.claudewatch.data.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream

/**
 * Handle to a live PTY shell over SSH. Reads are pull-based: the terminal's
 * reader loop calls [read] repeatedly on an IO thread.
 */
class ShellChannel(
    private val session: Session,
    private val channel: ChannelShell,
    private val input: InputStream,
    private val output: OutputStream,
    private val onClose: (() -> Unit)? = null,
) {
    val isConnected: Boolean get() = channel.isConnected && !channel.isClosed

    // JSch is NOT thread-safe for concurrent outbound traffic: two threads writing
    // (or writing + resizing) at once interleave packets on the shared SSH
    // transport and corrupt it, which makes the server drop the session. Writes
    // arrive from many short-lived coroutines on Dispatchers.IO (e.g. a bezel
    // scroll fires a burst of wheel events), so we serialize every outbound op.
    private val ioLock = Any()

    /** Blocking read into [buf]; returns bytes read, or -1 at EOF. */
    fun read(buf: ByteArray): Int = input.read(buf)

    /** Bytes already buffered and readable without blocking — used to detect the
     *  end of an output burst so the final frame renders without waiting. */
    fun available(): Int = input.available()

    /** Send raw bytes (keystrokes / control sequences) to the remote PTY. */
    fun write(bytes: ByteArray) {
        synchronized(ioLock) {
            output.write(bytes)
            output.flush()
        }
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    /** Tell the remote PTY the new window size (on rotation / layout change). */
    fun resize(cols: Int, rows: Int) {
        synchronized(ioLock) {
            channel.setPtySize(cols, rows, 0, 0)
        }
    }

    fun close() {
        if (onClose != null) {
            onClose.invoke()
        } else {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
        }
    }
}
