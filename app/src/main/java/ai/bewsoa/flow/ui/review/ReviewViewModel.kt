package ai.bewsoa.flow.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Editable mirror of [WeeklyReviewEntity], one field per template line. */
data class ReviewForm(
    val yksBlocksKept: Int = 0,
    val tytScore: String = "",
    val tytPrevScore: String = "",
    val biggestGap: String = "",
    val satHours: String = "",
    val satWeakest: String = "",
    val exactHourNotes: String = "",
    val bewsoaClockNotes: String = "",
    val commitCount: String = "",
    val gymSessions: Int = 0,
    val sleepAverage: String = "",
    val energy: Int = 5,
    val slowedMeDown: String = "",
    val nextWeekTask: String = ""
)

private val GYM_BLOCK_IDS = setOf("wd_gym", "sa_gym", "su_gym")

class ReviewViewModel(private val repo: ProgramRepository) : ViewModel() {

    val weekStart: LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private val _form = MutableStateFlow(ReviewForm())
    val form: StateFlow<ReviewForm> = _form.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    val pastReviews: StateFlow<List<WeeklyReviewEntity>> = repo.observeReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repo.getReview(weekStart)?.let { saved -> _form.value = saved.toForm() }
            autofillFromTracking()
        }
    }

    fun update(transform: (ReviewForm) -> ReviewForm) {
        _form.value = transform(_form.value)
        _justSaved.value = false
    }

    fun save() {
        viewModelScope.launch {
            repo.saveReview(_form.value.toEntity(weekStart))
            _justSaved.value = true
        }
    }

    /**
     * Pre-fills the counters the app already knows from tracking:
     * YKS morning blocks kept and gym sessions done this week.
     */
    private suspend fun autofillFromTracking() {
        val doneRows = repo.getWeek(weekStart).filter { it.done }
        val yksKept = doneRows
            .filter { it.taskId == "wd_yks_morning" }
            .map { it.date }
            .distinct().size
        val gymDone = doneRows
            .filter { it.taskId in GYM_BLOCK_IDS }
            .map { it.date to it.taskId }
            .distinct().size
        _form.value = _form.value.copy(
            yksBlocksKept = maxOf(_form.value.yksBlocksKept, yksKept),
            gymSessions = maxOf(_form.value.gymSessions, gymDone)
        )
    }
}

private fun WeeklyReviewEntity.toForm() = ReviewForm(
    yksBlocksKept = yksBlocksKept,
    tytScore = tytScore,
    tytPrevScore = tytPrevScore,
    biggestGap = biggestGap,
    satHours = satHours,
    satWeakest = satWeakest,
    exactHourNotes = exactHourNotes,
    bewsoaClockNotes = bewsoaClockNotes,
    commitCount = commitCount,
    gymSessions = gymSessions,
    sleepAverage = sleepAverage,
    energy = energy,
    slowedMeDown = slowedMeDown,
    nextWeekTask = nextWeekTask
)

private fun ReviewForm.toEntity(weekStart: LocalDate) = WeeklyReviewEntity(
    weekStart = weekStart.toString(),
    yksBlocksKept = yksBlocksKept,
    tytScore = tytScore,
    tytPrevScore = tytPrevScore,
    biggestGap = biggestGap,
    satHours = satHours,
    satWeakest = satWeakest,
    exactHourNotes = exactHourNotes,
    bewsoaClockNotes = bewsoaClockNotes,
    commitCount = commitCount,
    gymSessions = gymSessions,
    sleepAverage = sleepAverage,
    energy = energy,
    slowedMeDown = slowedMeDown,
    nextWeekTask = nextWeekTask,
    savedAt = System.currentTimeMillis()
)
