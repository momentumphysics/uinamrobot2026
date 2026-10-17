package id.uinam.intellink.control

/** Laju sudut servo pada dorongan kenop penuh (derajat/detik). */
const val SERVO_FULL_DEG_PER_SEC = 90f

/**
 * Sudut servo berikutnya setelah [deltaMs] milidetik pada laju [rate] (-1..+1,
 * dorong atas = positif). Hasil selalu dibatasi 0..180; laju NaN dianggap 0.
 */
fun advanceServoAngle(
    angle: Float,
    rate: Float,
    deltaMs: Long,
    degPerSec: Float = SERVO_FULL_DEG_PER_SEC,
): Float {
    val safeRate = if (rate.isNaN()) 0f else rate.coerceIn(-1f, 1f)
    val step = safeRate * degPerSec * (deltaMs / 1000f)
    return (angle + step).coerceIn(0f, 180f)
}
