package ai.bewsoa.flow.data

import android.content.Context
import ai.bewsoa.flow.data.db.AppDatabase
import ai.bewsoa.flow.data.db.SubtaskEntity
import ai.bewsoa.flow.data.db.TaskEntity
import ai.bewsoa.flow.data.db.TaskWithSubtasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The user-task layer. Wraps the task DAO and folds in the science modules:
 * - NLP entry and Zeigarnik splitting via [AiTaskParser],
 * - spaced repetition (Ebbinghaus): completing a review-flagged task schedules
 *   copies at +1/+3/+7/+30 days,
 * - Parkinson capacity: [SettingsRepository.dailyCapacityMinutes] is the ceiling
 *   the UI compares the day's estimated minutes against.
 */
class TaskRepository private constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository
) {

    private val dao get() = db.taskDao()

    fun observeForDate(date: LocalDate): Flow<List<TaskWithSubtasks>> =
        dao.observeForDate(date.toString())

    suspend fun getRange(from: LocalDate, to: LocalDate): List<TaskWithSubtasks> =
        dao.getRange(from.toString(), to.toString())

    val dailyCapacityMinutes: Flow<Int> = settings.dailyCapacityMinutes

    suspend fun setDailyCapacityMinutes(minutes: Int) = settings.setDailyCapacityMinutes(minutes)

    // Plain, instant add — the frictionless path when the AI isn't needed.
    suspend fun addQuickTask(date: LocalDate, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        dao.insertTask(
            TaskEntity(
                title = clean,
                scheduledDate = date.toString(),
                createdAt = System.currentTimeMillis(),
                sortOrder = dao.maxSortOrder(date.toString()) + 1
            )
        )
    }

    /**
     * Natural-language add: the model parses one sentence into a scheduled,
     * estimated, optionally split task. Failure carries a message for the UI.
     */
    suspend fun addTaskWithAi(sentence: String): Result<Unit> {
        val clean = sentence.trim()
        if (clean.isEmpty()) return Result.success(Unit)
        val provider = settings.aiProvider.first()
        val key = keyFor(provider)
        if (key.isBlank()) return Result.failure(NoApiKeyException())

        val today = LocalDate.now()
        return AiTaskParser.parse(provider, key, clean, today.toString(), today.dayOfWeek.name)
            .mapCatching { parsed ->
                val date = runCatching { LocalDate.parse(parsed.scheduledDate) }.getOrDefault(today)
                val id = dao.insertTask(
                    TaskEntity(
                        title = parsed.title.ifBlank { clean },
                        note = parsed.note,
                        track = parsed.track,
                        scheduledDate = date.toString(),
                        estimatedMinutes = parsed.estimatedMinutes,
                        createdAt = System.currentTimeMillis(),
                        sortOrder = dao.maxSortOrder(date.toString()) + 1,
                        needsReview = parsed.needsReview,
                        urgent = parsed.urgent,
                        important = parsed.important
                    )
                )
                if (parsed.subtasks.isNotEmpty()) dao.insertSubtasks(subtasksFor(id, parsed.subtasks))
            }
    }

    /** Zeigarnik: ask the model to break an existing task into checkable steps. */
    suspend fun splitWithAi(taskId: Long): Result<Unit> {
        val task = dao.getTask(taskId) ?: return Result.success(Unit)
        val provider = settings.aiProvider.first()
        val key = keyFor(provider)
        if (key.isBlank()) return Result.failure(NoApiKeyException())

        return AiTaskParser.split(provider, key, task.title, task.note).mapCatching { subs ->
            if (subs.isNotEmpty()) {
                dao.deleteSubtasksOf(taskId)
                dao.insertSubtasks(subtasksFor(taskId, subs))
            }
        }
    }

    suspend fun setTaskDone(task: TaskEntity, done: Boolean) {
        dao.updateTask(
            task.copy(done = done, completedAt = if (done) System.currentTimeMillis() else null)
        )
        // Only originals spawn reviews, and only once.
        if (done && task.needsReview && task.reviewParentId == null) spawnReviews(task)
    }

    suspend fun setSubtaskDone(subtask: SubtaskEntity, done: Boolean) {
        dao.updateSubtask(subtask.copy(done = done))
    }

    /** Eisenhower: reclassify by hand — the AI's guess is only a starting point. */
    suspend fun setQuadrant(task: TaskEntity, urgent: Boolean, important: Boolean) {
        dao.updateTask(task.copy(urgent = urgent, important = important))
    }

    suspend fun deleteTask(task: TaskEntity) {
        dao.deleteSubtasksOf(task.id)
        dao.deleteTask(task)
    }

    /**
     * Flexibility: push an unfinished task to the next day without guilt — the
     * routine (and streak) is the long-term contract, individual tasks can slide.
     */
    suspend fun moveToTomorrow(task: TaskEntity) {
        val current = runCatching { LocalDate.parse(task.scheduledDate) }
            .getOrDefault(LocalDate.now())
        val next = current.plusDays(1).toString()
        dao.updateTask(
            task.copy(
                scheduledDate = next,
                done = false,
                completedAt = null,
                sortOrder = dao.maxSortOrder(next) + 1
            )
        )
    }

    // Ebbinghaus review schedule ---------------------------------------------

    private suspend fun spawnReviews(task: TaskEntity) {
        if (dao.countReviewsOf(task.id) > 0) return
        val start = LocalDate.now()
        val reviews = REVIEW_STAGES.map { (days, label) ->
            val date = start.plusDays(days.toLong())
            TaskEntity(
                title = task.title,
                note = task.note,
                track = task.track,
                scheduledDate = date.toString(),
                estimatedMinutes = REVIEW_MINUTES,
                createdAt = System.currentTimeMillis(),
                needsReview = false,
                reviewParentId = task.id,
                reviewStage = label,
                // Reviewing important material is important; the clock resets, so not urgent.
                urgent = false,
                important = task.important
            )
        }
        dao.insertTasks(reviews)
    }

    // Helpers -----------------------------------------------------------------

    private fun subtasksFor(taskId: Long, titles: List<String>): List<SubtaskEntity> =
        titles.mapIndexed { i, t -> SubtaskEntity(taskId = taskId, title = t, sortOrder = i) }

    private suspend fun keyFor(provider: String): String =
        if (provider == SettingsRepository.PROVIDER_GEMINI) settings.geminiApiKey.first()
        else settings.apiKey.first()

    /** Raised when an AI action is requested but no key is configured. */
    class NoApiKeyException : Exception("Add an API key in Settings to use AI here.")

    companion object {
        private const val REVIEW_MINUTES = 15
        // Ebbinghaus-style expanding intervals: 1 day, 3 days, 1 week, 1 month.
        private val REVIEW_STAGES = listOf(1 to "1d", 3 to "3d", 7 to "1w", 30 to "1mo")

        @Volatile
        private var instance: TaskRepository? = null

        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) {
                instance ?: TaskRepository(
                    AppDatabase.getInstance(context),
                    SettingsRepository.get(context)
                ).also { instance = it }
            }
    }
}
