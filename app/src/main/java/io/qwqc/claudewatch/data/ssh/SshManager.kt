package io.qwqc.claudewatch.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import io.qwqc.claudewatch.data.settings.Settings
import io.qwqc.claudewatch.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Thin JSch wrapper. We use the maintained `com.github.mwiede:jsch` fork, which
 * supports modern OpenSSH-format keys (incl. ed25519) and PTY channels.
 *
 * Two modes:
 *  - [exec]: run a one-shot command (usage polling, send-keys for voice).
 *  - [openShell]: open an interactive PTY channel for the live terminal.
 */
class SshManager(private val settingsStore: SettingsStore) {

    data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /** Last IPv4 we successfully resolved [resolveHostPreferIpv4] to, reused when
     *  a later lookup fails. The dynamic-DNS host's address is stable, so this
     *  lets a reconnect survive a transient resolver hiccup (e.g. the radio still
     *  waking up after the system keyboard activity closed). */
    @Volatile private var lastResolvedIpv4: String? = null

    /**
     * Resolve [host] preferring IPv4. The server's sshd is IPv4-only, and watch
     * networks frequently return an AAAA record with no working IPv6 route, so
     * connecting to that just fails/times out. Falls back to [host] unchanged if
     * it's a literal or has no A record.
     */
    private fun resolveHostPreferIpv4(host: String): String = try {
        val addrs = InetAddress.getAllByName(host)
        val ip = addrs.firstOrNull { it is Inet4Address }?.hostAddress ?: addrs.first().hostAddress
        if (ip != null) lastResolvedIpv4 = ip
        ip ?: host
    } catch (e: Exception) {
        // Resolution failed (commonly: the watch suspended its radio while we were
        // backgrounded). Reuse the last good IP if we have one so a transient DNS
        // failure doesn't blow up the whole connect with UnknownHostException; the
        // connect itself will retry/recover once the network is back. If we never
        // resolved successfully, fall through to [host] and let JSch report it.
        lastResolvedIpv4 ?: host
    }

    private var cachedSession: Session? = null
    /** Identity of the connection [cachedSession] was opened for. When the active
     *  connection changes (different host/user/port/key) the signature differs and
     *  we drop the stale session and reconnect to the new target. */
    private var cachedSig: String? = null
    private val sessionLock = Any()

    private fun signature(s: Settings): String =
        "${s.user}@${s.host}:${s.port}#${s.privateKeyPem.hashCode()}"

    private fun getSession(s: Settings, connectTimeoutMs: Int): Session {
        synchronized(sessionLock) {
            val sig = signature(s)
            val current = cachedSession
            if (current != null && current.isConnected && cachedSig == sig) {
                return current
            }
            // Stale or pointed at a different connection — discard it.
            if (current != null && cachedSig != sig) {
                runCatching { current.disconnect() }
                cachedSession = null
            }

            require(s.isConfigured) { "SSH is not configured (host/user/private key missing)" }
            val jsch = JSch()
            val passphrase = s.keyPassphrase.takeIf { it.isNotBlank() }?.toByteArray()
            jsch.addIdentity("claudewatch", s.privateKeyPem.toByteArray(), null, passphrase)
            val session = jsch.getSession(s.user, resolveHostPreferIpv4(s.host), s.port).apply {
                // Personal device on a known host; skip interactive host-key prompts.
                setConfig("StrictHostKeyChecking", "no")
                setConfig("PreferredAuthentications", "publickey")
                // Use direct methods for keep-alives
                serverAliveInterval = 15000
                serverAliveCountMax = 3
                timeout = 0 // No socket timeout
            }
            session.connect(connectTimeoutMs)
            cachedSession = session
            cachedSig = sig
            return session
        }
    }

    /** Run a command against the saved settings and return exit code + output. */
    suspend fun exec(command: String, timeoutMs: Int = 20_000): ExecResult =
        exec(settingsStore.current(), command, timeoutMs)

    /**
     * Quick auth + connectivity probe for the Settings "Test connection" button.
     * Runs against the supplied (possibly unsaved) [settings] so the user can
     * verify before saving.
     */
    suspend fun test(settings: Settings): ExecResult =
        exec(settings, "echo claude-watch-ok", timeoutMs = 12_000)

    /**
     * Runs a one-shot command against the server and returns the result.
     *
     * This function is designed for short-lived commands. It opens an 'exec' channel,
     * reads the output until the channel closes or a timeout is reached, and then
     * disconnects the channel. The underlying SSH session is cached and reused.
     *
     * @param settings The SSH connection settings to use.
     * @param command The shell command to execute on the remote server.
     * @param timeoutMs The maximum time to wait for the command to complete.
     * @return [ExecResult] containing the exit code, stdout, and stderr.
     * @throws IOException if the command times out.
     * @throws com.jcraft.jsch.JSchException if there is an SSH-level error.
     */
    suspend fun exec(settings: Settings, command: String, timeoutMs: Int = 20_000): ExecResult =
        withContext(Dispatchers.IO) {
            val session = getSession(settings, connectTimeoutMs = 12_000)
            // ChannelExec does not implement Closeable, so we cannot use .use{}.
            // Manage the lifecycle manually with try/finally.
            val channel = session.openChannel("exec") as ChannelExec
            try {
                channel.setCommand(command)
                val stderr = ByteArrayOutputStream()
                channel.setErrStream(stderr)
                // stdout lives outside the inputStream.use block so we can read it
                // after the stream closes to build the ExecResult.
                val stdout = ByteArrayOutputStream()

                channel.inputStream.use { input ->
                    channel.connect()
                    val buf = ByteArray(8192)
                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (true) {
                        while (input.available() > 0) {
                            val n = input.read(buf)
                            if (n < 0) break
                            stdout.write(buf, 0, n)
                        }
                        if (channel.isClosed) {
                            if (input.available() > 0) continue
                            break
                        }
                        if (System.currentTimeMillis() > deadline) {
                            throw IOException("SSH exec timed out after ${timeoutMs}ms")
                        }
                        Thread.sleep(20)
                    }
                }

                // Last expression of the try block — the compiler sees ExecResult here,
                // so withContext<ExecResult> resolves correctly.
                ExecResult(
                    channel.exitStatus,
                    stdout.toString("UTF-8"),
                    stderr.toString("UTF-8")
                )
            } catch (e: Exception) {
                // If the session died, clear it so the next attempt starts fresh
                synchronized(sessionLock) { if (cachedSession === session) { cachedSession = null; cachedSig = null } }
                throw e
            } finally {
                channel.disconnect()
            }
        }

    /**
     * Open a long-lived PTY shell channel. The caller owns the returned
     * [ShellChannel] and must close it. Used by the live terminal.
     */
    suspend fun openShell(cols: Int, rows: Int): ShellChannel = withContext(Dispatchers.IO) {
        val s = settingsStore.current()
        val session = getSession(s, connectTimeoutMs = 12_000)
        val channel = session.openChannel("shell") as ChannelShell
        channel.setPtyType("xterm-256color", cols, rows, 0, 0)
        val out = channel.outputStream
        val input = channel.inputStream
        channel.connect(8_000)
        
        // We wrap the session disconnect so it's only called when explicitly closing 
        // the terminal, OR we let the cache handle it.
        // For a long-lived terminal, we DON'T want the cache to disconnect it.
        ShellChannel(session, channel, input, out) {
            // On close, we don't necessarily want to kill the pooled session,
            // just the channel.
            channel.disconnect()
        }
    }
}
