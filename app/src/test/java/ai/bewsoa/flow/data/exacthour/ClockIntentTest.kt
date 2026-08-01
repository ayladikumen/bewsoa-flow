package ai.bewsoa.flow.data.exacthour

import ai.bewsoa.flow.data.ActiveFocus
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * What the wall clock shows, and — just as important — when it is left alone.
 *
 * Times here are wall-clock only, matching the schedule; [nowMillis] is a
 * separate parameter purely so a focus session's epoch deadline can be
 * expressed without dragging a real date into the test.
 */
class ClockIntentTest {

    private fun block(id: String, start: String, end: String, track: Track = Track.YKS) =
        TaskBlock(
            id = id,
            title = id,
            track = track,
            start = LocalTime.parse(start),
            end = LocalTime.parse(end)
        )

    private fun resolve(
        focus: ActiveFocus? = null,
        blocks: List<TaskBlock> = emptyList(),
        doneIds: Set<String> = emptySet(),
        skippedIds: Set<String> = emptySet(),
        now: String = "10:00",
        nowMillis: Long = 0L,
        mirrorFocus: Boolean = true,
        mirrorBlocks: Boolean = true
    ) = ClockIntent.resolve(
        focus = focus,
        blocks = blocks,
        doneIds = doneIds,
        skippedIds = skippedIds,
        now = LocalTime.parse(now),
        nowMillis = nowMillis,
        mirrorFocus = mirrorFocus,
        mirrorBlocks = mirrorBlocks
    )

    @Test
    fun `a focus session beats the block it was started inside`() {
        // 20 minutes left on the session, 2 hours left on the block.
        val focus = ActiveFocus("Deep work", startedAt = 0L, plannedMinutes = 30)
        val plan = resolve(
            focus = focus,
            blocks = listOf(block("study", "09:00", "12:00")),
            nowMillis = 10 * 60_000L
        )
        val countdown = plan as ClockPlan.Countdown
        assertEquals(ClockSource.FOCUS, countdown.source)
        assertEquals("Deep work", countdown.label)
        assertEquals(20, countdown.minutes)
    }

    @Test
    fun `an expired focus session falls through to the block`() {
        val focus = ActiveFocus("Deep work", startedAt = 0L, plannedMinutes = 30)
        val plan = resolve(
            focus = focus,
            blocks = listOf(block("study", "09:00", "12:00")),
            now = "10:00",
            // Past the session's end, so it no longer counts.
            nowMillis = 45 * 60_000L
        )
        val countdown = plan as ClockPlan.Countdown
        assertEquals(ClockSource.BLOCK, countdown.source)
        assertEquals(120, countdown.minutes)
    }

    @Test
    fun `the running block becomes a countdown labelled with its title`() {
        val plan = resolve(
            blocks = listOf(block("gym", "10:00", "11:00", Track.GYM)),
            now = "10:30"
        )
        val countdown = plan as ClockPlan.Countdown
        assertEquals(30, countdown.minutes)
        // No track emoji: the matrix has no glyph for one and would draw a gap.
        assertEquals("gym", countdown.label)
    }

    @Test
    fun `a block longer than the device's limit scrolls its title instead of lying`() {
        // The built-in program really does have 8-hour blocks; a countdown
        // truncated to 270 minutes would show the wrong time.
        val plan = resolve(
            blocks = listOf(block("marathon", "08:00", "16:00")),
            now = "08:00"
        )
        assertTrue("expected a marquee, got $plan", plan is ClockPlan.Marquee)
    }

    @Test
    fun `the limit is a boundary, not a cliff`() {
        val long = listOf(block("long", "08:00", "16:00"))
        // Exactly 270 minutes left: still a countdown.
        val atLimit = resolve(blocks = long, now = "11:30")
        assertEquals(270, (atLimit as ClockPlan.Countdown).minutes)
        // One minute more than the device can hold: scroll the title instead.
        assertTrue(resolve(blocks = long, now = "11:29") is ClockPlan.Marquee)
    }

    @Test
    fun `a ticked-off block stops occupying the display`() {
        val plan = resolve(
            blocks = listOf(block("study", "09:00", "12:00")),
            doneIds = setOf("study")
        )
        assertEquals(ClockPlan.Clear, plan)
    }

    @Test
    fun `an excused block stops occupying the display`() {
        val plan = resolve(
            blocks = listOf(block("study", "09:00", "12:00")),
            skippedIds = setOf("study")
        )
        assertEquals(ClockPlan.Clear, plan)
    }

    @Test
    fun `meals and free time are mirrored too`() {
        // Unlike the reminders, the clock doesn't filter on `counted` — a meal
        // countdown is exactly what a wall clock is good at.
        val plan = resolve(
            blocks = listOf(
                TaskBlock("dinner", "Dinner", Track.MEAL, LocalTime.of(19, 0), LocalTime.of(19, 30), counted = false)
            ),
            now = "19:10"
        )
        assertEquals(20, (plan as ClockPlan.Countdown).minutes)
    }

    @Test
    fun `nothing scheduled means a blank display, not a stale countdown`() {
        assertEquals(ClockPlan.Clear, resolve())
        assertEquals(
            ClockPlan.Clear,
            resolve(blocks = listOf(block("morning", "06:00", "07:00")), now = "10:00")
        )
    }

    @Test
    fun `each mirror switch silences its own source`() {
        val focus = ActiveFocus("Deep work", startedAt = 0L, plannedMinutes = 30)
        val blocks = listOf(block("study", "09:00", "12:00"))

        // Focus off: the block shows through.
        assertEquals(
            ClockSource.BLOCK,
            (resolve(focus = focus, blocks = blocks, mirrorFocus = false) as ClockPlan.Countdown).source
        )
        // Blocks off, focus expired: nothing.
        assertEquals(
            ClockPlan.Clear,
            resolve(blocks = blocks, mirrorBlocks = false)
        )
        // Both off.
        assertEquals(
            ClockPlan.Clear,
            resolve(focus = focus, blocks = blocks, mirrorFocus = false, mirrorBlocks = false)
        )
    }

    @Test
    fun `re-resolving the same live block a second later gives the same key`() {
        // This is the regression test for "don't fight the user": the mirror
        // pushes only when the key changes, so a key that moved every second
        // would restart the countdown on every tick.
        val blocks = listOf(block("study", "09:00", "12:00"))
        val first = resolve(blocks = blocks, now = "10:00:00")
        val second = resolve(blocks = blocks, now = "10:00:01")
        assertNotEquals(
            (first as ClockPlan.Countdown).seconds,
            (second as ClockPlan.Countdown).seconds
        )
        assertEquals("the remaining time moved but the key must not", first.key, second.key)
    }

    @Test
    fun `a different block gives a different key, so the announce fires again`() {
        val morning = resolve(blocks = listOf(block("study", "09:00", "12:00")), now = "10:00")
        val afternoon = resolve(blocks = listOf(block("gym", "14:00", "15:00")), now = "14:10")
        assertNotEquals(morning.key, afternoon.key)
    }

    @Test
    fun `a focus session and a block that end at the same moment still differ`() {
        // Source is part of the key, so handing over from one to the other
        // always counts as a change worth pushing.
        val focus = ActiveFocus("study", startedAt = 0L, plannedMinutes = 30)
        val fromFocus = resolve(focus = focus, nowMillis = 0L)
        val fromBlock = resolve(blocks = listOf(block("study", "09:00", "12:00")), now = "10:00")
        assertNotEquals(fromFocus.key, fromBlock.key)
    }
}
