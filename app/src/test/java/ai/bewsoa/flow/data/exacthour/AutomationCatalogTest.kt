package ai.bewsoa.flow.data.exacthour

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A preset's step times are either a number or the *name of one of its own
 * params* — the device resolves the reference when the preset runs. Getting
 * that union wrong is the one thing that would silently break every template.
 */
class AutomationCatalogTest {

    private fun template(body: String) = AutomationTemplate.from(JSONObject(body))

    @Test
    fun `a literal number stays a literal`() {
        assertEquals(ParamValue.Literal(25), ParamValue.from(25))
        assertEquals(ParamValue.Literal(0), ParamValue.from(0))
    }

    @Test
    fun `a param name becomes a reference`() {
        assertEquals(ParamValue.Ref("work"), ParamValue.from("work"))
        assertEquals(ParamValue.Ref("rounds"), ParamValue.from("rounds"))
    }

    @Test
    fun `a numeric string is still a number`() {
        assertEquals(ParamValue.Literal(25), ParamValue.from("25"))
    }

    @Test
    fun `a missing value is zero rather than a crash`() {
        assertEquals(ParamValue.Literal(0), ParamValue.from(null))
    }

    @Test
    fun `a reference resolves against the chosen params`() {
        val ref = ParamValue.Ref("work")
        assertEquals(30, ref.resolve(mapOf("work" to 30)))
        // Unknown name: fall back rather than pretend it's zero minutes.
        assertEquals(5, ref.resolve(emptyMap(), fallback = 5))
        assertEquals(25, ParamValue.Literal(25).resolve(mapOf("work" to 30)))
    }

    @Test
    fun `the pomodoro preset parses as the device sends it`() {
        val pomodoro = template(
            """
            {"id":"pomodoro","name":"Pomodoro","description":"Work, break, repeat",
             "params":[
               {"name":"work","label":"Work minutes","default":25,"min":1,"max":270},
               {"name":"rest","label":"Break minutes","default":5,"min":1,"max":270},
               {"name":"rounds","label":"Rounds","default":4,"min":1,"max":99}],
             "repeat":"rounds",
             "steps":[{"label":"Work","text":"WORK","minutes":"work"},
                      {"label":"Break","text":"BREAK","minutes":"rest"}]}
            """
        )
        assertEquals("pomodoro", pomodoro.id)
        assertEquals(3, pomodoro.params.size)
        assertEquals(ParamValue.Ref("rounds"), pomodoro.repeat)
        assertEquals(ParamValue.Ref("work"), pomodoro.steps[0].minutes)
        assertEquals(mapOf("work" to 25, "rest" to 5, "rounds" to 4), pomodoro.defaults())
    }

    @Test
    fun `params are clamped to the preset's own limits`() {
        val param = AutomationParam("work", "Work minutes", default = 25, min = 1, max = 270)
        assertEquals(1, param.clamp(0))
        assertEquals(270, param.clamp(999))
        assertEquals(25, param.clamp(25))
    }

    @Test
    fun `a template with no params parses`() {
        val timer = template("""{"id":"timer","name":"Timer","steps":[{"minutes":10}]}""")
        assertTrue(timer.params.isEmpty())
        assertEquals(ParamValue.Literal(10), timer.steps[0].minutes)
        assertEquals("timer", timer.name.lowercase())
    }

    @Test
    fun `the catalog unwraps the templates key`() {
        val catalog = AutomationTemplate.catalog(
            JSONObject("""{"templates":[{"id":"a","name":"A"},{"id":"b","name":"B"}]}""")
        )
        assertEquals(listOf("a", "b"), catalog.map { it.id })
    }

    @Test
    fun `an empty or absent catalog is an empty list, not a crash`() {
        assertTrue(AutomationTemplate.catalog(JSONObject("""{"templates":[]}""")).isEmpty())
        assertTrue(AutomationTemplate.catalog(JSONObject("{}")).isEmpty())
    }
}
