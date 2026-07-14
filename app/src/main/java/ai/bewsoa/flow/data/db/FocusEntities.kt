package ai.bewsoa.flow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One completed Deep Focus session — a stretch of single-task attention the user
 * committed to up front ("what" + "how long") and confirmed at the end.
 *
 * Sessions are only stored once they finish: [date] is the day the session was
 * confirmed on (by the clock at that moment), so it lands in that day's tally
 * and in the week's Deep Focus total on the Progress screen.
 *
 * @param minutes focused minutes actually credited (planned time when the timer
 *        ran out, elapsed time on an early finish).
 * @param plannedMinutes what the user committed to when starting.
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val label: String,
    val minutes: Int,
    val plannedMinutes: Int,
    val startedAt: Long,
    val completedAt: Long
)
