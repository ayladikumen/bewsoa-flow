package ai.bewsoa.flow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.AiProgramUpdater
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.Export
import ai.bewsoa.flow.data.ExportBundle
import ai.bewsoa.flow.data.Insights
import ai.bewsoa.flow.data.ProgramDiff
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.TaskRepository
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import ai.bewsoa.flow.ui.share.ExportFormat
import ai.bewsoa.flow.ui.share.Sharing
import ai.bewsoa.flow.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProgramUiState(
    val mdText: String = "",
    val changeText: String = "",
    val theme: String = SettingsRepository.DEFAULT_THEME,
    val provider: String = SettingsRepository.PROVIDER_CLAUDE,
    val claudeKey: String = "",
    val geminiKey: String = "",
    val customActive: Boolean = false,
    val updatedAt: Long = 0L,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val error: String? = null,
    val justUpdated: Boolean = false,
    /** Short "what changed" lines, shown only right after a successful rebuild. */
    val diff: List<String>? = null
) {
    /** The key for the currently selected provider. */
    val apiKey: String
        get() = if (provider == SettingsRepository.PROVIDER_GEMINI) geminiKey else claudeKey

    /** The program's display name — the first markdown heading. */
    val programName: String
        get() = mdText.lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")?.trim()
            ?: "Weekly Program"
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository.get(app)

    private val _ui = MutableStateFlow(ProgramUiState())
    val ui: StateFlow<ProgramUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.value = ProgramUiState(
                mdText = settings.programMd.first() ?: loadBundledMd(),
                theme = settings.appTheme.first(),
                provider = settings.aiProvider.first(),
                claudeKey = settings.apiKey.first(),
                geminiKey = settings.geminiApiKey.first(),
                customActive = settings.programJson.first() != null,
                updatedAt = settings.programUpdatedAt.first()
            )
        }
    }

    private fun loadBundledMd(): String = runCatching {
        getApplication<Application>().assets.open("weekly_program.md")
            .bufferedReader().use { it.readText() }
    }.getOrDefault("")

    fun setChangeText(value: String) {
        _ui.value = _ui.value.copy(changeText = value, justUpdated = false)
    }

    fun setTheme(id: String) {
        _ui.value = _ui.value.copy(theme = id)
        viewModelScope.launch {
            settings.setAppTheme(id)
            // Widgets paint with the app palette — retheme them too.
            Widgets.refreshAll(getApplication())
        }
    }

    fun setProvider(value: String) {
        _ui.value = _ui.value.copy(provider = value, error = null)
        viewModelScope.launch { settings.setAiProvider(value) }
    }

    fun setApiKey(value: String) {
        val gemini = _ui.value.provider == SettingsRepository.PROVIDER_GEMINI
        _ui.value = if (gemini) {
            _ui.value.copy(geminiKey = value)
        } else {
            _ui.value.copy(claudeKey = value)
        }
        viewModelScope.launch {
            if (gemini) settings.setGeminiApiKey(value) else settings.setApiKey(value)
        }
    }

    /** Sends the current program plus the change request to the AI and installs the result. */
    fun rebuildWithAi() {
        val state = _ui.value
        if (state.loading) return
        if (state.apiKey.isBlank()) {
            val name = if (state.provider == SettingsRepository.PROVIDER_GEMINI) {
                "Gemini"
            } else {
                "Anthropic"
            }
            _ui.value = state.copy(error = "Enter your $name API key first.")
            return
        }
        if (state.changeText.isBlank() && state.customActive) {
            _ui.value = state.copy(error = "Write what you want changed first.")
            return
        }
        _ui.value = state.copy(loading = true, error = null, justUpdated = false, diff = null)
        viewModelScope.launch {
            val currentJson = if (state.customActive) settings.programJson.first() else null
            AiProgramUpdater.rebuild(
                state.provider, state.apiKey.trim(), state.mdText, currentJson, state.changeText
            )
                .onSuccess { json ->
                    val oldProgram = WeeklyProgram.weekMap()
                    val newProgram = CustomProgram.parse(json).getOrNull()
                    settings.setProgram(json, state.mdText)
                    CustomProgram.activate(json)
                    // Tomorrow's reminders must follow the new schedule.
                    TaskAlarmScheduler.scheduleUpcoming(getApplication())
                    Widgets.refreshAll(getApplication())
                    _ui.value = _ui.value.copy(
                        loading = false,
                        customActive = true,
                        updatedAt = System.currentTimeMillis(),
                        justUpdated = true,
                        changeText = "",
                        diff = newProgram?.let { ProgramDiff.summarize(oldProgram, it) }
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

    /**
     * Builds an export of the last [EXPORT_DAYS] days and hands the finished
     * text back through [onReady]; the screen owns the file write and the share
     * sheet, because nothing leaves the device until the user picks a target.
     */
    fun exportData(format: ExportFormat, onReady: (String, String) -> Unit) {
        if (_ui.value.exporting) return
        _ui.value = _ui.value.copy(exporting = true, error = null)
        viewModelScope.launch {
            runCatching {
                val app = getApplication<Application>()
                val programs = ProgramRepository.get(app)
                val tasks = TaskRepository.get(app)
                val to = LocalDate.now()
                val from = to.minusDays(EXPORT_DAYS)
                val bundle = ExportBundle(
                    from = from,
                    to = to,
                    completions = programs.getRange(from, to),
                    tasks = tasks.getRange(from, to),
                    reviews = programs.observeReviews().first(),
                    programJson = settings.programJson.first()
                )
                val content = when (format) {
                    ExportFormat.CSV -> Export.toCsv(bundle, WeeklyProgram::blocksFor)
                    ExportFormat.JSON -> Export.toJson(bundle, WeeklyProgram::blocksFor)
                    ExportFormat.MARKDOWN -> Export.toMarkdown(
                        bundle,
                        WeeklyProgram::blocksFor,
                        Insights.compute(to, bundle.completions, WeeklyProgram::blocksFor)
                    )
                }
                "bewsoa-flow-$to.${Sharing.extensionFor(format)}" to content
            }.onSuccess { (name, content) ->
                _ui.value = _ui.value.copy(exporting = false)
                onReady(name, content)
            }.onFailure { e ->
                _ui.value = _ui.value.copy(
                    exporting = false,
                    error = e.message ?: "Export failed."
                )
            }
        }
    }

    fun resetToBuiltIn() {
        viewModelScope.launch {
            settings.clearProgram()
            CustomProgram.clear()
            TaskAlarmScheduler.scheduleUpcoming(getApplication())
            Widgets.refreshAll(getApplication())
            _ui.value = _ui.value.copy(
                customActive = false,
                updatedAt = 0L,
                justUpdated = false,
                error = null,
                diff = null
            )
        }
    }

    companion object {
        /** Matches ProgramRepository's streak lookback — the app's whole memory. */
        private const val EXPORT_DAYS = 90L
    }
}
