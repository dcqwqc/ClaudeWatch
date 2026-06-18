package io.qwqc.claudewatch.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.qwqc.claudewatch.Graph
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

class SettingsViewModel : ViewModel() {
    private val store = Graph.settingsStore
    private val ssh = Graph.sshManager

    private val _form = MutableStateFlow(Settings())
    val form = _form.asStateFlow()

    /** Public key text shown after generating a key pair on the watch. */
    private val _publicKey = MutableStateFlow<String?>(null)
    val publicKey = _publicKey.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating = _generating.asStateFlow()

    private val _conn = MutableStateFlow<ConnState>(ConnState.Idle)
    val conn = _conn.asStateFlow()

    init { viewModelScope.launch { _form.value = store.current() } }

    fun edit(transform: (Settings) -> Settings) {
        _form.value = transform(_form.value)
        // Any edit invalidates a prior test result.
        _conn.value = ConnState.Idle
    }

    /** SSH in with the current (unsaved) form values and surface a tick or error. */
    fun testConnection() {
        if (_conn.value == ConnState.Testing) return
        val s = _form.value
        if (!s.isConfigured) {
            _conn.value = ConnState.Fail("Set host, user & generate a key first")
            return
        }
        _conn.value = ConnState.Testing
        viewModelScope.launch {
            _conn.value = runCatching { ssh.test(s) }.fold(
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
            val gen = withContext(Dispatchers.Default) { SshKeygen.generate() }
            _form.value = _form.value.copy(privateKeyPem = gen.privatePem)
            _publicKey.value = gen.publicOpenSsh
            _conn.value = ConnState.Idle
            _generating.value = false
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            store.save(_form.value)
            onSaved()
        }
    }
}
