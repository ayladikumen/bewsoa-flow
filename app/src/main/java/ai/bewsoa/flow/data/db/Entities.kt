package ai.bewsoa.flow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Every notification the app has shown, newest first on the Alerts screen. */
@Entity(tableName = "notification_log")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** "task" for end-of-block reminders, "motivation" for boosts. */
    val kind: String,
    val title: String,
    val message: String
)

/** What happened to one block on one day. */
enum class CompletionState {
    /** Not done — and still counts against you. Also the state of a row that never existed. */
    PENDING,
    DONE,

    /**
     * Excused for today. A skipped block leaves the denominator entirely: it is
     * not a miss, and it does not inflate completion either. Capped per week
     * (see ProgramRepository.SKIP_CAP_PER_WEEK) so a streak can't be faked.
     */
    SKIPPED
}

/**
 * One block's outcome on a given day.
 * Date is stored as ISO yyyy-MM-dd so string ordering equals date ordering.
 *
 * [state] is a String rather than the enum so Room needs no TypeConverter, and
 * so a value written by a future version can't crash an older parse.
 */
@Entity(tableName = "task_completions", primaryKeys = ["date", "taskId"])
data class TaskCompletionEntity(
    val date: String,
    val taskId: String,
    val state: String,
    val completedAt: Long?,
    val skipReason: String? = null
) {
    // Derived, not constructor params — Room does not persist these. Keeping
    // `done` readable means the many `.filter { it.done }` sites still compile,
    // so the compiler flags exactly the writes that need attention.
    val done: Boolean get() = state == CompletionState.DONE.name
    val skipped: Boolean get() = state == CompletionState.SKIPPED.name
}

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
