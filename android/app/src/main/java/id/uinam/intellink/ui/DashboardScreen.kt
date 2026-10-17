package id.uinam.intellink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.uinam.intellink.control.ConnectionState
import id.uinam.intellink.control.ControlViewModel

private val ACCENT = Color(0xFF40C4FF)
private val ONLINE = Color(0xFF00E676)
private val OFFLINE = Color(0xFFFF1744)
private val PENDING = Color(0xFFFFC107)
private val HINT = Color(0xFF9E9E9E)

@Composable
fun DashboardScreen(viewModel: ControlViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ip by viewModel.ip.collectAsStateWithLifecycle()
    val servo2 by viewModel.servo2.collectAsStateWithLifecycle()
    val servo3 by viewModel.servo3.collectAsStateWithLifecycle()
    val online = state == ConnectionState.ONLINE
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Intellink.py", color = ACCENT, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        val (statusText, statusColor) = when (state) {
            ConnectionState.CONNECTING -> "Menghubungkan…" to PENDING
            ConnectionState.ONLINE -> "Terhubung" to ONLINE
            ConnectionState.OFFLINE -> "Tidak terhubung" to OFFLINE
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(statusText, color = statusColor, fontSize = 18.sp)
        }

        var ipText by remember(ip) { mutableStateOf(ip) }
        OutlinedTextField(
            value = ipText,
            onValueChange = { ipText = it },
            label = { Text("IP Address ESP32") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.onIpChanged(ipText)
            }),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ServoControl(
                title = "CAPIT",
                hint = "BUKA ▲ · TUTUP ▼",
                angle = servo2,
                enabled = online,
                onDeflect = viewModel::setServo2Rate,
                onRelease = { viewModel.setServo2Rate(0f) },
                modifier = Modifier.weight(1f),
            )
            ServoControl(
                title = "LENGAN",
                hint = "NAIK ▲ · TURUN ▼",
                angle = servo3,
                enabled = online,
                onDeflect = viewModel::setServo3Rate,
                onRelease = { viewModel.setServo3Rate(0f) },
                modifier = Modifier.weight(1f),
            )
        }

        Joystick(
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = online,
            onMove = { viewModel.setDrive(it.throttle, it.steering) },
            onRelease = { viewModel.stopDrive() },
        )

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.stopAll()
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OFFLINE),
        ) {
            Text("STOP", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ServoControl(
    title: String,
    hint: String,
    angle: Int,
    enabled: Boolean,
    onDeflect: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ServoStick(
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            onDeflect = onDeflect,
            onRelease = onRelease,
        )
        Spacer(Modifier.height(8.dp))
        Text(title, color = ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = HINT, fontSize = 12.sp)
        Text("$angle°", color = Color.LightGray, fontSize = 14.sp)
    }
}
