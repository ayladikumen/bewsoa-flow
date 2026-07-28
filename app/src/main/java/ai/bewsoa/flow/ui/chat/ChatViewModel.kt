package ai.bewsoa.flow.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.AiAssistant
import ai.bewsoa.flow.data.ChatMessage
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.TaskRepository
import ai.bewsoa.flow.data.DayOverrides
import ai.bewsoa.flow.data.Xp
import ai.bewsoa.flow.data.XpRepository
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: AiAssistant.Draft? = null,
    val busy: Boolean = false,
    val aiAvailable: Boolean = false
)

/**
 * Owns the assistant conversation: persists the transcript, runs a turn with a
 * fresh context snapshot, holds the pending draft, and is the only place a
 * draft is actually applied — day edits land in [DayOverrides] (once),
 * permanent ones rewrite [CustomProgram], tasks insert via [TaskRepository].
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository.get(app)
    private val taskRepo = TaskRepository.get(app)
    private val programRepo = ProgramRepository.get(app)
    private val xpRepo = XpRepository.get(app)

    private data class Transient(
        val draft: AiAssistant.Draft? = null,
        val busy: Boolean = false
    )

    private val transient = MutableStateFlow(Transient())

    private val aiAvailable = combine(
        settings.aiProvider,
        settings.apiKey,
        settings.geminiApiKey
    ) { provider, claudeKey, geminiKey ->
        if (provider == SettingsRepository.PROVIDER_GEMINI) geminiKey.isNotBlank()
        else claudeKey.isNotBlank()
    }

    val uiState: StateFlow<ChatUiState> = combine(
        settings.chatHistoryJson.map { ChatMessage.listFromJson(it) },
        aiAvailable,
        transient
    ) { messages, ai, tr ->
        ChatUiState(
            messages = messages,
            draft = tr.draft,
            busy = tr.busy,
            aiAvailable = ai
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || transient.value.busy) return
        viewModelScope.launch {
            val history = uiState.value.messages
            append(ChatMessage(ChatMessage.ROLE_USER, clean, System.currentTimeMillis()))
            transient.update { it.copy(busy = true, draft = null) }

            val provider = settings.aiProvider.first()
            val key = if (provider == SettingsRepository.PROVIDER_GEMINI) {
                settings.geminiApiKey.first()
            } else {
                settings.apiKey.first()
            }
            val streak = programRepo.computeStreak(LocalDate.now())
            val level = Xp.levelFor(xpRepo.observeTotal().first())

            val result = AiAssistant.chat(
                provider = provider,
                apiKey = key,
                history = history,
                message = clean,
                taskSummary = taskSummaryFor(LocalDate.now()),
                streakDays = streak.current,
                levelIndex = level.index,
                levelTitle = level.title
            )

            result.fold(
                onSuccess = { turn ->
                    append(ChatMessage(ChatMessage.ROLE_AI, turn.reply, System.currentTimeMillis()))
                    transient.update { it.copy(busy = false, draft = turn.draft) }
                },
                onFailure = { e ->
                    append(
                        ChatMessage(
                            ChatMessage.ROLE_AI,
                            "⚠️ ${e.message ?: "Something went wrong — try again."}",
                            System.currentTimeMillis()
                        )
                    )
                    transient.update { it.copy(busy = false) }
                }
            )
        }
    }

    /** Applies whatever the pending draft is; the confirmation lands in the transcript. */
    fun applyDraft() {
        val draft = transient.value.draft ?: return
        viewModelScope.launch {
            when (draft) {
                is AiAssistant.Draft.Day -> {
                    DayOverrides.set(getApplication(), draft.date, draft.blocks)
                    TaskAlarmScheduler.scheduleUpcoming(getApplication())
                    Widgets.refreshAll(getApplication())
                    append(confirmation("Done — ${friendlyDate(draft.date)}'s plan is updated, just for that day."))
                }
                is AiAssistant.Draft.Week -> {
                    settings.setProgramJson(draft.json)
                    CustomProgram.activate(draft.json)
                    TaskAlarmScheduler.scheduleUpcoming(getApplication())
                    Widgets.refreshAll(getApplication())
                    append(confirmation("Done — your standing weekly program is updated."))
                }
                is AiAssistant.Draft.NewTask -> {
                    taskRepo.addParsedTask(draft.task)
                    append(confirmation("Task added: “${draft.task.title}” ✓"))
                }
            }
            transient.update { it.copy(draft = null) }
        }
    }

    fun dismissDraft() {
        transient.update { it.copy(draft = null) }
    }

    /** Reverts today to the standing program — the undo for an applied day edit. */
    fun clearTodayOverride() {
        viewModelScope.launch {
            DayOverrides.clear(getApplication(), LocalDate.now())
            TaskAlarmScheduler.scheduleUpcoming(getApplication())
            Widgets.refreshAll(getApplication())
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            settings.clearChatHistory()
            transient.update { it.copy(draft = null) }
        }
    }

    // Helpers -------------------------------------------------------------------

    private suspend fun append(message: ChatMessage) {
        val current = ChatMessage.listFromJson(settings.chatHistoryJson.first())
        val next = (current + message).takeLast(HISTORY_CAP)
        settings.setChatHistoryJson(ChatMessage.listToJson(next))
    }

    private fun confirmation(text: String) =
        ChatMessage(ChatMessage.ROLE_AI, text, System.currentTimeMillis())

    private suspend fun taskSummaryFor(date: LocalDate): List<String> =
        taskRepo.observeForDate(date).first().map { item ->
            buildString {
                append(item.task.title)
                if (item.task.estimatedMinutes > 0) {
                    append(" (~").append(formatHours(item.task.estimatedMinutes.toLong())).append(")")
                }
                if (item.isComplete) append(" ✓done")
            }
        }

    private fun friendlyDate(date: LocalDate): String = when (date) {
        LocalDate.now() -> "today"
        LocalDate.now().plusDays(1) -> "tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    }

    companion object {
        private const val HISTORY_CAP = 80
    }
}
