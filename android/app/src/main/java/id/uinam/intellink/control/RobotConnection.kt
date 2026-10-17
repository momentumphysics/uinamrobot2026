package id.uinam.intellink.control

import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { CONNECTING, ONLINE, OFFLINE }

/** Abstraksi transport — memudahkan unit test ViewModel dengan fake. */
interface RobotConnection {
    val state: StateFlow<ConnectionState>
    fun connect(ip: String)
    fun disconnect()
    /** @return true bila paket benar-benar dimasukkan ke antrean pengiriman. */
    fun send(packet: ByteArray): Boolean
}
