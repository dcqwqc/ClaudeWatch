package io.qwqc.claudewatch.data.usage

import io.qwqc.claudewatch.data.ssh.SshManager
import org.json.JSONObject
import java.io.IOException

/**
 * Reads Claude usage by running the server-side `claude-watch-usage` helper over
 * SSH (see server/claude-watch-usage.sh). That script reads the OAuth token
 * Claude Code stored on the server and asks the Anthropic API for the real
 * rate-limit utilisation, emitting a STABLE normalized shape:
 *
 * {
 *   "block": { "utilization": 0.24, "resetsInMinutes": 277, "status": "allowed" },
 *   "week":  { "utilization": 0.38, "resetsInMinutes": 5837, "status": "allowed" },
 *   "representative": "five_hour"
 * }
 *
 * On failure it emits {"error":"…"} and exits non-zero. We invoke it through
 * `bash -lc` so the login profile (PATH for jq/curl) is loaded.
 */
class UsageRepository(private val ssh: SshManager) {

    suspend fun fetch(): UsageSnapshot {
        val result = ssh.exec("bash -lc 'claude-watch-usage'", timeoutMs = 25_000)
        // The helper prints {"error":"…"} (and exits 1) on any failure — surface it verbatim.
        extractError(result.stdout)?.let { throw IOException(it) }
        if (!result.isSuccess) {
            val detail = result.stderr.ifBlank { result.stdout }.trim().take(200)
            throw IOException(detail.ifBlank { "usage helper exited ${result.exitCode}" })
        }
        return parse(result.stdout)
    }

    private fun extractError(raw: String): String? {
        val obj = firstJsonObject(raw) ?: return null
        return obj.optString("error").ifBlank { null }
    }

    private fun parse(raw: String): UsageSnapshot {
        val json = firstJsonObject(raw)
            ?: throw IOException("Unexpected usage output: ${raw.take(120)}")

        fun window(key: String): UsageWindow {
            val o = json.optJSONObject(key) ?: JSONObject()
            val resets = if (o.has("resetsInMinutes") && !o.isNull("resetsInMinutes")) {
                o.optInt("resetsInMinutes")
            } else null
            return UsageWindow(
                utilization = o.optDouble("utilization", 0.0).toFloat(),
                resetsInMinutes = resets,
                status = o.optString("status", "unknown"),
            )
        }

        return UsageSnapshot(
            block = window("block"),
            week = window("week"),
            representative = json.optString("representative", "five_hour"),
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    /** Tolerate leading profile noise: grab the first complete JSON object. */
    private fun firstJsonObject(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }
}
