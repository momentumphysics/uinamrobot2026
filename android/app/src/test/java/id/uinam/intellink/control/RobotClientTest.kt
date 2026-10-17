package id.uinam.intellink.control

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RobotClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: RobotClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RobotClient(port = server.port)
    }

    @After
    fun tearDown() {
        client.shutdown()
        server.shutdown()
    }

    /**
     * Memasang listener di balik pembungkus yang membalas (ack) close frame dari klien.
     * RealWebSocket sisi server tidak pernah otomatis membalas close; tanpa ack ini
     * MockWebServer.shutdown() menunggu antrean koneksi 5 detik lalu melempar IOException
     * "Gave up waiting for queue to shut down".
     */
    private fun enqueueUpgrade(listener: WebSocketListener) {
        val acked = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) =
                listener.onOpen(webSocket, response)

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) =
                listener.onMessage(webSocket, bytes)

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                listener.onClosing(webSocket, code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                listener.onClosed(webSocket, code, reason)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                listener.onFailure(webSocket, t, response)
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(acked))
    }

    private fun awaitUntil(timeoutMs: Long = 5000, message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Tidak tercapai dalam ${timeoutMs} ms: $message")
    }

    @Test
    fun `send sebelum tersambung mengembalikan false`() {
        assertFalse(client.send(ControlPacket.encode(90, 90, 0, 0)))
        assertEquals(ConnectionState.OFFLINE, client.state.value)
    }

    @Test
    fun `connect dengan host tidak valid tidak crash dan tidak menghubungi jaringan`() {
        client.connect("192.168.1.100:8080")

        assertEquals(ConnectionState.OFFLINE, client.state.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `connect lalu send sampai ke server`() {
        val received = CopyOnWriteArrayList<ByteArray>()
        val opened = CountDownLatch(1)
        enqueueUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = opened.countDown()
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                received.add(bytes.toByteArray())
            }
        })

        client.connect("127.0.0.1")
        assertTrue("socket server tidak terbuka", opened.await(5, TimeUnit.SECONDS))
        awaitUntil(message = "state ONLINE") { client.state.value == ConnectionState.ONLINE }

        val packet = ControlPacket.encode(90, 90, 0, 0)
        assertTrue(client.send(packet))
        awaitUntil(message = "paket diterima") { received.isNotEmpty() }
        assertArrayEquals(packet, received[0])
    }

    @Test
    fun `server memutus koneksi maka client reconnect dengan jeda minimal 1,5 detik`() {
        val openTimes = CopyOnWriteArrayList<Long>()
        val sockets = CopyOnWriteArrayList<WebSocket>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                openTimes.add(System.currentTimeMillis())
                sockets.add(webSocket)
            }
        }
        enqueueUpgrade(listener)
        enqueueUpgrade(listener)

        client.connect("127.0.0.1")
        awaitUntil(timeoutMs = 10_000, message = "koneksi pertama") { openTimes.size >= 1 }
        sockets[0].close(1000, "tes putus")
        awaitUntil(timeoutMs = 10_000, message = "koneksi ulang") { openTimes.size >= 2 }

        val gap = openTimes[1] - openTimes[0]
        assertTrue("jeda reconnect hanya $gap ms", gap >= 1400)
    }

    @Test
    fun `disconnect manual tidak memicu koneksi ulang`() {
        enqueueUpgrade(object : WebSocketListener() {})
        client.connect("127.0.0.1")
        awaitUntil(message = "state ONLINE") { client.state.value == ConnectionState.ONLINE }

        client.disconnect()
        assertEquals(ConnectionState.OFFLINE, client.state.value)
        Thread.sleep(2200)
        assertEquals("tidak boleh ada koneksi baru", 1, server.requestCount)
    }
}
