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

    /** Palette id from ui/theme/Palettes; also read by the widgets. */
    val appTheme: Flow<String> =
        context.settingsStore.data.map { it[KEY_APP_THEME] ?: DEFAULT_THEME }

    suspend fun setAppTheme(id: String) {
        context.settingsStore.edit { it[KEY_APP_THEME] = id }
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
        const val DEFAULT_THEME = "neon_night"

        private val KEY_OFFSET = intPreferencesKey("reminder_offset_minutes")
        private val KEY_CAPACITY = intPreferencesKey("daily_capacity_minutes")
        private val KEY_MOTIVATION = booleanPreferencesKey("motivation_enabled")
        private val KEY_INTENSITY = stringPreferencesKey("motivation_intensity")
        private val KEY_APP_THEME = stringPreferencesKey("app_theme")
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_API_KEY = stringPreferencesKey("anthropic_api_key")
        private val KEY_GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_PROGRAM_JSON = stringPreferencesKey("program_json")
        private val KEY_PROGRAM_MD = stringPreferencesKey("program_md")
        private val KEY_PROGRAM_UPDATED = longPreferencesKey("program_updated_at")
        private val KEY_PROPOSAL_JSON = stringPreferencesKey("coach_proposal_json")
        private val KEY_PROPOSAL_NOTE = stringPreferencesKey("coach_proposal_note")

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
