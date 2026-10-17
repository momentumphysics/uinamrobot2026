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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlin.math.roundToInt

private val ACCENT = Color(0xFF40C4FF)
private val ONLINE = Color(0xFF00E676)
private val OFFLINE = Color(0xFFFF1744)
private val PENDING = Color(0xFFFFC107)

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

        ServoSlider(
            label = "Servo 2 (GPIO 4)",
            value = servo2,
            enabled = online,
            onValueChange = viewModel::setServo2,
        )
        ServoSlider(
            label = "Servo 3 (GPIO 16)",
            value = servo3,
            enabled = online,
            onValueChange = viewModel::setServo3,
        )

        Joystick(
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = online,
            onMove = { viewModel.setDrive(it.throttle, it.steering) },
            onRelease = { viewModel.stopDrive() },
        )

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.stopDrive()
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OFFLINE),
        ) {
            Text("STOP", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ServoSlider(label: String, value: Int, enabled: Boolean, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value°", color = Color.LightGray, fontSize = 14.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..180f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = ONLINE,
                activeTrackColor = ONLINE,
            ),
        )
    }
}
