package ai.bewsoa.flow.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A task the user entered themselves — the "what I actually need to do" layer that
 * sits on top of the fixed weekly routine. The routine stays the time backbone;
 * these are the concrete jobs the AI helps shape (split, estimate, schedule reviews).
 *
 * @param scheduledDate ISO yyyy-MM-dd — the day the task shows up on Today.
 * @param estimatedMinutes user/AI estimate used for the daily capacity check
 *        (Parkinson's law); 0 means "not estimated".
 * @param track optional [ai.bewsoa.flow.data.Track] name, so a task can be tagged
 *        to the same tracks the routine uses; null for anything general.
 * @param needsReview flagged for spaced repetition — completing it spawns review
 *        copies at +1/+3/+7/+30 days (Ebbinghaus).
 * @param reviewParentId set on an auto-generated review copy, pointing at the task
 *        it reviews; null on originals. Used to avoid spawning reviews of reviews.
 * @param reviewStage human label of the review interval ("1d", "3d", "1w", "1mo"),
 *        empty on non-review tasks.
 */
@Entity(tableName = "user_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val track: String? = null,
    val scheduledDate: String,
    val estimatedMinutes: Int = 0,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long,
    val sortOrder: Int = 0,
    val needsReview: Boolean = false,
    val reviewParentId: Long? = null,
    val reviewStage: String = ""
)

/** One AI- or user-made step of a [TaskEntity] (Zeigarnik: close the open loop step by step). */
@Entity(tableName = "task_subtasks")
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0
)

/** A task with its subtasks, read together so the UI can show progress in one shot. */
data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subtasks: List<SubtaskEntity>
) {
    /** All subtasks checked (or the task itself checked when it has none). */
    val isComplete: Boolean
        get() = if (subtasks.isEmpty()) task.done else subtasks.all { it.done }

    /** 0..1 fill for the progress bar; falls back to the task's own done flag. */
    val progress: Float
        get() = when {
            subtasks.isNotEmpty() -> subtasks.count { it.done }.toFloat() / subtasks.size
            task.done -> 1f
            else -> 0f
        }
}
