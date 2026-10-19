package id.uinam.intellink.ui

import kotlin.math.abs

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
