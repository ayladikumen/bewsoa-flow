package ai.bewsoa.flow.data

import java.time.LocalTime

/**
 * Turns a drag on the Day checklist into concrete clock times.
 *
 * The checklist hides its timestamps, but alarms, the NOW pill and the
 * "ends in" countdown still run on them — so a reorder has to produce times
 * that mean what the user meant: "this is what I'm doing next, from now".
 *
 * Blocks that are already history (done, skipped, or ended) keep their
 * original times. The rest are re-laid in the dragged order, each with its
 * own duration, starting from now — or from the day's first pending slot if
 * the day hasn't reached it yet — keeping the breathing room the original
 * plan had between blocks.
 */
object DayReorder {

    private const val LAST_MINUTE = 23 * 60 + 59

    fun retime(
        ordered: List<TaskBlock>,
        doneIds: Set<String>,
        skippedIds: Set<String>,
        now: LocalTime
    ): List<TaskBlock> {
        val pending = ordered.filterNot {
            it.id in doneIds || it.id in skippedIds || it.end <= now
        }
        if (pending.isEmpty()) return ordered

        // The original pending slots, in clock order. Their spacing is kept by
        // position: the gap after the 1st slot follows the 1st re-laid block,
        // whichever block that now is.
        val slots = pending.sortedBy { it.start }
        val gapAfter = List(slots.size - 1) { i ->
            (slots[i + 1].start.toMinute() - slots[i].end.toMinute()).coerceAtLeast(0)
        }

        // A drag mid-block keeps that block's elapsed time: if the first thing
        // in the new order was already running, its start doesn't move.
        val first = pending.first()
        var cursor =
            if (first.start <= now && now < first.end) first.start.toMinute()
            else maxOf(now.toMinute(), slots.first().start.toMinute())

        val retimed = HashMap<String, TaskBlock>(pending.size)
        pending.forEachIndexed { index, block ->
            val start = cursor.coerceAtMost(LAST_MINUTE)
            val end = (start + block.durationMinutes.toInt()).coerceAtMost(LAST_MINUTE)
            retimed[block.id] = block.copy(start = fromMinute(start), end = fromMinute(end))
            cursor = end + if (index < gapAfter.size) gapAfter[index] else 0
        }
        return ordered.map { retimed[it.id] ?: it }
    }

    private fun LocalTime.toMinute() = hour * 60 + minute

    private fun fromMinute(m: Int) = LocalTime.of(m / 60, m % 60)
}
