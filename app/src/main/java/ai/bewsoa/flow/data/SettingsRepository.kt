package ai.bewsoa.flow.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "bewsoa_settings")

class SettingsRepository private constructor(private val context: Context) {

    /** Minutes after a block's end time before the reminder fires. */
    val reminderOffsetMinutes: Flow<Int> =
        context.settingsStore.data.map { it[KEY_OFFSET] ?: DEFAULT_OFFSET }

    val motivationEnabled: Flow<Boolean> =
        context.settingsStore.data.map { it[KEY_MOTIVATION] ?: true }

    val motivationIntensity: Flow<String> =
        context.settingsStore.data.map { it[KEY_INTENSITY] ?: INTENSITY_NORMAL }

    /** Minutes of task work the user considers a full day — the capacity ceiling for
     *  the Parkinson's-law warning on the Today task list. */
    val dailyCapacityMinutes: Flow<Int> =
        context.settingsStore.data.map { it[KEY_CAPACITY] ?: DEFAULT_CAPACITY }

    suspend fun setDailyCapacityMinutes(minutes: Int) {
        context.settingsStore.edit { it[KEY_CAPACITY] = minutes.coerceIn(60, 16 * 60) }
    }

    /** JSON map date → block-id order, backing [DayBlockOrder]. */
    val dayOrderJson: Flow<String?> =
        context.settingsStore.data.map { it[KEY_DAY_ORDER] }

    suspend fun setDayOrderJson(json: String) {
        context.settingsStore.edit { it[KEY_DAY_ORDER] = json }
    }

    /** JSON map date → full block list, backing [DayOverrides] (one-time AI edits). */
    val dayOverridesJson: Flow<String?> =
        context.settingsStore.data.map { it[KEY_DAY_OVERRIDES] }

    suspend fun setDayOverridesJson(json: String) {
        context.settingsStore.edit { it[KEY_DAY_OVERRIDES] = json }
    }

    /** The assistant conversation, serialized by the chat screen. */
    val chatHistoryJson: Flow<String?> =
        context.settingsStore.data.map { it[KEY_CHAT_HISTORY] }

    suspend fun setChatHistoryJson(json: String) {
        context.settingsStore.edit { it[KEY_CHAT_HISTORY] = json }
    }

    suspend fun clearChatHistory() {
        context.settingsStore.edit { it.remove(KEY_CHAT_HISTORY) }
    }

    /** Last versionCode whose "What's new" the user has dismissed. */
    val seenVersionCode: Flow<Int> =
        context.settingsStore.data.map { it[KEY_SEEN_VERSION] ?: 0 }

    suspend fun setSeenVersionCode(version: Int) {
        context.settingsStore.edit { it[KEY_SEEN_VERSION] = version }
    }

    // Active Deep Focus session — persisted so the countdown survives app restarts.

    val focusLabel: Flow<String?> =
        context.settingsStore.data.map { it[KEY_FOCUS_LABEL] }

    val focusStartedAt: Flow<Long> =
        context.settingsStore.data.map { it[KEY_FOCUS_STARTED] ?: 0L }

    val focusDurationMinutes: Flow<Int> =
        context.settingsStore.data.map { it[KEY_FOCUS_MINUTES] ?: 0 }

    suspend fun setFocusSession(label: String, startedAt: Long, minutes: Int) {
        context.settingsStore.edit {
            it[KEY_FOCUS_LABEL] = label
            it[KEY_FOCUS_STARTED] = startedAt
            it[KEY_FOCUS_MINUTES] = minutes
        }
    }

    suspend fun clearFocusSession() {
        context.settingsStore.edit {
            it.remove(KEY_FOCUS_LABEL)
            it.remove(KEY_FOCUS_STARTED)
            it.remove(KEY_FOCUS_MINUTES)
        }
    }

    /** Palette id from ui/theme/Palettes; also read by the widgets. */
    val appTheme: Flow<String> =
        context.settingsStore.data.map { it[KEY_APP_THEME] ?: DEFAULT_THEME }

    suspend fun setAppTheme(id: String) {
        context.settingsStore.edit { it[KEY_APP_THEME] = id }
    }

    /**
     * The 3.0 "new start" happens exactly once: whatever palette an older
     * version had saved, the first 3.0 launch flips to Sunrise so the redesign
     * actually greets the user. Their next pick in Profile is final — this
     * flag guarantees we never override a choice twice.
     */
    suspend fun migrateThemeToSunriseOnce() {
        context.settingsStore.edit {
            if (it[KEY_SUNRISE_INTRO] != true) {
                it[KEY_APP_THEME] = DEFAULT_THEME
                it[KEY_SUNRISE_INTRO] = true
            }
        }
    }

    suspend fun setReminderOffset(minutes: Int) {
        context.settingsStore.edit { it[KEY_OFFSET] = minutes.coerceIn(0, 60) }
    }

