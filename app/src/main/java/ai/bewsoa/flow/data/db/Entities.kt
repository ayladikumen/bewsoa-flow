package ai.bewsoa.flow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One checked-off (or unchecked) block on a given day.
 * Date is stored as ISO yyyy-MM-dd so string ordering equals date ordering.
 */
@Entity(tableName = "task_completions", primaryKeys = ["date", "taskId"])
data class TaskCompletionEntity(
    val date: String,
    val taskId: String,
    val done: Boolean,
    val completedAt: Long?
)

/**
 * The Sunday weekly review, mirroring the template in docs/weekly_program.md.
 * Keyed by the Monday of the week it covers.
 */
@Entity(tableName = "weekly_reviews")
data class WeeklyReviewEntity(
    @PrimaryKey val weekStart: String,
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
    val nextWeekTask: String = "",
    val savedAt: Long = 0L
)
