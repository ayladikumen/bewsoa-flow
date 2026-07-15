package ai.bewsoa.flow.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.ChestState
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.DayBlockOrder
import ai.bewsoa.flow.data.FocusRepository
import ai.bewsoa.flow.data.Insight
import ai.bewsoa.flow.data.Insights
import ai.bewsoa.flow.data.LevelInfo
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SkipBudget
import ai.bewsoa.flow.data.StreakInfo
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.WeekStats
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.data.Xp
import ai.bewsoa.flow.data.XpRepository
import ai.bewsoa.flow.data.buildWeekStats
import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.widget.Widgets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The week's Deep Focus tally: total time, session count, minutes per day (Mon..Sun). */
data class FocusWeekStats(
    val totalMinutes: Int = 0,
    val sessions: Int = 0,
    val dayMinutes: List<Int> = List(7) { 0 }
)

/** Level, weekly earnings and the two chests that can matter at once. */
data class XpProgress(
    val level: LevelInfo = Xp.levelFor(0),
    val totalXp: Int = 0,
    val weekXp: Int = 0,
    val chest: ChestState? = null,
    /** Last week's chest, only when it is still waiting to be opened. */
    val lastChest: ChestState? = null
)

data class ProgressUiState(
    val stats: WeekStats? = null,
    val streak: StreakInfo = StreakInfo(0, yesterdayKept = true, todayKept = false),
    val insights: List<Insight> = emptyList(),
    val focus: FocusWeekStats = FocusWeekStats(),
    val xp: XpProgress = XpProgress()
)

// The plan browser (Day mode) -------------------------------------------------

/** One block on the browsed day, with what the log says about it. */
data class PlanBlock(val block: TaskBlock, val state: CompletionState)

/** One chip in the Mon..Sun selector: enough to draw a letter and a status dot. */
data class PlanDayChip(
    val date: LocalDate,
    val planned: Int,
    val done: Int,
    val skipped: Int
)

