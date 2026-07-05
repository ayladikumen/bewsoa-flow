package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ProgramDiffTest {

    private fun block(
        id: String,
        title: String = id,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(10, 0)
    ) = TaskBlock(id, title, Track.YKS, start, end)

    private fun week(vararg blocks: TaskBlock): Map<DayOfWeek, List<TaskBlock>> =
        DayOfWeek.entries.associateWith { blocks.toList() }

    @Test
    fun `identical programs report nothing changed`() {
        val program = week(block("a"))
        assertEquals(
            listOf("Same schedule — nothing changed."),
            ProgramDiff.summarize(program, program)
        )
    }

    @Test
    fun `weekday-wide time change is grouped as Mon to Fri`() {
        val old = week(block("gym", "Gym", LocalTime.of(17, 0), LocalTime.of(18, 45)))
        val new = old.mapValues { (day, blocks) ->
            if (day in DayOfWeek.entries.take(5)) {
                blocks.map { it.copy(start = LocalTime.of(18, 0), end = LocalTime.of(19, 45)) }
            } else blocks
        }
        val lines = ProgramDiff.summarize(old, new)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("Gym"))
        assertTrue(lines[0].contains("17:00–18:45 → 18:00–19:45"))
        assertTrue(lines[0].contains("Mon–Fri"))
    }

    @Test
    fun `added and removed blocks are listed with signs`() {
        val old = week(block("a", "Old block"))
        val new = week(block("b", "New block"))
        val lines = ProgramDiff.summarize(old, new)
        assertTrue(lines.any { it.startsWith("+ New block") && it.contains("every day") })
        assertTrue(lines.any { it.startsWith("− Old block") })
    }

    @Test
    fun `long change lists are capped at eight lines`() {
        val old = week(*(1..12).map { block("b$it", "Block $it") }.toTypedArray())
        val new = week(*(1..12).map {
            block("b$it", "Block $it", LocalTime.of(11, 0), LocalTime.of(12, 0))
        }.toTypedArray())
        val lines = ProgramDiff.summarize(old, new)
        assertEquals(9, lines.size)
        assertTrue(lines.last().contains("more"))
    }
}
