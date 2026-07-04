package ai.bewsoa.flow.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.StreakInfo
import ai.bewsoa.flow.data.WeekStats
import ai.bewsoa.flow.data.buildWeekStats
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The only part of the weekly review a person fills in. Everything else
 * (streak, days kept, hours) is generated from tracking and can't be edited.
 */
data class NotesForm(
    val tytScore: String = "",
    val slowedMeDown: String = "",
    val nextWeekTask: String = ""
)

/** The auto-generated report for the current week. */
data class WeekReport(
    val stats: WeekStats,
    val streak: StreakInfo
)

/** A saved past week: its notes plus the report recomputed from logged days. */
data class PastWeek(
    val weekStart: LocalDate,
    val notes: NotesForm,
    val savedAt: Long,
    val stats: WeekStats
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(private val repo: ProgramRepository) : ViewModel() {

    val weekStart: LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Non-fillable weekly report: recomputed live from every logged day. */
    val report: StateFlow<WeekReport?> = repo.observeWeek(weekStart)
        .mapLatest { rows ->
            WeekReport(
                stats = buildWeekStats(weekStart, rows),
                streak = repo.computeStreak(LocalDate.now())
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notes = MutableStateFlow(NotesForm())
    val notes: StateFlow<NotesForm> = _notes.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    /** Past saved weeks, each with its report rebuilt from that week's logs. */
    val pastWeeks: StateFlow<List<PastWeek>> = repo.observeReviews()
        .mapLatest { saved ->
            saved.filter { it.weekStart != weekStart.toString() }
                .mapNotNull { entity ->
                    val start = runCatching { LocalDate.parse(entity.weekStart) }
                        .getOrNull() ?: return@mapNotNull null
                    PastWeek(
                        weekStart = start,
                        notes = entity.toNotes(),
                        savedAt = entity.savedAt,
                        stats = buildWeekStats(start, repo.getWeek(start))
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repo.getReview(weekStart)?.let { saved -> _notes.value = saved.toNotes() }
        }
    }

    fun update(transform: (NotesForm) -> NotesForm) {
        _notes.value = transform(_notes.value)
        _justSaved.value = false
    }

    fun save() {
        viewModelScope.launch {
            val n = _notes.value
            repo.saveReview(
                WeeklyReviewEntity(
                    weekStart = weekStart.toString(),
                    tytScore = n.tytScore,
                    slowedMeDown = n.slowedMeDown,
                    nextWeekTask = n.nextWeekTask,
                    savedAt = System.currentTimeMillis()
                )
            )
            _justSaved.value = true
        }
    }
}

private fun WeeklyReviewEntity.toNotes() = NotesForm(
    tytScore = tytScore,
    slowedMeDown = slowedMeDown,
    nextWeekTask = nextWeekTask
)