data class PlanUiState(
    val weekStart: LocalDate,
    val selected: LocalDate,
    val chips: List<PlanDayChip> = emptyList(),
    val blocks: List<PlanBlock> = emptyList(),
    val skipBudget: SkipBudget = SkipBudget(0, ProgramRepository.SKIP_CAP_PER_WEEK)
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    app: Application,
    private val repo: ProgramRepository,
    focusRepo: FocusRepository,
    private val xpRepo: XpRepository
) : AndroidViewModel(app) {

    val weekStart: LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Insights look back a month beyond the visible week. */
    private val historyStart: LocalDate = weekStart.minusWeeks(4)

    private val xpFlow = combine(
        xpRepo.observeTotal(),
        xpRepo.observeWeekXp(weekStart),
        xpRepo.observeChest(weekStart),
        xpRepo.observeChest(weekStart.minusWeeks(1))
    ) { total, week, chest, lastChest ->
        XpProgress(
            level = Xp.levelFor(total),
            totalXp = total,
            weekXp = week,
            chest = chest,
            lastChest = lastChest.takeIf { it.claimable }
        )
    }

    val uiState: StateFlow<ProgressUiState> =
        combine(
            repo.observeRange(historyStart, weekStart.plusDays(6)),
            focusRepo.observeRange(weekStart, weekStart.plusDays(6)),
            CustomProgram.version,
            xpFlow
        ) { rows, focusSessions, _, xp -> Triple(rows, focusSessions, xp) }
        .mapLatest { (rows, focusSessions, xp) ->
            val weekRows = rows.filter { it.date >= weekStart.toString() }
            val dayMinutes = MutableList(7) { 0 }
            focusSessions.forEach { session ->
                val index = runCatching {
                    java.time.temporal.ChronoUnit.DAYS
                        .between(weekStart, LocalDate.parse(session.date)).toInt()
                }.getOrDefault(-1)
                if (index in 0..6) dayMinutes[index] += session.minutes
            }
            ProgressUiState(
                stats = buildWeekStats(weekStart, weekRows),
                streak = repo.computeStreak(LocalDate.now()),
                insights = Insights.compute(LocalDate.now(), rows, WeeklyProgram::blocksFor),
                focus = FocusWeekStats(
                    totalMinutes = focusSessions.sumOf { it.minutes },
                    sessions = focusSessions.size,
                    dayMinutes = dayMinutes
                ),
                xp = xp
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun openChest() {
        viewModelScope.launch { xpRepo.openChest(weekStart) }
    }

    fun openLastChest() {
        viewModelScope.launch { xpRepo.openChest(weekStart.minusWeeks(1)) }
    }

    // Plan browser ---------------------------------------------------------------

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _skipRejected = MutableSharedFlow<Unit>()

    /** Fires when a skip was refused because that week's budget is spent. */
    val skipRejected: SharedFlow<Unit> = _skipRejected.asSharedFlow()

    val planState: StateFlow<PlanUiState> = combine(
        selectedDate,
        CustomProgram.version,
        DayBlockOrder.version
    ) { selected, _, _ -> selected }
        .flatMapLatest { selected ->
            val planWeek = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            combine(
                repo.observeWeek(planWeek),
                repo.observeSkipBudget(selected)
            ) { rows, budget ->
                val doneByDate = rows.filter { it.done }
                    .groupBy({ it.date }, { it.taskId })
                    .mapValues { (_, ids) -> ids.toSet() }
                val skippedByDate = rows.filter { it.skipped }
                    .groupBy({ it.date }, { it.taskId })
                    .mapValues { (_, ids) -> ids.toSet() }

                val chips = (0L..6L).map { shift ->
                    val date = planWeek.plusDays(shift)
                    val counted = repo.blocksFor(date).filter { it.counted }
                    val skipped = skippedByDate[date.toString()].orEmpty()
                    val effective = counted.filterNot { skipped.contains(it.id) }
                    val doneIds = doneByDate[date.toString()].orEmpty()
                    PlanDayChip(
                        date = date,
                        planned = effective.size,
                        done = effective.count { doneIds.contains(it.id) },
                        skipped = counted.size - effective.size
                    )
                }

                val selectedRows = rows.filter { it.date == selected.toString() }
                    .associateBy { it.taskId }
                val blocks = repo.blocksFor(selected).map { block ->
                    val state = selectedRows[block.id]
                        ?.let { runCatching { CompletionState.valueOf(it.state) }.getOrNull() }
                        ?: CompletionState.PENDING
                    PlanBlock(block, state)
                }

                PlanUiState(
                    weekStart = planWeek,
                    selected = selected,
                    chips = chips,
                    blocks = blocks,
                    skipBudget = budget
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlanUiState(weekStart = weekStart, selected = LocalDate.now())
        )

    fun selectDay(date: LocalDate) {
        selectedDate.value = date
    }

    /** ‹ › on the week header; keeps the same weekday selected across weeks. */
    fun shiftWeek(deltaWeeks: Long) {
        selectedDate.value = selectedDate.value.plusWeeks(deltaWeeks)
    }

    fun jumpToToday() {
        selectedDate.value = LocalDate.now()
    }

    /** Logging is allowed for today and the past — the future stays unwritten. */
    fun setPlanDone(date: LocalDate, blockId: String, done: Boolean) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            repo.setDone(date, blockId, done)
            Widgets.refreshAll(getApplication())
        }
    }

    fun skipPlanBlock(date: LocalDate, blockId: String) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            if (repo.skip(date, blockId)) {
                Widgets.refreshAll(getApplication())
            } else {
                _skipRejected.emit(Unit)
            }
        }
    }

    fun unskipPlanBlock(date: LocalDate, blockId: String) {
        viewModelScope.launch {
            repo.unskip(date, blockId)
            Widgets.refreshAll(getApplication())
        }
    }
}
