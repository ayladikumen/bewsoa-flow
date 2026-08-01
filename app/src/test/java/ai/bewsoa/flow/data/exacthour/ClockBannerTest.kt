package ai.bewsoa.flow.data.exacthour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A block finishing and the next one starting are the same moment. The clock
 * only gets one overlay out of it, so that overlay has to carry both halves —
 * otherwise the next countdown's reset() wipes the celebration before anyone
 * reads it.
 */
class ClockBannerTest {

    private fun countdown(label: String) = ClockPlan.Countdown(
        label = label,
        minutes = 45,
        seconds = 0,
        source = ClockSource.BLOCK,
        endsAtEpochSecond = 54_000L
    )

    @Test
    fun `finishing one block and starting the next reads as one line`() {
        val banner = ClockMirror.banner("Deep work DONE", countdown("Gym"))
        assertEquals("Deep work DONE - Gym", banner)
    }

    @Test
    fun `ticking a block off early says only that`() {
        // The plan hasn't changed — you're still inside the same block — so the
        // mirror sends the flourish alone and never touches the timer.
        assertEquals("Deep work DONE", ClockMirror.banner("Deep work DONE", ClockPlan.Clear))
    }

    @Test
    fun `a plain block change announces just the new block`() {
        assertEquals("Gym", ClockMirror.banner(null, countdown("Gym")))
    }

    @Test
    fun `characters the matrix can't draw are removed, not left as gaps`() {
        // The device substitutes a blank for every glyph it lacks, so an emoji
        // would arrive as a hole in the middle of the message.
        assertEquals("Gym", ClockMirror.banner(null, countdown("🏋️ Gym")))
        assertEquals(
            "Deep work DONE - Morning study",
            ClockMirror.banner("✓ Deep work DONE", countdown("📚 Morning study"))
        )
    }

    @Test
    fun `nothing to say means no text call at all`() {
        assertNull(ClockMirror.banner(null, ClockPlan.Clear))
        assertNull(ClockMirror.banner("", ClockPlan.Clear))
        assertNull(ClockMirror.banner("   ", ClockPlan.Clear))
    }

    @Test
    fun `a marquee plan announces its own text`() {
        val plan = ClockPlan.Marquee("Morning study", ClockSource.BLOCK)
        assertEquals("Morning study", ClockMirror.banner(null, plan))
        assertEquals("Gym DONE - Morning study", ClockMirror.banner("Gym DONE", plan))
    }

    @Test
    fun `an over-long pair is trimmed here rather than by the firmware`() {
        val banner = ClockMirror.banner("${"x".repeat(100)} DONE", countdown("y".repeat(100)))
        assertTrue(requireNotNull(banner).length <= TextOverlay.MAX_CHARS)
    }
}
