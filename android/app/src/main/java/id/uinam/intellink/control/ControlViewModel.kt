package id.uinam.intellink.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * State kontrol + ticker 33 ms (~30 Hz, sekaligus heartbeat failsafe ESP32).
 * Ticker selalu berjalan; paket hanya dikirim saat ONLINE.
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

    init {
        if (initialIp.isNotBlank()) connection.connect(initialIp)
        viewModelScope.launch {
            while (true) {
                sendCurrentValues()
                delay(33)
            }
        }
    }

    fun setServo2(value: Int) {
        _servo2.value = value.coerceIn(0, 180)
    }

    fun setServo3(value: Int) {
        _servo3.value = value.coerceIn(0, 180)
    }

    fun setDrive(throttle: Int, steering: Int) {
        _throttle.value = throttle.coerceIn(-255, 255)
        _steering.value = steering.coerceIn(-255, 255)
    }

    /** Tombol STOP dan lepas joystick: nol + kirim seketika (tidak menunggu tick). */
    fun stopDrive() {
        setDrive(0, 0)
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
        setDrive(0, 0)
        sendCurrentValues()
        connection.disconnect()
    }

    private fun sendCurrentValues() {
        if (connection.state.value != ConnectionState.ONLINE) return
        connection.send(
            ControlPacket.encode(_servo2.value, _servo3.value, _throttle.value, _steering.value)
        )
    }
}
