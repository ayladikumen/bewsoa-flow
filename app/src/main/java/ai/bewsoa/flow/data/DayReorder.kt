package ai.bewsoa.flow.data

import java.time.LocalTime

/**
 * Turns a drag on the Day checklist into concrete clock times.
 *
 * The checklist hides its timestamps, but alarms, the NOW pill and the
 * "ends in" countdown still run on them — so a reorder has to produce times
 * that mean what the user meant: "this is what I'm doing next, from now".
 *
 * Only logged history — done or skipped blocks — keeps its original times.
 * Everything else is still on the checklist, and an unchecked row reads as
 * "still to do" no matter what its hidden clock says; a block that quietly
 * ended unlogged reflows with the rest. Pending blocks are re-laid in the
 * dragged order, each with its own duration, starting from now — or from the
 * day's first pending slot if the day hasn't reached it yet — keeping the
 * breathing room the original plan had between blocks.
 */
object DayReorder {

    private const val LAST_MINUTE = 23 * 60 + 59

    /**
     * [plannedMinutes] maps block id → the standing program's duration. A block
     * squeezed against midnight by an earlier drag (its end pinned at 23:59)
     * reads its length from here, so truncation never compounds across drags.
     */
    fun retime(
        ordered: List<TaskBlock>,
        doneIds: Set<String>,
        skippedIds: Set<String>,
        now: LocalTime,
        plannedMinutes: Map<String, Int> = emptyMap()
    ): List<TaskBlock> {
        val pending = ordered.filterNot { it.id in doneIds || it.id in skippedIds }
        if (pending.isEmpty()) return ordered

        fun lengthOf(block: TaskBlock): Int {
            val stored = block.durationMinutes.toInt()
            // end == 23:59 is the truncation fingerprint — a deliberate edit
            // (AI or program) never needs restoring, a midnight squeeze does.
            val planned = plannedMinutes[block.id] ?: return stored
            return if (block.end.toMinute() == LAST_MINUTE && planned > stored) planned
            else stored
        }

        // The original pending slots, in clock order. Their spacing is kept by
        // position: the gap after the 1st slot follows the 1st re-laid block,
        // whichever block that now is.
        val slots = pending.sortedBy { it.start }
        var gapAfter = List(slots.size - 1) { i ->
            (slots[i + 1].start.toMinute() - slots[i].end.toMinute()).coerceAtLeast(0)
        }

        // A drag mid-block keeps that block's elapsed time: if the first thing
        // in the new order was already running, its start doesn't move.
        val first = pending.first()
        val cursorStart =
            if (first.start <= now && now < first.end) first.start.toMinute()
            else maxOf(now.toMinute(), slots.first().start.toMinute())

        // Breathing room is a luxury: when the evening is already too short for
        // the remaining blocks, gaps go first — durations are squeezed only by
        // midnight itself.
        val total = pending.sumOf { lengthOf(it) } + gapAfter.sum()
        if (cursorStart + total > LAST_MINUTE) gapAfter = List(gapAfter.size) { 0 }

        var cursor = cursorStart

        val retimed = HashMap<String, TaskBlock>(pending.size)
        pending.forEachIndexed { index, block ->
            val start = cursor.coerceAtMost(LAST_MINUTE)
            val end = (start + lengthOf(block)).coerceAtMost(LAST_MINUTE)
            retimed[block.id] = block.copy(start = fromMinute(start), end = fromMinute(end))
            cursor = end + if (index < gapAfter.size) gapAfter[index] else 0
        }
        return ordered.map { retimed[it.id] ?: it }
    }

    private fun LocalTime.toMinute() = hour * 60 + minute

    private fun fromMinute(m: Int) = LocalTime.of(m / 60, m % 60)
}
