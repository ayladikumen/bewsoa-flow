package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/**
 * A drag means "this is what I'm doing next, from now": history keeps its
 * times, pending blocks reflow from the current moment with their own
 * durations, and the countdown on the new top block reads its full length.
 */
class DayReorderTest {

    private fun block(id: String, start: String, end: String) = TaskBlock(
        id = id,
        title = id,
        track = Track.YKS,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end)
    )

    @Test
    fun `before the day starts, the plan reflows from its original start`() {
        val a = block("a", "09:00", "11:00") // 2h
        val b = block("b", "11:30", "12:30") // 1h, after a 30m gap
        val result = DayReorder.retime(
            ordered = listOf(b, a),
            doneIds = emptySet(),
            skippedIds = emptySet(),
            now = LocalTime.of(7, 0)
        )
        // b leads with its own 1h, the 30m gap stays, a keeps its 2h;
        // the day still starts at 09:00 and ends at 12:30.
        assertEquals(LocalTime.of(9, 0), result[0].start)
        assertEquals(LocalTime.of(10, 0), result[0].end)
        assertEquals(LocalTime.of(10, 30), result[1].start)
        assertEquals(LocalTime.of(12, 30), result[1].end)
    }

    @Test
    fun `mid-day, the dragged block starts now with its full duration`() {
        val done = block("done", "09:00", "11:00")
        val a = block("a", "14:00", "15:00")
        val b = block("b", "20:00", "22:00") // dragged up to do next
        val result = DayReorder.retime(
            ordered = listOf(done, b, a),
            doneIds = setOf("done"),
            skippedIds = emptySet(),
            now = LocalTime.of(13, 30)
        )
        // History untouched.
        assertEquals(LocalTime.of(9, 0), result[0].start)
        assertEquals(LocalTime.of(11, 0), result[0].end)
        // b takes the next pending slot's start (the 13:30–14:00 break is not
        // eaten) and runs its own 2h — the countdown shows 2h, not 1h.
        assertEquals(LocalTime.of(14, 0), result[1].start)
        assertEquals(LocalTime.of(16, 0), result[1].end)
        // a follows with its own hour (plus the original 5h gap, by position).
        assertEquals(60, result[2].durationMinutes.toInt())
    }

    @Test
    fun `a running block kept on top keeps its elapsed time`() {
        val running = block("running", "13:00", "15:00")
        val later = block("later", "16:00", "17:00")
        val result = DayReorder.retime(
            ordered = listOf(running, later),
            doneIds = emptySet(),
            skippedIds = emptySet(),
            now = LocalTime.of(13, 40)
        )
        // The countdown keeps ticking from 13:00 instead of restarting.
        assertEquals(LocalTime.of(13, 0), result[0].start)
        assertEquals(LocalTime.of(15, 0), result[0].end)
    }

    @Test
    fun `skipped blocks are left alone`() {
        val skip = block("skip", "18:00", "19:00")
        val a = block("a", "20:00", "21:00")
        val result = DayReorder.retime(
            ordered = listOf(a, skip),
            doneIds = emptySet(),
            skippedIds = setOf("skip"),
            now = LocalTime.of(17, 0)
        )
        assertEquals(LocalTime.of(18, 0), result.first { it.id == "skip" }.start)
        // a is the only pending block and starts from its original slot's start
        // — the day hasn't reached 20:00, but a drag says "do it next", so it
        // reflows to the earliest pending slot, never earlier than now.
        assertEquals(LocalTime.of(20, 0), result.first { it.id == "a" }.start)
    }

    @Test
    fun `late-night reflow clamps at 23_59 instead of wrapping`() {
        val a = block("a", "22:50", "23:50") // 1h, in progress
        val b = block("b", "23:50", "23:59")
        val result = DayReorder.retime(
            ordered = listOf(b, a),
            doneIds = emptySet(),
            skippedIds = emptySet(),
            now = LocalTime.of(23, 0)
        )
        // b runs 23:00–23:09; a's own hour would cross midnight → clamped.
        assertEquals(LocalTime.of(23, 9), result[0].end)
        assertEquals(LocalTime.of(23, 9), result[1].start)
        assertEquals(LocalTime.of(23, 59), result[1].end)
    }
}
