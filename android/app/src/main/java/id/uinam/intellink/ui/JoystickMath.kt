package id.uinam.intellink.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/** Perintah drive yang sudah diskalakan ke rentang protokol. */
data class DriveCommand(val throttle: Int, val steering: Int)

/**
 * Konversi offset kenop joystick (px; dy positif = ke bawah mengikuti konvensi layar)
 * ke perintah drive: dorong atas => throttle positif, geser kanan => steering positif.
 * Nilai selalu valid (-255..255) walau input ekstrem/NaN.
 */
fun joystickToDrive(dx: Float, dy: Float, radius: Float): DriveCommand {
    if (radius <= 0f || dx.isNaN() || dy.isNaN()) return DriveCommand(0, 0)
    val steering = (dx / radius * 255f).roundToInt().coerceIn(-255, 255)
    val throttle = (-dy / radius * 255f).roundToInt().coerceIn(-255, 255)
    return DriveCommand(throttle, steering)
}

/** Batasi offset kenop supaya tidak keluar lingkaran joystick. */
fun clampToCircle(offset: Offset, radius: Float): Offset {
    val length = offset.getDistance()
    if (length <= radius || length == 0f) return offset
    return offset * (radius / length)
}
