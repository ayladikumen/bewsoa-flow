package ai.bewsoa.flow.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The AI side of the task layer. Instead of rewriting a whole weekly program
 * (what [AiProgramUpdater] does), this turns a single natural-language sentence
 * into one structured task, and can break an existing task into subtasks.
 *
 * It reuses the same two providers and the same locally-stored API keys as the
 * program updater — Claude (Messages API, json_schema) or Gemini
 * (generateContent, responseSchema) — so the reply is always shaped to schema.
 */
object AiTaskParser {

    /** A task parsed from one sentence. [track] is a [Track] name or null. */
    data class ParsedTask(
        val title: String,
        val note: String,
        val track: String?,
        val scheduledDate: String,
        val estimatedMinutes: Int,
        val subtasks: List<String>,
        val needsReview: Boolean
    )

    private const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val CLAUDE_MODEL = "claude-opus-4-8"
    private const val GEMINI_MODEL = "gemini-2.5-flash"
    private const val GEMINI_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    /** Tracks a user task may be tagged with; "NONE" maps to null (general task). */
    private val TASK_TRACKS = listOf("YKS", "TYT", "SAT", "PROJECT", "GYM", "NONE")

    private const val PARSE_SYSTEM =
        "You turn one sentence from a student into a single structured to-do for the " +
            "Bewsoa Flow app. The student is prepping for big exams (YKS/TYT, SAT) and also " +
            "builds side projects. Rules: keep the task's language exactly as the user wrote " +
            "it (Turkish stays Turkish). 'title' is a short imperative task. 'note' holds any " +
            "extra detail or is empty. 'track' is the best fit of YKS (main exam study), TYT " +
            "(practice exams), SAT, PROJECT (coding/hardware), GYM, or NONE when nothing fits. " +
            "'scheduledDate' is an ISO yyyy-MM-dd date: resolve relative words like 'bugün', " +
            "'yarın', 'cumartesi', 'haftaya' against the provided today's date; default to " +
            "today when no time is implied. 'estimatedMinutes' is a realistic whole-minute " +
            "estimate of focused work (e.g. 3 tests ~ 60), or 0 if truly unknowable. " +
            "'subtasks' breaks a big or multi-step task into 2-6 concrete steps in the user's " +
            "language (e.g. 'konu anlatımı izle', '30 soru çöz', 'yanlışları defterine yaz'); " +
            "leave it empty for an already-atomic task. 'needsReview' is true only when the " +
            "task is memorization the student will forget without repetition (formulas, " +
            "vocabulary, dates), false for practice or building work."

    private const val SPLIT_SYSTEM =
        "You break a student's study/build task into concrete subtasks for the Bewsoa Flow " +
            "app. Return 3-6 ordered, actionable steps in the same language as the task " +
            "(Turkish stays Turkish). Each step is a short imperative phrase a person can " +
            "check off in one sitting. No numbering, no extra commentary."

    // Schemas -----------------------------------------------------------------

