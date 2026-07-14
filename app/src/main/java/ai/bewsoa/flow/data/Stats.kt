package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.TaskCompletionEntity
import java.time.LocalDate

/**
 * @param planned counted blocks *minus* those skipped — the excused-skip
 *        contract. A skipped block is not a miss, so it cannot sit in the
 *        denominator; it also isn't a win, so it never lands in [done].
 * @param skipped kept for the UI ("2 of 3 skips left"), never for the ratio.
 */
data class DayStat(
    val date: LocalDate,
    val planned: Int,
    val done: Int,
    val skipped: Int = 0
) {
    val ratio: Float get() = if (planned == 0) 0f else done.toFloat() / planned
}

data class TrackStat(
    val track: Track,
    val plannedMinutes: Long,
    val doneMinutes: Long,
    val plannedSessions: Int,
    val doneSessions: Int
) {
    val ratio: Float
        get() = if (plannedMinutes == 0L) 0f
        else (doneMinutes.toFloat() / plannedMinutes).coerceAtMost(1f)
}

data class WeekStats(
    val weekStart: LocalDate,
    val days: List<DayStat>,
    val tracks: List<TrackStat>,
    val plannedBlocks: Int,
    val doneBlocks: Int
) {
    val overallRatio: Float
        get() = if (plannedBlocks == 0) 0f else doneBlocks.toFloat() / plannedBlocks
}

/** Tracks that get their own row on the Progress screen. */
private val STAT_TRACKS = listOf(
    Track.YKS, Track.TYT, Track.SAT, Track.PROJECT, Track.GYM, Track.REVIEW
)

fun buildWeekStats(weekStart: LocalDate, rows: List<TaskCompletionEntity>): WeekStats {
    val doneByDate: Map<String, Set<String>> = rows
        .filter { it.done }
        .groupBy({ it.date }, { it.taskId })
        .mapValues { (_, ids) -> ids.toSet() }
    val skippedByDate: Map<String, Set<String>> = rows
        .filter { it.skipped }
        .groupBy({ it.date }, { it.taskId })
        .mapValues { (_, ids) -> ids.toSet() }

    val days = (0L..6L).map { shift ->
        val date = weekStart.plusDays(shift)
        val counted = WeeklyProgram.blocksFor(date).filter { it.counted }
        val skippedIds = skippedByDate[date.toString()].orEmpty()
        val effective = counted.filterNot { skippedIds.contains(it.id) }
        val doneIds = doneByDate[date.toString()].orEmpty()
        DayStat(
            date = date,
            planned = effective.size,
            done = effective.count { doneIds.contains(it.id) },
            skipped = counted.count { skippedIds.contains(it.id) }
        )
    }

    val tracks = STAT_TRACKS.map { track ->
        var plannedMin = 0L
        var doneMin = 0L
        var plannedSessions = 0
        var doneSessions = 0
        (0L..6L).forEach { shift ->
            val date = weekStart.plusDays(shift)
            val doneIds = doneByDate[date.toString()].orEmpty()
            val skippedIds = skippedByDate[date.toString()].orEmpty()
            WeeklyProgram.blocksFor(date)
                .filter { it.counted && it.track == track }
                // A skipped block never planned, as far as the stats are concerned.
                .filterNot { skippedIds.contains(it.id) }
                .forEach { block ->
                    plannedMin += block.durationMinutes
                    plannedSessions++
                    if (doneIds.contains(block.id)) {
                        doneMin += block.durationMinutes
                        doneSessions++
                    }
                }
        }
        TrackStat(track, plannedMin, doneMin, plannedSessions, doneSessions)
    }

    return WeekStats(
        weekStart = weekStart,
        days = days,
        tracks = tracks,
        plannedBlocks = days.sumOf { it.planned },
        doneBlocks = days.sumOf { it.done }
    )
}
