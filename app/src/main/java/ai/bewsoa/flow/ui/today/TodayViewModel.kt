package ai.bewsoa.flow.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.StreakInfo
import ai.bewsoa.flow.data.TaskBlock
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
    val streak: StreakInfo = StreakInfo(0, yesterdayKept = true, todayKept = false)
) {
    val progress: Float
        get() = if (countedCount == 0) 0f else doneCount.toFloat() / countedCount
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: ProgramRepository) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<TodayUiState> = combine(date, CustomProgram.version) { day, _ -> day }
        .flatMapLatest { day ->
            repo.observeDay(day).mapLatest { rows ->
                val doneIds = rows.filter { it.done }.map { it.taskId }.toSet()
                val blocks = repo.blocksFor(day).map {
                    BlockWithStatus(it, doneIds.contains(it.id))
                }
                val counted = blocks.filter { it.block.counted }
                TodayUiState(
                    date = day,
                    blocks = blocks,
                    doneCount = counted.count { it.done },
                    countedCount = counted.size,
                    streak = repo.computeStreak(day)
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
}
