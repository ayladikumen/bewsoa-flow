package ai.bewsoa.flow.ui.clock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.exacthour.AutomationTemplate
import ai.bewsoa.flow.data.exacthour.ClockState
import ai.bewsoa.flow.data.exacthour.ClockStatus
import ai.bewsoa.flow.data.exacthour.ExactHourClient
import ai.bewsoa.flow.data.exacthour.ExactHourError
import ai.bewsoa.flow.data.exacthour.ExactHourRepository
import ai.bewsoa.flow.data.exacthour.TextOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClockRemoteUiState(
    val status: ClockStatus? = null,
    /** Null until the saved endpoint has been read — don't claim either way. */
    val configured: Boolean? = null,
    val templates: List<AutomationTemplate> = emptyList(),
    val automationsAvailable: Boolean = true,
    /** Per-template, per-param values the user has dialled in. */
    val params: Map<String, Map<String, Int>> = emptyMap(),
    val holdMinutes: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val note: String? = null
) {
    val state: ClockState get() = status?.state ?: ClockState.IDLE

    /** The device ignores /api/adjust and /api/set outright while it's running. */
    val timerEditable: Boolean get() = status?.timerEditable ?: true

    val maxMinutes: Int get() = status?.maxMinutes ?: 270

    val automationRunning: Boolean get() = status?.automationRunning == true

    val automationError: String? get() = status?.automationError
}

/**
 * The manual remote. Polls only while the screen is on top — `WhileSubscribed`
 * is doing real work here, it's what guarantees the app never talks to the
 * clock in the background.
 */
class ClockRemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ExactHourRepository.get(app)

    private val local = MutableStateFlow(ClockRemoteUiState())

    private var failures = 0

    /**
     * The poll loop *is* the ticker, and the ticker only runs while the UI is
     * collecting (see `WhileSubscribed` below). That's what guarantees the app
     * never talks to the clock once this screen is off the top of the stack.
     */
    private val ticker = flow {
        while (true) {
            poll()
            emit(Unit)
            val status = repo.status.value
            val interval = when {
                // A clock that's switched off shouldn't be pestered every second.
                failures >= FAILURES_BEFORE_BACKOFF -> BACKOFF_MS
                status?.state == ClockState.RUNNING || status?.automationRunning == true -> FAST_MS
                else -> IDLE_MS
            }
            delay(interval)
        }
    }

    val ui: StateFlow<ClockRemoteUiState> = combine(
        repo.status,
        local,
        ticker
    ) { status, state, _ ->
        state.copy(status = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), ClockRemoteUiState())

    init {
        viewModelScope.launch {
            val endpoint = repo.endpoint()
            val hold = repo.holdRemainingMinutes()
            local.update { it.copy(configured = endpoint != null, holdMinutes = hold) }
            loadAutomations()
        }
    }

    private suspend fun poll() {
        repo.refresh()
            .onSuccess { failures = 0 }
            .onFailure { failures++ }
        val hold = repo.holdRemainingMinutes()
        local.update { it.copy(holdMinutes = hold) }
    }

    private suspend fun loadAutomations() {
        val client = repo.client() ?: return
        client.automations()
            .onSuccess { found -> local.update { it.copy(templates = found, automationsAvailable = true) } }
            .onFailure { error ->
                // A device built without the Automations library 404s the whole
                // family of routes. That's a capability, not a failure.
                if (error is ExactHourError.AutomationsDisabled) {
                    local.update { it.copy(automationsAvailable = false) }
                }
            }
    }

    // Commands ------------------------------------------------------------

    fun toggle() = send { it.toggle() }

    fun start() = send { it.start() }

    fun pause() = send { it.pause() }

    fun resume() = send { it.resume() }

    fun reset() = send { it.reset() }

    fun adjust(deltaMinutes: Int) = send(
        // The device silently drops these while running, so say so rather than
        // letting the tap look like it worked.
        rejection = "The clock ignored that — it's running. Pause first."
    ) { it.adjust(deltaMinutes) }

    fun setTime(minutes: Int, seconds: Int) = send(
        rejection = "The clock ignored that — it's running. Pause first."
    ) { it.set(minutes, seconds) }

    fun showText(text: String, scroll: Boolean) = send {
        it.text(
            TextOverlay(
                text = text,
                mode = if (scroll) TextOverlay.MODE_SCROLL else TextOverlay.MODE_AUTO
            )
        )
    }

    fun clearText() = send { it.text(TextOverlay.clear()) }

    fun runAutomation(template: AutomationTemplate) {
        val values = paramsFor(template)
        send { it.runAutomation(template.id, values) }
    }

    fun stopAutomation() = send { it.stopAutomation() }

    fun setParam(templateId: String, param: String, value: Int) {
        local.update { state ->
            val forTemplate = state.params[templateId].orEmpty() + (param to value)
            state.copy(params = state.params + (templateId to forTemplate))
        }
    }

    fun paramsFor(template: AutomationTemplate): Map<String, Int> {
        val overrides = local.value.params[template.id].orEmpty()
        return template.params.associate { it.name to it.clamp(overrides[it.name] ?: it.default) }
    }

    fun releaseHold() {
        viewModelScope.launch {
            repo.releaseAuto()
            local.update { it.copy(holdMinutes = 0, note = null) }
        }
    }

    /**
     * Every command goes through here, so every command also arms the manual
     * hold — the moment the user drives the clock themselves, the auto-mirror
     * stops changing it out from under them.
     *
     * [rejection] is shown when the device accepts the request but the timer
     * doesn't move, which is how its RUNNING lock manifests: HTTP 200, nothing
     * changed.
     */
    private fun send(
        rejection: String? = null,
        op: suspend (ExactHourClient) -> Result<ClockStatus>
    ) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null, note = null) }
        viewModelScope.launch {
            repo.holdAuto()
            repo.command(op)
                .onSuccess { after ->
                    // The device's rule is exactly this: if it's running when
                    // the command lands, the command was dropped. Comparing
                    // before/after times instead would be unreliable, since a
                    // live countdown moves on its own between the two reads.
                    val ignored = rejection != null && after.state == ClockState.RUNNING
                    val hold = repo.holdRemainingMinutes()
                    local.update {
                        it.copy(
                            busy = false,
                            note = if (ignored) rejection else null,
                            holdMinutes = hold
                        )
                    }
                }
                .onFailure { e ->
                    local.update {
                        it.copy(busy = false, error = e.message ?: "The clock didn't answer.")
                    }
                }
        }
    }

    private companion object {
        const val FAST_MS = 1_000L
        const val IDLE_MS = 3_000L
        const val BACKOFF_MS = 10_000L
        const val FAILURES_BEFORE_BACKOFF = 3
    }
}
