package id.uinam.intellink.control

import kotlinx.coroutines.flow.MutableStateFlow

/** Fake RobotConnection; waktu diambil dari scheduler test lewat [now]. */
class FakeConnection(private val now: () -> Long) : RobotConnection {
    override val state = MutableStateFlow(ConnectionState.OFFLINE)
    val sent = mutableListOf<Pair<Long, ByteArray>>()
    val connectCalls = mutableListOf<String>()
    var disconnectCalls = 0
        private set

    override fun connect(ip: String) {
        connectCalls += ip
        state.value = ConnectionState.ONLINE
    }

    override fun disconnect() {
        disconnectCalls++
        state.value = ConnectionState.OFFLINE
    }

    override fun send(packet: ByteArray): Boolean {
        sent += now() to packet
        return true
    }
}
