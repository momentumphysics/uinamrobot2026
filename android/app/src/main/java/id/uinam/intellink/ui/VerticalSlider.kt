package id.uinam.intellink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RANGE = 255f
private val TRACK_WIDTH = 8.dp
private val THUMB_RADIUS = 13.dp
private val ACTIVE = Color(0xFF00E676)
private val ACCENT = Color(0xFF40C4FF)
private val DISABLED = Color(0xFF555555)
private val TRACK = Color(0xFF333333)

/**
 * Slider vertikal dua arah (-255..255) untuk drive, padanan slider di controller.html.
 * Atas = nilai positif (maju / kanan), bawah = negatif (mundur / kiri), tengah = 0.
 * Lepas jari memanggil [onRelease]; pemanggil yang mengembalikan nilai ke 0 (pegas).
 */
@Composable
fun VerticalSlider(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnRelease by rememberUpdatedState(onRelease)

    Canvas(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val thumb = THUMB_RADIUS.toPx()
                val travel = (size.height - thumb * 2f).coerceAtLeast(1f)
                var startValue = 0f
                var dragged = 0f
                detectDragGestures(
                    onDragStart = {
                        startValue = currentValue.toFloat()
                        dragged = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { currentOnRelease() },
                    onDragCancel = { currentOnRelease() },
                    onDrag = { change, amount ->
                        change.consume()
                        dragged += amount.y
                        val next = (startValue - dragged / travel * (RANGE * 2f))
                            .roundToInt()
                            .coerceIn(-255, 255)
                        currentOnChange(next)
                    },
                )
            },
    ) {
        val centerX = size.width / 2f
        val thumbR = THUMB_RADIUS.toPx()
        val trackW = TRACK_WIDTH.toPx()
        val top = thumbR
        val bottom = size.height - thumbR
        val travel = (bottom - top).coerceAtLeast(1f)
        val fraction = (value.coerceIn(-255, 255) + 255) / 510f
        val thumbY = bottom - fraction * travel
        val midY = top + travel / 2f
        val activeColor = if (enabled) ACTIVE else DISABLED
        val accentColor = if (enabled) ACCENT else DISABLED
        val trackColor = if (enabled) TRACK else DISABLED.copy(alpha = 0.35f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(centerX - trackW / 2f, top),
            size = Size(trackW, travel),
            cornerRadius = CornerRadius(trackW / 2f),
        )
        // Segmen aktif dari titik nol (tengah) ke posisi kenop.
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(centerX - trackW / 2f, minOf(midY, thumbY)),
            size = Size(trackW, abs(midY - thumbY)),
            cornerRadius = CornerRadius(trackW / 2f),
        )
        // Penanda titik nol.
        drawCircle(
            color = accentColor.copy(alpha = 0.6f),
            radius = trackW * 0.35f,
            center = Offset(centerX, midY),
        )
        drawCircle(color = activeColor, radius = thumbR, center = Offset(centerX, thumbY))
    }
}
