package io.qwqc.claudewatch.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import io.qwqc.claudewatch.data.terminal.TerminalProfile
import io.qwqc.claudewatch.presentation.mascot.ClaudeMascot
import io.qwqc.claudewatch.presentation.mascot.MascotMood
import io.qwqc.claudewatch.presentation.terminal.TerminalsViewModel
import io.qwqc.claudewatch.presentation.theme.ClaudePalette
import io.qwqc.claudewatch.presentation.usage.UsageBars
import io.qwqc.claudewatch.presentation.usage.UsageViewModel

@Composable
fun HomeScreen(
    onOpenTerminal: (String) -> Unit,
    onEditTerminal: (String) -> Unit,
    onAddTerminal: () -> Unit,
    onSettings: () -> Unit,
) {
    val usageVm: UsageViewModel = viewModel()
    val terminalsVm: TerminalsViewModel = viewModel()
    val usage by usageVm.state.collectAsState()
    val terminals by terminalsVm.terminals.collectAsState()

    // Auto-refresh usage on every full minute, while Home is on screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            usageVm.refreshSilently()
        }
    }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dancing Claude
            item { ClaudeMascot(mood = MascotMood.Idle, modifier = Modifier.size(96.dp)) }

            // Two usage bars right below the mascot
            item { UsageBars(state = usage, onRetry = usageVm::refresh) }

            item {
                Text(
                    "Terminals",
                    style = MaterialTheme.typography.caption1,
                    color = ClaudePalette.Sand,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            items(terminals) { profile ->
                TerminalRow(
                    profile = profile,
                    onOpen = { onOpenTerminal(profile.id) },
                    onEdit = { onEditTerminal(profile.id) },
                )
            }

            item {
                Chip(
                    onClick = onAddTerminal,
                    label = { Text("Add terminal") },
                    icon = { Text("＋") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Chip(
                    onClick = onSettings,
                    label = { Text("Settings") },
                    icon = { Text("⚙") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A terminal entry: tap the chip to open it, tap the gear to edit name/session. */
@Composable
private fun TerminalRow(
    profile: TerminalProfile,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(
            onClick = onOpen,
            label = { Text(profile.name) },
            secondaryLabel = { Text(profile.tmuxSession, color = ClaudePalette.Muted) },
            icon = { Text("▮", color = ClaudePalette.Orange) },
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = ClaudePalette.Surface,
                contentColor = ClaudePalette.Cream,
            ),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(ClaudePalette.SurfaceHi)
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙", color = ClaudePalette.Sand)
        }
    }
}
