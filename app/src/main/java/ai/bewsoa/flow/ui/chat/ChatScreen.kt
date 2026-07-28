package ai.bewsoa.flow.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.AiAssistant
import ai.bewsoa.flow.data.ChatMessage
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.DraftCard
import ai.bewsoa.flow.ui.components.appear
import ai.bewsoa.flow.ui.components.popIn
import ai.bewsoa.flow.ui.components.pressBounce
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The assistant tab — the reference design's chat: hero + suggestion chips
 * when empty, ink-and-paper bubbles, drafts as cards with Apply buttons, and
 * a pill input with a round send button.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val palette = LocalPalette.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // New content (message, draft, typing dots) pulls the view to the bottom.
    LaunchedEffect(state.messages.size, state.busy, state.draft != null) {
        val count = state.messages.size + (if (state.draft != null) 1 else 0) + (if (state.busy) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ChatHeader(
            hasHistory = state.messages.isNotEmpty(),
            onClear = viewModel::clearConversation
        )

        Box(Modifier.weight(1f)) {
            if (state.messages.isEmpty() && !state.busy) {
                EmptyChat(
                    aiAvailable = state.aiAvailable,
                    onSuggestion = { text, sendNow ->
                        if (sendNow) viewModel.send(text) else input = text
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    itemsIndexed(state.messages) { index, message ->
                        val isLast = index == state.messages.lastIndex
                        if (message.role == ChatMessage.ROLE_USER) {
                            UserBubble(message.text, emphasize = isLast)
                        } else {
                            AiBubble(message.text, emphasize = isLast)
                        }
                    }
                    state.draft?.let { draft ->
                        item(key = "draft") {
                            Box(Modifier.padding(top = Space.s)) {
                                ChatDraft(
                                    draft = draft,
                                    onApply = viewModel::applyDraft,
                                    onDismiss = viewModel::dismissDraft
                                )
                            }
                        }
                    }
                    if (state.busy) {
                        item(key = "typing") { TypingBubble() }
                    }
                }
            }
        }

        if (state.aiAvailable) {
            ChatInput(
                value = input,
                onValueChange = { input = it },
                enabled = !state.busy,
                onSend = {
                    viewModel.send(input)
                    input = ""
                }
            )
        } else {
            NoKeyCard()
        }
    }
}

@Composable
private fun ChatHeader(hasHistory: Boolean, onClear: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.l, end = Space.s, top = Space.l, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                "Assistant",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.textBright
            )
            Text(
                "Knows your plan, tasks & streak",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim
            )
        }
        if (hasHistory) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    contentDescription = "Clear conversation",
                    tint = palette.textDim
                )
            }
        }
    }
}

/** The hero state: big sparkle, "How can I help?", and a 2×2 of starters. */
@Composable
private fun EmptyChat(
    aiAvailable: Boolean,
    onSuggestion: (text: String, sendNow: Boolean) -> Unit
) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .popIn(true)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(Space.l))
        Text(
            "How can I help?",
            style = MaterialTheme.typography.headlineMedium,
            color = palette.textBright,
            modifier = Modifier.appear(1)
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Change one day, reshape your week,\nor throw a task at me.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(2)
        )
        if (aiAvailable) {
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SuggestionCard(
                    "📋", "What's today?", "See what's left",
                    Modifier.weight(1f).appear(3)
                ) { onSuggestion("What's left in my plan today?", true) }
                SuggestionCard(
                    "🌙", "Ease tonight", "One-time change",
                    Modifier.weight(1f).appear(4)
                ) { onSuggestion("I'm exhausted — make tonight lighter, just for today.", true) }
            }
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SuggestionCard(
                    "➕", "Add a task", "In plain words",
                    Modifier.weight(1f).appear(5)
                ) { onSuggestion("Add a task: ", false) }
                SuggestionCard(
                    "📈", "This week", "How it's going",
                    Modifier.weight(1f).appear(6)
                ) { onSuggestion("How is my week going so far?", true) }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    emoji: String,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressBounce(interaction)
            .clip(RoundedCornerShape(Radius.card))
            .background(palette.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(Space.l)
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(Space.s))
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.textBright)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = palette.textDim)
    }
}

@Composable
private fun UserBubble(text: String, emphasize: Boolean) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .then(if (emphasize) Modifier.appear() else Modifier)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(palette.textBright)
                .padding(horizontal = Space.l, vertical = Space.m)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.surface
            )
        }
    }
}

@Composable
private fun AiBubble(text: String, emphasize: Boolean) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .then(if (emphasize) Modifier.appear() else Modifier)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(palette.surface)
                .padding(horizontal = Space.l, vertical = Space.m)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textBright
            )
        }
    }
}

/** Three dots breathing while the model thinks. */
@Composable
private fun TypingBubble() {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(palette.surface)
                .padding(horizontal = Space.l, vertical = Space.m),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, delayMillis = i * 160),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$i"
                )
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.textDim.copy(alpha = alpha))
                )
            }
        }
    }
}

/** Maps each draft type onto the shared [DraftCard] with honest scope copy. */
@Composable
private fun ChatDraft(
    draft: AiAssistant.Draft,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    when (draft) {
        is AiAssistant.Draft.Day -> DraftCard(
            scopeLabel = "✦ draft · ${friendlyDate(draft.date)} only",
            title = "Plan change",
            changes = draft.changes,
            applyText = "Apply · ${friendlyDate(draft.date)} only",
            onApply = onApply,
            onDismiss = onDismiss
        )
        is AiAssistant.Draft.Week -> DraftCard(
            scopeLabel = "✦ draft · every week",
            title = "Program change",
            changes = draft.changes,
            note = "This rewrites your standing weekly program.",
            applyText = "Apply to my week",
            onApply = onApply,
            onDismiss = onDismiss
        )
        is AiAssistant.Draft.NewTask -> DraftCard(
            scopeLabel = "✦ draft · new task",
            title = draft.task.title,
            changes = buildList {
                val meta = buildString {
                    if (draft.task.estimatedMinutes > 0) {
                        append("~").append(formatHours(draft.task.estimatedMinutes.toLong()))
                    }
                    draft.task.track?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                    if (isNotEmpty()) append(" · ")
                    append(draft.task.scheduledDate)
                }
                add(meta)
                draft.task.subtasks.forEach { add("• $it") }
            },
            applyText = "Add task",
            onApply = onApply,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val canSend = enabled && value.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radius.pill))
                .background(palette.surface)
                .padding(horizontal = Space.l, vertical = 14.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    "Ask anything about your plan…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textDim
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textBright),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(Space.s))
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressBounce(interaction, 0.88f)
                .clip(CircleShape)
                .background(if (canSend) palette.textBright else palette.outline)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = canSend,
                    onClick = onSend
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send",
                tint = if (canSend) palette.surface else palette.textDim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NoKeyCard() {
    val palette = LocalPalette.current
    Card(modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
        Text(
            "Add an API key to wake the assistant",
            style = MaterialTheme.typography.titleMedium,
            color = palette.textBright
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Profile → AI & program. Claude or Gemini — your key stays on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
    }
}

private fun friendlyDate(date: LocalDate): String = when (date) {
    LocalDate.now() -> "today"
    LocalDate.now().plusDays(1) -> "tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
}
