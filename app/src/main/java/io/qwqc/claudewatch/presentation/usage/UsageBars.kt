package io.qwqc.claudewatch.presentation.usage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import io.qwqc.claudewatch.presentation.theme.ClaudePalette
import io.qwqc.claudewatch.util.formatResetIn

/**
 * The two usage bars shown on Home, just below the mascot: the rolling 5-hour
 * window and the rolling weekly window, each with a "resets in …" countdown.
 * The fill is the REAL rate-limit utilisation reported by the API.
 */
@Composable
fun UsageBars(
    state: UsageUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (state) {
            is UsageUiState.Loading -> Text(
                "Reading usage…",
                style = MaterialTheme.typography.caption2,
                color = ClaudePalette.Muted,
            )

            is UsageUiState.NotConfigured -> Text(
                state.reason,
                style = MaterialTheme.typography.caption2,
                color = ClaudePalette.Muted,
                textAlign = TextAlign.Center,
            )

            is UsageUiState.Error -> Text(
                "Usage: ${state.message}",
                style = MaterialTheme.typography.caption2,
                color = ClaudePalette.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onRetry),
            )

            is UsageUiState.Ready -> {
                val snap = state.snapshot
                UsageBar("5-hour", snap.block.utilization, snap.block.resetsInMinutes)
                UsageBar("Weekly", snap.week.utilization, snap.week.resetsInMinutes)
            }
        }
    }
}

@Composable
private fun UsageBar(label: String, utilization: Float, resetsInMinutes: Int?) {
    val pct = (utilization.coerceAtLeast(0f) * 100f).toInt()
    val animated by animateFloatAsState(
        targetValue = utilization.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "bar",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.caption2, color = ClaudePalette.Sand)
            Text("$pct%", style = MaterialTheme.typography.caption2, color = barColor(utilization))
        }
        // track + fill
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ClaudePalette.SurfaceHi),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor(utilization)),
            )
        }
        val reset = formatResetIn(resetsInMinutes)
        Text(
            if (reset != null) "resets in $reset" else "—",
            style = MaterialTheme.typography.caption2,
            color = ClaudePalette.Muted,
        )
    }
}

// Consistent Claude orange for both bars; red only once a window is maxed out.
private fun barColor(fraction: Float): Color =
    if (fraction >= 1f) ClaudePalette.Red else ClaudePalette.Orange
