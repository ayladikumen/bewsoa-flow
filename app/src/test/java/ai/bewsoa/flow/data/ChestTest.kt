package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The weekly chest's two deliberate differences from the streak: it is blind
 * to freezes, and a day of pure excuses is not progress. The chest pays for
 * work that happened.
 */
class ChestTest {

    private val a = TaskBlock("a", "A", Track.YKS, LocalTime.of(9, 0), LocalTime.of(10, 0))
    private val b = TaskBlock("b", "B", Track.SAT, LocalTime.of(11, 0), LocalTime.of(12, 0))
    private val blocksFor: (LocalDate) -> List<TaskBlock> = { listOf(a, b) }

    /** A Monday, so the whole week is deterministic. */
    private val weekStart: LocalDate = LocalDate.of(2026, 7, 13)

    private fun row(date: LocalDate, id: String, state: CompletionState) =
        TaskCompletionEntity(
            date = date.toString(),
            taskId = id,
            state = state.name,
            completedAt = if (state == CompletionState.DONE) 1L else null
        )

    private fun fullDay(date: LocalDate) =
        listOf(row(date, "a", CompletionState.DONE), row(date, "b", CompletionState.DONE))

    // Rewards -----------------------------------------------------------------

    @Test
    fun `locked below five days then 150 plus 25 per extra day`() {
        assertEquals(0, Xp.chestReward(4))
        assertEquals(150, Xp.chestReward(5))
        assertEquals(175, Xp.chestReward(6))
        assertEquals(200, Xp.chestReward(7))
    }

    // Kept-day counting ----------------------------------------------------------

    @Test
    fun `fully done days count`() {
        val rows = (0L..4L).flatMap { fullDay(weekStart.plusDays(it)) }
        assertEquals(5, Xp.chestKeptDays(weekStart, weekStart.plusDays(6), rows, blocksFor))
    }

    @Test
    fun `one of two done is under the sixty percent rule`() {
        val rows = listOf(row(weekStart, "a", CompletionState.DONE))
        assertEquals(0, Xp.chestKeptDays(weekStart, weekStart.plusDays(6), rows, blocksFor))
    }

    @Test
    fun `future days cannot count even if rows exist`() {
        val today = weekStart.plusDays(1)
        val rows = fullDay(weekStart) + fullDay(weekStart.plusDays(3))
        assertEquals(1, Xp.chestKeptDays(weekStart, today, rows, blocksFor))
    }

    @Test
    fun `a freeze-rescued day is still an unworked day to the chest`() {
        // The streak would forgive this gap with a freeze; the chest has no
        // freeze input at all — day 2 simply has no work in it.
        val rows = fullDay(weekStart) + fullDay(weekStart.plusDays(2))
        assertEquals(2, Xp.chestKeptDays(weekStart, weekStart.plusDays(6), rows, blocksFor))
    }

    @Test
    fun `skipping everything keeps the streak but earns no chest progress`() {
        val skippedDay = listOf(
            row(weekStart, "a", CompletionState.SKIPPED),
            row(weekStart, "b", CompletionState.SKIPPED)
        )
        // Sanity: the streak's kept() would call this day kept.
        val streak = Streak.compute(weekStart.plusDays(1), skippedDay, emptySet(), blocksFor)
        assertTrue(streak.yesterdayKept)
        // The chest does not.
        assertEquals(0, Xp.chestKeptDays(weekStart, weekStart.plusDays(6), skippedDay, blocksFor))
    }

    @Test
    fun `an excused block still leaves the denominator for the day that kept it`() {
        // Skip one of two, do the other: 1 of 1 effective = kept.
        val rows = listOf(
            row(weekStart, "a", CompletionState.SKIPPED),
            row(weekStart, "b", CompletionState.DONE)
        )
        assertEquals(1, Xp.chestKeptDays(weekStart, weekStart.plusDays(6), rows, blocksFor))
    }

    // State machine ----------------------------------------------------------------

    @Test
    fun `unlocks at five kept days and claims once`() {
        val locked = ChestState(weekStart, keptDays = 4, opened = false, expired = false)
        assertFalse(locked.unlocked)
        assertFalse(locked.claimable)

        val ready = ChestState(weekStart, keptDays = 5, opened = false, expired = false)
        assertTrue(ready.claimable)
        assertEquals(150, ready.reward)

        val opened = ready.copy(opened = true)
        assertFalse(opened.claimable)
    }

    @Test
    fun `an expired chest is no longer claimable`() {
        val expired = ChestState(weekStart, keptDays = 6, opened = false, expired = true)
        assertTrue(expired.unlocked)
        assertFalse(expired.claimable)
    }

    @Test
    fun `the claim window is fourteen days past the week's end`() {
        val expiry = Xp.chestExpiry(weekStart)
        assertEquals(weekStart.plusDays(6).plusDays(14), expiry)
    }
}
