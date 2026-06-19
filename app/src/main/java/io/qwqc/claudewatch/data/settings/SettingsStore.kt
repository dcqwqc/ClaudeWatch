package io.qwqc.claudewatch.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.qwqc.claudewatch.data.terminal.TerminalProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "claude_watch_settings")

/**
 * Persists [Settings] in a DataStore.
 *
 * The watch can hold several [ConnectionProfile]s (PC, Server, Laptop …), each
 * with its own key. Exactly one is "active"; the active connection is projected
 * into [Settings] so SSH/usage/push code keeps operating on a single set of
 * host/user/key values.
 *
 * NOTE: SSH private keys are stored here in app-private storage. For a personal,
 * non-published app that is acceptable; if you ever harden this, wrap the keys
 * with the Android Keystore (EncryptedSharedPreferences) instead.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        // Legacy single-connection keys — still read once for migration.
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val user = stringPreferencesKey("user")
        val key = stringPreferencesKey("private_key")
        val passphrase = stringPreferencesKey("key_passphrase")
        // Multi-connection storage.
        val connections = stringPreferencesKey("connections")
        val activeConn = stringPreferencesKey("active_connection")
        // Global, connection-independent.
        val session = stringPreferencesKey("tmux_session")
        val cap5h = longPreferencesKey("cap_5h")
        val capWeek = longPreferencesKey("cap_week")
        val fcmToken = stringPreferencesKey("fcm_token")
        val terminals = stringPreferencesKey("terminals")
    }

    // ---- Connections ----

    /** All configured connections; always at least one (seeded/migrated). */
    val connections: Flow<List<ConnectionProfile>> = context.dataStore.data.map { p ->
        decodeConnections(p[Keys.connections], legacyConnection(p))
    }

    /** The id of the active connection (defaults to the first one). */
    val activeConnectionId: Flow<String> = context.dataStore.data.map { p ->
        resolveActiveId(p)
    }

    suspend fun currentConnections(): List<ConnectionProfile> = connections.first()

    suspend fun currentActiveConnectionId(): String = activeConnectionId.first()

    suspend fun saveConnections(list: List<ConnectionProfile>) {
        context.dataStore.edit { it[Keys.connections] = encodeConnections(list) }
    }

    suspend fun setActiveConnection(id: String) {
        context.dataStore.edit { it[Keys.activeConn] = id }
    }

    /** Insert (new id) or update (existing id) a connection profile. */
    suspend fun saveConnection(profile: ConnectionProfile) {
        context.dataStore.edit { p ->
            val list = decodeConnections(p[Keys.connections], legacyConnection(p)).toMutableList()
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) list[idx] = profile else list.add(profile)
            p[Keys.connections] = encodeConnections(list)
            // First connection added becomes active by default.
            if (p[Keys.activeConn].isNullOrBlank()) p[Keys.activeConn] = profile.id
        }
    }

    /** Remove a connection. Never leaves the list empty; re-points active if needed. */
    suspend fun deleteConnection(id: String) {
        context.dataStore.edit { p ->
            val remaining = decodeConnections(p[Keys.connections], legacyConnection(p))
                .filterNot { it.id == id }
                .ifEmpty { listOf(ConnectionProfile("default", "Default")) }
            p[Keys.connections] = encodeConnections(remaining)
            if (p[Keys.activeConn] == id) p[Keys.activeConn] = remaining.first().id
        }
    }

    // ---- Active connection projected as Settings ----

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val conns = decodeConnections(p[Keys.connections], legacyConnection(p))
        val activeId = resolveActiveId(p)
        val active = conns.firstOrNull { it.id == activeId } ?: conns.first()
        val def = Settings()
        Settings(
            connectionId = active.id,
            connectionName = active.name,
            host = active.host,
            port = active.port,
            user = active.user,
            privateKeyPem = active.privateKeyPem,
            keyPassphrase = active.keyPassphrase,
            tmuxSession = p[Keys.session] ?: def.tmuxSession,
            cap5hTokens = p[Keys.cap5h] ?: def.cap5hTokens,
            capWeekTokens = p[Keys.capWeek] ?: def.capWeekTokens,
        )
    }

    suspend fun current(): Settings = settings.first()

    /** Save the active connection's edited fields + global caps. */
    suspend fun save(s: Settings) {
        context.dataStore.edit { p ->
            p[Keys.session] = s.tmuxSession
            p[Keys.cap5h] = s.cap5hTokens
            p[Keys.capWeek] = s.capWeekTokens

            val list = decodeConnections(p[Keys.connections], legacyConnection(p)).toMutableList()
            val updated = ConnectionProfile(
                id = s.connectionId,
                name = s.connectionName,
                host = s.host,
                port = s.port,
                user = s.user,
                privateKeyPem = s.privateKeyPem,
                keyPassphrase = s.keyPassphrase,
            )
            val idx = list.indexOfFirst { it.id == s.connectionId }
            if (idx >= 0) list[idx] = updated else list.add(updated)
            p[Keys.connections] = encodeConnections(list)
            p[Keys.activeConn] = s.connectionId
        }
    }

    /** The last FCM token we registered (so the server can push to this watch). */
    val fcmToken: Flow<String?> = context.dataStore.data.map { it[Keys.fcmToken] }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[Keys.fcmToken] = token }
    }

    // ---- Terminal profiles (named tmux targets) ----

    /** The user's terminal list; always returns at least one (seeded) profile. */
    val terminals: Flow<List<TerminalProfile>> = context.dataStore.data.map { p ->
        decodeTerminals(p[Keys.terminals], p[Keys.session] ?: Settings().tmuxSession)
    }

    suspend fun currentTerminals(): List<TerminalProfile> = terminals.first()

    suspend fun saveTerminals(list: List<TerminalProfile>) {
        context.dataStore.edit { it[Keys.terminals] = encodeTerminals(list) }
    }

    // ---- Encode / decode helpers ----

    /** Build a "Default" connection from the legacy single-connection keys (for migration). */
    private fun legacyConnection(p: Preferences): ConnectionProfile = ConnectionProfile(
        id = "default",
        name = "Default",
        host = p[Keys.host] ?: "",
        port = p[Keys.port] ?: 22,
        user = p[Keys.user] ?: "",
        privateKeyPem = p[Keys.key] ?: "",
        keyPassphrase = p[Keys.passphrase] ?: "",
    )

    private fun resolveActiveId(p: Preferences): String {
        val stored = p[Keys.activeConn]
        if (!stored.isNullOrBlank()) return stored
        return decodeConnections(p[Keys.connections], legacyConnection(p)).first().id
    }

    private fun encodeConnections(list: List<ConnectionProfile>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("host", c.host)
                    .put("port", c.port)
                    .put("user", c.user)
                    .put("key", c.privateKeyPem)
                    .put("pass", c.keyPassphrase),
            )
        }
        return arr.toString()
    }

    private fun decodeConnections(raw: String?, legacy: ConnectionProfile): List<ConnectionProfile> {
        // No stored list yet → migrate from the legacy single connection.
        if (raw.isNullOrBlank()) return listOf(legacy)
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ConnectionProfile(
                    id = o.optString("id", "c$i"),
                    name = o.optString("name", "Connection"),
                    host = o.optString("host", ""),
                    port = o.optInt("port", 22),
                    user = o.optString("user", ""),
                    privateKeyPem = o.optString("key", ""),
                    keyPassphrase = o.optString("pass", ""),
                )
            }.ifEmpty { listOf(legacy) }
        }.getOrDefault(listOf(legacy))
    }

    private fun encodeTerminals(list: List<TerminalProfile>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("name", t.name).put("session", t.tmuxSession))
        }
        return arr.toString()
    }

    private fun decodeTerminals(raw: String?, fallbackSession: String): List<TerminalProfile> {
        val seed = listOf(TerminalProfile("t1", "Claude 1", fallbackSession))
        if (raw.isNullOrBlank()) return seed
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TerminalProfile(
                    id = o.optString("id", "t$i"),
                    name = o.optString("name", "Claude"),
                    tmuxSession = o.optString("session", "claude"),
                )
            }.ifEmpty { seed }
        }.getOrDefault(seed)
    }
}
