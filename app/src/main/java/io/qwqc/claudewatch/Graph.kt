package io.qwqc.claudewatch

import android.content.Context
import io.qwqc.claudewatch.data.settings.SettingsStore
import io.qwqc.claudewatch.data.ssh.SshManager
import io.qwqc.claudewatch.data.terminal.TerminalController
import io.qwqc.claudewatch.data.usage.UsageRepository

/**
 * Minimal hand-rolled service locator. For a single-user personal app this is
 * plenty; swap for Hilt later if the app grows.
 */
object Graph {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var sshManager: SshManager
        private set
    lateinit var usageRepository: UsageRepository
        private set
    lateinit var terminalController: TerminalController
        private set

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        settingsStore = SettingsStore(appContext)
        sshManager = SshManager(settingsStore)
        usageRepository = UsageRepository(sshManager)
        terminalController = TerminalController(sshManager)
        initialized = true
    }
}
