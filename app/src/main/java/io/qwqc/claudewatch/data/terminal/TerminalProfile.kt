package io.qwqc.claudewatch.data.terminal

/**
 * A named terminal target. The user can keep several (e.g. "Claude 1" -> tmux
 * session `claude`, "Claude 2" -> `claude2`) and open each independently.
 */
data class TerminalProfile(
    val id: String,
    val name: String,
    val tmuxSession: String,
)
