package io.qwqc.claudewatch.data.settings

import android.content.Context
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
 * NOTE: the SSH private key is stored here in app-private storage. For a
 * personal, non-published app that is acceptable; if you ever harden this,
 * wrap the key with the Android Keystore (EncryptedSharedPreferences) instead.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val user = stringPreferencesKey("user")
        val key = stringPreferencesKey("private_key")
        val passphrase = stringPreferencesKey("key_passphrase")
        val session = stringPreferencesKey("tmux_session")
        val cap5h = longPreferencesKey("cap_5h")
        val capWeek = longPreferencesKey("cap_week")
        val fcmToken = stringPreferencesKey("fcm_token")
        val terminals = stringPreferencesKey("terminals")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val def = Settings()
        Settings(
            host = p[Keys.host] ?: def.host,
            port = p[Keys.port] ?: def.port,
            user = p[Keys.user] ?: def.user,
            privateKeyPem = p[Keys.key] ?: def.privateKeyPem,
            keyPassphrase = p[Keys.passphrase] ?: def.keyPassphrase,
            tmuxSession = p[Keys.session] ?: def.tmuxSession,
            cap5hTokens = p[Keys.cap5h] ?: def.cap5hTokens,
            capWeekTokens = p[Keys.capWeek] ?: def.capWeekTokens,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun save(s: Settings) {
        context.dataStore.edit { p ->
            p[Keys.host] = s.host
            p[Keys.port] = s.port
            p[Keys.user] = s.user
            p[Keys.key] = s.privateKeyPem
            p[Keys.passphrase] = s.keyPassphrase
            p[Keys.session] = s.tmuxSession
            p[Keys.cap5h] = s.cap5hTokens
            p[Keys.capWeek] = s.capWeekTokens
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
