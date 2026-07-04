package ai.bewsoa.flow.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Turns the weekly program markdown into the app's JSON schedule by calling
 * the Claude API (Messages API, structured outputs — the response is
 * guaranteed to match [SCHEMA], so parsing never depends on prompt luck).
 */
object AiProgramUpdater {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-opus-4-8"

    private const val SYSTEM_PROMPT =
        "You convert a personal weekly study/build program written in markdown into a JSON " +
            "schedule for the Bewsoa Flow app. Rules: give every day of the week its blocks in " +
            "chronological order with non-overlapping 24h HH:MM times. Tracks: YKS (main exam " +
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

    // Structured-output schema: every object closed with additionalProperties=false.
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

    /** Returns the validated program JSON, ready for [CustomProgram.activate]. */
    suspend fun rebuild(apiKey: String, markdown: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = post(apiKey, buildBody(markdown))
                val programJson = extractText(response)
                // Validate before anyone stores it.
                CustomProgram.parse(programJson).getOrThrow()
                programJson
            }
        }

    private fun buildBody(markdown: String): String = JSONObject()
        .put("model", MODEL)
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
                    .put("content", "Convert this weekly program to the JSON schedule:\n\n$markdown")
            )
        )
        .toString()

    private fun post(apiKey: String, body: String): JSONObject {
        val connection = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            // Opus can think for a while on a full week's schedule.
            readTimeout = 300_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
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

    private fun extractText(response: JSONObject): String {
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
}
