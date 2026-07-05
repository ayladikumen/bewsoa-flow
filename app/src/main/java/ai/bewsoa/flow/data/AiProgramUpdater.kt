package ai.bewsoa.flow.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Turns the weekly program markdown into the app's JSON schedule by calling
 * an AI API — Claude (Messages API, structured outputs) or Gemini
 * (generateContent, responseSchema). Both constrain the response to the
 * schedule schema, so parsing never depends on prompt luck.
 */
object AiProgramUpdater {

    private const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val CLAUDE_MODEL = "claude-opus-4-8"
    private const val GEMINI_MODEL = "gemini-2.5-flash"
    private const val GEMINI_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    private const val SYSTEM_PROMPT =
        "You maintain the JSON schedule of the Bewsoa Flow app. You either convert a weekly " +
            "study/build program written in markdown into the schedule, or apply the user's " +
            "requested changes to their current schedule JSON, returning the full updated " +
            "schedule. Rules: give every day of the week its blocks in " +
            "chronological order with non-overlapping 24h HH:MM times between 00:00 and 23:59 " +
            "(write midnight at the end of a day as 23:59, never 24:00). Tracks: YKS (main exam " +
            "deep work and review), TYT (full practice exams), SAT, PROJECT (coding/hardware " +
            "work), GYM, MEAL (food breaks), REVIEW (the weekly review block), FREE (free time). " +
            "Set counted=false only for MEAL and FREE blocks — they don't count toward daily " +
            "progress. Ids must be short snake_case and stable across regenerations: prefix " +
            "wd_ for blocks identical on all weekdays, sa_ for Saturday, su_ for Sunday. " +
            "When a block clearly matches one of these existing ids, reuse it exactly so the " +
            "user's history is preserved: wd_yks_morning, wd_gym, wd_dinner, wd_yks_review, " +
            "wd_sat, wd_project, wd_free, sa_tyt, sa_lunch, sa_gym, sa_project, sa_dinner, " +
            "sa_yks, sa_free, su_sat_deep, su_lunch, su_project, su_gym, su_review, su_free. " +
            "If the markdown implies the same plan for Monday through Friday, output identical " +
            "block lists (same ids) for those five days."

    private const val USER_PREFIX = "Convert this weekly program to the JSON schedule:\n\n"

    private val DAYS = listOf(
        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    )
    private val TRACKS = listOf("YKS", "TYT", "SAT", "PROJECT", "GYM", "MEAL", "REVIEW", "FREE")
    private val BLOCK_FIELDS = listOf("id", "title", "track", "start", "end", "note", "counted")

