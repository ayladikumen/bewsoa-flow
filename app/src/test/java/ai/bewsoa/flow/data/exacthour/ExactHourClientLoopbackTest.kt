package ai.bewsoa.flow.data.exacthour

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * The transport, against a real socket.
 *
 * A hand-rolled `ServerSocket` rather than the JDK's HttpServer: unit tests
 * compile against android.jar, which has no `com.sun.net.httpserver`. Loopback
 * is inside [PrivateNet]'s allow list, so the client treats this exactly like a
 * Pi on the LAN — which is the point, since this is the only test that
 * exercises the socket path at all.
 */
class ExactHourClientLoopbackTest {

    private lateinit var server: ServerSocket
    private lateinit var client: ExactHourClient
    private lateinit var acceptor: Thread

    /** Path -> (status code, body). */
    private val routes = mutableMapOf<String, Pair<Int, String>>()

    /** Paths the server should stall on, to provoke a read timeout. */
    private val stalled = mutableSetOf<String>()

    private val bodies = CopyOnWriteArrayList<String>()

    @Before
    fun start() {
        server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        client = ExactHourClient(ClockEndpoint("127.0.0.1", server.localPort))
        acceptor = thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use(::respond)
                } catch (e: SocketException) {
                    return@thread // closed in teardown
                } catch (e: Exception) {
                    // A client that gave up mid-request; keep serving.
                }
            }
        }
    }

    @After
    fun stop() {
        server.close()
    }

    private fun respond(socket: java.net.Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val path = requestLine.split(" ").getOrNull(1)?.substringBefore("?").orEmpty()

        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            reader.read(buffer, 0, contentLength)
            bodies += String(buffer)
        }

        if (path in stalled) {
            // Longer than the client's read budget, so it gives up first.
            Thread.sleep(6_000)
        }

        val (code, body) = routes[path] ?: (404 to """{"error":"not found"}""")
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    @Test
    fun `a status read parses the snapshot`() = runBlocking {
        routes["/api/status"] = 200 to
            """{"state":"RUNNING","minutes":5,"seconds":0,"display":"5:00"}"""
        val status = client.status().getOrThrow()
        assertEquals(ClockState.RUNNING, status.state)
        assertEquals("5:00", status.display)
    }

    @Test
    fun `a post sends its body and parses what comes back`() = runBlocking {
        routes["/api/set"] = 200 to """{"state":"IDLE","minutes":25,"display":"25:00"}"""
        val status = client.set(25, 0).getOrThrow()
        assertEquals(25, status.minutes)
        assertTrue("body should carry the minutes: $bodies", bodies.any { it.contains("\"minutes\":25") })
    }

    @Test
    fun `an unknown route is a NotFound, not a crash`() = runBlocking {
        val error = client.status().exceptionOrNull()
        assertTrue("got $error", error is ExactHourError.NotFound)
    }

    @Test
    fun `automations 404 means the feature is off, not a broken URL`() = runBlocking {
        // The device 404s the whole /api/automations* family when it was
        // started without the Automations library.
        val error = client.automations().exceptionOrNull()
        assertEquals(ExactHourError.AutomationsDisabled, error)
    }

    @Test
    fun `a garbage body is Malformed`() = runBlocking {
        routes["/api/status"] = 200 to "<html>not json</html>"
        assertEquals(ExactHourError.Malformed, client.status().exceptionOrNull())
    }

    @Test
    fun `an unexpected code is reported with the code`() = runBlocking {
        routes["/api/status"] = 500 to "boom"
        val error = client.status().exceptionOrNull()
        assertTrue("got $error", error is ExactHourError.Unexpected)
        assertEquals(500, (error as ExactHourError.Unexpected).code)
    }

    @Test
    fun `a probe rejects something that answers JSON but isn't a clock`() = runBlocking {
        routes["/api/status"] = 200 to """{"status":"ok","uptime":42}"""
        assertEquals(ExactHourError.Malformed, client.probe().exceptionOrNull())
    }

    @Test
    fun `a stalled device times out as Unreachable rather than hanging`() = runBlocking {
        routes["/api/status"] = 200 to """{"state":"IDLE","display":"0:00"}"""
        stalled += "/api/status"
        val error = client.status().exceptionOrNull()
        assertTrue("got $error", error is ExactHourError.Unreachable)
    }

    @Test
    fun `a public address never gets a cleartext request`() = runBlocking {
        val error = ExactHourClient(ClockEndpoint("8.8.8.8", 8080)).status().exceptionOrNull()
        assertTrue("got $error", error is ExactHourError.NotPrivate)
    }
}
