package ai.bewsoa.flow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.AiProgramUpdater
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProgramUiState(
    val mdText: String = "",
    val apiKey: String = "",
    val customActive: Boolean = false,
    val updatedAt: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
    val justUpdated: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository.get(app)

    private val _ui = MutableStateFlow(ProgramUiState())
    val ui: StateFlow<ProgramUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.value = ProgramUiState(
                mdText = settings.programMd.first() ?: loadBundledMd(),
                apiKey = settings.apiKey.first(),
                customActive = settings.programJson.first() != null,
                updatedAt = settings.programUpdatedAt.first()
            )
        }
    }

    private fun loadBundledMd(): String = runCatching {
        getApplication<Application>().assets.open("weekly_program.md")
            .bufferedReader().use { it.readText() }
    }.getOrDefault("")

    fun setMdText(value: String) {
        _ui.value = _ui.value.copy(mdText = value, justUpdated = false)
    }

    fun setApiKey(value: String) {
        _ui.value = _ui.value.copy(apiKey = value)
        viewModelScope.launch { settings.setApiKey(value) }
    }

    /** Sends the markdown to the Claude API and installs the returned schedule. */
    fun rebuildWithAi() {
        val state = _ui.value
        if (state.loading) return
        if (state.apiKey.isBlank()) {
            _ui.value = state.copy(error = "Enter your Anthropic API key first.")
            return
        }
        if (state.mdText.isBlank()) {
            _ui.value = state.copy(error = "Write or paste your program markdown first.")
            return
        }
        _ui.value = state.copy(loading = true, error = null, justUpdated = false)
        viewModelScope.launch {
            AiProgramUpdater.rebuild(state.apiKey.trim(), state.mdText)
                .onSuccess { json ->
                    settings.setProgram(json, state.mdText)
                    CustomProgram.activate(json)
                    // Tomorrow's reminders must follow the new schedule.
                    TaskAlarmScheduler.scheduleUpcoming(getApplication())
                    _ui.value = _ui.value.copy(
                        loading = false,
                        customActive = true,
                        updatedAt = System.currentTimeMillis(),
                        justUpdated = true
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        error = e.message ?: "Something went wrong. Try again."
                    )
                }
        }
    }

    fun resetToBuiltIn() {
        viewModelScope.launch {
            settings.clearProgram()
            CustomProgram.clear()
            TaskAlarmScheduler.scheduleUpcoming(getApplication())
            _ui.value = _ui.value.copy(
                customActive = false,
                updatedAt = 0L,
                justUpdated = false,
                error = null
            )
        }
    }
}
