package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The draft card's change list — what the user reads before deciding Apply.
 * It has to name adds, drops, moves and renames, and say "nothing" honestly.
 */
class AssistantDiffTest {

    private fun block(
        id: String,
        title: String = id,
        start: Int = 9,
        end: Int = 10
    ) = TaskBlock(id, title, Track.YKS, LocalTime.of(start, 0), LocalTime.of(end, 0))

    @Test
    fun `identical days say nothing changed`() {
        val day = listOf(block("a"), block("b", start = 10, end = 11))
        assertEquals(
            listOf("Same plan — nothing would change."),
            AiAssistant.dayChanges(day, day)
        )
    }

    @Test
    fun `adds drops and moves each get a line`() {
        val before = listOf(
            block("keep"),
            block("gone", title = "Old block"),
            block("moved", title = "Gym", start = 17, end = 18)
        )
        val after = listOf(
            block("keep"),
            block("moved", title = "Gym", start = 18, end = 19),
            block("x_new", title = "Wedding", start = 20, end = 23)
        )
        val changes = AiAssistant.dayChanges(before, after)
        assertEquals(3, changes.size)
        assertTrue(changes.any { it.startsWith("＋ Wedding") })
        assertTrue(changes.any { it == "− Old block" })
        assertTrue(changes.any { it.contains("Gym") && it.contains("17:00–18:00 → 18:00–19:00") })
    }

    @Test
    fun `a rename reads as a rename, not a move`() {
        val before = listOf(block("a", title = "SAT drill"))
        val after = listOf(block("a", title = "SAT reading"))
        assertEquals(listOf("“SAT drill” → “SAT reading”"), AiAssistant.dayChanges(before, after))
    }
}
