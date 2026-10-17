package id.uinam.intellink.control

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encoder paket kontrol 8 byte little-endian — harus identik dengan controller.html
 * dan vehicle_control_ws.ino.
 *
 * Layout: [0]=0xA5, [1]=servo2 (0..180), [2]=servo3 (0..180),
 *         [3..4]=int16 throttle (-255..255), [5..6]=int16 steering (-255..255),
 *         [7]=XOR byte 0..6.
 */
object ControlPacket {

    fun encode(servo2: Int, servo3: Int, throttle: Int, steering: Int): ByteArray {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0xA5.toByte())
        buffer.put(servo2.coerceIn(0, 180).toByte())
        buffer.put(servo3.coerceIn(0, 180).toByte())
        buffer.putShort(throttle.coerceIn(-255, 255).toShort())
        buffer.putShort(steering.coerceIn(-255, 255).toShort())
        var checksum = 0
        for (i in 0 until 7) checksum = checksum xor (buffer.get(i).toInt() and 0xFF)
        buffer.put(checksum.toByte())
        return buffer.array()
    }
}
