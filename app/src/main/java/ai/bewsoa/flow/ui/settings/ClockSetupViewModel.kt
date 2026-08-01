package ai.bewsoa.flow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.exacthour.ClockMirror
import ai.bewsoa.flow.data.exacthour.DiscoveredClock
import ai.bewsoa.flow.data.exacthour.ExactHourDiscovery
import ai.bewsoa.flow.data.exacthour.ExactHourRepository
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClockSetupUiState(
    val enabled: Boolean = false,
    val host: String = "",
    val port: String = SettingsRepository.DEFAULT_CLOCK_PORT.toString(),
    val name: String = "",
    val mirrorFocus: Boolean = true,
    val mirrorBlocks: Boolean = true,
    val lastSeenAt: Long = 0L,
    val testing: Boolean = false,
    val scanning: Boolean = false,
    val scanProbed: Int = 0,
    val scanTotal: Int = 0,
    val found: List<DiscoveredClock> = emptyList(),
    val status: String? = null,
    val error: String? = null
) {
    val configured: Boolean get() = host.isNotBlank()
}

/**
 * Setting the clock up: where it is, whether it's on, and what it mirrors.
 * Driving it lives on the remote screen; this is only the plumbing.
 */
class ClockSetupViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository.get(app)
    private val clock = ExactHourRepository.get(app)

    private val _ui = MutableStateFlow(ClockSetupUiState())
    val ui: StateFlow<ClockSetupUiState> = _ui.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            _ui.value = ClockSetupUiState(
                enabled = settings.clockEnabled.first(),
                host = settings.clockHost.first(),
                port = settings.clockPort.first().toString(),
                name = settings.clockName.first(),
                mirrorFocus = settings.clockMirrorFocus.first(),
                mirrorBlocks = settings.clockMirrorBlocks.first(),
                lastSeenAt = settings.clockLastSeenAt.first()
            )
        }
    }

    fun setHost(value: String) {
        _ui.value = _ui.value.copy(host = value, error = null, status = null)
        persistEndpoint()
    }

    fun setPort(value: String) {
        // Digits only — the field is a port, and a stray letter would silently
        // fall back to 8080 on save.
        _ui.value = _ui.value.copy(port = value.filter { it.isDigit() }.take(5), error = null)
        persistEndpoint()
    }

    fun setEnabled(enabled: Boolean) {
        _ui.value = _ui.value.copy(enabled = enabled, error = null)
        viewModelScope.launch {
            settings.setClockEnabled(enabled)
            // Turning it on has to arm the block-boundary alarms; turning it
            // off lets them lapse at the next reschedule.
            TaskAlarmScheduler.scheduleUpcoming(getApplication())
            if (enabled) ClockMirror.syncQuietly(getApplication())
        }
    }

    fun setMirrorFocus(enabled: Boolean) {
        _ui.value = _ui.value.copy(mirrorFocus = enabled)
        viewModelScope.launch {
            settings.setClockMirrorFocus(enabled)
            ClockMirror.syncQuietly(getApplication())
        }
    }

    fun setMirrorBlocks(enabled: Boolean) {
        _ui.value = _ui.value.copy(mirrorBlocks = enabled)
        viewModelScope.launch {
            settings.setClockMirrorBlocks(enabled)
            ClockMirror.syncQuietly(getApplication())
        }
    }

    /** One read of /api/status, reported in plain words. */
    fun testConnection() {
        val state = _ui.value
        if (state.testing) return
        if (!state.configured) {
            _ui.value = state.copy(error = "Enter the clock's address first.")
            return
        }
        _ui.value = state.copy(testing = true, error = null, status = null)
        viewModelScope.launch {
            clock.refresh()
                .onSuccess { status ->
                    settings.setClockEndpoint(
                        host = _ui.value.host,
                        port = _ui.value.port.toIntOrNull() ?: SettingsRepository.DEFAULT_CLOCK_PORT,
                        name = status.name
                    )
                    _ui.value = _ui.value.copy(
                        testing = false,
                        name = status.name,
                        lastSeenAt = System.currentTimeMillis(),
                        status = "${status.name} answered · ${status.display}"
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        testing = false,
                        error = e.message ?: "Couldn't reach the clock."
                    )
                }
        }
    }

    fun findClock() {
        if (_ui.value.scanning) return
        _ui.value = _ui.value.copy(
            scanning = true,
            scanProbed = 0,
            scanTotal = 0,
            found = emptyList(),
            error = null,
            status = null
        )
        scanJob = viewModelScope.launch {
            // This callback arrives from 24 probe coroutines at once, so it has
            // to be an atomic update — a read-modify-write here would clobber
            // whatever the main thread wrote in between.
            ExactHourDiscovery.scan(getApplication()) { probed, total ->
                _ui.update { it.copy(scanProbed = probed, scanTotal = total) }
            }
                .onSuccess { clocks ->
                    _ui.value = _ui.value.copy(
                        scanning = false,
                        found = clocks,
                        status = when {
                            clocks.isEmpty() ->
                                "Nothing answered on this Wi-Fi. If the clock is on a " +
                                    "different subnet, type its address instead."
                            clocks.size == 1 -> "Found ${clocks.first().name}."
                            else -> "Found ${clocks.size} clocks — pick one."
                        }
                    )
                    // One result and nothing configured yet: just take it.
                    if (clocks.size == 1 && !_ui.value.configured) select(clocks.first())
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        scanning = false,
                        error = e.message ?: "Couldn't scan this network."
                    )
                }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _ui.value = _ui.value.copy(scanning = false)
    }

    fun select(clock: DiscoveredClock) {
        _ui.value = _ui.value.copy(
            host = clock.host,
            port = clock.port.toString(),
            name = clock.name,
            found = emptyList(),
            error = null,
            status = "Using ${clock.name} at ${clock.host}."
        )
        viewModelScope.launch {
            settings.setClockEndpoint(clock.host, clock.port, clock.name)
            if (!_ui.value.enabled) setEnabled(true)
        }
    }

    private fun persistEndpoint() {
        val state = _ui.value
        viewModelScope.launch {
            settings.setClockEndpoint(
                host = state.host,
                port = state.port.toIntOrNull() ?: SettingsRepository.DEFAULT_CLOCK_PORT,
                name = state.name
            )
        }
    }
}
