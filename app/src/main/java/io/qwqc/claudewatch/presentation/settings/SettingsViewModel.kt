package io.qwqc.claudewatch.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.qwqc.claudewatch.Graph
import io.qwqc.claudewatch.data.settings.ConnectionProfile
import io.qwqc.claudewatch.data.settings.Settings
import io.qwqc.claudewatch.data.ssh.SshKeygen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of the Settings "Test connection" probe, surfaced as a tick or error. */
sealed interface ConnState {
    data object Idle : ConnState
    data object Testing : ConnState
    data object Ok : ConnState
    data class Fail(val message: String) : ConnState
}

/**
 * Drives the Settings screen, which now manages a LIST of connections (PC,
 * Server, Laptop …). One connection is edited at a time in [form]; the list and
 * the active selection are exposed separately.
 */
class SettingsViewModel : ViewModel() {
    private val store = Graph.settingsStore
    private val ssh = Graph.sshManager

    /** All configured connections. */
    private val _connections = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val connections = _connections.asStateFlow()

    /** Id of the active connection (the one the watch actually uses). */
    private val _activeId = MutableStateFlow("")
    val activeId = _activeId.asStateFlow()

    /** The connection currently open in the editor. */
    private val _form = MutableStateFlow(ConnectionProfile(id = "default", name = "Default"))
    val form = _form.asStateFlow()

    /** Public key text shown after generating a key pair on the watch. */
    private val _publicKey = MutableStateFlow<String?>(null)
    val publicKey = _publicKey.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating = _generating.asStateFlow()

    private val _conn = MutableStateFlow<ConnState>(ConnState.Idle)
    val conn = _conn.asStateFlow()

    init {
        viewModelScope.launch { store.connections.collect { _connections.value = it } }
        viewModelScope.launch { store.activeConnectionId.collect { _activeId.value = it } }
        // Open the active connection in the editor to start.
        viewModelScope.launch {
            val s = store.current()
            _form.value = _connections.value.firstOrNull { it.id == s.connectionId }
                ?: ConnectionProfile(id = s.connectionId, name = s.connectionName, host = s.host, port = s.port, user = s.user, privateKeyPem = s.privateKeyPem, keyPassphrase = s.keyPassphrase)
        }
    }

    /** Load an existing connection into the editor. */
    fun selectForEdit(id: String) {
        _connections.value.firstOrNull { it.id == id }?.let {
            _form.value = it
            _publicKey.value = null
            _conn.value = ConnState.Idle
        }
    }

    /** Start a fresh, unsaved connection in the editor. */
    fun addConnection() {
        val n = _connections.value.size + 1
        _form.value = ConnectionProfile(id = "c${System.currentTimeMillis()}", name = "Connection $n")
        _publicKey.value = null
        _conn.value = ConnState.Idle
    }

    fun edit(transform: (ConnectionProfile) -> ConnectionProfile) {
        _form.value = transform(_form.value)
        _conn.value = ConnState.Idle
    }

    /** SSH in with the current (unsaved) form values and surface a tick or error. */
    fun testConnection() {
        if (_conn.value == ConnState.Testing) return
        val c = _form.value
        if (!c.isConfigured) {
            _conn.value = ConnState.Fail("Set host, user & generate a key first")
            return
        }
        _conn.value = ConnState.Testing
        viewModelScope.launch {
            val probe = Settings(
                connectionId = c.id, connectionName = c.name,
                host = c.host, port = c.port, user = c.user,
                privateKeyPem = c.privateKeyPem, keyPassphrase = c.keyPassphrase,
            )
            _conn.value = runCatching { ssh.test(probe) }.fold(
                onSuccess = { r ->
                    if (r.isSuccess) ConnState.Ok
                    else ConnState.Fail(r.stderr.trim().ifBlank { "Exited ${r.exitCode}" })
                },
                onFailure = { e -> ConnState.Fail(e.message ?: "Connection failed") },
            )
        }
    }

    fun generateKey() {
        if (_generating.value) return
        _generating.value = true
        viewModelScope.launch {
            val gen = withContext(Dispatchers.Default) { SshKeygen.generate(_form.value.name) }
            _form.value = _form.value.copy(privateKeyPem = gen.privatePem)
            _publicKey.value = gen.publicOpenSsh
            _conn.value = ConnState.Idle
            _generating.value = false
        }
    }

    /** Persist the edited connection and make it the active one. */
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            store.saveConnection(_form.value)
            switchActive(_form.value.id)
            onSaved()
        }
    }

    /** Make [id] the active connection (and drop any live terminal on the old one). */
    fun setActive(id: String) {
        viewModelScope.launch { switchActive(id) }
    }

    private suspend fun switchActive(id: String) {
        if (_activeId.value != id) {
            // The open terminal is bound to the old machine; tear it down so the
            // next open attaches to the newly-active connection.
            Graph.terminalController.disconnect()
        }
        store.setActiveConnection(id)
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            store.deleteConnection(id)
            // If we deleted the one being edited, reload the editor with the active one.
            if (_form.value.id == id) {
                val activeId = store.currentActiveConnectionId()
                val conns = store.currentConnections()
                _form.value = conns.firstOrNull { it.id == activeId } ?: conns.first()
            }
            onDone()
        }
    }
}
