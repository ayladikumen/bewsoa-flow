package ai.bewsoa.flow.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.TaskRepository
import ai.bewsoa.flow.data.db.SubtaskEntity
import ai.bewsoa.flow.data.db.TaskEntity
import ai.bewsoa.flow.data.db.TaskWithSubtasks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TasksUiState(
    val date: LocalDate,
    val tasks: List<TaskWithSubtasks> = emptyList(),
    val plannedMinutes: Int = 0,
    val capacityMinutes: Int = SettingsRepository.DEFAULT_CAPACITY,
    val aiAvailable: Boolean = false,
    val aiBusy: Boolean = false,
    val message: String? = null
) {
    val doneCount: Int get() = tasks.count { it.isComplete }
    val overCapacity: Boolean get() = plannedMinutes > capacityMinutes
    val capacityRatio: Float
        get() = if (capacityMinutes <= 0) 0f else plannedMinutes.toFloat() / capacityMinutes
}

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val repo: TaskRepository,
    settings: SettingsRepository
) : ViewModel() {

    private data class Transient(val aiBusy: Boolean = false, val message: String? = null)

    private val date = MutableStateFlow(LocalDate.now())
    private val transient = MutableStateFlow(Transient())

    private val aiAvailable = combine(
        settings.aiProvider,
        settings.apiKey,
        settings.geminiApiKey
    ) { provider, claudeKey, geminiKey ->
        if (provider == SettingsRepository.PROVIDER_GEMINI) geminiKey.isNotBlank()
        else claudeKey.isNotBlank()
    }

    val uiState: StateFlow<TasksUiState> = combine(
        date.flatMapLatest { day -> repo.observeForDate(day).map { day to it } },
        repo.dailyCapacityMinutes,
        aiAvailable,
        transient
    ) { dayTasks, capacity, ai, tr ->
        val (day, tasks) = dayTasks
        TasksUiState(
            date = day,
            // Eisenhower order inside the open/done groups; ties keep the DAO's order.
            tasks = tasks.sortedWith(
                compareBy({ it.task.done }, { quadrantRank(it.task) })
            ),
            plannedMinutes = tasks.sumOf { it.task.estimatedMinutes },
            capacityMinutes = capacity,
            aiAvailable = ai,
            aiBusy = tr.aiBusy,
            message = tr.message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TasksUiState(LocalDate.now())
    )

    /** Keeps the list on the right day across a midnight rollover. */
    fun onTick() {
        val now = LocalDate.now()
        if (now != date.value) date.value = now
    }

    fun addQuick(title: String) {
        viewModelScope.launch { repo.addQuickTask(date.value, title) }
    }

    fun addWithAi(sentence: String) {
        if (sentence.isBlank()) return
        viewModelScope.launch {
            transient.update { it.copy(aiBusy = true, message = null) }
            val result = repo.addTaskWithAi(sentence)
            transient.update { Transient(aiBusy = false, message = result.errorMessage()) }
        }
    }

    fun splitTask(taskId: Long) {
        viewModelScope.launch {
            transient.update { it.copy(aiBusy = true, message = null) }
            val result = repo.splitWithAi(taskId)
            transient.update { Transient(aiBusy = false, message = result.errorMessage()) }
        }
    }

    fun toggleTask(item: TaskWithSubtasks) {
        viewModelScope.launch {
            val target = !item.isComplete
            item.subtasks.forEach { if (it.done != target) repo.setSubtaskDone(it, target) }
            repo.setTaskDone(item.task, target)
        }
    }

    fun toggleSubtask(item: TaskWithSubtasks, subtask: SubtaskEntity) {
        viewModelScope.launch {
            val newDone = !subtask.done
            repo.setSubtaskDone(subtask, newDone)
            // Closing the last open step closes the whole loop (Zeigarnik).
            val allDone = item.subtasks.all { if (it.id == subtask.id) newDone else it.done }
            if (allDone != item.task.done) repo.setTaskDone(item.task, allDone)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { repo.deleteTask(task) }
    }

    /** Walks the Eisenhower quadrants: Do first → Schedule → Quick win → Later. */
    fun cycleQuadrant(task: TaskEntity) {
        val (urgent, important) = when {
            task.urgent && task.important -> false to true // Do first  -> Schedule
            task.important -> true to false                // Schedule  -> Quick win
            task.urgent -> false to false                  // Quick win -> Later
            else -> true to true                           // Later     -> Do first
        }
        viewModelScope.launch { repo.setQuadrant(task, urgent, important) }
    }

    fun moveToTomorrow(task: TaskEntity) {
        viewModelScope.launch { repo.moveToTomorrow(task) }
    }

    fun adjustCapacity(deltaMinutes: Int) {
        viewModelScope.launch {
            repo.setDailyCapacityMinutes(uiState.value.capacityMinutes + deltaMinutes)
        }
    }

    fun clearMessage() {
        transient.update { it.copy(message = null) }
    }

    private fun Result<Unit>.errorMessage(): String? =
        exceptionOrNull()?.let { it.message ?: "Something went wrong." }

    companion object {
        /** Q1 do-first, Q2 schedule, Q3 quick win, Q4 later. */
        private fun quadrantRank(task: TaskEntity): Int = when {
            task.urgent && task.important -> 0
            task.important -> 1
            task.urgent -> 2
            else -> 3
        }
    }
}
