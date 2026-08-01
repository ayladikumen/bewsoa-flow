package ai.bewsoa.flow.data.exacthour

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ai.bewsoa.flow.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * The app's handle on the physical clock: where it lives, what it last said,
 * and the rule that a command is not believed until the device confirms it.
 *
 * Singleton in the shape every other repository here uses — private
 * constructor, `@Volatile instance`, `get(context)`.
 */
class ExactHourRepository private constructor(
    private val context: Context,
    private val settings: SettingsRepository
) {

    private val _status = MutableStateFlow<ClockStatus?>(null)

    /** The last snapshot we believe. Null until something answers. */
    val status: StateFlow<ClockStatus?> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<ExactHourError?>(null)
    val lastError: StateFlow<ExactHourError?> = _lastError.asStateFlow()

    suspend fun endpoint(): ClockEndpoint? {
        val host = settings.clockHost.first().trim()
        if (host.isEmpty()) return null
        return ClockEndpoint(
            host = host,
            port = settings.clockPort.first(),
            name = settings.clockName.first()
        )
    }

    suspend fun client(): ExactHourClient? = endpoint()?.let(::ExactHourClient)

    /**
     * True only on Wi-Fi or Ethernet. The clock is unreachable over cellular by
     * definition, and checking costs nothing next to a 1.5s connect timeout on
     * every trigger while the user is out of the house.
     */
    fun onLocalNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** A one-off read that also publishes what it found. */
    suspend fun refresh(): Result<ClockStatus> {
        val client = client() ?: return Result.failure(ExactHourError.NotConfigured)
        return client.status().also { publish(it) }
    }

    /**
     * Run a command and return what the device *actually* ended up doing.
     *
     * The clock's HTTP thread only enqueues work; on its 1 second internal
     * timeout it answers 200 with a snapshot that may predate the command. So
     * the POST's reply is published immediately for a snappy repaint, then a
     * real `/api/status` read replaces it with the truth.
     *
     * This is also what makes the device's silent locks legible: `/api/set`
     * during a running countdown returns 200 and changes nothing, and only the
     * confirming read reveals that — letting the UI say "the clock ignored
     * that" instead of showing a change that never happened.
     */
    suspend fun command(op: suspend (ExactHourClient) -> Result<ClockStatus>): Result<ClockStatus> {
        val client = client() ?: return Result.failure(ExactHourError.NotConfigured)
        val optimistic = op(client)
        publish(optimistic)
        if (optimistic.isFailure) return optimistic
        delay(CONFIRM_DELAY_MS)
        val confirmed = client.status()
        publish(confirmed)
        return confirmed.recover { optimistic.getOrThrow() }
    }

    /**
     * The user just touched a control on the remote screen, so the auto-mirror
     * steps back for a while rather than yanking the display out from under
     * them at the next block boundary.
     */
    suspend fun holdAuto() {
        settings.setClockHoldUntil(
            System.currentTimeMillis() + SettingsRepository.CLOCK_HOLD_MINUTES * 60_000L
        )
    }

    suspend fun releaseAuto() {
        settings.setClockHoldUntil(0L)
    }

    suspend fun holdRemainingMinutes(): Int {
        val until = settings.clockHoldUntil.first()
        val left = until - System.currentTimeMillis()
        return if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }

    private suspend fun publish(result: Result<ClockStatus>) {
        result
            .onSuccess {
                _status.value = it
                _lastError.value = null
                settings.setClockLastSeenAt(System.currentTimeMillis())
            }
            .onFailure { _lastError.value = it as? ExactHourError }
    }

    companion object {
        /** Long enough for the device's queue to drain, short enough to feel instant. */
        private const val CONFIRM_DELAY_MS = 250L

        @Volatile
        private var instance: ExactHourRepository? = null

        fun get(context: Context): ExactHourRepository =
            instance ?: synchronized(this) {
                instance ?: ExactHourRepository(
                    context.applicationContext,
                    SettingsRepository.get(context)
                ).also { instance = it }
            }
    }
}
