package ai.bewsoa.flow.data

import java.time.Duration
import java.time.LocalTime

/**
 * One block on the daily schedule.
 *
 * @param id stable identifier — completions in the database are keyed by (date, id),
 *           so changing an id orphans its history.
 * @param counted whether the block counts toward daily progress and gets an
 *                end-of-block reminder. Meals and free time don't.
 */
data class TaskBlock(
    val id: String,
    val title: String,
    val track: Track,
    val start: LocalTime,
    val end: LocalTime,
    val note: String = "",
    val counted: Boolean = true
) {
    val durationMinutes: Long
        get() = Duration.between(start, end).toMinutes()
}
