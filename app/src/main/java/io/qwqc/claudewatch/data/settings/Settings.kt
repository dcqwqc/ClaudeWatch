package io.qwqc.claudewatch.data.settings

/**
 * All user-configurable connection + calibration values.
 *
 * The token "caps" are calibration knobs: Claude's subscription does not expose
 * an official "% of 5-hour / weekly limit" API, so we read real token totals
 * from ccusage and show them as a fraction of these caps. Tune them on the
 * Settings screen until the rings match what `/usage` reports in Claude Code.
 */
data class Settings(
    // Identity of the active connection these values came from, so save() can
    // write edits back to the right ConnectionProfile in the list.
    val connectionId: String = "default",
    val connectionName: String = "Default",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val privateKeyPem: String = "",
    val keyPassphrase: String = "",
    val tmuxSession: String = "claude",
    val cap5hTokens: Long = 12_000_000,
    val capWeekTokens: Long = 120_000_000,
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && user.isNotBlank() && privateKeyPem.isNotBlank()
}
