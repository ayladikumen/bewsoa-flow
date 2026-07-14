package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The streak and freeze rules. None of this was testable before the pure
 * [Streak] split — computeStreak read the DB and (in the original design) would
 * also have written freezes, from widgets and workers alike.
 */
class StreakTest {

    private val a = TaskBlock("a", "A", Track.YKS, LocalTime.of(9, 0), LocalTime.of(10, 0))
    private val b = TaskBlock("b", "B", Track.SAT, LocalTime.of(11, 0), LocalTime.of(12, 0))
    private val meal = TaskBlock(
        "meal", "Meal", Track.MEAL, LocalTime.of(12, 0), LocalTime.of(12, 30), counted = false
    )

    /** Two counted blocks a day, every day — so 1 of 2 = 50% is a miss, 2 of 2 is kept. */
    private val blocksFor: (LocalDate) -> List<TaskBlock> = { listOf(a, b, meal) }

    private val today: LocalDate = LocalDate.of(2026, 7, 15)

    private fun row(date: LocalDate, id: String, state: CompletionState) =
        TaskCompletionEntity(
            date = date.toString(),
            taskId = id,
            state = state.name,
            completedAt = if (state == CompletionState.DONE) 1L else null
        )

    /** Marks [days] consecutive days ending yesterday as fully done. */
    private fun keptDays(days: Int, endingAt: LocalDate): List<TaskCompletionEntity> =
        (0 until days).flatMap { i ->
            val d = endingAt.minusDays(i.toLong())
            listOf(row(d, "a", CompletionState.DONE), row(d, "b", CompletionState.DONE))
        }

    private fun compute(
        rows: List<TaskCompletionEntity>,
        freezesUsed: Set<LocalDate> = emptySet()
    ) = Streak.compute(today, rows, freezesUsed, blocksFor)

    // Keeping ----------------------------------------------------------------

    @Test
    fun `no history means no streak`() {
        assertEquals(0, compute(emptyList()).current)
    }

    @Test
    fun `consecutive fully-done days build the streak`() {
        assertEquals(3, compute(keptDays(3, today.minusDays(1))).current)
    }

    @Test
    fun `today joins the streak once it is kept`() {
        val rows = keptDays(3, today.minusDays(1)) + keptDays(1, today)
        assertEquals(4, compute(rows).current)
        assertTrue(compute(rows).todayKept)
    }

    @Test
    fun `half a day is below the threshold and breaks the streak`() {
        // 1 of 2 = 50%, under KEEP_THRESHOLD of 60%.
        val rows = keptDays(3, today.minusDays(2)) +
            listOf(row(today.minusDays(1), "a", CompletionState.DONE))
        assertFalse(compute(rows).yesterdayKept)
        assertEquals(0, compute(rows).current)
    }

    @Test
    fun `a day with no counted blocks is kept - a free day is not a failure`() {
        val info = Streak.compute(today, keptDays(1, today.minusDays(1)), emptySet()) { listOf(meal) }
        assertTrue(info.yesterdayKept)
    }

    @Test
    fun `an excused day is kept even with nothing done`() {
        val rows = keptDays(3, today.minusDays(2)) + listOf(
            row(today.minusDays(1), "a", CompletionState.SKIPPED),
            row(today.minusDays(1), "b", CompletionState.SKIPPED)
        )
        assertTrue(compute(rows).yesterdayKept)
        assertEquals(4, compute(rows).current)
    }

    @Test
    fun `uncounted blocks never affect keeping`() {
        val rows = keptDays(1, today.minusDays(1)) +
            listOf(row(today.minusDays(1), "meal", CompletionState.PENDING))
        assertTrue(compute(rows).yesterdayKept)
    }

    // Freeze earning ---------------------------------------------------------

    @Test
    fun `one freeze is earned every seven kept days`() {
        assertEquals(0, Streak.freezesAvailable(totalKeptDays = 6, freezesUsed = 0))
        assertEquals(1, Streak.freezesAvailable(totalKeptDays = 7, freezesUsed = 0))
        assertEquals(2, Streak.freezesAvailable(totalKeptDays = 14, freezesUsed = 0))
    }

    @Test
    fun `freezes are capped at two - insurance, not a bank`() {
        assertEquals(2, Streak.freezesAvailable(totalKeptDays = 700, freezesUsed = 0))
    }

    @Test
    fun `spent freezes are deducted from what is available`() {
        assertEquals(1, Streak.freezesAvailable(totalKeptDays = 14, freezesUsed = 1))
        assertEquals(0, Streak.freezesAvailable(totalKeptDays = 14, freezesUsed = 2))
    }

    @Test
    fun `available never goes negative`() {
        assertEquals(0, Streak.freezesAvailable(totalKeptDays = 0, freezesUsed = 5))
    }

    @Test
    fun `a fresh install earns no freezes from the empty days before it`() {
        // The trap: empty days count as kept, so measuring from the 90-day
        // lookback rather than the first real record would hand out free freezes.
        val rows = keptDays(1, today.minusDays(1))
        assertEquals(0, compute(rows).freezesAvailable)
    }

    // Freeze spending --------------------------------------------------------

    @Test
    fun `no freeze is proposed when yesterday was kept`() {
        assertNull(compute(keptDays(10, today.minusDays(1))).freezeToConsume)
    }

    @Test
    fun `a held freeze rescues a broken yesterday`() {
        // 8 kept days (earns 1 freeze), then yesterday missed entirely.
        val rows = keptDays(8, today.minusDays(2))
        val info = compute(rows)
        assertEquals(today.minusDays(1), info.freezeToConsume)
        assertTrue("the rescued day must read as kept", info.yesterdayKept)
        assertEquals(9, info.current)
    }

    @Test
    fun `no freeze without one available`() {
        // Only 3 kept days — a streak worth saving, but nothing to save it with.
        val info = compute(keptDays(3, today.minusDays(2)))
        assertNull(info.freezeToConsume)
        assertFalse(info.yesterdayKept)
    }

    @Test
    fun `a freeze is not wasted on a trivial streak`() {
        // 14 kept days ending well in the past (so a freeze is banked), then a
        // gap, then only 2 kept days before yesterday's miss. Below
        // FREEZE_MIN_STREAK, so the freeze is held for something that matters.
        val rows = keptDays(14, today.minusDays(20)) + keptDays(2, today.minusDays(2))
        val info = compute(rows)
        assertNull(info.freezeToConsume)
    }

    @Test
    fun `an already-consumed freeze makes that day kept and is not re-spent`() {
        val rows = keptDays(8, today.minusDays(2))
        val info = compute(rows, freezesUsed = setOf(today.minusDays(1)))
        assertTrue(info.yesterdayKept)
        assertNull("the freeze was already spent on that day", info.freezeToConsume)
        assertEquals(9, info.current)
    }

    @Test
    fun `compute never proposes a freeze for any day but yesterday`() {
        // A freeze is a rescue, not a rewrite of history.
        val rows = keptDays(20, today.minusDays(5))
        val info = compute(rows)
        assertTrue(info.freezeToConsume == null || info.freezeToConsume == today.minusDays(1))
    }
}
