package ai.bewsoa.flow.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import javax.net.ssl.HttpsURLConnection

/** One message of the assistant conversation, as persisted. */
data class ChatMessage(val role: String, val text: String, val ts: Long) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_AI = "assistant"

        fun listToJson(messages: List<ChatMessage>): String {
            val arr = JSONArray()
            messages.forEach {
                arr.put(
                    JSONObject().put("role", it.role).put("text", it.text).put("ts", it.ts)
                )
            }
            return arr.toString()
        }

        fun listFromJson(json: String?): List<ChatMessage> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    ChatMessage(o.getString("role"), o.getString("text"), o.optLong("ts"))
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * The conversational side of the AI: reads the user's plan, answers questions,
 * and drafts changes — but never applies anything itself. Every mutation comes
 * back as a [Draft] the chat screen shows with explicit Apply buttons.
 *
 * The core contract (straight from the user's spec): a change mentioned for a
 * time/day is a ONE-TIME edit of that date unless the user says it's permanent
 * — "tonight I'm out" touches today only; "move gym to 6pm every day" rewrites
 * the standing program.
 */
object AiAssistant {

    /** What the model drafted, decoded and validated, ready for the UI. */
    sealed interface Draft {
        /** Replace [date]'s plan, once. */
        data class Day(
            val date: LocalDate,
            val blocks: List<TaskBlock>,
            val changes: List<String>
        ) : Draft

        /** Rewrite the standing weekly program. */
        data class Week(val json: String, val changes: List<String>) : Draft

        /** Add one to-do. */
        data class NewTask(val task: AiTaskParser.ParsedTask) : Draft
    }

    data class Turn(val reply: String, val draft: Draft?)

    private const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val CLAUDE_MODEL = "claude-opus-4-8"
    private const val GEMINI_MODEL = "gemini-2.5-flash"
    private const val GEMINI_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    /** Prior turns sent back to the model; older ones only waste tokens. */
    private const val HISTORY_LIMIT = 12

    private val TRACKS = listOf("YKS", "TYT", "SAT", "PROJECT", "GYM", "MEAL", "REVIEW", "FREE")
    private val TASK_TRACKS = listOf("YKS", "TYT", "SAT", "PROJECT", "GYM", "NONE")

    private const val SYSTEM_PROMPT =
        "You are the in-app assistant of Bewsoa Flow, a daily program and task app for a " +
            "student grinding toward the YKS/TYT and SAT exams while building the Bewsoa AI " +
            "projects. Each user message ends with a fresh context block: today's date and " +
            "time, today's effective plan, the standing weekly program, today's tasks and " +
            "streak/XP state. Ground every answer in that context; never invent blocks or " +
            "numbers that are not in it.\n\n" +
            "Per turn you either just talk, or draft EXACTLY ONE change:\n" +
            "- action \"none\": questions, summaries, encouragement. reply only.\n" +
            "- action \"edit_today\": the user wants their plan different for ONE specific " +
            "day. This is the DEFAULT for any change request that does not clearly say it " +
            "is forever. Fill targetDate (resolve today/tonight/tomorrow/weekday words " +
            "against the context date) and dayBlocks = the COMPLETE block list of that day " +
            "AFTER the change. Copy every unchanged block exactly (same id, title, track, " +
            "times, note, counted); only touch what was asked. New blocks get a short " +
            "snake_case id prefixed x_ (e.g. x_wedding). To remove a block, leave it out. " +
            "Set permanent=false.\n" +
            "- action \"edit_week\": ONLY when the user clearly wants a standing change " +
            "(\"always\", \"every week\", \"from now on\", \"permanently\", \"her hafta\", " +
            "\"artık\", \"bundan sonra\", \"kalıcı\"). Fill week with every day's complete " +
            "blocks, keeping unchanged ids identical to the context program. Set " +
            "permanent=true.\n" +
            "- action \"add_task\": a to-do rather than a schedule change (\"I need to…\", " +
            "\"remind me to…\"). Fill task; resolve relative dates against the context date.\n" +
            "If the wording is ambiguous between once and permanent, choose edit_today — " +
            "never guess permanent. Applying is the user's decision: the app renders your " +
            "draft with Apply buttons, so describe the draft in the reply but NEVER claim " +
            "it is already applied.\n\n" +
            "Block rules: chronological, non-overlapping, 24h HH:MM between 00:00 and " +
            "23:59 (write midnight as 23:59, never 24:00). Tracks: YKS (main exam deep " +
            "work), TYT (practice exams), SAT, PROJECT (coding/hardware), GYM, MEAL (food), " +
            "REVIEW (weekly review), FREE (free time). counted=false only for MEAL and FREE " +
            "blocks.\n\n" +
            "reply is 1-3 short, warm sentences in the user's language (Turkish stays " +
            "Turkish). Match their energy; you are a coach, not a formal agent."

    // Response schema ----------------------------------------------------------

    /** Claude json_schema — nullable slots so one schema covers all four actions. */
    private val CLAUDE_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "reply": {"type": "string"},
        "action": {"type": "string", "enum": ["none","edit_today","edit_week","add_task"]},
        "permanent": {"type": "boolean"},
        "targetDate": {"type": ["string","null"], "description": "ISO yyyy-MM-dd of the day being edited"},
        "dayBlocks": {"anyOf": [{"type": "null"}, {"${'$'}ref": "#/${'$'}defs/blocks"}]},
        "week": {
          "anyOf": [
            {"type": "null"},
            {
              "type": "object",
              "properties": {
                "days": {
                  "type": "object",
                  "properties": {
                    "MONDAY": {"${'$'}ref": "#/${'$'}defs/blocks"},
                    "TUESDAY": {"${'$'}ref": "#/${'$'}defs/blocks"},
                    "WEDNESDAY": {"${'$'}ref": "#/${'$'}defs/blocks"},
                    "THURSDAY": {"${'$'}ref": "#/${'$'}defs/blocks"},
                    "FRIDAY": {"${'$'}ref": "#/${'$'}defs/blocks"},
                    "SATURDAY": {"${'$'}ref": "#/${'$'}defs/blocks"},
                    "SUNDAY": {"${'$'}ref": "#/${'$'}defs/blocks"}
                  },
                  "required": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
                  "additionalProperties": false
                }
              },
              "required": ["days"],
              "additionalProperties": false
            }
          ]
        },
        "task": {
          "anyOf": [
            {"type": "null"},
            {
              "type": "object",
              "properties": {
                "title": {"type": "string"},
                "note": {"type": "string"},
                "track": {"type": "string", "enum": ["YKS","TYT","SAT","PROJECT","GYM","NONE"]},
                "scheduledDate": {"type": "string", "description": "ISO yyyy-MM-dd"},
                "estimatedMinutes": {"type": "integer"},
                "subtasks": {"type": "array", "items": {"type": "string"}},
                "needsReview": {"type": "boolean"},
                "urgent": {"type": "boolean"},
                "important": {"type": "boolean"}
              },
              "required": ["title","note","track","scheduledDate","estimatedMinutes","subtasks","needsReview","urgent","important"],
              "additionalProperties": false
            }
          ]
        }
      },
      "required": ["reply","action","permanent","targetDate","dayBlocks","week","task"],
      "additionalProperties": false,
      "${'$'}defs": {
        "blocks": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {"type": "string", "description": "stable snake_case id; new blocks prefixed x_"},
              "title": {"type": "string"},
              "track": {"type": "string", "enum": ["YKS","TYT","SAT","PROJECT","GYM","MEAL","REVIEW","FREE"]},
              "start": {"type": "string", "description": "24h HH:MM"},
              "end": {"type": "string", "description": "24h HH:MM"},
              "note": {"type": "string"},
              "counted": {"type": "boolean"}
            },
            "required": ["id","title","track","start","end","note","counted"],
            "additionalProperties": false
          }
        }
      }
    }
    """.trimIndent()

    // Public API ---------------------------------------------------------------

    /**
     * One conversation turn. [history] is the persisted transcript (already
     * including nothing about this turn); [message] is what the user just sent.
     * The context snapshot is rebuilt fresh on every call so the model always
     * sees the plan as it stands after earlier applied drafts.
     */
    suspend fun chat(
        provider: String,
        apiKey: String,
        history: List<ChatMessage>,
        message: String,
        taskSummary: List<String>,
        streakDays: Int,
        levelIndex: Int,
        levelTitle: String
    ): Result<Turn> = withContext(Dispatchers.IO) {
        runCatching {
            val context = buildContext(taskSummary, streakDays, levelIndex, levelTitle)
            val finalUser = "$message\n\n$context"
            val turns = history.takeLast(HISTORY_LIMIT).map { it.role to it.text } +
                (ChatMessage.ROLE_USER to finalUser)
            val json = if (provider == SettingsRepository.PROVIDER_GEMINI) {
                extractGeminiText(
                    post(
                        GEMINI_ENDPOINT,
                        mapOf("x-goog-api-key" to apiKey),
                        buildGeminiBody(turns)
                    )
                )
            } else {
                extractClaudeText(
                    post(
                        CLAUDE_ENDPOINT,
                        mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
                        buildClaudeBody(turns)
                    )
                )
            }
            decode(json)
        }
    }

    // Context ------------------------------------------------------------------

    private fun buildContext(
        taskSummary: List<String>,
        streakDays: Int,
        levelIndex: Int,
        levelTitle: String
    ): String {
        val today = LocalDate.now()
        val now = LocalTime.now()
        return buildString {
            append("«context»\n")
            append("Now: $today (${today.dayOfWeek}) ${"%02d:%02d".format(now.hour, now.minute)}\n")
            append("Streak: $streakDays days · Level $levelIndex ($levelTitle)\n")
            if (DayOverrides.hasOverride(today)) {
                append("Note: today's plan already carries a one-time edit.\n")
            }
            append("Today's effective plan:\n")
            append(BlockCodec.toJson(WeeklyProgram.blocksFor(today)).toString())
            append("\nStanding weekly program (days):\n")
            append(weekJson().toString())
            append("\nToday's tasks:\n")
            if (taskSummary.isEmpty()) append("- none\n")
            else taskSummary.forEach { append("- ").append(it).append('\n') }
            append("«/context»")
        }
    }

    /** The active program as {"days": {...}} — built-in or custom, same shape. */
    private fun weekJson(): JSONObject {
        val days = JSONObject()
        WeeklyProgram.weekMap().forEach { (day, blocks) ->
            days.put(day.name, BlockCodec.toJson(blocks))
        }
        return JSONObject().put("days", days)
    }

    // Decoding -----------------------------------------------------------------

    private fun decode(json: String): Turn {
        val obj = JSONObject(json)
        val reply = obj.getString("reply").trim()
        val draft = when (obj.optString("action", "none")) {
            "edit_today" -> decodeDay(obj)
            "edit_week" -> decodeWeek(obj)
            "add_task" -> decodeTask(obj)
            else -> null
        }
        return Turn(reply = reply, draft = draft)
    }

    private fun decodeDay(obj: JSONObject): Draft.Day? {
        val arr = obj.optJSONArray("dayBlocks") ?: return null
        val date = runCatching {
            LocalDate.parse(obj.optString("targetDate"))
        }.getOrDefault(LocalDate.now())
        val blocks = BlockCodec.fromJson(arr)
        val before = WeeklyProgram.blocksFor(date)
        return Draft.Day(date, blocks, dayChanges(before, blocks))
    }

    private fun decodeWeek(obj: JSONObject): Draft.Week? {
        val week = obj.optJSONObject("week") ?: return null
        val json = week.toString()
        val parsed = CustomProgram.parse(json).getOrThrow()
        return Draft.Week(json, ProgramDiff.summarize(WeeklyProgram.weekMap(), parsed))
    }

    private fun decodeTask(obj: JSONObject): Draft.NewTask? {
        val t = obj.optJSONObject("task") ?: return null
        val track = t.optString("track", "NONE").ifBlank { "NONE" }
        return Draft.NewTask(
            AiTaskParser.ParsedTask(
                title = t.getString("title").trim(),
                note = t.optString("note", "").trim(),
                track = track.takeIf { it != "NONE" },
                scheduledDate = t.optString("scheduledDate", LocalDate.now().toString()),
                estimatedMinutes = t.optInt("estimatedMinutes", 0).coerceIn(0, 24 * 60),
                subtasks = t.optJSONArray("subtasks").toStringList(),
                needsReview = t.optBoolean("needsReview", false),
                urgent = t.optBoolean("urgent", false),
                important = t.optBoolean("important", false)
            )
        )
    }

    /** Human diff of one day: what the draft adds, drops, moves or renames. */
    fun dayChanges(old: List<TaskBlock>, new: List<TaskBlock>): List<String> {
        val before = old.associateBy { it.id }
        val after = new.associateBy { it.id }
        val lines = mutableListOf<String>()
        (after.keys - before.keys).forEach { id ->
            val b = after.getValue(id)
            lines += "＋ ${b.title} · ${b.start}–${b.end}"
        }
        (before.keys - after.keys).forEach { id ->
            lines += "− ${before.getValue(id).title}"
        }
        (before.keys intersect after.keys).forEach { id ->
            val b = before.getValue(id)
            val a = after.getValue(id)
            when {
                b.start != a.start || b.end != a.end ->
                    lines += "${a.title} · ${b.start}–${b.end} → ${a.start}–${a.end}"
                b.title != a.title -> lines += "“${b.title}” → “${a.title}”"
            }
        }
        if (lines.isEmpty()) return listOf("Same plan — nothing would change.")
        return if (lines.size <= 8) lines else lines.take(8) + "…and ${lines.size - 8} more"
    }

    // Claude -------------------------------------------------------------------

    private fun buildClaudeBody(turns: List<Pair<String, String>>): String = JSONObject()
        .put("model", CLAUDE_MODEL)
        .put("max_tokens", 8000)
        .put("system", SYSTEM_PROMPT)
        .put(
            "output_config",
            JSONObject().put(
                "format",
                JSONObject()
                    .put("type", "json_schema")
                    .put("schema", JSONObject(CLAUDE_SCHEMA))
            )
        )
        .put(
            "messages",
            JSONArray().also { arr ->
                turns.forEach { (role, text) ->
                    arr.put(JSONObject().put("role", role).put("content", text))
                }
            }
        )
        .toString()

    private fun extractClaudeText(response: JSONObject): String {
        when (response.optString("stop_reason")) {
            "max_tokens" -> error("The reply was cut off — try a shorter request.")
            "refusal" -> error("The model declined this request — try rewording it.")
        }
        val content = response.getJSONArray("content")
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.getString("type") == "text") return block.getString("text")
        }
        error("The model returned nothing.")
    }

    // Gemini -------------------------------------------------------------------

    private fun buildGeminiBody(turns: List<Pair<String, String>>): String = JSONObject()
        .put(
            "system_instruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
        )
        .put(
            "contents",
            JSONArray().also { arr ->
                turns.forEach { (role, text) ->
                    arr.put(
                        JSONObject()
                            .put("role", if (role == ChatMessage.ROLE_AI) "model" else "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", text)))
                    )
                }
            }
        )
        .put(
            "generationConfig",
            JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", geminiSchema())
                .put("maxOutputTokens", 8000)
                .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        )
        .toString()

    // Gemini's responseSchema: no $refs/additionalProperties; nullable via "nullable".
    private fun geminiSchema(): JSONObject {
        fun str() = JSONObject().put("type", "string")
        fun blocks() = JSONObject()
            .put("type", "array")
            .put(
                "items",
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("id", str())
                            .put("title", str())
                            .put("track", JSONObject().put("type", "string").put("enum", JSONArray(TRACKS)))
                            .put("start", str())
                            .put("end", str())
                            .put("note", str())
                            .put("counted", JSONObject().put("type", "boolean"))
                    )
                    .put(
                        "required",
                        JSONArray(listOf("id", "title", "track", "start", "end", "note", "counted"))
                    )
            )

        val dayProps = JSONObject()
        listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
            .forEach { dayProps.put(it, blocks()) }

        val task = JSONObject()
            .put("type", "object")
            .put("nullable", true)
            .put(
                "properties",
                JSONObject()
                    .put("title", str())
                    .put("note", str())
                    .put("track", JSONObject().put("type", "string").put("enum", JSONArray(TASK_TRACKS)))
                    .put("scheduledDate", str())
                    .put("estimatedMinutes", JSONObject().put("type", "integer"))
                    .put("subtasks", JSONObject().put("type", "array").put("items", str()))
                    .put("needsReview", JSONObject().put("type", "boolean"))
                    .put("urgent", JSONObject().put("type", "boolean"))
                    .put("important", JSONObject().put("type", "boolean"))
            )
            .put(
                "required",
                JSONArray(
                    listOf(
                        "title", "note", "track", "scheduledDate", "estimatedMinutes",
                        "subtasks", "needsReview", "urgent", "important"
                    )
                )
            )

        return JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("reply", str())
                    .put(
                        "action",
                        JSONObject().put("type", "string")
                            .put("enum", JSONArray(listOf("none", "edit_today", "edit_week", "add_task")))
                    )
                    .put("permanent", JSONObject().put("type", "boolean"))
                    .put("targetDate", JSONObject().put("type", "string").put("nullable", true))
                    .put("dayBlocks", blocks().put("nullable", true))
                    .put(
                        "week",
                        JSONObject()
                            .put("type", "object")
                            .put("nullable", true)
                            .put(
                                "properties",
                                JSONObject().put(
                                    "days",
                                    JSONObject().put("type", "object").put("properties", dayProps)
                                )
                            )
                            .put("required", JSONArray(listOf("days")))
                    )
                    .put("task", task)
            )
            .put("required", JSONArray(listOf("reply", "action", "permanent")))
    }

    private fun extractGeminiText(response: JSONObject): String {
        val blockReason = response.optJSONObject("promptFeedback")
            ?.optString("blockReason").orEmpty()
        if (blockReason.isNotEmpty()) {
            error("The model declined this request — try rewording it.")
        }
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: error("The model returned nothing.")
        when (candidate.optString("finishReason")) {
            "MAX_TOKENS" -> error("The reply was cut off — try a shorter request.")
            "SAFETY", "PROHIBITED_CONTENT", "RECITATION" ->
                error("The model declined this request — try rewording it.")
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: error("The model returned nothing.")
        val text = buildString {
            for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }
        if (text.isBlank()) error("The model returned nothing.")
        return text
    }

    // Shared -------------------------------------------------------------------

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length())
            .map { optString(it, "").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun post(endpoint: String, headers: Map<String, String>, body: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault(text.take(200))
                error("API error $code: $message")
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