    suspend fun setMotivationEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_MOTIVATION] = enabled }
    }

    suspend fun setMotivationIntensity(value: String) {
        context.settingsStore.edit { it[KEY_INTENSITY] = value }
    }

    // Exact Hour clock -------------------------------------------------------
    // The LED matrix clock on the LAN. Everything here is plain local state:
    // an IP the user typed or discovered, plus the mirror's own bookkeeping.

    val clockEnabled: Flow<Boolean> =
        context.settingsStore.data.map { it[KEY_CLOCK_ENABLED] ?: false }

    val clockHost: Flow<String> =
        context.settingsStore.data.map { it[KEY_CLOCK_HOST] ?: "" }

    val clockPort: Flow<Int> =
        context.settingsStore.data.map { it[KEY_CLOCK_PORT] ?: DEFAULT_CLOCK_PORT }

    /** Whatever the device calls itself — "Exact Hour", or "Exact Hour (demo)". */
    val clockName: Flow<String> =
        context.settingsStore.data.map { it[KEY_CLOCK_NAME] ?: "" }

    val clockMirrorFocus: Flow<Boolean> =
        context.settingsStore.data.map { it[KEY_CLOCK_MIRROR_FOCUS] ?: true }

    val clockMirrorBlocks: Flow<Boolean> =
        context.settingsStore.data.map { it[KEY_CLOCK_MIRROR_BLOCKS] ?: true }

    /** While this is in the future the auto-mirror keeps its hands off. */
    val clockHoldUntil: Flow<Long> =
        context.settingsStore.data.map { it[KEY_CLOCK_HOLD_UNTIL] ?: 0L }

    /** Fingerprint of the last plan successfully pushed — the no-op guard. */
    val clockLastPlanKey: Flow<String> =
        context.settingsStore.data.map { it[KEY_CLOCK_LAST_PLAN] ?: "" }

    val clockLastSeenAt: Flow<Long> =
        context.settingsStore.data.map { it[KEY_CLOCK_LAST_SEEN] ?: 0L }

    suspend fun setClockEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_CLOCK_ENABLED] = enabled }
    }

    /** Host, port and name move together — they're one device, not three settings. */
    suspend fun setClockEndpoint(host: String, port: Int, name: String = "") {
        context.settingsStore.edit {
            it[KEY_CLOCK_HOST] = host.trim()
            it[KEY_CLOCK_PORT] = if (port in 1..65535) port else DEFAULT_CLOCK_PORT
            it[KEY_CLOCK_NAME] = name
            // A different box means the old plan key says nothing about what
            // this one is showing.
            it[KEY_CLOCK_LAST_PLAN] = ""
        }
    }

    suspend fun setClockMirrorFocus(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_CLOCK_MIRROR_FOCUS] = enabled }
    }

    suspend fun setClockMirrorBlocks(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_CLOCK_MIRROR_BLOCKS] = enabled }
    }

    suspend fun setClockHoldUntil(epochMillis: Long) {
        context.settingsStore.edit { it[KEY_CLOCK_HOLD_UNTIL] = epochMillis }
    }

    suspend fun setClockLastPlanKey(key: String) {
        context.settingsStore.edit { it[KEY_CLOCK_LAST_PLAN] = key }
    }

    suspend fun setClockLastSeenAt(epochMillis: Long) {
        context.settingsStore.edit { it[KEY_CLOCK_LAST_SEEN] = epochMillis }
    }

    // Program override (MD + AI) --------------------------------------------

    /** Which AI rebuilds the program: [PROVIDER_CLAUDE] or [PROVIDER_GEMINI]. */
    val aiProvider: Flow<String> =
        context.settingsStore.data.map { it[KEY_AI_PROVIDER] ?: PROVIDER_CLAUDE }

    /** Anthropic API key for the program updater. Stored locally, never synced. */
    val apiKey: Flow<String> =
        context.settingsStore.data.map { it[KEY_API_KEY] ?: "" }

    /** Google Gemini API key for the program updater. Stored locally, never synced. */
    val geminiApiKey: Flow<String> =
        context.settingsStore.data.map { it[KEY_GEMINI_KEY] ?: "" }

    /** The generated schedule JSON; null means the built-in program is active. */
    val programJson: Flow<String?> =
        context.settingsStore.data.map { it[KEY_PROGRAM_JSON] }

    /** The user's last markdown source, so edits survive app restarts. */
    val programMd: Flow<String?> =
        context.settingsStore.data.map { it[KEY_PROGRAM_MD] }

    val programUpdatedAt: Flow<Long> =
        context.settingsStore.data.map { it[KEY_PROGRAM_UPDATED] ?: 0L }

    // Weekly coach proposal, waiting for the user to accept or dismiss ---------

    val pendingProposalJson: Flow<String?> =
        context.settingsStore.data.map { it[KEY_PROPOSAL_JSON] }

    val pendingProposalNote: Flow<String?> =
        context.settingsStore.data.map { it[KEY_PROPOSAL_NOTE] }

    suspend fun setPendingProposal(json: String, note: String) {
        context.settingsStore.edit {
            it[KEY_PROPOSAL_JSON] = json
            it[KEY_PROPOSAL_NOTE] = note
        }
    }

    suspend fun clearPendingProposal() {
        context.settingsStore.edit {
            it.remove(KEY_PROPOSAL_JSON)
            it.remove(KEY_PROPOSAL_NOTE)
        }
    }

    /** Installs a new schedule without touching the stored markdown source. */
    suspend fun setProgramJson(json: String) {
        context.settingsStore.edit {
            it[KEY_PROGRAM_JSON] = json
            it[KEY_PROGRAM_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun setAiProvider(value: String) {
        context.settingsStore.edit { it[KEY_AI_PROVIDER] = value }
    }

    suspend fun setApiKey(key: String) {
        context.settingsStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.settingsStore.edit { it[KEY_GEMINI_KEY] = key }
    }

    suspend fun setProgram(json: String, markdown: String) {
        context.settingsStore.edit {
            it[KEY_PROGRAM_JSON] = json
            it[KEY_PROGRAM_MD] = markdown
            it[KEY_PROGRAM_UPDATED] = System.currentTimeMillis()
        }
    }

    /** Back to the built-in program; keeps the markdown so edits aren't lost. */
    suspend fun clearProgram() {
        context.settingsStore.edit {
            it.remove(KEY_PROGRAM_JSON)
            it.remove(KEY_PROGRAM_UPDATED)
        }
    }

    companion object {
        const val DEFAULT_OFFSET = 20
        const val DEFAULT_CAPACITY = 480
        const val INTENSITY_CHILL = "chill"
        const val INTENSITY_NORMAL = "normal"
        const val INTENSITY_BEAST = "beast"
        const val PROVIDER_CLAUDE = "claude"
        const val PROVIDER_GEMINI = "gemini"
        // The 3.0 "new start": Sunrise is the default unless the user has
        // explicitly picked another palette in Settings.
        const val DEFAULT_THEME = "sunrise"
        const val DEFAULT_CLOCK_PORT = 8080
        /** How long a manual command on the remote screen pauses the mirror. */
        const val CLOCK_HOLD_MINUTES = 30

        private val KEY_OFFSET = intPreferencesKey("reminder_offset_minutes")
        private val KEY_CAPACITY = intPreferencesKey("daily_capacity_minutes")
        private val KEY_MOTIVATION = booleanPreferencesKey("motivation_enabled")
        private val KEY_INTENSITY = stringPreferencesKey("motivation_intensity")
        private val KEY_APP_THEME = stringPreferencesKey("app_theme")
        private val KEY_SUNRISE_INTRO = booleanPreferencesKey("sunrise_intro_done")
        private val KEY_DAY_ORDER = stringPreferencesKey("day_block_order")
        private val KEY_DAY_OVERRIDES = stringPreferencesKey("day_overrides_json")
        private val KEY_CHAT_HISTORY = stringPreferencesKey("chat_history_json")
        private val KEY_SEEN_VERSION = intPreferencesKey("seen_version_code")
        private val KEY_FOCUS_LABEL = stringPreferencesKey("focus_label")
        private val KEY_FOCUS_STARTED = longPreferencesKey("focus_started_at")
        private val KEY_FOCUS_MINUTES = intPreferencesKey("focus_duration_minutes")
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_API_KEY = stringPreferencesKey("anthropic_api_key")
        private val KEY_GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_PROGRAM_JSON = stringPreferencesKey("program_json")
        private val KEY_PROGRAM_MD = stringPreferencesKey("program_md")
        private val KEY_PROGRAM_UPDATED = longPreferencesKey("program_updated_at")
        private val KEY_PROPOSAL_JSON = stringPreferencesKey("coach_proposal_json")
        private val KEY_PROPOSAL_NOTE = stringPreferencesKey("coach_proposal_note")
        private val KEY_CLOCK_ENABLED = booleanPreferencesKey("exact_hour_enabled")
        private val KEY_CLOCK_HOST = stringPreferencesKey("exact_hour_host")
        private val KEY_CLOCK_PORT = intPreferencesKey("exact_hour_port")
        private val KEY_CLOCK_NAME = stringPreferencesKey("exact_hour_name")
        private val KEY_CLOCK_MIRROR_FOCUS = booleanPreferencesKey("exact_hour_mirror_focus")
        private val KEY_CLOCK_MIRROR_BLOCKS = booleanPreferencesKey("exact_hour_mirror_blocks")
        private val KEY_CLOCK_HOLD_UNTIL = longPreferencesKey("exact_hour_hold_until")
        private val KEY_CLOCK_LAST_PLAN = stringPreferencesKey("exact_hour_last_plan_key")
        private val KEY_CLOCK_LAST_SEEN = longPreferencesKey("exact_hour_last_seen_at")

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
