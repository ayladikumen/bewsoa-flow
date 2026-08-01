package ai.bewsoa.flow.data.exacthour

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The wire format of the Exact Hour clock (remote_control.py on the Pi).
 *
 * Deliberately free of Android imports so the whole parse layer is unit
 * testable on the JVM. The one rule every parser here follows: **never throw**.
 * The device answers 200 to almost everything and degrades malformed input to
 * defaults rather than erroring, so a client that blows up on a missing field
 * would be stricter than the server it talks to.
 */

/** Base URL + a display name for a clock the user has pointed us at. */
data class ClockEndpoint(val host: String, val port: Int, val name: String = "") {
    val baseUrl: String get() = "http://$host:$port"
    fun url(path: String): String = baseUrl + path
}

enum class ClockState {
    IDLE, RUNNING, PAUSED, FINISHED;

    companion object {
        /**
         * The device sends uppercase names. Never `valueOf` or `uppercase()`
         * here: this app's users run tr-TR, where "I".lowercase() is "ı" and
         * the round trip stops matching.
         */
        fun parse(raw: String?): ClockState {
            val trimmed = raw?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) } ?: IDLE
        }
    }
}

/** Progress of a running Automations preset, or null when none is loaded. */
data class AutomationStatus(
    val running: Boolean,
    val name: String?,
    val label: String?,
    val step: Int,
    val steps: Int,
    val error: String?
) {
    companion object {
        fun from(json: JSONObject): AutomationStatus = AutomationStatus(
            running = json.optBoolean("running", false),
            name = json.optStringOrNull("name"),
            label = json.optStringOrNull("label"),
            step = json.optInt("step", 0),
            steps = json.optInt("steps", 0),
            error = json.optStringOrNull("error")
        )
    }
}

/**
 * One status snapshot. Every route except `/api/automations/upload` answers
 * with exactly this shape, so it is the app's single view of the device.
 */
data class ClockStatus(
    val state: ClockState,
    val minutes: Int,
    val seconds: Int,
    val remainingSeconds: Int,
    /**
     * Render this verbatim. The device formats "5:00", "1:05:00" past an hour,
     * and the literal "BITTI" when finished — recomputing from minutes/seconds
     * would lose the last two.
     */
    val display: String,
    val maxMinutes: Int,
    val name: String,
    /** Non-null while a text overlay is showing. */
    val message: String?,
    val automation: AutomationStatus?
) {
    /**
     * /api/adjust and /api/set are *silently ignored* while the timer runs —
     * the same lock the physical UP/DOWN buttons have. Nothing comes back to
     * say so, so the UI has to grey those controls out itself.
     */
    val timerEditable: Boolean get() = state != ClockState.RUNNING

    /** A failed automation is an HTTP 200 with the reason in here. */
    val automationError: String? get() = automation?.error?.takeIf { it.isNotBlank() }

    val automationRunning: Boolean get() = automation?.running == true

    companion object {
        fun from(json: JSONObject): ClockStatus {
            val minutes = json.optInt("minutes", 0)
            val seconds = json.optInt("seconds", 0)
            return ClockStatus(
                state = ClockState.parse(json.optStringOrNull("state")),
                minutes = minutes,
                seconds = seconds,
                remainingSeconds = json.optInt("remaining_seconds", minutes * 60 + seconds),
                // Locale.US: the fallback must produce ASCII digits whatever
                // the phone's locale does with numbers.
                display = json.optStringOrNull("display")
                    ?: String.format(Locale.US, "%d:%02d", minutes, seconds),
                maxMinutes = json.optInt("max_minutes", ClockLimits.DEFAULT_MAX_MINUTES),
                name = json.optStringOrNull("name") ?: "Exact Hour",
                message = json.optStringOrNull("message"),
                automation = json.optJSONObject("automation")?.let(AutomationStatus::from)
            )
        }

        /**
         * A body is a clock snapshot if it names itself, or failing that if it
         * carries the two fields nothing else would. The second clause is what
         * keeps discovery working on a device the user has renamed.
         */
        fun looksLikeClock(json: JSONObject): Boolean {
            val name = json.optStringOrNull("name")
            if (name != null && name.startsWith("Exact Hour", ignoreCase = true)) return true
            return json.has("state") && json.has("display")
        }
    }
}

