package id.uinam.intellink.control

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun decodeInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)

    /**
     * Menjalankan skenario ViewModel + FakeConnection di scheduler uji.
     * Ticker ViewModel hidup selamanya di viewModelScope; dibatalkan di finally
     * (seperti saat ViewModel di-clear) agar runTest tidak menunggu tanpa akhir.
     */
    private fun viewModelTest(
        initialIp: String,
        block: TestScope.(FakeConnection, ControlViewModel) -> Unit,
    ) = runTest(mainDispatcherRule.testDispatcher) {
        val fake = FakeConnection(now = { testScheduler.currentTime })
        val vm = ControlViewModel(fake, initialIp)
        try {
            block(fake, vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `connect otomatis saat dibuat dan ticker mengirim tiap 33 ms`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, _ ->
            assertEquals(listOf("192.168.1.100"), fake.connectCalls)

            advanceTimeBy(100)
            assertEquals(listOf(0L, 33L, 66L, 99L), fake.sent.map { it.first })
        }

    @Test
    fun `tidak mengirim paket saat offline`() = viewModelTest(initialIp = "") { fake, _ ->
        advanceTimeBy(100)
        assertTrue(fake.sent.isEmpty())
    }

    @Test
    fun `laju servo menggeser sudut dan nilai drive masuk ke paket berikutnya`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            advanceTimeBy(40)   // tick t=0 dan t=33

            vm.setServo2Rate(1f)
            vm.setDrive(throttle = 100, steering = -50)
            advanceTimeBy(40)   // tick t=66

            val packet = fake.sent.last().second
            assertEquals(93, packet[1].toInt() and 0xFF)   // 90 + 2.97 per tick
            assertEquals(100, decodeInt16LE(packet, 3))
            assertEquals(-50, decodeInt16LE(packet, 5))
        }

    @Test
    fun `stopAll menolkan drive dan menghentikan laju servo`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            advanceTimeBy(40)
            vm.setServo2Rate(1f)
            advanceTimeBy(40)   // sudut di tick t=66: 93
            val before = fake.sent.size

            vm.stopAll()

            assertEquals(before + 1, fake.sent.size)
            assertEquals(0, decodeInt16LE(fake.sent.last().second, 3))

            advanceTimeBy(100)
            val packet = fake.sent.last().second
            assertEquals(93, packet[1].toInt() and 0xFF)   // sudut terakhir dipertahankan
            assertEquals(0, decodeInt16LE(packet, 3))
        }

    @Test
    fun `koneksi putus menghentikan laju servo dan sudut terakhir dipertahankan`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            advanceTimeBy(40)
            vm.setServo2Rate(1f)
            advanceTimeBy(40)   // sudut di tick t=66: 93

            fake.state.value = ConnectionState.OFFLINE
            advanceTimeBy(100)

            fake.connect("192.168.1.100")
            advanceTimeBy(40)

            val packet = fake.sent.last().second
            assertEquals(93, packet[1].toInt() and 0xFF)
            assertEquals(0, decodeInt16LE(packet, 3))
        }

    @Test
    fun `stopDrive mengirim nol seketika tanpa menunggu tick`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            advanceTimeBy(100)
            val before = fake.sent.size

            vm.stopDrive()

            assertEquals(before + 1, fake.sent.size)
            val packet = fake.sent.last().second
            assertEquals(0, decodeInt16LE(packet, 3))
            assertEquals(0, decodeInt16LE(packet, 5))
        }

    @Test
    fun `drive dinolkan saat koneksi putus sehingga paket pasca-reconnect netral`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            advanceTimeBy(40)
            vm.setDrive(throttle = 200, steering = -150)
            advanceTimeBy(40)
            assertEquals(200, decodeInt16LE(fake.sent.last().second, 3))

            fake.state.value = ConnectionState.OFFLINE
            advanceTimeBy(40)

            fake.connect("192.168.1.100")
            advanceTimeBy(40)

            val packet = fake.sent.last().second
            assertEquals(0, decodeInt16LE(packet, 3))
            assertEquals(0, decodeInt16LE(packet, 5))
        }

    @Test
    fun `onBackground mengirim stop lalu memutus koneksi dan berhenti mengirim`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            advanceTimeBy(100)
            val before = fake.sent.size

            vm.onBackground()

            assertEquals(before + 1, fake.sent.size)
            val packet = fake.sent.last().second
            assertEquals(0, decodeInt16LE(packet, 3))
            assertEquals(0, decodeInt16LE(packet, 5))
            assertEquals(1, fake.disconnectCalls)

            advanceTimeBy(200)
            assertEquals(before + 1, fake.sent.size)
        }

    @Test
    fun `onBackground sebelum pernah tersambung tidak error`() =
        viewModelTest(initialIp = "") { fake, vm ->
            vm.onBackground()

            assertEquals(1, fake.disconnectCalls)
            assertTrue(fake.sent.isEmpty())
        }

    @Test
    fun `onForeground saat offline mengconnect ulang`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            vm.onBackground()
            vm.onForeground()

            assertEquals(listOf("192.168.1.100", "192.168.1.100"), fake.connectCalls)
        }

    @Test
    fun `onForeground saat masih online tidak mengconnect ulang`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            vm.onForeground()

            assertEquals(1, fake.connectCalls.size)
        }

    @Test
    fun `onIpChanged kosong memutus koneksi tanpa connect ke string kosong`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            vm.onIpChanged("   ")

            assertEquals(1, fake.disconnectCalls)
            assertEquals(listOf("192.168.1.100"), fake.connectCalls)
            assertEquals(ConnectionState.OFFLINE, vm.state.value)
        }

    @Test
    fun `onIpChanged baru mengconnect ke IP baru`() =
        viewModelTest(initialIp = "192.168.1.100") { fake, vm ->
            vm.onIpChanged("10.0.0.5")

            assertEquals(listOf("192.168.1.100", "10.0.0.5"), fake.connectCalls)
            assertEquals("10.0.0.5", vm.ip.value)
        }
}
