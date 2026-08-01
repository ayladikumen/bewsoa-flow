package ai.bewsoa.flow.data

import java.time.LocalTime

/**
 * "What's on right now, and what's after it."
 *
 * The same four lines were being rewritten at every call site that needed the
 * live block — the home hero, the day list, the Now widget, the motivation
 * worker — and now the Exact Hour mirror needs it too. One definition means
 * the wall clock and the widget can never disagree about which block is
 * running.
 *
 * Ticked-off and excused blocks are invisible here by design: once you've
 * marked it done, it is no longer what you should be doing.
 */
object CurrentBlock {

    fun at(
        blocks: List<TaskBlock>,
        now: LocalTime,
        doneIds: Set<String> = emptySet(),
        skippedIds: Set<String> = emptySet()
    ): TaskBlock? = blocks.firstOrNull {
        now >= it.start && now < it.end && it.id !in doneIds && it.id !in skippedIds
    }

    fun next(
        blocks: List<TaskBlock>,
        now: LocalTime,
        doneIds: Set<String> = emptySet(),
        skippedIds: Set<String> = emptySet()
    ): TaskBlock? = blocks.firstOrNull {
        it.start > now && it.id !in doneIds && it.id !in skippedIds
    }
}
