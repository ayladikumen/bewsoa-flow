package ai.bewsoa.flow.data

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The one-time day-edit layer: the assistant's "do it once" contract depends
 * on this override winning over the standing program for exactly one date and
 * never leaking into the rest of the week.
 */
class DayOverridesTest {

    // DayOverrides and CustomProgram are process-wide objects — reset both so
    // one test's state can't bleed into the next.
    @After
    fun reset() {
        DayOverrides.load(null)
        CustomProgram.clear()
    }

    private fun overrideJson(date: LocalDate): String {
        val block = BlockCodec.toJson(
            TaskBlock(
                id = "x_wedding",
                title = "Wedding",
                track = Track.FREE,
                start = LocalTime.of(19, 0),
                end = LocalTime.of(23, 0),
                counted = false
            )
        )
        return "{\"$date\": ${JSONArray().put(block)}}"
    }

    @Test
    fun `override replaces exactly its own date`() {
        val date = LocalDate.now()
        DayOverrides.load(overrideJson(date))

        val today = WeeklyProgram.blocksFor(date)
        assertEquals(listOf("x_wedding"), today.map { it.id })

        // The rest of the week still serves the standing program.
        val tomorrow = WeeklyProgram.blocksFor(date.plusDays(1))
        assertTrue(tomorrow.size > 1)
        assertTrue(tomorrow.none { it.id == "x_wedding" })
    }

    @Test
    fun `override beats a custom program on its date`() {
        val date = LocalDate.now()
        val dayName = date.dayOfWeek.name
        val custom = """
            {"days": {"$dayName": [
              {"id": "c1", "title": "Custom", "track": "YKS",
               "start": "09:00", "end": "10:00", "note": "", "counted": true}
            ]}}
        """.trimIndent()
        CustomProgram.activate(custom)
        assertEquals(listOf("c1"), WeeklyProgram.blocksFor(date).map { it.id })

        DayOverrides.load(overrideJson(date))
        assertEquals(listOf("x_wedding"), WeeklyProgram.blocksFor(date).map { it.id })
    }

    @Test
    fun `clearing restores the standing program`() {
        val date = LocalDate.now()
        DayOverrides.load(overrideJson(date))
        assertTrue(DayOverrides.hasOverride(date))

        DayOverrides.load(null)
        assertNull(DayOverrides.forDate(date))
        assertTrue(WeeklyProgram.blocksFor(date).none { it.id == "x_wedding" })
    }

    @Test
    fun `garbage json loads as no overrides`() {
        DayOverrides.load("{not json")
        assertNull(DayOverrides.forDate(LocalDate.now()))
    }

    @Test
    fun `blocks come back sorted by start time`() {
        val date = LocalDate.now()
        val a = BlockCodec.toJson(
            TaskBlock("late", "Late", Track.YKS, LocalTime.of(20, 0), LocalTime.of(21, 0))
        )
        val b = BlockCodec.toJson(
            TaskBlock("early", "Early", Track.YKS, LocalTime.of(9, 0), LocalTime.of(10, 0))
        )
        DayOverrides.load("{\"$date\": ${JSONArray().put(a).put(b)}}")
        assertEquals(listOf("early", "late"), DayOverrides.forDate(date)?.map { it.id })
    }
}
