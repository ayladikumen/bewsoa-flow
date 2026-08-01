package ai.bewsoa.flow.data.exacthour

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The device clamps all of this itself and never rejects anything. Clamping on
 * this side too means the app never shows a number the clock won't honour.
 */
class ClockLimitsTest {

    @Test
    fun `text is trimmed to what the matrix can hold`() {
        val long = TextOverlay("a".repeat(500)).sanitized()
        assertEquals(TextOverlay.MAX_CHARS, long.text.length)
    }

    @Test
    fun `speed, hold and repeat are pulled into range`() {
        val wild = TextOverlay("hi", speed = 999.0, repeat = 1_000, hold = 0.0).sanitized()
        assertEquals(TextOverlay.MAX_SPEED, wild.speed, 0.001)
        assertEquals(TextOverlay.MAX_REPEAT, wild.repeat)
        assertEquals(TextOverlay.MIN_HOLD, wild.hold, 0.001)

        val tiny = TextOverlay("hi", speed = -5.0, repeat = 0, hold = 9_999.0).sanitized()
        assertEquals(TextOverlay.MIN_SPEED, tiny.speed, 0.001)
        assertEquals(TextOverlay.MIN_REPEAT, tiny.repeat)
        assertEquals(TextOverlay.MAX_HOLD, tiny.hold, 0.001)
    }

    @Test
    fun `an unrecognised mode falls back to auto`() {
        assertEquals(TextOverlay.MODE_AUTO, TextOverlay("hi", mode = "sideways").sanitized().mode)
        assertEquals(TextOverlay.MODE_SCROLL, TextOverlay("hi", mode = "scroll").sanitized().mode)
        assertEquals(TextOverlay.MODE_STATIC, TextOverlay("hi", mode = "static").sanitized().mode)
    }

    @Test
    fun `an empty overlay is what clears the display`() {
        assertEquals("", TextOverlay.clear().sanitized().text)
    }

    @Test
    fun `only what the matrix can draw is sent`() {
        // The clock's font has 46 glyphs and turns everything else into a
        // blank, so an emoji would arrive as a hole rather than vanish.
        assertEquals("GYM", TextOverlay.displayable("🏋️ GYM"))
        assertEquals("Deep work", TextOverlay.displayable("✓ Deep work"))
        // The leftover double space collapses, so nothing scrolls past as a gap.
        assertEquals("A B", TextOverlay.displayable("A · B"))
        assertEquals("STRETCH", TextOverlay.displayable("  STRETCH  "))
        // Punctuation the font does have is kept.
        assertEquals("NEXT: GYM - 25:00!", TextOverlay.displayable("NEXT: GYM - 25:00!"))
    }

    @Test
    fun `turkish letters survive, because the device folds them itself`() {
        // "MOLA BİTTİ" has to draw — the device maps İ onto I.
        assertEquals("MOLA BITTI", TextOverlay.displayable("MOLA BİTTİ"))
        assertEquals("calisma", TextOverlay.displayable("çalışma"))
    }

    @Test
    fun `sanitizing runs the text through the same filter`() {
        assertEquals("GYM", TextOverlay("🏋️ GYM").sanitized().text)
    }

    @Test
    fun `the json body carries every field the device reads`() {
        val json = TextOverlay("GO", mode = TextOverlay.MODE_SCROLL).toJson()
        assertEquals("GO", json.getString("text"))
        assertEquals("scroll", json.getString("mode"))
        assertEquals(TextOverlay.DEFAULT_SPEED, json.getDouble("speed"), 0.001)
        assertEquals(TextOverlay.DEFAULT_REPEAT, json.getInt("repeat"))
        assertEquals(TextOverlay.DEFAULT_HOLD, json.getDouble("hold"), 0.001)
    }

    @Test
    fun `minutes and seconds are coerced before they leave`() {
        assertEquals(270, ClockLimits.clampMinutes(9_999))
        assertEquals(0, ClockLimits.clampMinutes(-5))
        assertEquals(25, ClockLimits.clampMinutes(25))
        // A device reporting a different ceiling is trusted over the constant.
        assertEquals(60, ClockLimits.clampMinutes(120, max = 60))

        assertEquals(59, ClockLimits.clampSeconds(90))
        assertEquals(0, ClockLimits.clampSeconds(-1))
    }
}
