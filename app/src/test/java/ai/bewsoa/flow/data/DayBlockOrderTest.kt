package ai.bewsoa.flow.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Dragging reorders the day without stealing time: every block keeps its own
 * duration and the gaps between blocks stay where the program put them, so the
 * day still starts and ends at the same clock times.
 */
class DayBlockOrderTest {

    // DayBlockOrder is a process-wide object — reset it between tests.
    @After
    fun reset() {
        DayBlockOrder.load(null)
    }

    private val date: LocalDate = LocalDate.of(2026, 7, 28)

    private fun block(id: String, start: String, end: String) = TaskBlock(
        id = id,
        title = id,
        track = Track.YKS,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end)
    )

    private fun loadOrder(vararg ids: String) {
        DayBlockOrder.load(
            JSONObject().put(date.toString(), JSONArray(ids.toList())).toString()
        )
    }

    @Test
    fun `blocks keep their own duration when swapped`() {
        // long is 2h, short is 1h, with a 30m gap between them.
        val blocks = listOf(
            block("long", "09:00", "11:00"),
            block("short", "11:30", "12:30")
        )
        loadOrder("short", "long")

        val result = DayBlockOrder.applyTo(blocks, date)

        assertEquals(listOf("short", "long"), result.map { it.id })
        // short still runs 1h, from the day's original start.
        assertEquals(LocalTime.of(9, 0), result[0].start)
        assertEquals(LocalTime.of(10, 0), result[0].end)
        // the 30m gap survives, then long runs its full 2h.
        assertEquals(LocalTime.of(10, 30), result[1].start)
        assertEquals(LocalTime.of(12, 30), result[1].end)
    }

    @Test
    fun `day still ends at the original time for any order`() {
        val blocks = listOf(
            block("a", "08:00", "09:30"),
            block("b", "10:00", "10:45"),
            block("c", "12:00", "14:00")
        )
        loadOrder("c", "a", "b")

        val result = DayBlockOrder.applyTo(blocks, date)

        assertEquals(LocalTime.of(8, 0), result.first().start)
        assertEquals(LocalTime.of(14, 0), result.last().end)
        // Each block's length is untouched.
        assertEquals(
            blocks.associate { it.id to it.durationMinutes },
            result.associate { it.id to it.durationMinutes }
        )
    }

    @Test
    fun `stale order from a changed program is ignored`() {
        val blocks = listOf(
            block("a", "08:00", "09:00"),
            block("b", "09:00", "10:00")
        )
        loadOrder("a", "b", "gone")

        assertEquals(blocks, DayBlockOrder.applyTo(blocks, date))
    }

    @Test
    fun `other dates are untouched`() {
        val blocks = listOf(
            block("a", "08:00", "09:00"),
            block("b", "09:00", "10:00")
        )
        loadOrder("b", "a")

        assertEquals(blocks, DayBlockOrder.applyTo(blocks, date.plusDays(1)))
    }
}
