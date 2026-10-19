package id.uinam.intellink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * Slider dua arah (-255..255) untuk drive, padanan slider di controller.html.
 * Vertikal: atas = positif. Horizontal: kanan = positif. Tengah = 0.
 * Lepas jari memanggil [onRelease]; pemanggil yang mengembalikan nilai ke 0 (pegas).
 */
@Composable
fun DriveSlider(
    value: Int,
    enabled: Boolean,
    orientation: Orientation,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnRelease by rememberUpdatedState(onRelease)

    val sizeModifier = if (orientation == Orientation.Vertical) {
        Modifier.width(48.dp).fillMaxHeight()
    } else {
        Modifier.fillMaxWidth().height(48.dp)
    }

    Canvas(
        modifier = modifier
            .then(sizeModifier)
            .pointerInput(enabled, orientation) {
                if (!enabled) return@pointerInput
                val thumb = THUMB_RADIUS.toPx()
                val length = if (orientation == Orientation.Vertical) {
                    size.height - thumb * 2f
                } else {
                    size.width - thumb * 2f
                }
                val travel = length.coerceAtLeast(1f)
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
                        dragged += if (orientation == Orientation.Vertical) amount.y else amount.x
                        val direction = if (orientation == Orientation.Vertical) -1f else 1f
                        val next = (startValue + direction * dragged / travel * (RANGE * 2f))
                            .roundToInt()
                            .coerceIn(-255, 255)
                        currentOnChange(next)
                    },
                )
            },
    ) {
        val thumbR = THUMB_RADIUS.toPx()
        val trackW = TRACK_WIDTH.toPx()
        val activeColor = if (enabled) ACTIVE else DISABLED
        val accentColor = if (enabled) ACCENT else DISABLED
        val trackColor = if (enabled) TRACK else DISABLED.copy(alpha = 0.35f)
        val fraction = (value.coerceIn(-255, 255) + 255) / 510f

        if (orientation == Orientation.Vertical) {
            val centerX = size.width / 2f
            val top = thumbR
            val bottom = size.height - thumbR
            val travel = (bottom - top).coerceAtLeast(1f)
            val thumbY = bottom - fraction * travel
            val midY = top + travel / 2f

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - trackW / 2f, top),
                size = Size(trackW, travel),
                cornerRadius = CornerRadius(trackW / 2f),
            )
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(centerX - trackW / 2f, minOf(midY, thumbY)),
                size = Size(trackW, abs(midY - thumbY)),
                cornerRadius = CornerRadius(trackW / 2f),
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.6f),
                radius = trackW * 0.35f,
                center = Offset(centerX, midY),
            )
            drawCircle(color = activeColor, radius = thumbR, center = Offset(centerX, thumbY))
        } else {
            val centerY = size.height / 2f
            val left = thumbR
            val right = size.width - thumbR
            val travel = (right - left).coerceAtLeast(1f)
            val thumbX = left + fraction * travel
            val midX = left + travel / 2f

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, centerY - trackW / 2f),
                size = Size(travel, trackW),
                cornerRadius = CornerRadius(trackW / 2f),
            )
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(minOf(midX, thumbX), centerY - trackW / 2f),
                size = Size(abs(midX - thumbX), trackW),
                cornerRadius = CornerRadius(trackW / 2f),
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.6f),
                radius = trackW * 0.35f,
                center = Offset(midX, centerY),
            )
            drawCircle(color = activeColor, radius = thumbR, center = Offset(thumbX, centerY))
        }
    }
}
