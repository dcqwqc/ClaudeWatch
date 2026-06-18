package io.qwqc.claudewatch.presentation.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import io.qwqc.claudewatch.data.terminal.TerminalProfile
import io.qwqc.claudewatch.presentation.theme.ClaudePalette

/**
 * Add or edit one terminal profile. [editId] is null when adding a new one.
 */
@Composable
fun TerminalEditScreen(
    editId: String?,
    onDone: () -> Unit,
) {
    val vm: TerminalsViewModel = viewModel()
    val terminals by vm.terminals.collectAsState()
    val existing = terminals.firstOrNull { it.id == editId }

    var name by remember(existing) { mutableStateOf(existing?.name ?: vm.nextDefaultName()) }
    var session by remember(existing) { mutableStateOf(existing?.tmuxSession ?: "claude") }

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
                Text(
                    if (editId == null) "New terminal" else "Edit terminal",
                    style = MaterialTheme.typography.title3,
                    color = ClaudePalette.Orange,
                )
            }
            item { Field("Name", name) { name = it } }
            item { Field("tmux session", session) { session = it } }
            item {
                Button(
                    onClick = {
                        vm.save(
                            TerminalProfile(
                                id = existing?.id ?: vm.newId(),
                                name = name.trim().ifBlank { "Claude" },
                                tmuxSession = session.trim().ifBlank { "claude" },
                            ),
                        )
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text("Save") }
            }
            if (existing != null && terminals.size > 1) {
                item {
                    Button(
                        onClick = { vm.delete(existing.id); onDone() },
                        colors = ButtonDefaults.secondaryButtonColors(
                            backgroundColor = ClaudePalette.SurfaceHi,
                            contentColor = ClaudePalette.Red,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.caption2, color = ClaudePalette.Sand)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = ClaudePalette.Cream, fontSize = 14.sp),
            cursorBrush = SolidColor(ClaudePalette.Orange),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ClaudePalette.Surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
