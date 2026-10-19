package id.uinam.intellink.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ServoMathTest {

    @Test
    fun `defleksi servo nol di titik tengah`() {
        assertEquals(0f, servoDeflection(0f, 100f), 0.0001f)
    }

    @Test
    fun `defleksi servo nol di dalam deadzone`() {
        assertEquals(0f, servoDeflection(10f, 100f), 0.0001f)
        assertEquals(0f, servoDeflection(-15f, 100f), 0.0001f)
    }

    @Test
    fun `defleksi servo dorong atas penuh bernilai satu`() {
        assertEquals(1f, servoDeflection(-100f, 100f), 0.0001f)
    }

    @Test
    fun `defleksi servo dorong bawah penuh bernilai negatif satu`() {
        assertEquals(-1f, servoDeflection(100f, 100f), 0.0001f)
    }

    @Test
    fun `defleksi servo di luar radius tetap dibatasi`() {
        assertEquals(1f, servoDeflection(-500f, 100f), 0.0001f)
        assertEquals(-1f, servoDeflection(500f, 100f), 0.0001f)
    }

    @Test
    fun `defleksi servo di luar deadzone diskalakan proporsional`() {
        // 50% radius -> (0.5 - 0.15) / 0.85
        assertEquals(0.41176f, servoDeflection(-50f, 100f), 0.0001f)
    }

    @Test
    fun `defleksi servo NaN atau radius nol menghasilkan nol`() {
        assertEquals(0f, servoDeflection(Float.NaN, 100f), 0.0001f)
        assertEquals(0f, servoDeflection(10f, 0f), 0.0001f)
    }
}
