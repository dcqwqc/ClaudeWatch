package io.qwqc.claudewatch.presentation.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.qwqc.claudewatch.Graph
import io.qwqc.claudewatch.data.usage.UsageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UsageUiState {
    data object Loading : UsageUiState
    data class NotConfigured(val reason: String) : UsageUiState
    data class Error(val message: String) : UsageUiState
    data class Ready(val snapshot: UsageSnapshot) : UsageUiState
}

class UsageViewModel : ViewModel() {
    private val repo = Graph.usageRepository
    private val settings = Graph.settingsStore

    private val _state = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    /** Manual/initial refresh — shows the loading state. */
    fun refresh() = load(showLoading = true)

    /**
     * Background tick (every full minute): refresh quietly, without the loading
     * flash, and keep the last good reading if this fetch happens to fail.
     */
    fun refreshSilently() = load(showLoading = false)

    private fun load(showLoading: Boolean) {
        if (showLoading) _state.value = UsageUiState.Loading
        viewModelScope.launch {
            // Skip quiet background polls if the terminal is currently active, 
            // to avoid SSH contention and 429s during a session.
            if (!showLoading && Graph.terminalController.status.value is io.qwqc.claudewatch.data.terminal.TerminalController.Status.Connected) {
                return@launch
            }

            val s = settings.current()
            if (!s.isConfigured) {
                _state.value = UsageUiState.NotConfigured("Set up SSH in Settings")
                return@launch
            }
            runCatching { repo.fetch() }
                .onSuccess { _state.value = UsageUiState.Ready(it) }
                .onFailure { e ->
                    // For quiet refreshes, only update state if we don't have a good reading yet.
                    // Otherwise, keep the old data to avoid flickering to an error.
                    if (showLoading || _state.value !is UsageUiState.Ready) {
                        _state.value = UsageUiState.Error(e.message ?: "Couldn't read usage")
                    }
                }
        }
    }
}
