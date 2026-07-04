package ai.bewsoa.flow.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    suspend fun setReminderOffset(minutes: Int) {
        context.settingsStore.edit { it[KEY_OFFSET] = minutes.coerceIn(0, 60) }
    }

    suspend fun setMotivationEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_MOTIVATION] = enabled }
    }

    suspend fun setMotivationIntensity(value: String) {
        context.settingsStore.edit { it[KEY_INTENSITY] = value }
    }

    companion object {
        const val DEFAULT_OFFSET = 20
        const val INTENSITY_CHILL = "chill"
        const val INTENSITY_NORMAL = "normal"
        const val INTENSITY_BEAST = "beast"

        private val KEY_OFFSET = intPreferencesKey("reminder_offset_minutes")
        private val KEY_MOTIVATION = booleanPreferencesKey("motivation_enabled")
        private val KEY_INTENSITY = stringPreferencesKey("motivation_intensity")

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
