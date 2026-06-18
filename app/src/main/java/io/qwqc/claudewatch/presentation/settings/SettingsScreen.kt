package io.qwqc.claudewatch.presentation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.qwqc.claudewatch.util.qrImageBitmap
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import io.qwqc.claudewatch.presentation.theme.ClaudePalette

@Composable
fun SettingsScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val form by vm.form.collectAsState()
    val publicKey by vm.publicKey.collectAsState()
    val generating by vm.generating.collectAsState()
    val conn by vm.conn.collectAsState()
    val listState = rememberScalingLazyListState()

    // Largest QR that comfortably fits the round face's inscribed square (~0.7×
    // diameter); 0.6 leaves a margin so the code never touches the bezel.
    val config = LocalConfiguration.current
    val qrSize = (minOf(config.screenWidthDp, config.screenHeightDp) * 0.6f).dp
    val qrBitmap = remember(publicKey) { publicKey?.let { qrImageBitmap(it) } }
    // Text fallback for when a scanner struggles (denser RSA keys) — reset per key.
    var showKeyText by remember(publicKey) { mutableStateOf(false) }

    // Port uses a local string mirror (seeded once from the loaded form).
    var portStr by remember { mutableStateOf("") }
    LaunchedEffect(form.port) { portStr = form.port.toString() }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Text("Settings", style = MaterialTheme.typography.title3, color = ClaudePalette.Orange) }

            item { Field("Host", form.host) { vm.edit { s -> s.copy(host = it) } } }
            item { Field("User", form.user) { vm.edit { s -> s.copy(user = it) } } }
            item { Field("Port", portStr, KeyboardType.Number) { portStr = it } }

            item {
                Text(
                    "SSH key",
                    style = MaterialTheme.typography.caption1,
                    color = ClaudePalette.Sand,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                Text(
                    if (form.privateKeyPem.isBlank()) "No key yet" else "Private key stored ✓",
                    style = MaterialTheme.typography.caption2,
                    color = if (form.privateKeyPem.isBlank()) ClaudePalette.Muted else ClaudePalette.Green,
                )
            }
            item {
                Button(onClick = vm::generateKey, enabled = !generating, modifier = Modifier.fillMaxWidth()) {
                    Text(if (generating) "Generating…" else "Generate key pair")
                }
            }
            item {
                Button(
                    onClick = vm::testConnection,
                    enabled = conn != ConnState.Testing,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text(if (conn == ConnState.Testing) "Testing…" else "Test connection") }
            }
            item { ConnStatus(conn) }
            if (qrBitmap != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Scan with your phone to copy the public key, then add it to the server's ~/.ssh/authorized_keys",
                            style = MaterialTheme.typography.caption2,
                            color = ClaudePalette.Sand,
                            textAlign = TextAlign.Center,
                        )
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "SSH public key QR code",
                            // Nearest-neighbour keeps module edges sharp when upscaled.
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(qrSize)
                                // White card + inner padding gives the quiet zone a
                                // scanner needs against the dark watch theme.
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(6.dp),
                        )
                        Button(
                            onClick = { showKeyText = !showKeyText },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) { Text(if (showKeyText) "Hide text" else "Show as text") }
                        if (showKeyText) {
                            Text(
                                publicKey!!,
                                style = MaterialTheme.typography.caption2.copy(fontSize = 9.sp),
                                color = ClaudePalette.Cream,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClaudePalette.Surface)
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        vm.edit { s -> s.copy(port = portStr.toIntOrNull() ?: s.port) }
                        vm.save(onSaved)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ConnStatus(conn: ConnState) {
    val (text, color) = when (conn) {
        ConnState.Idle -> return
        ConnState.Testing -> "Connecting…" to ClaudePalette.Sand
        ConnState.Ok -> "Connected ✓" to ClaudePalette.Green
        is ConnState.Fail -> "✗ ${conn.message}" to ClaudePalette.Red
    }
    Text(
        text,
        style = MaterialTheme.typography.caption2,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.caption2, color = ClaudePalette.Sand)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = ClaudePalette.Cream, fontSize = 14.sp),
            cursorBrush = SolidColor(ClaudePalette.Orange),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ClaudePalette.Surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
