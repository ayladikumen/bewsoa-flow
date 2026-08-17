package ai.bewsoa.flow.ui.program

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.AiProgramUpdater
import ai.bewsoa.flow.data.ProgramDiff
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.ProgramTemplate
import ai.bewsoa.flow.data.ProgramTemplates
import ai.bewsoa.flow.data.ProgramValidation
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.Track
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.data.WeeklyProgramDraft
import ai.bewsoa.flow.data.WeeklyProgramValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** The two halves of the builder: describe the week, or edit it block by block. */
enum class BuilderMode { AI, EDITOR }

/** Which entry point opened the builder, so it starts on the right foot. */
enum class BuilderStart {
    /** Generic "create / edit program" — pick up the standing program if there is one. */
    MENU,

    /** "Build with AI" — blank canvas, AI side. */
    AI,

    /** "Start from scratch" — blank canvas, editor side. */
    SCRATCH,

    /** "Use current week as template" — the standing program, editor side. */
    CURRENT;

    companion object {
        fun of(raw: String?): BuilderStart =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MENU
    }
}

/** The compact "what you are about to save" numbers shown before saving. */
data class ProgramSummary(
    val configuredDays: Int = 0,
    val totalMinutes: Long = 0L,
    val countedMinutes: Long = 0L,
    val studyBlocks: Int = 0,
    val gymBlocks: Int = 0,
    val freeBlocks: Int = 0
)

data class ProgramBuilderUiState(
    val mode: BuilderMode = BuilderMode.AI,
    val draft: WeeklyProgramDraft = WeeklyProgramDraft.EMPTY,
    val selectedDay: DayOfWeek = DayOfWeek.MONDAY,
    val request: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val aiAvailable: Boolean = false,
    /** A custom program is already active, so this is an edit, not a first build. */
    val editingExisting: Boolean = false,
    /** The draft has been touched since it was loaded — the unsaved-changes flag. */
    val touched: Boolean = false,
    val validation: ProgramValidation = ProgramValidation.CLEAN,
    /** Human "what changed" lines against the standing program, from [ProgramDiff]. */
    val changes: List<String> = emptyList(),
    val summary: ProgramSummary = ProgramSummary(),
    val reviewing: Boolean = false,
    val saved: Boolean = false
) {
    val canSave: Boolean get() = !busy && !draft.isEmpty && validation.isSavable
}

/**
 * Owns the weekly draft while the user builds it.
 *
 * Nothing here touches the active program: the AI writes into the draft, manual
 * edits rewrite the draft, and only [save] hands it to
 * [ProgramRepository.saveWeeklyProgram] — the same pipeline Chat's week draft
 * and the coach's proposal go through. The draft lives in the ViewModel, so it
 * survives rotation and coming back from another screen in the same session.
 */
