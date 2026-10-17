package id.uinam.intellink.control

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Klien WebSocket ke ESP32 (ws://<ip>:<port>, default port 81).
 * - Auto-reconnect tiap [reconnectDelayMs] setelah koneksi putus, kecuali [disconnect] dipanggil manual.
 * - [send] mengembalikan false bila belum ONLINE atau antrean kirim membengkak (> 64) — frame dibuang, bukan mengantre lag.
 */
class RobotClient(
    private val port: Int = 81,
    private val reconnectDelayMs: Long = 1500L,
) : RobotConnection {

    private val httpClient = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ConnectionState.OFFLINE)
    override val state: StateFlow<ConnectionState> = _state

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentIp: String? = null
    private var autoReconnect = false
    private var generation = 0

    @Synchronized
    override fun connect(ip: String) {
        currentIp = ip
        autoReconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        val gen = ++generation
        socket?.close(1000, null)
        _state.value = ConnectionState.CONNECTING
        open(ip, gen)
    }

    @Synchronized
    override fun disconnect() {
        autoReconnect = false
        generation++
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, null)
        socket = null
        _state.value = ConnectionState.OFFLINE
    }

    @Synchronized
    override fun send(packet: ByteArray): Boolean {
        val ws = socket ?: return false
        if (_state.value != ConnectionState.ONLINE) return false
        if (ws.queueSize() > 64) return false
        return ws.send(ByteString.of(*packet))
    }

    /** Menutup totale — dipakai pengujian; app normal cukup memakai [disconnect]. */
    @Synchronized
    fun shutdown() {
        disconnect()
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun open(ip: String, gen: Int) {
        val request = Request.Builder().url("ws://$ip:$port").build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != generation) return
                _state.value = ConnectionState.ONLINE
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // OkHttp tidak otomatis membalas close frame dari peer. Balas agar
                // handshake tuntas dan onClosed terpicu — pemicu reconnect saat
                // ESP32 menutup koneksi dengan rapi.
                webSocket.close(code, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != generation) return
                _state.value = ConnectionState.OFFLINE
                scheduleReconnect(gen)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen != generation) return
                _state.value = ConnectionState.OFFLINE
                scheduleReconnect(gen)
            }
        })
    }

    private fun scheduleReconnect(gen: Int) {
        if (!autoReconnect || gen != generation) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            if (autoReconnect && gen == generation) {
                currentIp?.let { connect(it) }
            }
        }
    }
}
