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
import kotlinx.coroutines.delay

/**
 * The main screen of the application.
 *
 * Displays the mascot, usage bars, a list of configured terminals, and navigation
 * buttons for adding a new terminal or accessing settings.
 *
 * @param onOpenTerminal Callback invoked when a terminal row is tapped.
 * @param onEditTerminal Callback invoked when a terminal's edit button is tapped.
 * @param onAddTerminal Callback for the "Add terminal" button.
 * @param onSettings Callback for the "Settings" button.
 */
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
    val activeConnection by terminalsVm.activeConnection.collectAsState()

    // Auto-refresh usage on every full minute, while Home is on screen.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
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
            item {
                ClaudeMascot(
                    mood = MascotMood.Idle, // Mood is currently static
                    modifier = Modifier.size(96.dp)
                )
            }

            item { UsageBars(state = usage, onRetry = usageVm::refresh) }

            item {
                Text(
                    text = if (activeConnection.isBlank()) "Terminals" else "Terminals · $activeConnection",
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

/**
 * A single row in the terminal list, showing the terminal name and an edit button.
 */
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
