package id.uinam.intellink.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TICK_MS = 33L

/**
 * State kontrol + ticker 33 ms (~30 Hz, sekaligus heartbeat failsafe ESP32).
 * Ticker selalu berjalan; paket hanya dikirim saat ONLINE.
 *
 * Sudut servo disimpan halus (float) supaya laju kecil tetap terakumulasi antar
 * tick; laju kenop = -1..+1 dengan dorong atas positif (buka/naik).
 */
class ControlViewModel(
    private val connection: RobotConnection,
    initialIp: String,
) : ViewModel() {

    private val _ip = MutableStateFlow(initialIp)
    val ip: StateFlow<String> = _ip

    val state: StateFlow<ConnectionState> = connection.state

    private val _servo2 = MutableStateFlow(90)
    private val _servo3 = MutableStateFlow(90)
    private val _throttle = MutableStateFlow(0)
    private val _steering = MutableStateFlow(0)
    val servo2: StateFlow<Int> = _servo2
    val servo3: StateFlow<Int> = _servo3
    val throttle: StateFlow<Int> = _throttle
    val steering: StateFlow<Int> = _steering

    private var servo2Angle = 90f
    private var servo3Angle = 90f
    private var servo2Rate = 0f
    private var servo3Rate = 0f

    init {
        if (initialIp.isNotBlank()) connection.connect(initialIp)
        viewModelScope.launch {
            // Koneksi putus dapat membatalkan gesture tanpa onDragCancel
            // (pointerInput dibatalkan saat enabled berubah), sehingga nilai drive
            // dan laju servo lama bisa tersangkut. Nolkan begitu tidak ONLINE agar
            // paket pertama setelah reconnect selalu netral.
            connection.state.collect { current ->
                if (current != ConnectionState.ONLINE) clearCommands()
            }
        }
        viewModelScope.launch {
            while (true) {
                integrateServos()
                sendCurrentValues()
                delay(TICK_MS)
            }
        }
    }

    /** Laju servo 2 (capit): -1..+1, dorong kenop atas = positif (buka). */
    fun setServo2Rate(rate: Float) {
        servo2Rate = sanitizeRate(rate)
    }

    /** Laju servo 3 (lengan): -1..+1, dorong kenop atas = positif (naik). */
    fun setServo3Rate(rate: Float) {
        servo3Rate = sanitizeRate(rate)
    }

    /** Nilai throttle -255..255 (positif = maju); dipakai slider vertikal kiri. */
    fun setThrottle(value: Int) {
        _throttle.value = value.coerceIn(-255, 255)
    }

    /** Nilai steering -255..255 (positif = kanan); dipakai slider vertikal kanan. */
    fun setSteering(value: Int) {
        _steering.value = value.coerceIn(-255, 255)
    }

    /** Lepas slider throttle: kembali ke 0 dan kirim seketika (pegas seperti HTML). */
    fun releaseThrottle() {
        setThrottle(0)
        sendCurrentValues()
    }

    /** Lepas slider steering: kembali ke 0 dan kirim seketika (pegas seperti HTML). */
    fun releaseSteering() {
        setSteering(0)
        sendCurrentValues()
    }

    /** Tombol STOP: hentikan drive dan laju servo, kirim seketika (tidak menunggu tick). */
    fun stopAll() {
        clearCommands()
        sendCurrentValues()
    }

    /** Dipanggil saat pengguna selesai mengubah IP (IME Done). */
    fun onIpChanged(newIp: String) {
        val trimmed = newIp.trim()
        _ip.value = trimmed
        if (trimmed.isBlank()) {
            connection.disconnect()
        } else {
            connection.connect(trimmed)
        }
    }

    fun onForeground() {
        val current = _ip.value
        if (current.isNotBlank() && connection.state.value == ConnectionState.OFFLINE) {
            connection.connect(current)
        }
    }

    fun onBackground() {
        clearCommands()
        sendCurrentValues()
        connection.disconnect()
    }

    private fun integrateServos() {
        servo2Angle = advanceServoAngle(servo2Angle, servo2Rate, TICK_MS)
        servo3Angle = advanceServoAngle(servo3Angle, servo3Rate, TICK_MS)
        _servo2.value = servo2Angle.roundToInt()
        _servo3.value = servo3Angle.roundToInt()
    }

    private fun clearCommands() {
        setThrottle(0)
        setSteering(0)
        servo2Rate = 0f
        servo3Rate = 0f
    }

    private fun sanitizeRate(rate: Float): Float =
        if (rate.isNaN()) 0f else rate.coerceIn(-1f, 1f)

    private fun sendCurrentValues() {
        if (connection.state.value != ConnectionState.ONLINE) {
            // Sabuk pengaman kedua setelah kolektor di init: jangan pernah membawa
            // sisa perintah drive/servo lama melewati masa offline.
            clearCommands()
            return
        }
        connection.send(
            ControlPacket.encode(_servo2.value, _servo3.value, _throttle.value, _steering.value)
        )
    }
}