    // Claude structured-output schema: every object closed with additionalProperties=false.
    private val SCHEMA = """
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
      "additionalProperties": false,
      "${'$'}defs": {
        "blocks": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {"type": "string", "description": "stable snake_case id, e.g. wd_yks_morning"},
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

    /**
     * Returns the validated program JSON, ready for [CustomProgram.activate].
     *
     * The base program is [currentJson] when an AI-built schedule is already
     * active, otherwise the [markdown] source; [changeRequest] is what the
     * user wants different this time.
     */
    suspend fun rebuild(
        provider: String,
        apiKey: String,
        markdown: String,
        currentJson: String?,
        changeRequest: String
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userMessage = buildUserMessage(markdown, currentJson, changeRequest)
                val programJson = if (provider == SettingsRepository.PROVIDER_GEMINI) {
                    val response = post(
                        GEMINI_ENDPOINT,
                        mapOf("x-goog-api-key" to apiKey),
                        buildGeminiBody(userMessage)
                    )
                    extractGeminiText(response)
                } else {
                    val response = post(
                        CLAUDE_ENDPOINT,
                        mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
                        buildClaudeBody(userMessage)
                    )
                    extractClaudeText(response)
                }
                // Validate before anyone stores it.
                CustomProgram.parse(programJson).getOrThrow()
                programJson
            }
        }

    private fun buildUserMessage(
        markdown: String,
        currentJson: String?,
        changeRequest: String
    ): String = buildString {
        if (currentJson != null) {
            append("Here is the user's current schedule JSON:\n\n")
            append(currentJson)
        } else {
            append(USER_PREFIX)
            append(markdown)
        }
        if (changeRequest.isNotBlank()) {
            append(
                "\n\nApply exactly the changes the user asks for below and keep every " +
                    "other block identical, with the same ids:\n"
            )
            append(changeRequest)
        }
    }

    // Claude ------------------------------------------------------------------

    private fun buildClaudeBody(userMessage: String): String = JSONObject()
        .put("model", CLAUDE_MODEL)
        .put("max_tokens", 16000)
        .put("thinking", JSONObject().put("type", "adaptive"))
        .put("system", SYSTEM_PROMPT)
        .put(
            "output_config",
            JSONObject().put(
                "format",
                JSONObject()
                    .put("type", "json_schema")
                    .put("schema", JSONObject(SCHEMA))
            )
        )
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", userMessage)
            )
        )
        .toString()

    private fun extractClaudeText(response: JSONObject): String {
        when (response.optString("stop_reason")) {
            "max_tokens" -> error("The reply was cut off — try a shorter program file.")
            "refusal" -> error("The model declined this request. Reword the program file and try again.")
        }
        val content = response.getJSONArray("content")
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.getString("type") == "text") return block.getString("text")
        }
        error("The model returned no schedule.")
    }

    // Gemini ------------------------------------------------------------------

    private fun buildGeminiBody(userMessage: String): String = JSONObject()
        .put(
            "system_instruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
        )
        .put(
            "contents",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", userMessage))
                    )
            )
        )
        .put(
            "generationConfig",
            JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", geminiSchema())
                .put("maxOutputTokens", 16000)
                // Straight md→JSON mapping; skip thinking so tokens go to the schedule.
                .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        )
        .toString()

    // Gemini's responseSchema has no $ref/$defs, so the block schema is inlined per day.
    private fun geminiSchema(): JSONObject {
        fun blocks(): JSONObject {
            val block = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "id",
                            JSONObject().put("type", "string")
                                .put("description", "stable snake_case id, e.g. wd_yks_morning")
                        )
                        .put("title", JSONObject().put("type", "string"))
                        .put(
                            "track",
                            JSONObject().put("type", "string").put("enum", JSONArray(TRACKS))
                        )
                        .put(
                            "start",
                            JSONObject().put("type", "string").put("description", "24h HH:MM")
                        )
                        .put(
                            "end",
                            JSONObject().put("type", "string").put("description", "24h HH:MM")
                        )
                        .put("note", JSONObject().put("type", "string"))
                        .put("counted", JSONObject().put("type", "boolean"))
                )
                .put("required", JSONArray(BLOCK_FIELDS))
            return JSONObject().put("type", "array").put("items", block)
        }

        val dayProperties = JSONObject()
        DAYS.forEach { dayProperties.put(it, blocks()) }
        return JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "days",
                    JSONObject()
                        .put("type", "object")
                        .put("properties", dayProperties)
                        .put("required", JSONArray(DAYS))
                )
            )
            .put("required", JSONArray().put("days"))
    }

    private fun extractGeminiText(response: JSONObject): String {
        val blockReason = response.optJSONObject("promptFeedback")
            ?.optString("blockReason").orEmpty()
        if (blockReason.isNotEmpty()) {
            error("The model declined this request. Reword the program file and try again.")
        }
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: error("The model returned no schedule.")
        when (candidate.optString("finishReason")) {
            "MAX_TOKENS" -> error("The reply was cut off — try a shorter program file.")
            "SAFETY", "PROHIBITED_CONTENT", "RECITATION" ->
                error("The model declined this request. Reword the program file and try again.")
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: error("The model returned no schedule.")
        val text = buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }
        if (text.isBlank()) error("The model returned no schedule.")
        return text
    }

    // Shared ------------------------------------------------------------------

    private fun post(endpoint: String, headers: Map<String, String>, body: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            // The model can take a while on a full week's schedule.
            readTimeout = 300_000
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
                // Anthropic and Gemini both wrap errors as {"error": {"message": ...}}.
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
