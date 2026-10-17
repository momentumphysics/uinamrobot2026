package id.uinam.intellink.control

import org.junit.Assert.assertEquals
import org.junit.Test

class ServoMotionTest {

    @Test
    fun `sudut bergerak sesuai laju dan durasi`() {
        assertEquals(135f, advanceServoAngle(90f, 0.5f, 1000L), 0.0001f)
        assertEquals(45f, advanceServoAngle(90f, -0.5f, 1000L), 0.0001f)
    }

    @Test
    fun `sudut selalu dibatasi 0 sampai 180`() {
        assertEquals(180f, advanceServoAngle(170f, 1f, 1000L), 0.0001f)
        assertEquals(0f, advanceServoAngle(10f, -1f, 1000L), 0.0001f)
    }

    @Test
    fun `laju nol atau NaN menahan sudut`() {
        assertEquals(90f, advanceServoAngle(90f, 0f, 1000L), 0.0001f)
        assertEquals(90f, advanceServoAngle(90f, Float.NaN, 1000L), 0.0001f)
    }

    @Test
    fun `laju kecil tetap terakumulasi antar tick`() {
        var angle = 90f
        repeat(100) { angle = advanceServoAngle(angle, 0.01f, 33L) }
        assertEquals(92.97f, angle, 0.05f)
    }
}