// Automations ---------------------------------------------------------------

/**
 * A step's minutes/seconds and a template's repeat are **either** a literal
 * number **or** the name of one of the template's params — the device resolves
 * the reference when the preset runs.
 */
sealed interface ParamValue {
    data class Literal(val value: Int) : ParamValue
    data class Ref(val name: String) : ParamValue

    fun resolve(params: Map<String, Int>, fallback: Int = 0): Int = when (this) {
        is Literal -> value
        is Ref -> params[name] ?: fallback
    }

    companion object {
        fun from(raw: Any?): ParamValue = when (raw) {
            is Number -> Literal(raw.toInt())
            is String -> raw.trim().toIntOrNull()?.let(::Literal)
                ?: Ref(raw.trim())
            else -> Literal(0)
        }
    }
}

data class AutomationParam(
    val name: String,
    val label: String,
    val default: Int,
    val min: Int,
    val max: Int
) {
    fun clamp(value: Int): Int = if (min <= max) value.coerceIn(min, max) else value

    companion object {
        fun from(json: JSONObject): AutomationParam {
            val name = json.optStringOrNull("name").orEmpty()
            return AutomationParam(
                name = name,
                label = json.optStringOrNull("label") ?: name,
                default = json.optInt("default", 0),
                min = json.optInt("min", 0),
                max = json.optInt("max", ClockLimits.DEFAULT_MAX_MINUTES)
            )
        }
    }
}

data class AutomationStep(
    val label: String,
    val text: String,
    val minutes: ParamValue,
    val seconds: ParamValue
) {
    companion object {
        fun from(json: JSONObject): AutomationStep = AutomationStep(
            label = json.optStringOrNull("label").orEmpty(),
            text = json.optStringOrNull("text").orEmpty(),
            minutes = ParamValue.from(json.opt("minutes")),
            seconds = ParamValue.from(json.opt("seconds"))
        )
    }
}

data class AutomationTemplate(
    val id: String,
    val name: String,
    val description: String,
    val params: List<AutomationParam>,
    val repeat: ParamValue,
    val steps: List<AutomationStep>
) {
    fun defaults(): Map<String, Int> = params.associate { it.name to it.default }

    companion object {
        fun from(json: JSONObject): AutomationTemplate {
            val id = json.optStringOrNull("id").orEmpty()
            return AutomationTemplate(
                id = id,
                name = json.optStringOrNull("name") ?: id,
                description = json.optStringOrNull("description").orEmpty(),
                params = json.optJSONArray("params").mapObjects(AutomationParam::from)
                    .filter { it.name.isNotEmpty() },
                repeat = ParamValue.from(json.opt("repeat")),
                steps = json.optJSONArray("steps").mapObjects(AutomationStep::from)
            )
        }

        /** `GET /api/automations` answers `{"templates": [...]}`, not a bare array. */
        fun catalog(json: JSONObject): List<AutomationTemplate> =
            json.optJSONArray("templates").mapObjects(::from).filter { it.id.isNotEmpty() }
    }
}

// Text overlay --------------------------------------------------------------

/**
 * Text painted on top of the clock. The countdown keeps running underneath and
 * the digits come back when the message ends — which is exactly why the block
 * announce rides on top of an already-started countdown instead of delaying it.
 */
