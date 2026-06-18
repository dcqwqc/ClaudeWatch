package io.qwqc.claudewatch.fcm

import io.qwqc.claudewatch.data.settings.SettingsStore
import io.qwqc.claudewatch.data.ssh.SshManager

/**
 * Publishes this watch's FCM token to the server at ~/.claude-watch/watch-token.
 * The Claude Code Stop hook reads that file to know where to push "done" events,
 * so registration travels over the same SSH channel as everything else.
 */
class PushTokenRegistrar(
    private val ssh: SshManager,
    private val settings: SettingsStore,
) {
    suspend fun registerIfPossible(token: String) {
        val s = settings.current()
        if (!s.isConfigured) return
        // FCM tokens are [A-Za-z0-9:_-], safe inside single quotes.
        val cmd = "mkdir -p ~/.claude-watch && printf '%s' '$token' > ~/.claude-watch/watch-token"
        runCatching { ssh.exec(cmd, timeoutMs = 12_000) }
    }
}
