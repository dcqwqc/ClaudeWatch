package io.qwqc.claudewatch.util

import java.util.Locale

/** 1_234 -> "1.2K", 3_400_000 -> "3.4M". */
fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", tokens / 1_000_000_000.0)
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

fun formatCost(usd: Double): String = String.format(Locale.US, "$%.2f", usd)

/** Minutes -> "3d 4h" / "2h 22m" / "47m". */
fun formatResetIn(minutes: Int?): String? {
    if (minutes == null || minutes < 0) return null
    val d = minutes / 1440
    val h = (minutes % 1440) / 60
    val m = minutes % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

fun formatPercent(fraction: Float): String =
    "${(fraction.coerceIn(0f, 1f) * 100f).toInt()}%"
