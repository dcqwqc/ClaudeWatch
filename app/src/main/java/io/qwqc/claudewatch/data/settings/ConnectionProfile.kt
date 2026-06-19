package io.qwqc.claudewatch.data.settings

/**
 * One machine the watch can SSH into (e.g. "PC", "Server", "Laptop"). Each
 * connection carries its own key pair generated on the watch, so a single
 * connection can be revoked (deleted) without affecting the others.
 *
 * The user keeps a list of these; exactly one is "active" at a time, and the
 * active one is projected into [Settings] so the rest of the app (SSH, usage,
 * push) continues to work against a single set of host/user/key values.
 */
data class ConnectionProfile(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val privateKeyPem: String = "",
    val keyPassphrase: String = "",
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && user.isNotBlank() && privateKeyPem.isNotBlank()
}
