package id.uinam.intellink.control

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlPacketTest {

    @Test
    fun `golden - encode(90, 90, 0, 0)`() {
        val expected = byteArrayOf(
            0xA5.toByte(), 0x5A, 0x5A, 0x00, 0x00, 0x00, 0x00, 0xA5.toByte(),
        )
        assertArrayEquals(expected, ControlPacket.encode(90, 90, 0, 0))
    }

    @Test
    fun `int16 little-endian dengan nilai negatif`() {
        val p = ControlPacket.encode(90, 90, -255, 0)
        assertEquals(0x01, p[3].toInt() and 0xFF)
        assertEquals(0xFF, p[4].toInt() and 0xFF)

        val q = ControlPacket.encode(90, 90, 255, -255)
        assertEquals(0xFF, q[3].toInt() and 0xFF)
        assertEquals(0x00, q[4].toInt() and 0xFF)
        assertEquals(0x01, q[5].toInt() and 0xFF)
        assertEquals(0xFF, q[6].toInt() and 0xFF)
        assertEquals(0xA4, q[7].toInt() and 0xFF)
    }

    @Test
    fun `nilai di luar rentang diklem`() {
        val expected = byteArrayOf(
            0xA5.toByte(), 0xB4.toByte(), 0x00, 0xFF.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x10,
        )
        assertArrayEquals(expected, ControlPacket.encode(999, -999, 999, -999))
    }

    @Test
    fun `checksum adalah XOR byte 0 sampai 6`() {
        val cases = listOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(180, 180, 255, -255),
            intArrayOf(45, 135, -100, 200),
            intArrayOf(90, 90, 1, -1),
        )
        for (c in cases) {
            val p = ControlPacket.encode(c[0], c[1], c[2], c[3])
            var xor = 0
            for (i in 0..6) xor = xor xor (p[i].toInt() and 0xFF)
            assertEquals("kasus ${c.toList()}", xor, p[7].toInt() and 0xFF)
        }
    }
}
