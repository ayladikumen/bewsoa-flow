package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The level curve: quadratic, monotonic, and never stalls. If someone retunes
 * [Xp.totalXpAtLevel], every expectation here states what they're changing.
 */
class LevelCurveTest {

    @Test
    fun `the curve starts at zero and known anchors hold`() {
        assertEquals(0, Xp.totalXpAtLevel(1))
        assertEquals(100, Xp.totalXpAtLevel(2))
        assertEquals(1000, Xp.totalXpAtLevel(5))
        assertEquals(4500, Xp.totalXpAtLevel(10))
        assertEquals(19000, Xp.totalXpAtLevel(20))
    }

    @Test
    fun `the curve is strictly increasing`() {
        (1..60).zipWithNext().forEach { (lo, hi) ->
            assertTrue(Xp.totalXpAtLevel(lo) < Xp.totalXpAtLevel(hi))
        }
    }

    @Test
    fun `levelFor round-trips the curve exactly`() {
        (1..50).forEach { level ->
            assertEquals(level, Xp.levelFor(Xp.totalXpAtLevel(level)).index)
            // One XP short of the next level is still this level.
            assertEquals(level, Xp.levelFor(Xp.totalXpAtLevel(level + 1) - 1).index)
        }
    }

    @Test
    fun `zero and negative totals are level 1 with zero progress`() {
        assertEquals(1, Xp.levelFor(0).index)
        assertEquals(0, Xp.levelFor(0).xpIntoLevel)
        assertEquals(1, Xp.levelFor(-50).index)
    }

    @Test
    fun `xpIntoLevel plus the floor reconstructs the total`() {
        val info = Xp.levelFor(1234)
        assertEquals(1234, Xp.totalXpAtLevel(info.index) + info.xpIntoLevel)
    }

    @Test
    fun `progress stays inside the unit interval`() {
        listOf(0, 99, 100, 999, 1000, 12345, 100000).forEach { xp ->
            val p = Xp.levelFor(xp).progress
            assertTrue(p in 0f..1f)
        }
    }

    @Test
    fun `the twelve named titles land on their levels`() {
        assertEquals("Spark", Xp.levelFor(0).title)
        assertEquals("Kindling", Xp.levelFor(100).title)
        assertEquals("Wildfire", Xp.titleFor(12))
    }

    @Test
    fun `beyond the names the fire keeps counting in roman numerals`() {
        assertEquals("Wildfire II", Xp.titleFor(13))
        assertEquals("Wildfire III", Xp.titleFor(14))
        assertEquals("Wildfire XI", Xp.titleFor(22))
    }
}
