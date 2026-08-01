package ai.bewsoa.flow.data.exacthour

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The device answers 200 to nearly everything and degrades bad input to
 * defaults rather than erroring. A client that throws on a missing field would
 * be stricter than the server it talks to, so every case here has to parse.
 */
class ExactHourParseTest {

    private fun snapshot(body: String) = ClockStatus.from(JSONObject(body))

    @Test
    fun `the device's uppercase state names map onto the enum`() {
        assertEquals(ClockState.IDLE, ClockState.parse("IDLE"))
        assertEquals(ClockState.RUNNING, ClockState.parse("RUNNING"))
        assertEquals(ClockState.PAUSED, ClockState.parse("PAUSED"))
        assertEquals(ClockState.FINISHED, ClockState.parse("FINISHED"))
    }

    @Test
    fun `an unknown state falls back to IDLE instead of throwing`() {
        assertEquals(ClockState.IDLE, ClockState.parse("SOMETHING_NEW"))
        assertEquals(ClockState.IDLE, ClockState.parse(null))
        assertEquals(ClockState.IDLE, ClockState.parse(""))
    }

    @Test
    fun `state parsing survives a Turkish locale`() {
        // In tr-TR "I".lowercase() is "ı", so a valueOf/uppercase round trip
        // would stop matching. The device's own display literally reads BITTI,
        // so this locale is not hypothetical here.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals(ClockState.IDLE, ClockState.parse("IDLE"))
            assertEquals(ClockState.IDLE, ClockState.parse("idle"))
            assertEquals(ClockState.FINISHED, ClockState.parse("finished"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `a full snapshot round trips`() {
        val status = snapshot(
            """
            {"state":"RUNNING","minutes":5,"seconds":0,"remaining_seconds":300,
             "display":"5:00","max_minutes":270,"name":"Exact Hour",
             "message":null,"automation":null}
            """
        )
        assertEquals(ClockState.RUNNING, status.state)
        assertEquals(5, status.minutes)
        assertEquals(300, status.remainingSeconds)
        assertEquals("5:00", status.display)
        assertEquals(270, status.maxMinutes)
        assertEquals("Exact Hour", status.name)
        assertNull(status.message)
        assertNull(status.automation)
    }

    @Test
    fun `the finished display is kept verbatim`() {
        // "BITTI" is not a time and must never be recomputed from
        // minutes/seconds — the same goes for the H:MM:SS form past an hour.
        assertEquals("BITTI", snapshot("""{"state":"FINISHED","display":"BITTI"}""").display)
        assertEquals("1:05:00", snapshot("""{"state":"RUNNING","display":"1:05:00"}""").display)
    }

    @Test
    fun `a snapshot missing every optional field still parses`() {
        val status = snapshot("{}")
        assertEquals(ClockState.IDLE, status.state)
        assertEquals(0, status.minutes)
        assertEquals(ClockLimits.DEFAULT_MAX_MINUTES, status.maxMinutes)
        assertEquals("Exact Hour", status.name)
        assertNull(status.message)
        assertNull(status.automation)
    }

    @Test
    fun `a JSON null message is null, not the string "null"`() {
        assertNull(snapshot("""{"message":null}""").message)
        assertEquals("STRETCH", snapshot("""{"message":"STRETCH"}""").message)
    }

    @Test
    fun `a running automation and its error are preserved`() {
        val status = snapshot(
            """
            {"state":"RUNNING","automation":{"running":true,"name":"Pomodoro",
             "label":"Work","step":2,"steps":8,"error":null}}
            """
        )
        val automation = requireNotNull(status.automation)
        assertTrue(automation.running)
        assertEquals("Pomodoro", automation.name)
        assertEquals("Work", automation.label)
        assertEquals(2, automation.step)
        assertEquals(8, automation.steps)
        assertNull(status.automationError)
    }

    @Test
    fun `a rejected preset surfaces its reason rather than an HTTP failure`() {
        // The device answers 200 for a bad automation; the only sign is here.
        val status = snapshot(
            """
            {"state":"IDLE","automation":{"running":false,"name":null,"label":null,
             "step":0,"steps":0,"error":"work must be between 1 and 270 (got 999)"}}
            """
        )
        assertEquals("work must be between 1 and 270 (got 999)", status.automationError)
    }

    @Test
    fun `the timer is only locked while running`() {
        assertTrue(snapshot("""{"state":"IDLE"}""").timerEditable)
        assertTrue(snapshot("""{"state":"PAUSED"}""").timerEditable)
        assertTrue(snapshot("""{"state":"FINISHED"}""").timerEditable)
        assertTrue(!snapshot("""{"state":"RUNNING"}""").timerEditable)
    }

    @Test
    fun `discovery recognises a clock, and a renamed one`() {
        assertTrue(ClockStatus.looksLikeClock(JSONObject("""{"name":"Exact Hour"}""")))
        assertTrue(ClockStatus.looksLikeClock(JSONObject("""{"name":"Exact Hour (demo)"}""")))
        // Renamed device: the shape still gives it away.
        assertTrue(
            ClockStatus.looksLikeClock(
                JSONObject("""{"name":"Study clock","state":"IDLE","display":"0:00"}""")
            )
        )
    }

    @Test
    fun `discovery ignores something else on the LAN that answers JSON`() {
        assertTrue(!ClockStatus.looksLikeClock(JSONObject("""{"status":"ok"}""")))
        assertTrue(!ClockStatus.looksLikeClock(JSONObject("""{"name":"Some NAS"}""")))
    }
}
