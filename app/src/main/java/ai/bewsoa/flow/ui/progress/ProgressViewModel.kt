package ai.bewsoa.flow.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.StreakInfo
import ai.bewsoa.flow.data.WeekStats
import ai.bewsoa.flow.data.buildWeekStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ProgressUiState(
    val stats: WeekStats? = null,
    val streak: StreakInfo = StreakInfo(0, yesterdayKept = true, todayKept = false)
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(private val repo: ProgramRepository) : ViewModel() {

    val weekStart: LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val uiState: StateFlow<ProgressUiState> =
        combine(repo.observeWeek(weekStart), CustomProgram.version) { rows, _ -> rows }
        .mapLatest { rows ->
            ProgressUiState(
                stats = buildWeekStats(weekStart, rows),
                streak = repo.computeStreak(LocalDate.now())
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
