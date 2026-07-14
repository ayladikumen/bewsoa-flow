package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The excused-skip contract, at the stats layer.
 *
 * The rule: a skipped block leaves the denominator entirely. It is not a miss
 * (so it can't drag the ratio down) and it is not a win (so it can't push the
 * ratio up). Everything here exists to stop a future change from quietly
 * turning skip back into either.
 *
 * These tests use the real WeeklyProgram via buildWeekStats, so they assert
 * relationships between numbers rather than hard-coded totals.
 */
class SkipDenominatorTest {

    private val monday: LocalDate = LocalDate.of(2026, 7, 13)

    private fun row(date: LocalDate, id: String, state: CompletionState) =
        TaskCompletionEntity(
            date = date.toString(),
            taskId = id,
            state = state.name,
            completedAt = if (state == CompletionState.DONE) 1L else null
        )

    private fun countedOn(date: LocalDate) =
        WeeklyProgram.blocksFor(date).filter { it.counted }

    @Test
    fun `skipping a block removes it from planned without touching done`() {
        val target = countedOn(monday).first()
        val before = buildWeekStats(monday, emptyList()).days.first()
        val after = buildWeekStats(monday, listOf(row(monday, target.id, CompletionState.SKIPPED)))
            .days.first()

        assertEquals(before.planned - 1, after.planned)
        assertEquals(before.done, after.done)
        assertEquals(1, after.skipped)
    }

    @Test
    fun `skipping raises the ratio because the miss leaves the denominator`() {
        val counted = countedOn(monday)
        // Do the first, leave the rest untouched, then excuse one of the misses.
        val done = row(monday, counted[0].id, CompletionState.DONE)
        val missed = buildWeekStats(monday, listOf(done)).days.first()
        val excused = buildWeekStats(
            monday,
            listOf(done, row(monday, counted[1].id, CompletionState.SKIPPED))
        ).days.first()

        assertTrue(
            "excusing a miss should raise the ratio (${missed.ratio} -> ${excused.ratio})",
            excused.ratio > missed.ratio
        )
    }

    @Test
    fun `skipping a block you already did does not inflate the ratio`() {
        val target = countedOn(monday).first()
        val done = buildWeekStats(monday, listOf(row(monday, target.id, CompletionState.DONE)))
            .days.first()
        // Same block, now skipped: it must leave *both* sides, not just planned.
        val skipped = buildWeekStats(monday, listOf(row(monday, target.id, CompletionState.SKIPPED)))
            .days.first()

        assertEquals(done.planned - 1, skipped.planned)
        assertEquals(done.done - 1, skipped.done)
    }

    @Test
    fun `skipping every counted block leaves nothing planned and a zero ratio`() {
        val rows = countedOn(monday).map { row(monday, it.id, CompletionState.SKIPPED) }
        val day = buildWeekStats(monday, rows).days.first()

        assertEquals(0, day.planned)
        assertEquals(0, day.done)
        assertEquals(0f, day.ratio, 0.0001f)
        assertEquals(countedOn(monday).size, day.skipped)
    }

    @Test
    fun `a day of nothing but skips is still kept - it can never be a miss`() {
        val rows = countedOn(monday).map { row(monday, it.id, CompletionState.SKIPPED) }
        val info = Streak.compute(
            today = monday.plusDays(1),
            rows = rows,
            freezesUsed = emptySet(),
            blocksFor = WeeklyProgram::blocksFor
        )
        assertTrue("an entirely excused day must not break the streak", info.yesterdayKept)
    }

    @Test
    fun `track minutes exclude skipped blocks from planned`() {
        val target = countedOn(monday).first()
        val before = buildWeekStats(monday, emptyList())
            .tracks.first { it.track == target.track }
        val after = buildWeekStats(monday, listOf(row(monday, target.id, CompletionState.SKIPPED)))
            .tracks.first { it.track == target.track }

        assertEquals(before.plannedMinutes - target.durationMinutes, after.plannedMinutes)
        assertEquals(before.plannedSessions - 1, after.plannedSessions)
    }

    @Test
    fun `a pending block still counts against you`() {
        // The counterpart to every test above: only SKIPPED is excused.
        // PENDING must stay in the denominator or the feature means nothing.
        val target = countedOn(monday).first()
        val before = buildWeekStats(monday, emptyList()).days.first()
        val after = buildWeekStats(monday, listOf(row(monday, target.id, CompletionState.PENDING)))
            .days.first()

        assertEquals(before.planned, after.planned)
        assertEquals(0, after.skipped)
    }

    @Test
    fun `week totals drop by exactly the skipped blocks`() {
        val target = countedOn(monday).first()
        val before = buildWeekStats(monday, emptyList())
        val after = buildWeekStats(monday, listOf(row(monday, target.id, CompletionState.SKIPPED)))

        assertEquals(before.plannedBlocks - 1, after.plannedBlocks)
    }
}
