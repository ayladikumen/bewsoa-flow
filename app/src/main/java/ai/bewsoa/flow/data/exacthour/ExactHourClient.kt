package ai.bewsoa.flow.data.exacthour

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The only file in the app that opens a socket to the clock.
 *
 * Follows the hand-rolled `HttpURLConnection` + org.json idiom of
 * [ai.bewsoa.flow.data.AiAssistant] — those helpers cast to
 * `HttpsURLConnection` and so can't be reused for a plain-http LAN box, but the
 * shape is the same and it keeps the app dependency-free.
 *
 * Every public method is `suspend` + [Dispatchers.IO]. `Widgets.refreshAll`
 * reaches the mirror from `MainActivity.onResume` on the main thread, so that
 * is not a stylistic choice — it's what stops a `NetworkOnMainThreadException`.
 */
class ExactHourClient(private val endpoint: ClockEndpoint) {

    // Status --------------------------------------------------------------

    suspend fun status(): Result<ClockStatus> =
        get("/api/status", STATUS_CONNECT_MS, STATUS_READ_MS).map(ClockStatus::from)

    /**
     * A discovery probe: same route, a budget short enough to sweep 254 hosts,
     * and it rejects anything that answers JSON without being a clock — plenty
     * of things on a home LAN will happily 200 at you.
     */
    suspend fun probe(): Result<ClockStatus> =
        get("/api/status", PROBE_CONNECT_MS, PROBE_READ_MS).mapCatching { json ->
            if (!ClockStatus.looksLikeClock(json)) throw ExactHourError.Malformed
            ClockStatus.from(json)
        }

    // Timer ---------------------------------------------------------------

    suspend fun toggle(): Result<ClockStatus> = post("/api/toggle")

    suspend fun start(): Result<ClockStatus> = post("/api/start")

    suspend fun pause(): Result<ClockStatus> = post("/api/pause")

    suspend fun resume(): Result<ClockStatus> = post("/api/resume")

    suspend fun reset(): Result<ClockStatus> = post("/api/reset")

    /** Minutes, positive or negative. Silently ignored by the device while RUNNING. */
    suspend fun adjust(delta: Int): Result<ClockStatus> =
        post("/api/adjust", JSONObject().put("delta", delta))

    /** Absolute time. Silently ignored by the device while RUNNING. */
    suspend fun set(minutes: Int, seconds: Int): Result<ClockStatus> = post(
        "/api/set",
        JSONObject()
            .put("minutes", ClockLimits.clampMinutes(minutes))
            .put("seconds", ClockLimits.clampSeconds(seconds))
    )

    suspend fun text(overlay: TextOverlay): Result<ClockStatus> =
        post("/api/text", overlay.toJson())

    // Automations ---------------------------------------------------------

    suspend fun automations(): Result<List<AutomationTemplate>> =
        get("/api/automations", STATUS_CONNECT_MS, STATUS_READ_MS)
            .map(AutomationTemplate::catalog)

    /**
     * Note the device answers **200** even when the preset is rejected — the
     * reason arrives as [ClockStatus.automationError], not as a failed Result.
     */
    suspend fun runAutomation(templateId: String, params: Map<String, Int>): Result<ClockStatus> {
        val body = JSONObject()
            .put("template", templateId)
            .put("params", JSONObject().apply { params.forEach { (k, v) -> put(k, v) } })
        return post("/api/automations/run", body)
    }

    suspend fun stopAutomation(): Result<ClockStatus> = post("/api/automations/stop")

    // Transport -----------------------------------------------------------

    private suspend fun post(path: String, body: JSONObject? = null): Result<ClockStatus> =
        request(path, "POST", body ?: JSONObject(), CONNECT_MS, READ_MS).map(ClockStatus::from)

    private suspend fun get(path: String, connectMs: Int, readMs: Int): Result<JSONObject> =
        request(path, "GET", null, connectMs, readMs)

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject?,
        connectMs: Int,
        readMs: Int
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            PrivateNet.require(endpoint.host)
            val connection = (URL(endpoint.url(path)).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectMs
                readTimeout = readMs
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                // The Pi runs a stdlib single-thread server and can reboot at
                // any moment; a pooled keep-alive socket is a dependable source
                // of "unexpected end of stream". Closing per request costs
                // nothing on a LAN.
                setRequestProperty("Connection", "close")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (body != null) {
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = try {
                    connection.responseCode
                } catch (e: IOException) {
                    // The server catches handler exceptions and prints them,
                    // leaving the request with no response at all. Transient.
                    throw ExactHourError.Unreachable(endpoint.host)
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    connection.errorStream?.close()
                    throw errorFor(code, path)
                }
                val text = connection.inputStream.readBoundedText()
                try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw ExactHourError.Malformed
                }
            } finally {
                connection.disconnect()
            }
        }.recoverCatching { cause ->
            // Anything that isn't already one of ours is the network being the
            // network: a dark Pi, a dropped Wi-Fi, a timeout.
            throw if (cause is ExactHourError) cause else ExactHourError.Unreachable(endpoint.host)
        }
    }

    private fun errorFor(code: Int, path: String): ExactHourError = when {
        code != HttpURLConnection.HTTP_NOT_FOUND -> ExactHourError.Unexpected(code)
        // Every /api/automations* route 404s as a unit when the device was
        // started without an Automations library. That's a capability answer,
        // not a broken URL.
        path.startsWith("/api/automations") -> ExactHourError.AutomationsDisabled
        else -> ExactHourError.NotFound(path)
    }

    /** Never let a wedged device stream us into an OOM. */
    private fun InputStream.readBoundedText(): String {
        val buffer = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        use { stream ->
            while (out.size() < MAX_BODY_BYTES) {
                val read = stream.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, minOf(read, MAX_BODY_BYTES - out.size()))
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        // LAN budgets. The AI clients' 20s/180s would leave the UI hanging on a
        // clock that is simply switched off; here the whole point is to fail
        // fast and try again at the next trigger.
        private const val CONNECT_MS = 1_500
        // The device's submit() blocks up to 1.0s before answering, so this
        // needs headroom over that and nothing more.
        private const val READ_MS = 3_000
        private const val STATUS_CONNECT_MS = 1_200
        private const val STATUS_READ_MS = 2_000
        private const val PROBE_CONNECT_MS = 250
        private const val PROBE_READ_MS = 400

        private const val MAX_BODY_BYTES = 64 * 1024
    }
}
