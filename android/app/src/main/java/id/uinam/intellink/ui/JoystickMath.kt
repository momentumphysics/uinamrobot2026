package id.uinam.intellink.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
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

/** Zona tengah kenop servo yang dianggap diam (fraksi radius). */
const val SERVO_DEADZONE = 0.15f

/**
 * Defleksi kenop servo dari titik tengah: +1 = dorong atas penuh (buka/naik),
 * -1 = dorong bawah penuh (tutup/turun). Di dalam [SERVO_DEADZONE] dianggap 0
 * supaya posisi "diam" nyaman; hasil selalu -1..+1 walau input ekstrem/NaN.
 */
fun servoDeflection(dy: Float, radius: Float): Float {
    if (radius <= 0f || dy.isNaN()) return 0f
    val raw = (-dy / radius).coerceIn(-1f, 1f)
    val magnitude = abs(raw)
    if (magnitude <= SERVO_DEADZONE) return 0f
    val scaled = (magnitude - SERVO_DEADZONE) / (1f - SERVO_DEADZONE)
    return if (raw < 0f) -scaled else scaled
}
