package ai.bewsoa.flow.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.ActiveFocus
import ai.bewsoa.flow.data.FocusRepository
import ai.bewsoa.flow.data.db.FocusSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FocusUiState(
    val active: ActiveFocus? = null,
    /** Wall-clock "now", ticked every second while the screen is visible. */
    val now: Long = System.currentTimeMillis(),
    val todaySessions: List<FocusSessionEntity> = emptyList()
) {
    val todayMinutes: Int get() = todaySessions.sumOf { it.minutes }

    val remainingMillis: Long
        get() = active?.let { (it.endsAt - now).coerceAtLeast(0L) } ?: 0L

    /** The committed time has fully run out — time for the verdict. */
    val timeUp: Boolean get() = active != null && now >= active.endsAt

    /** Whole minutes already spent in the running session (for early finishes). */
    val elapsedMinutes: Int
        get() = active?.let { ((now - it.startedAt) / 60_000L).toInt() } ?: 0

    /** 0..1 share of the committed time already behind you. */
    val sessionProgress: Float
        get() = active?.let {
            ((now - it.startedAt).toFloat() / (it.plannedMinutes * 60_000L)).coerceIn(0f, 1f)
        } ?: 0f
}

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(private val repo: FocusRepository) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    // Drives the countdown; also rolls the "today" list over at midnight.
    // Runs only while the UI collects (stateIn WhileSubscribed).
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            val today = LocalDate.now()
            if (date.value != today) date.value = today
            delay(1_000L)
        }
    }

    val uiState: StateFlow<FocusUiState> = combine(
        repo.activeSession,
        ticker,
        date.flatMapLatest { repo.observeForDate(it) }
    ) { active, now, sessions ->
        FocusUiState(active = active, now = now, todaySessions = sessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun start(label: String, minutes: Int) {
        viewModelScope.launch { repo.start(label, minutes) }
    }

    /** The timer ran out and the user says it's done — full committed time counts. */
    fun completeFull() {
        viewModelScope.launch { repo.complete() }
    }

    /** Wrapped up ahead of the timer — credit the minutes actually spent. */
    fun finishEarly() {
        val elapsed = uiState.value.elapsedMinutes
        viewModelScope.launch { repo.complete(creditedMinutes = elapsed) }
    }

    /** Didn't happen — log nothing, keep no guilt. */
    fun abandon() {
        viewModelScope.launch { repo.abandon() }
    }
}
