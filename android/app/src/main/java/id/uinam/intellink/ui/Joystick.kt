package id.uinam.intellink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

private const val BASE_RADIUS_FACTOR = 0.6f      // radius area gerak = 60% setengah sisi
private const val KNOB_RADIUS_FACTOR = 0.12f     // radius kenop
private val ACCENT = Color(0xFF40C4FF)
private val ACTIVE = Color(0xFF00E676)
private val DISABLED = Color(0xFF555555)

/**
 * Joystick lingkaran: dorong ke arah mana pun (dibatasi lingkaran), lepas => kembali ke tengah
 * dan memanggil [onRelease] (UI akan memanggil stop seketika).
 */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onMove: (DriveCommand) -> Unit,
    onRelease: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var knob by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val radius = minOf(size.width, size.height) / 2f * BASE_RADIUS_FACTOR
                detectDragGestures(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        knob = Offset.Zero
                        onRelease()
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        onRelease()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        knob = clampToCircle(knob + dragAmount, radius)
                        onMove(joystickToDrive(knob.x, knob.y, radius))
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f * BASE_RADIUS_FACTOR
        val accent = if (enabled) ACCENT else DISABLED
        val knobColor = if (enabled) ACTIVE else DISABLED

        drawCircle(color = accent.copy(alpha = 0.12f), radius = radius, center = center)
        drawCircle(color = accent.copy(alpha = 0.6f), radius = radius, center = center, style = Stroke(width = 4f))
        drawCircle(
            color = knobColor,
            radius = minOf(size.width, size.height) * KNOB_RADIUS_FACTOR,
            center = center + knob,
        )
    }
}
