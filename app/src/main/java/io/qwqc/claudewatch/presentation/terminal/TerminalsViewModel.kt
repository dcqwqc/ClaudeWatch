package io.qwqc.claudewatch.presentation.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.qwqc.claudewatch.Graph
import io.qwqc.claudewatch.data.terminal.TerminalProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owns the user's list of named terminals (tmux targets). Backed by DataStore,
 * so Home (the list) and the edit screen stay in sync through the same flow.
 */
class TerminalsViewModel : ViewModel() {
    private val store = Graph.settingsStore

    private val _terminals = MutableStateFlow<List<TerminalProfile>>(emptyList())
    val terminals = _terminals.asStateFlow()

    /** Name of the active connection (which machine the terminals run on). */
    private val _activeConnection = MutableStateFlow("")
    val activeConnection = _activeConnection.asStateFlow()

    init {
        viewModelScope.launch { store.terminals.collect { _terminals.value = it } }
        viewModelScope.launch {
            combine(store.connections, store.activeConnectionId) { conns, activeId ->
                conns.firstOrNull { it.id == activeId }?.name ?: ""
            }.collect { _activeConnection.value = it }
        }
    }

    /** Insert (new id) or update (existing id) a profile. */
    fun save(profile: TerminalProfile) {
        viewModelScope.launch {
            val cur = store.currentTerminals().toMutableList()
            val idx = cur.indexOfFirst { it.id == profile.id }
            if (idx >= 0) cur[idx] = profile else cur.add(profile)
            store.saveTerminals(cur)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val remaining = store.currentTerminals().filterNot { it.id == id }
            // Never leave the user with zero terminals.
            store.saveTerminals(remaining.ifEmpty { listOf(TerminalProfile("t1", "Claude 1", "claude")) })
        }
    }

    fun newId(): String = "t${System.currentTimeMillis()}"

    /** Default name for the next added terminal, e.g. "Claude 3". */
    fun nextDefaultName(): String = "Claude ${_terminals.value.size + 1}"
}
