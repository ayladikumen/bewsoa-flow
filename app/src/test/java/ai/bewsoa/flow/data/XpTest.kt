package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The earning formulas. These numbers are the economy — a change here should
 * be a deliberate rebalance, and this file is where it becomes visible.
 */
class XpTest {

    private fun block(
        track: Track,
        minutes: Int,
        counted: Boolean = true
    ) = TaskBlock(
        id = "t",
        title = "T",
        track = track,
        start = LocalTime.of(9, 0),
        end = LocalTime.of(9, 0).plusMinutes(minutes.toLong()),
        counted = counted
    )

    // blockXp ------------------------------------------------------------------

    @Test
    fun `a 90-minute YKS block pays 35`() {
        // (10 base + 6 quarter-hours * 3) * 1.25 = 35
        assertEquals(35, Xp.blockXp(block(Track.YKS, 90)))
    }

    @Test
    fun `a 60-minute gym block pays 22`() {
        // (10 + 4*3) * 1.0 = 22
        assertEquals(22, Xp.blockXp(block(Track.GYM, 60)))
    }

    @Test
    fun `mission tracks outrank equal-length maintenance`() {
        assertTrue(Xp.blockXp(block(Track.SAT, 60)) > Xp.blockXp(block(Track.GYM, 60)))
        assertTrue(Xp.blockXp(block(Track.PROJECT, 60)) > Xp.blockXp(block(Track.GYM, 60)))
    }

    @Test
    fun `meals and free time pay nothing`() {
        assertEquals(0, Xp.blockXp(block(Track.MEAL, 30)))
        assertEquals(0, Xp.blockXp(block(Track.FREE, 120)))
    }

    @Test
    fun `a non-counted block pays nothing regardless of track`() {
        assertEquals(0, Xp.blockXp(block(Track.YKS, 90, counted = false)))
    }

    @Test
    fun `one absurd block cannot mint a level - the cap holds`() {
        // 8h YKS: (10 + 96) * 1.25 = 132.5 → capped.
        assertEquals(Xp.BLOCK_XP_CAP, Xp.blockXp(block(Track.YKS, 480)))
    }

    // taskXp -------------------------------------------------------------------

    @Test
    fun `an unestimated task still pays the floor`() {
        assertEquals(6, Xp.taskXp(0))
    }

    @Test
    fun `task pay grows with the estimate but stays under block pay`() {
        assertEquals(8, Xp.taskXp(15))
        assertEquals(14, Xp.taskXp(60))
        // A monster estimate caps below what a modest YKS block pays.
        assertEquals(40, Xp.taskXp(600))
        assertTrue(Xp.taskXp(600) < Xp.blockXp(block(Track.YKS, 90)) + 10)
    }

    // focusXp ------------------------------------------------------------------

    @Test
    fun `focus pays 4 per completed 10 minutes`() {
        assertEquals(8, Xp.focusXp(25))
        assertEquals(20, Xp.focusXp(50))
        assertEquals(36, Xp.focusXp(90))
    }

    @Test
    fun `focus sessions under the anti-farm floor pay nothing`() {
        assertEquals(0, Xp.focusXp(4))
        assertEquals(0, Xp.focusXp(9))
    }

    @Test
    fun `focus pay caps per session`() {
        assertEquals(60, Xp.focusXp(400))
    }

    // dailyGoal ----------------------------------------------------------------

    @Test
    fun `the goal is 80 percent of a perfect day rounded to tens`() {
        // Perfect day: 35 + 22 = 57 → 45.6 → 50... below the 40 floor? No: 50.
        val blocks = listOf(block(Track.YKS, 90), block(Track.GYM, 60))
        assertEquals(50, Xp.dailyGoal(blocks))
    }

    @Test
    fun `a tiny day still asks for the floor`() {
        assertEquals(40, Xp.dailyGoal(listOf(block(Track.GYM, 30))))
        assertEquals(40, Xp.dailyGoal(emptyList()))
    }

    @Test
    fun `a monster day is capped so the goal stays reachable`() {
        val blocks = List(10) { block(Track.YKS, 240) }
        assertEquals(300, Xp.dailyGoal(blocks))
    }

    @Test
    fun `skipped-for-xp blocks like meals do not raise the goal`() {
        val withMeals = listOf(block(Track.YKS, 90), block(Track.MEAL, 60, counted = false))
        assertEquals(Xp.dailyGoal(listOf(block(Track.YKS, 90))), Xp.dailyGoal(withMeals))
    }

    // milestones -----------------------------------------------------------------

    @Test
    fun `milestones pay on the named days and nothing between`() {
        assertEquals(50, Xp.milestoneXp(7))
        assertEquals(100, Xp.milestoneXp(30))
        assertEquals(250, Xp.milestoneXp(100))
        assertEquals(500, Xp.milestoneXp(365))
        assertEquals(0, Xp.milestoneXp(8))
    }
}
