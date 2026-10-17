package id.uinam.intellink.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class JoystickMathTest {

    @Test
    fun `titik tengah menghasilkan nol`() {
        assertEquals(DriveCommand(0, 0), joystickToDrive(0f, 0f, 100f))
    }

    @Test
    fun `dorong atas penuh menghasilkan throttle maksimal`() {
        assertEquals(DriveCommand(255, 0), joystickToDrive(0f, -100f, 100f))
    }

    @Test
    fun `dorong bawah penuh menghasilkan throttle negatif maksimal`() {
        assertEquals(DriveCommand(-255, 0), joystickToDrive(0f, 100f, 100f))
    }

    @Test
    fun `geser kanan penuh menghasilkan steering maksimal`() {
        assertEquals(DriveCommand(0, 255), joystickToDrive(100f, 0f, 100f))
    }

    @Test
    fun `geser kiri penuh menghasilkan steering negatif maksimal`() {
        assertEquals(DriveCommand(0, -255), joystickToDrive(-100f, 0f, 100f))
    }

    @Test
    fun `nilai setengah diskalakan proporsional`() {
        assertEquals(DriveCommand(64, 128), joystickToDrive(50f, -25f, 100f))
    }

    @Test
    fun `di luar radius tetap dibatasi 255`() {
        assertEquals(DriveCommand(-255, 255), joystickToDrive(500f, 500f, 100f))
    }

    @Test
    fun `NaN atau radius nol menghasilkan nol`() {
        assertEquals(DriveCommand(0, 0), joystickToDrive(Float.NaN, 0f, 100f))
        assertEquals(DriveCommand(0, 0), joystickToDrive(0f, Float.NaN, 100f))
        assertEquals(DriveCommand(0, 0), joystickToDrive(10f, 10f, 0f))
    }

    @Test
    fun `clampToCircle membatasi panjang vektor`() {
        val clamped = clampToCircle(Offset(300f, 0f), 100f)
        assertEquals(100f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)

        assertEquals(Offset.Zero, clampToCircle(Offset.Zero, 100f))

        val inside = Offset(50f, 20f)
        assertEquals(inside, clampToCircle(inside, 100f))
    }
}