    // Claude json_schema: every object closed with additionalProperties=false.
    private val PARSE_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "title": {"type": "string"},
        "note": {"type": "string"},
        "track": {"type": "string", "enum": ["YKS","TYT","SAT","PROJECT","GYM","NONE"]},
        "scheduledDate": {"type": "string", "description": "ISO yyyy-MM-dd"},
        "estimatedMinutes": {"type": "integer"},
        "subtasks": {"type": "array", "items": {"type": "string"}},
        "needsReview": {"type": "boolean"}
      },
      "required": ["title","note","track","scheduledDate","estimatedMinutes","subtasks","needsReview"],
      "additionalProperties": false
    }
    """.trimIndent()

    private val SPLIT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "subtasks": {"type": "array", "items": {"type": "string"}}
      },
      "required": ["subtasks"],
      "additionalProperties": false
    }
    """.trimIndent()

    // Public API --------------------------------------------------------------

    suspend fun parse(
        provider: String,
        apiKey: String,
        sentence: String,
        todayIso: String,
        weekday: String
    ): Result<ParsedTask> = withContext(Dispatchers.IO) {
        runCatching {
            val userMessage =
                "Today is $todayIso ($weekday). Turn this into one task:\n\n$sentence"
            val json = call(provider, apiKey, PARSE_SYSTEM, userMessage, PARSE_SCHEMA, geminiParseSchema())
            val obj = JSONObject(json)
            val track = obj.optString("track", "NONE").ifBlank { "NONE" }
            ParsedTask(
                title = obj.getString("title").trim(),
                note = obj.optString("note", "").trim(),
                track = track.takeIf { it != "NONE" },
                scheduledDate = obj.optString("scheduledDate", todayIso).ifBlank { todayIso },
                estimatedMinutes = obj.optInt("estimatedMinutes", 0).coerceIn(0, 24 * 60),
                subtasks = obj.optJSONArray("subtasks").toStringList(),
                needsReview = obj.optBoolean("needsReview", false)
            )
        }
    }

    suspend fun split(
        provider: String,
        apiKey: String,
        title: String,
        note: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val userMessage = buildString {
                append("Break this task into subtasks:\n\n")
                append(title)
                if (note.isNotBlank()) append("\n\nDetail: ").append(note)
            }
            val json = call(provider, apiKey, SPLIT_SYSTEM, userMessage, SPLIT_SCHEMA, geminiSplitSchema())
            JSONObject(json).optJSONArray("subtasks").toStringList()
        }
    }

    // Provider dispatch -------------------------------------------------------

    private fun call(
        provider: String,
        apiKey: String,
        system: String,
        userMessage: String,
        claudeSchema: String,
        geminiSchema: JSONObject
    ): String = if (provider == SettingsRepository.PROVIDER_GEMINI) {
        val response = post(
            GEMINI_ENDPOINT,
            mapOf("x-goog-api-key" to apiKey),
            buildGeminiBody(system, userMessage, geminiSchema)
        )
        extractGeminiText(response)
    } else {
        val response = post(
            CLAUDE_ENDPOINT,
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            buildClaudeBody(system, userMessage, claudeSchema)
        )
        extractClaudeText(response)
    }

    // Claude ------------------------------------------------------------------

    private fun buildClaudeBody(system: String, userMessage: String, schema: String): String =
        JSONObject()
            .put("model", CLAUDE_MODEL)
            .put("max_tokens", 2000)
            .put("system", system)
            .put(
                "output_config",
                JSONObject().put(
                    "format",
                    JSONObject().put("type", "json_schema").put("schema", JSONObject(schema))
                )
            )
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", userMessage)
                )
            )
            .toString()

    private fun extractClaudeText(response: JSONObject): String {
        when (response.optString("stop_reason")) {
            "max_tokens" -> error("The reply was cut off — try a shorter task.")
            "refusal" -> error("The model declined this request. Reword the task and try again.")
        }
        val content = response.getJSONArray("content")
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.getString("type") == "text") return block.getString("text")
        }
        error("The model returned nothing.")
    }

    // Gemini ------------------------------------------------------------------

    private fun buildGeminiBody(system: String, userMessage: String, schema: JSONObject): String =
        JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema)
                    .put("maxOutputTokens", 2000)
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            )
            .toString()

    // Gemini's responseSchema has no additionalProperties / $ref support.
    private fun geminiParseSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("title", strType())
                .put("note", strType())
                .put("track", JSONObject().put("type", "string").put("enum", JSONArray(TASK_TRACKS)))
                .put("scheduledDate", JSONObject().put("type", "string").put("description", "ISO yyyy-MM-dd"))
                .put("estimatedMinutes", JSONObject().put("type", "integer"))
                .put("subtasks", JSONObject().put("type", "array").put("items", strType()))
                .put("needsReview", JSONObject().put("type", "boolean"))
        )
        .put(
            "required",
            JSONArray(
                listOf("title", "note", "track", "scheduledDate", "estimatedMinutes", "subtasks", "needsReview")
            )
        )

    private fun geminiSplitSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject().put("subtasks", JSONObject().put("type", "array").put("items", strType()))
        )
        .put("required", JSONArray(listOf("subtasks")))

    private fun strType(): JSONObject = JSONObject().put("type", "string")

    private fun extractGeminiText(response: JSONObject): String {
        val blockReason = response.optJSONObject("promptFeedback")
            ?.optString("blockReason").orEmpty()
        if (blockReason.isNotEmpty()) {
            error("The model declined this request. Reword the task and try again.")
        }
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: error("The model returned nothing.")
        when (candidate.optString("finishReason")) {
            "MAX_TOKENS" -> error("The reply was cut off — try a shorter task.")
            "SAFETY", "PROHIBITED_CONTENT", "RECITATION" ->
                error("The model declined this request. Reword the task and try again.")
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: error("The model returned nothing.")
        val text = buildString {
            for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }
        if (text.isBlank()) error("The model returned nothing.")
        return text
    }

    // Shared ------------------------------------------------------------------

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
            readTimeout = 60_000
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
