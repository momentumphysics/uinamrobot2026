package id.uinam.intellink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * Kontrol servo satu sumbu (atas-turun) bergaya joystick, pasangan visual [Joystick].
 * Dorong ke atas => [onDeflect] positif (buka/naik), ke bawah => negatif (tutup/turun);
 * lepas => kenop kembali ke tengah dan [onRelease] dipanggil (sudut berhenti di nilai terakhir).
 */
@Composable
fun ServoStick(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDeflect: (Float) -> Unit,
    onRelease: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var knobY by remember { mutableFloatStateOf(0f) }

    // Sama seperti Joystick: fokus jari bisa dibatalkan saat koneksi putus tanpa
    // onDragCancel (pointerInput dibatalkan karena `enabled` berubah), jadi
    // kenop direset dan laju dinolkan di sini.
    LaunchedEffect(enabled) {
        if (!enabled) {
            knobY = 0f
            onRelease()
        }
    }

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
                        knobY = 0f
                        onRelease()
                    },
                    onDragCancel = {
                        knobY = 0f
                        onRelease()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        knobY = (knobY + dragAmount.y).coerceIn(-radius, radius)
                        onDeflect(servoDeflection(knobY, radius))
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
        // Garis panduan vertikal: menandakan kontrol ini hanya bergerak atas-turun.
        drawLine(
            color = accent.copy(alpha = 0.25f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 3f,
        )
        drawCircle(
            color = knobColor,
            radius = minOf(size.width, size.height) * KNOB_RADIUS_FACTOR,
            center = center + Offset(0f, knobY),
        )
    }
}
