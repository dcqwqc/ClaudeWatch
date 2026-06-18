package io.qwqc.claudewatch.presentation.terminal

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import io.qwqc.claudewatch.data.terminal.TerminalController
import io.qwqc.claudewatch.data.terminal.TerminalEmulator
import io.qwqc.claudewatch.data.terminal.TermSpan
import io.qwqc.claudewatch.presentation.theme.ClaudePalette
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FONT_SP = 9f
private const val CHAR_WIDTH_EM = 0.6f
private const val LINE_HEIGHT_EM = 1.18f
private const val KEY_TEXT = "claude_text"

// Raw control-key byte sequences sent to the PTY.
private val ESC = byteArrayOf(0x1B)
private val TAB = byteArrayOf(0x09)
private val CTRL_C = byteArrayOf(0x03)
private val ARROW_U = byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())
private val ARROW_D = byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())
private val ARROW_R = byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())
private val ARROW_L = byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())
private val BACKSPACE = byteArrayOf(0x7F) // DEL — Claude/readline treats this as backspace

@Composable
fun TerminalScreen(
    profileId: String,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: TerminalViewModel = viewModel()
    val snapshot by vm.snapshot.collectAsState()
    val status by vm.status.collectAsState()
    val needsConfig by vm.needsConfig.collectAsState()
    val title by vm.title.collectAsState()

    LaunchedEffect(profileId) { vm.bind(profileId) }

    // Native Wear text entry (keyboard / voice / handwriting). The user's enter
    // confirms the dialog; we then type the text into tmux and press Enter.
    val keyboard = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        // The user confirmed the dialog — type the text into Claude's input box
        // at the cursor (an insert, not a replace), with no Enter so they can
        // position it / review first.
        RemoteInput.getResultsFromIntent(data)?.getCharSequence(KEY_TEXT)?.let {
            vm.typeText(it.toString())
        }
    }
    val launchKeyboard: () -> Unit = {
        val inputs = listOf(RemoteInput.Builder(KEY_TEXT).setLabel("Message Claude").build())
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, inputs)
        keyboard.launch(intent)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color(TerminalEmulator.DEF_BG)),
    ) {
        val density = LocalDensity.current
        val dMin = minOf(maxWidth, maxHeight)
        val square = dMin * 0.75f // Larger view for scrolling

        val fontPx = with(density) { FONT_SP.sp.toPx() }
        val charWpx = fontPx * CHAR_WIDTH_EM
        val lineHpx = fontPx * LINE_HEIGHT_EM
        val squarePx = with(density) { square.toPx() }
        val cols = (squarePx / charWpx).toInt().coerceIn(16, 120)
        val rows = (squarePx / lineHpx).toInt().coerceIn(8, 60)

        LaunchedEffect(cols, rows) { vm.onSize(cols, rows) }

        // Title
        Text(
            title,
            style = MaterialTheme.typography.caption1,
            color = ClaudePalette.Orange,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        )

        // Terminal grid (scrollable with rotary/touch)
        TerminalGrid(
            snapshot = snapshot,
            charWpx = charWpx,
            lineHpx = lineHpx,
            onScroll = vm::onScroll,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 18.dp, bottom = 26.dp)
                .size(square),
        )

        // Control keys laid out along the bottom arc.
        BottomArcKeys(
            modifier = Modifier.fillMaxSize(),
            startDeg = 178f,   // spread wider for 11 keys
            endDeg = 2f,
            radiusFraction = 0.88f,
        ) {
            KeyButton("scr") { vm.enterCopyMode() }
            KeyButton("esc") { vm.sendBytes(ESC) }
            KeyButton("tab") { vm.sendBytes(TAB) }
            KeyButton("^C") { vm.sendBytes(CTRL_C) }
            KeyButton("⌨", accent = true, onClick = launchKeyboard)
            KeyButton("⌫") { vm.sendBytes(BACKSPACE) }
            KeyButton("⏎", accent = true) { vm.pressEnter() }
            KeyButton("←") { vm.sendBytes(ARROW_L) }
            KeyButton("↓") { vm.sendBytes(ARROW_D) }
            KeyButton("↑") { vm.sendBytes(ARROW_U) }
            KeyButton("→") { vm.sendBytes(ARROW_R) }
        }

        // Status overlays. A first connect (no frame yet) gets the full overlay;
        // a re-attach after a background drop (e.g. while the keyboard dialog is up)
        // keeps the last frame on screen with only a small spinner, so the terminal
        // doesn't blank out every time we briefly lose the radio.
        when (val st = status) {
            is TerminalController.Status.Connecting -> {
                if (snapshot.rows.isEmpty()) {
                    Overlay {
                        CircularProgressIndicator(indicatorColor = ClaudePalette.Orange)
                        Text("Attaching tmux…", style = MaterialTheme.typography.caption1)
                    }
                } else {
                    CircularProgressIndicator(
                        indicatorColor = ClaudePalette.Orange,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(14.dp),
                    )
                }
            }
            is TerminalController.Status.Error -> Overlay {
                Text("SSH error", color = ClaudePalette.Red, style = MaterialTheme.typography.title3)
                Text(st.message, style = MaterialTheme.typography.caption2, maxLines = 4)
                if (needsConfig) {
                    Button(onClick = onConfigure) { Text("Settings") }
                } else {
                    Button(onClick = { vm.retry() }) { Text("Retry") }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun TerminalGrid(
    snapshot: io.qwqc.claudewatch.data.terminal.TerminalSnapshot,
    charWpx: Float,
    lineHpx: Float,
    onScroll: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val all = snapshot.rows
    val lines = remember(snapshot) { all.map { it.toAnnotated() } }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Auto-scroll to bottom on new output if we are already near the end
    LaunchedEffect(lines.size) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lines.isNotEmpty() && lastVisible >= lines.size - 15) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    // Request focus for rotary input
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(TerminalEmulator.DEF_BG))
            .onRotaryScrollEvent {
                // Send the scroll delta to the ViewModel to drive tmux/PTY scrolling
                onScroll(it.verticalScrollPixels)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = false, // Disable touch scroll; use rotary only for PTY scrolling
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { i, line ->
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        text = line,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = FONT_SP.sp,
                            lineHeight = (FONT_SP * LINE_HEIGHT_EM).sp,
                            letterSpacing = 0.sp,
                        ),
                    )
                    // Draw cursor only on the active emulator line
                    if (snapshot.cursorVisible && i == snapshot.cursorRow && snapshot.cols > 0) {
                        val charWdp = with(density) { charWpx.toDp() }
                        val lineHdp = with(density) { lineHpx.toDp() }
                        Box(
                            Modifier
                                .offset(x = charWdp * snapshot.cursorCol.toFloat())
                                .size(charWdp, lineHdp)
                                .background(ClaudePalette.Orange.copy(alpha = 0.55f)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lays out its children evenly along an arc (from [startDeg] to [endDeg],
 * measured clockwise with 90° = bottom) at [radiusFraction] of the radius —
 * so the keys hug the bottom curve of the round screen.
 */
@Composable
private fun BottomArcKeys(
    modifier: Modifier,
    startDeg: Float,
    endDeg: Float,
    radiusFraction: Float,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(w, h) {
            val cx = w / 2f
            val cy = h / 2f
            val r = (minOf(w, h) / 2f) * radiusFraction
            val n = placeables.size
            placeables.forEachIndexed { i, p ->
                val t = if (n <= 1) 0.5f else i.toFloat() / (n - 1)
                val rad = Math.toRadians((startDeg + (endDeg - startDeg) * t).toDouble())
                val x = cx + (r * cos(rad)).toFloat() - p.width / 2f
                val y = cy + (r * sin(rad)).toFloat() - p.height / 2f
                p.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (accent) ClaudePalette.Orange else ClaudePalette.SurfaceHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (accent) ClaudePalette.Black else ClaudePalette.Cream,
            style = MaterialTheme.typography.caption2,
        )
    }
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) { content() }
    }
}

/** Build a styled monospace line from terminal spans. */
private fun List<TermSpan>.toAnnotated(): AnnotatedString =
    buildAnnotatedString {
        this@toAnnotated.forEach { sp ->
            withStyle(
                SpanStyle(
                    color = Color(sp.fg),
                    background = Color(sp.bg),
                    fontWeight = if (sp.bold) FontWeight.Bold else FontWeight.Normal,
                ),
            ) { append(sp.text) }
        }
    }
