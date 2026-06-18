package io.qwqc.claudewatch.data.usage

/**
 * One rate-limit window's real state, as reported by the Anthropic API's
 * `anthropic-ratelimit-unified-*` response headers (the same data `/usage`
 * shows). On a Pro/Max plan there is no published token cap, so [utilization]
 * — a true 0..1 fraction — is the only meaningful "how full am I" signal.
 */
data class UsageWindow(
    /** Fraction of this window's limit consumed (0f..1f, may exceed 1f). */
    val utilization: Float,
    /** Minutes until this window resets, or null if unknown. */
    val resetsInMinutes: Int?,
    /** Rate-limit status: "allowed", "rejected", "unknown", … */
    val status: String,
)

data class UsageSnapshot(
    /** Rolling 5-hour window. */
    val block: UsageWindow,
    /** Rolling 7-day window. */
    val week: UsageWindow,
    /** Which window is currently binding ("five_hour" / "seven_day"). */
    val representative: String,
    val fetchedAtMillis: Long,
)
