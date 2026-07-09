package ai.bewsoa.flow.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.StreakInfo
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BlockWithStatus(val block: TaskBlock, val done: Boolean)

data class TodayUiState(
    val date: LocalDate,
    val blocks: List<BlockWithStatus> = emptyList(),
    val doneCount: Int = 0,
    val countedCount: Int = 0,
    val streak: StreakInfo = StreakInfo(0, yesterdayKept = true, todayKept = false),
    /** Yesterday's counted blocks still not logged — the catch-up list. */
    val yesterdayMissed: List<BlockWithStatus> = emptyList(),
    /** Minutes of focused (deep-work track) blocks already completed today. */
    val deepWorkMinutes: Int = 0
) {
    val progress: Float
        get() = if (countedCount == 0) 0f else doneCount.toFloat() / countedCount
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: ProgramRepository) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<TodayUiState> = combine(date, CustomProgram.version) { day, _ -> day }
        .flatMapLatest { day ->
            val yesterday = day.minusDays(1)
            repo.observeRange(yesterday, day).mapLatest { rows ->
                val doneToday = rows.filter { it.date == day.toString() && it.done }
                    .map { it.taskId }.toSet()
                val doneYesterday = rows.filter { it.date == yesterday.toString() && it.done }
                    .map { it.taskId }.toSet()

                val blocks = repo.blocksFor(day).map {
                    BlockWithStatus(it, doneToday.contains(it.id))
                }
                val counted = blocks.filter { it.block.counted }
                val yesterdayMissed = repo.blocksFor(yesterday)
                    .filter { it.counted }
                    .map { BlockWithStatus(it, doneYesterday.contains(it.id)) }
                    .filter { !it.done }
                val deepWork = blocks
                    .filter { it.done && it.block.counted && it.block.track in DEEP_TRACKS }
                    .sumOf { it.block.durationMinutes }
                    .toInt()

                TodayUiState(
                    date = day,
                    blocks = blocks,
                    doneCount = counted.count { it.done },
                    countedCount = counted.size,
                    streak = repo.computeStreak(day),
                    yesterdayMissed = yesterdayMissed,
                    deepWorkMinutes = deepWork
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TodayUiState(LocalDate.now())
        )

    /** Called by the UI ticker so the screen rolls over at midnight. */
    fun onTick() {
        val now = LocalDate.now()
        if (now != date.value) date.value = now
    }

    fun setDone(taskId: String, done: Boolean) {
        viewModelScope.launch { repo.setDone(date.value, taskId, done) }
    }

    /** Retroactively log (or unlog) one of yesterday's blocks from the catch-up list. */
    fun setYesterdayDone(taskId: String, done: Boolean) {
        viewModelScope.launch { repo.setDone(date.value.minusDays(1), taskId, done) }
    }

    companion object {
        /** Tracks that count as focused "deep work" for the daily deep-work meter. */
        private val DEEP_TRACKS = setOf(Track.YKS, Track.TYT, Track.SAT, Track.PROJECT)
    }
}
