package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExportTest {

    private val monday: LocalDate = LocalDate.of(2026, 7, 13)

    private fun block(
        id: String,
        title: String = "Block",
        track: Track = Track.YKS,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(10, 30),
        counted: Boolean = true
    ) = TaskBlock(id, title, track, start, end, counted = counted)

    private fun row(id: String, state: CompletionState, date: LocalDate = monday) =
        TaskCompletionEntity(
            date = date.toString(),
            taskId = id,
            state = state.name,
            completedAt = if (state == CompletionState.DONE) 1L else null
        )

    private fun bundle(
        completions: List<TaskCompletionEntity> = emptyList(),
        reviews: List<WeeklyReviewEntity> = emptyList(),
        programJson: String? = null,
        to: LocalDate = monday
    ) = ExportBundle(monday, to, completions, emptyList(), reviews, programJson)

    // CSV --------------------------------------------------------------------

    @Test
    fun `csv leaves plain values unquoted`() {
        assertEquals("YKS morning", Export.csv("YKS morning"))
    }

    @Test
    fun `csv quotes and escapes values containing commas and quotes`() {
        // The failure this guards: a block titled `Paragraf, 20 soru` silently
        // shifting every later column by one.
        assertEquals("\"Paragraf, 20 soru\"", Export.csv("Paragraf, 20 soru"))
        assertEquals("\"say \"\"hi\"\"\"", Export.csv("say \"hi\""))
        assertEquals("\"two\nlines\"", Export.csv("two\nlines"))
    }

    @Test
    fun `csv has a header and one row per block per day`() {
        val csv = Export.toCsv(bundle(to = monday.plusDays(1))) {
            listOf(block("a"), block("b"))
        }
        val lines = csv.trim().lines()
        assertEquals("date,taskId,title,track,start,end,state,minutes", lines[0])
        assertEquals(5, lines.size) // header + 2 blocks x 2 days
    }

    @Test
    fun `csv reports each state and PENDING when no row exists`() {
        val rows = listOf(
            row("a", CompletionState.DONE),
            row("b", CompletionState.PENDING),
            row("d", CompletionState.SKIPPED)
        )
        val csv = Export.toCsv(bundle(rows)) {
            listOf(block("a"), block("b"), block("c"), block("d"))
        }
        val byId = csv.trim().lines().drop(1).associate { line ->
            val cols = line.split(",")
            cols[1] to cols[6]
        }
        assertEquals("DONE", byId["a"])
        assertEquals("PENDING", byId["b"])
        assertEquals("PENDING", byId["c"]) // no row at all
        assertEquals("SKIPPED", byId["d"])
    }

    @Test
    fun `csv carries the block duration`() {
        val csv = Export.toCsv(bundle()) {
            listOf(block("a", start = LocalTime.of(9, 0), end = LocalTime.of(10, 30)))
        }
        assertTrue(csv.trim().lines()[1].endsWith(",90"))
    }

    // JSON -------------------------------------------------------------------

    @Test
    fun `json escapes the characters that would otherwise break the document`() {
        assertEquals("\"a\\\"b\"", Export.json("a\"b"))
        assertEquals("\"a\\\\b\"", Export.json("a\\b"))
        assertEquals("\"a\\nb\"", Export.json("a\nb"))
        assertEquals("\"a\\u0007b\"", Export.json("ab"))
    }

    @Test
    fun `json parses back and preserves a title containing quotes`() {
        val json = Export.toJson(bundle()) { listOf(block("a", title = "say \"hi\"")) }
        val parsed = MiniJson.parse(json)
        assertTrue(parsed is Map<*, *>)
        val completions = (parsed as Map<*, *>)["completions"] as List<*>
        val first = completions.first() as Map<*, *>
        assertEquals("say \"hi\"", first["title"])
        assertEquals("a", first["taskId"])
    }

    @Test
    fun `json embeds a custom program as an object rather than a string`() {
        val program = """{"MONDAY":[]}"""
        val json = Export.toJson(bundle(programJson = program)) { emptyList() }
        val parsed = MiniJson.parse(json) as Map<*, *>
        val programNode = parsed["program"] as Map<*, *>
        assertEquals(true, programNode["custom"])
        assertTrue("days should be an object, not a re-encoded string", programNode["days"] is Map<*, *>)
    }

    @Test
    fun `json marks the built-in program as not custom`() {
        val json = Export.toJson(bundle(programJson = null)) { emptyList() }
        val parsed = MiniJson.parse(json) as Map<*, *>
        val programNode = parsed["program"] as Map<*, *>
        assertEquals(false, programNode["custom"])
        assertEquals(null, programNode["days"])
    }

    @Test
    fun `json stays parseable with no data at all`() {
        val json = Export.toJson(bundle()) { emptyList() }
        val parsed = MiniJson.parse(json) as Map<*, *>
        assertEquals(emptyList<Any>(), parsed["completions"])
        assertEquals(emptyList<Any>(), parsed["tasks"])
    }

    // Markdown ---------------------------------------------------------------

    @Test
    fun `markdown counts only counted blocks toward the total`() {
        val rows = listOf(row("a", CompletionState.DONE))
        val md = Export.toMarkdown(
            bundle(rows),
            { listOf(block("a"), block("meal", track = Track.MEAL, counted = false)) },
            emptyList()
        )
        // MEAL is excluded, so this is 1 of 1 — not 1 of 2.
        assertTrue(md, md.contains("**1 / 1**"))
    }

    @Test
    fun `markdown lists insights when there are any`() {
        val md = Export.toMarkdown(
            bundle(),
            { listOf(block("a")) },
            listOf(Insight(Insight.Kind.TREND, "You are up 11% this week"))
        )
        assertTrue(md.contains("## Patterns"))
        assertTrue(md.contains("You are up 11% this week"))
    }

    @Test
    fun `markdown omits the patterns section when there are no insights`() {
        val md = Export.toMarkdown(bundle(), { listOf(block("a")) }, emptyList())
        assertTrue(!md.contains("## Patterns"))
    }
}

/**
 * A minimal JSON reader, here only so the tests can prove [Export.toJson]
 * produces parseable output. org.json is an Android stub that throws on the
 * JVM, and pulling in a real parser to test a hand-written writer would be
 * circular; this is deliberately dumb and independent.
 */
private object MiniJson {

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        require(p.done()) { "trailing content at ${p.pos}" }
        return v
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun done() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            return when (s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun literal(text: String, value: Any?): Any? {
            require(s.startsWith(text, pos)) { "bad literal at $pos" }
            pos += text.length
            return value
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (s[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                expect(':')
                out[k] = value()
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return out }
                    else -> error("expected , or } at $pos")
                }
            }
        }

        private fun arr(): List<Any?> {
            expect('[')
            val out = mutableListOf<Any?>()
            skipWs()
            if (s[pos] == ']') { pos++; return out }
            while (true) {
                out += value()
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return out }
                    else -> error("expected , or ] at $pos")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                when (val c = s[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        else -> error("bad escape \\$e at $pos")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "-+.eE")) pos++
            val raw = s.substring(start, pos)
            require(raw.isNotEmpty()) { "expected a value at $start" }
            return raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun expect(c: Char) {
            require(s[pos] == c) { "expected $c at $pos, found ${s[pos]}" }
            pos++
        }
    }
}
