package id.uinam.intellink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
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
    val throttle by viewModel.throttle.collectAsStateWithLifecycle()
    val steering by viewModel.steering.collectAsStateWithLifecycle()
    val online = state == ConnectionState.ONLINE
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Intellink.py", color = ACCENT, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            val (statusText, statusColor) = when (state) {
                ConnectionState.CONNECTING -> "Menghubungkan…" to PENDING
                ConnectionState.ONLINE -> "Terhubung" to ONLINE
                ConnectionState.OFFLINE -> "Tidak terhubung" to OFFLINE
            }
            Box(Modifier.size(12.dp).background(statusColor, CircleShape))
            Text(statusText, color = statusColor, fontSize = 14.sp)

            Spacer(Modifier.weight(1f))

            var ipText by remember(ip) { mutableStateOf(ip) }
            OutlinedTextField(
                value = ipText,
                onValueChange = { ipText = it },
                label = { Text("IP Address ESP32") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.onIpChanged(ipText)
                }),
                modifier = Modifier.width(220.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DriveSliderColumn(
                title = "MAJU / MUNDUR",
                hint = "MAJU ▲ · ▼ MUNDUR",
                value = throttle,
                enabled = online,
                onValueChange = viewModel::setThrottle,
                onRelease = viewModel::releaseThrottle,
                modifier = Modifier.weight(1f),
            )

            Row(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
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

            DriveSliderColumn(
                title = "KIRI / KANAN",
                hint = "KANAN ▲ · ▼ KIRI",
                value = steering,
                enabled = online,
                onValueChange = viewModel::setSteering,
                onRelease = viewModel::releaseSteering,
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.stopAll()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OFFLINE),
        ) {
            Text("STOP", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DriveSliderColumn(
    title: String,
    hint: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            text = formatDriveValue(value),
            color = Color.LightGray,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        VerticalSlider(
            value = value,
            enabled = enabled,
            onValueChange = onValueChange,
            onRelease = onRelease,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
        Text(hint, color = HINT, fontSize = 11.sp)
    }
}

private fun formatDriveValue(value: Int): String =
    if (value > 0) "+$value" else "$value"

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
        Spacer(Modifier.height(6.dp))
        Text(title, color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = HINT, fontSize = 10.sp)
        Text("$angle°", color = Color.LightGray, fontSize = 13.sp)
    }
}