data class TextOverlay(
    val text: String,
    val mode: String = MODE_AUTO,
    val speed: Double = DEFAULT_SPEED,
    val repeat: Int = DEFAULT_REPEAT,
    val hold: Double = DEFAULT_HOLD
) {
    /**
     * The device clamps all of these itself and never rejects anything, but
     * clamping here too means the UI never shows a number the clock won't
     * honour.
     */
    fun sanitized(): TextOverlay = copy(
        text = displayable(text).take(MAX_CHARS),
        mode = if (mode in MODES) mode else MODE_AUTO,
        speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
        repeat = repeat.coerceIn(MIN_REPEAT, MAX_REPEAT),
        hold = hold.coerceIn(MIN_HOLD, MAX_HOLD)
    )

    fun toJson(): JSONObject = sanitized().let {
        JSONObject()
            .put("text", it.text)
            .put("mode", it.mode)
            .put("speed", it.speed)
            .put("repeat", it.repeat)
            .put("hold", it.hold)
    }

    companion object {
        const val MODE_STATIC = "static"
        const val MODE_SCROLL = "scroll"
        const val MODE_AUTO = "auto"
        val MODES = setOf(MODE_STATIC, MODE_SCROLL, MODE_AUTO)

        const val MAX_CHARS = 120
        const val MIN_SPEED = 1.0
        const val MAX_SPEED = 60.0
        const val DEFAULT_SPEED = 14.0
        const val MIN_HOLD = 0.5
        const val MAX_HOLD = 600.0
        const val DEFAULT_HOLD = 3.0
        const val MIN_REPEAT = 1
        const val MAX_REPEAT = 99
        const val DEFAULT_REPEAT = 1

        /** An empty message clears whatever is showing. */
        fun clear(): TextOverlay = TextOverlay("")

        /**
         * Every character the 7-pixel font can actually draw (see the clock's
         * `text_display.py`). Everything else the device silently turns into a
         * blank — so an emoji doesn't disappear, it becomes a hole in the
         * message.
         */
        private const val GLYPHS = " !'+,-./0123456789:?ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        /** Turkish letters the device folds onto ASCII, so they're safe to send. */
        private val FOLD = mapOf(
            'ç' to 'c', 'Ç' to 'C', 'ğ' to 'g', 'Ğ' to 'G', 'ı' to 'i', 'İ' to 'I',
            'ö' to 'o', 'Ö' to 'O', 'ş' to 's', 'Ş' to 'S', 'ü' to 'u', 'Ü' to 'U'
        )

        /**
         * Drop what the matrix can't draw, instead of letting the device turn
         * it into whitespace.
         *
         * The clock's own `clean()` substitutes a space for every glyph it
         * lacks, which is the right call for it — a message always draws. But
         * an emoji is two codepoints, so "🏋️ Gym" arrives as "   GYM" with the
         * gap still in it. Removing them here and collapsing the leftover
         * spaces is what makes an announce read as a sentence rather than a
         * ransom note.
         */
        fun displayable(raw: String): String {
            val kept = buildString {
                raw.forEach { ch ->
                    val folded = FOLD[ch] ?: ch
                    if (folded.uppercaseChar() in GLYPHS) append(folded)
                }
            }
            return kept.replace(SPACES, " ").trim()
        }

        private val SPACES = Regex("\\s{2,}")
    }
}

object ClockLimits {
    const val DEFAULT_MAX_MINUTES = 270
    const val DEFAULT_PORT = 8080

    /** Ports `dev/demo_server.py` lands on, in the order it tries them. */
    val DEMO_PORTS = listOf(8731, 8732, 8733)

    fun clampMinutes(minutes: Int, max: Int = DEFAULT_MAX_MINUTES): Int = minutes.coerceIn(0, max)

    fun clampSeconds(seconds: Int): Int = seconds.coerceIn(0, 59)
}

// Errors --------------------------------------------------------------------

/**
 * Typed failures, so the background mirror can swallow "the Pi is unplugged"
 * while the remote screen says something useful about it.
 */
sealed class ExactHourError(message: String) : Exception(message) {
    data object NotConfigured : ExactHourError("No clock set up yet.")

    class NotPrivate(host: String) : ExactHourError("$host isn't on a private network.")

    data object OffNetwork : ExactHourError("Join the clock's Wi-Fi first.")

    class Unreachable(host: String) : ExactHourError("Couldn't reach the clock at $host.")

    class NotFound(path: String) : ExactHourError("The clock doesn't know $path.")

    data object AutomationsDisabled : ExactHourError("Automations are switched off on the clock.")

    data object Malformed : ExactHourError("The clock sent something unreadable.")

    class Unexpected(val code: Int) : ExactHourError("The clock answered $code.")
}

// org.json helpers ----------------------------------------------------------

/**
 * org.json turns a JSON null into the string "null" via optString, which has
 * bitten every hand-rolled client in this app at least once. Nullable means
 * nullable.
 */
internal fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotEmpty() }
}

internal fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }.map(transform)
}