class ProgramBuilderViewModel(
    app: Application,
    private val programs: ProgramRepository
) : AndroidViewModel(app) {

    private val settings = SettingsRepository.get(app)

    /**
     * The standing program as it was when the builder opened — the base every
     * diff is taken against. Never a day override: this edits the recurring week.
     */
    private var baseline: Map<DayOfWeek, List<TaskBlock>> = emptyMap()

    private val _ui = MutableStateFlow(
        ProgramBuilderUiState(selectedDay = LocalDate.now().dayOfWeek)
    )
    val ui: StateFlow<ProgramBuilderUiState> = _ui.asStateFlow()

    private var started = false

    /** Called once by the screen with the entry point that opened it. */
    fun begin(start: BuilderStart) {
        if (started) return
        started = true
        viewModelScope.launch {
            val provider = settings.aiProvider.first()
            val hasKey = apiKeyFor(provider).isNotBlank()
            val customActive = settings.programJson.first() != null
            baseline = WeeklyProgram.standingWeekMap()

            val draft: WeeklyProgramDraft
            val mode: BuilderMode
            when (start) {
                BuilderStart.AI -> {
                    draft = WeeklyProgramDraft.EMPTY
                    mode = BuilderMode.AI
                }
                BuilderStart.SCRATCH -> {
                    draft = WeeklyProgramDraft.EMPTY
                    mode = BuilderMode.EDITOR
                }
                BuilderStart.CURRENT -> {
                    draft = ProgramTemplates.fromStandingProgram()
                    mode = BuilderMode.EDITOR
                }
                BuilderStart.MENU -> {
                    // Editing an existing program starts from it; a first build
                    // starts on the AI side, or in the editor with no key.
                    draft = if (customActive) {
                        ProgramTemplates.fromStandingProgram()
                    } else {
                        WeeklyProgramDraft.EMPTY
                    }
                    mode = when {
                        customActive -> BuilderMode.EDITOR
                        hasKey -> BuilderMode.AI
                        else -> BuilderMode.EDITOR
                    }
                }
            }
            _ui.value = _ui.value.copy(
                mode = mode,
                aiAvailable = hasKey,
                editingExisting = customActive
            )
            install(draft, touched = false)
        }
    }

    // Mode, day, transient messages ---------------------------------------------

    fun setMode(mode: BuilderMode) {
        _ui.value = _ui.value.copy(mode = mode, error = null)
    }

    fun setRequest(text: String) {
        _ui.value = _ui.value.copy(request = text, error = null)
    }

    fun selectDay(day: DayOfWeek) {
        _ui.value = _ui.value.copy(selectedDay = day, notice = null)
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null, error = null)
    }

    // Starting points ------------------------------------------------------------

    fun useTemplate(template: ProgramTemplate) {
        install(
            template.draft(),
            touched = true,
            notice = "${template.name} loaded — edit anything before saving."
        )
        _ui.value = _ui.value.copy(mode = BuilderMode.EDITOR)
    }

    /** "Use current program as template" — the recurring week, not today's plan. */
    fun useCurrentProgram() {
        install(
            ProgramTemplates.fromStandingProgram(),
            touched = true,
            notice = "Your current week loaded as a starting point."
        )
        _ui.value = _ui.value.copy(mode = BuilderMode.EDITOR)
    }

    // AI build mode --------------------------------------------------------------

    /**
     * Asks the AI for a week. The reply becomes a draft in the editor — it is
     * never activated here, and the active program is untouched either way.
     */
    fun generateWithAi() {
        val state = _ui.value
        if (state.busy) return
        val request = state.request.trim()
        if (request.isEmpty()) {
            _ui.value = state.copy(error = "Tell me what your week looks like first.")
            return
        }
        _ui.value = state.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            val provider = settings.aiProvider.first()
            val key = apiKeyFor(provider).trim()
            if (key.isBlank()) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = "Add your API key in Profile → Program to build with AI."
                )
                return@launch
            }
            // A week already on the canvas is sent along, so "make it more
            // realistic" reworks what the user is looking at.
            val base = state.draft.takeIf { !it.isEmpty }?.toJson()
            AiProgramUpdater.buildWeek(provider, key, request, base).fold(
                onSuccess = { json ->
                    WeeklyProgramDraft.fromJson(json).fold(
                        onSuccess = { draft ->
                            _ui.value = _ui.value.copy(
                                busy = false,
                                mode = BuilderMode.EDITOR,
                                request = ""
                            )
                            install(
                                draft,
                                touched = true,
                                notice = "Draft ready — nothing is saved yet. Edit anything, then save."
                            )
                        },
                        onFailure = { e ->
                            _ui.value = _ui.value.copy(
                                busy = false,
                                error = e.message ?: "That schedule couldn't be read. Try again."
                            )
                        }
                    )
                },
                onFailure = { e ->
                    _ui.value = _ui.value.copy(
                        busy = false,
                        error = e.message ?: "Something went wrong. Try again."
                    )
                }
            )
        }
    }

    // Manual editing -------------------------------------------------------------

    /** Adds a block to [day], and optionally the same block to [alsoOn] as copies. */
    fun addBlock(day: DayOfWeek, block: TaskBlock, alsoOn: Set<DayOfWeek> = emptySet()) {
        edit(noticeFor("Added “${block.title}”", alsoOn)) { draft ->
            draft.addBlock(day, block).addBlockToDays(alsoOn - day, block)
        }
    }

    /** Saves an edit in place: the id, and so the block's history, is preserved. */
    fun updateBlock(day: DayOfWeek, block: TaskBlock) {
        edit { it.updateBlock(day, block) }
    }

    fun deleteBlock(day: DayOfWeek, id: String) {
        val title = _ui.value.draft.block(day, id)?.title
        edit(title?.let { "Removed “$it”" }) { it.deleteBlock(day, id) }
    }

    fun duplicateBlock(day: DayOfWeek, id: String) {
        edit("Duplicated on ${label(day)}") { it.duplicateBlock(day, id) }
    }

    /** ▲/▼ in the day list: same length, same day span, new running order. */
    fun moveBlock(day: DayOfWeek, id: String, delta: Int) {
        edit { it.moveBlock(day, id, delta) }
    }

    fun moveBlockToDay(from: DayOfWeek, to: DayOfWeek, id: String) {
        edit("Moved to ${label(to)}") { it.moveBlockToDay(from, to, id) }
        _ui.value = _ui.value.copy(selectedDay = to)
    }

    /** "Repeat on…": a copy of the block, with its own id, on every target day. */
    fun repeatBlockOn(day: DayOfWeek, id: String, targets: Set<DayOfWeek>) {
        if (targets.isEmpty()) return
        edit("Repeated on ${targets.sorted().joinToString(", ") { shortLabel(it) }}") {
            it.copyBlockToDays(day, id, targets)
        }
    }

    fun copyDayTo(from: DayOfWeek, to: DayOfWeek) {
        edit("${label(from)} copied to ${label(to)}") { it.copyDayTo(from, to) }
    }

    fun clearDay(day: DayOfWeek) {
        edit("${label(day)} cleared") { it.clearDay(day) }
    }

    // Review and save ------------------------------------------------------------

    fun startReview() {
        val state = _ui.value
        if (state.draft.isEmpty) {
            _ui.value = state.copy(error = "Your week is empty — add a block first.")
            return
        }
        _ui.value = state.copy(reviewing = true, notice = null, error = null)
    }

    fun keepEditing() {
        _ui.value = _ui.value.copy(reviewing = false)
    }

    /**
     * Validates, then hands the draft to the one shared save path: persist,
     * activate, reschedule alarms, refresh widgets. Screens observe
     * `CustomProgram.version`, so they pick the new program up immediately.
     */
    fun save() {
        val state = _ui.value
        if (state.busy) return
        val blocker = state.validation.errors.firstOrNull()
        if (blocker != null) {
            _ui.value = state.copy(reviewing = false, error = blocker.message)
            return
        }
        _ui.value = state.copy(busy = true, error = null)
        viewModelScope.launch {
            programs.saveWeeklyProgram(state.draft.toJson()).fold(
                onSuccess = {
                    _ui.value = _ui.value.copy(
                        busy = false,
                        reviewing = false,
                        saved = true,
                        touched = false
                    )
                },
                onFailure = { e ->
                    _ui.value = _ui.value.copy(
                        busy = false,
                        reviewing = false,
                        error = e.message ?: "Couldn't save your program. Try again."
                    )
                }
            )
        }
    }

    // Internals ------------------------------------------------------------------

    private fun edit(
        notice: String? = null,
        transform: (WeeklyProgramDraft) -> WeeklyProgramDraft
    ) {
        install(transform(_ui.value.draft), touched = true, notice = notice)
    }

    /** The one place the draft changes: validation, diff and summary follow it. */
    private fun install(
        draft: WeeklyProgramDraft,
        touched: Boolean,
        notice: String? = null
    ) {
        _ui.value = _ui.value.copy(
            draft = draft,
            touched = touched,
            validation = WeeklyProgramValidator.validate(draft),
            changes = ProgramDiff.summarize(baseline, draft.days),
            summary = summaryOf(draft),
            notice = notice,
            error = null
        )
    }

    private fun summaryOf(draft: WeeklyProgramDraft) = ProgramSummary(
        configuredDays = draft.configuredDays,
        totalMinutes = draft.totalMinutes,
        countedMinutes = draft.countedMinutes,
        studyBlocks = draft.countBlocks(Track.YKS, Track.TYT, Track.SAT, Track.REVIEW),
        gymBlocks = draft.countBlocks(Track.GYM),
        freeBlocks = draft.countBlocks(Track.FREE, Track.MEAL)
    )

    private fun noticeFor(text: String, alsoOn: Set<DayOfWeek>): String =
        if (alsoOn.isEmpty()) {
            text
        } else {
            "$text on ${alsoOn.sorted().joinToString(", ") { shortLabel(it) }}"
        }

    private suspend fun apiKeyFor(provider: String): String =
        if (provider == SettingsRepository.PROVIDER_GEMINI) {
            settings.geminiApiKey.first()
        } else {
            settings.apiKey.first()
        }

    private fun label(day: DayOfWeek): String = WeeklyProgramValidator.label(day)

    private fun shortLabel(day: DayOfWeek): String =
        day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
}
