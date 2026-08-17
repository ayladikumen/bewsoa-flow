package ai.bewsoa.flow.ui.program

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.IssueLevel
import ai.bewsoa.flow.data.ProgramIssue
import ai.bewsoa.flow.data.ProgramTemplate
import ai.bewsoa.flow.data.ProgramTemplates
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.AppTextField
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.CardTone
import ai.bewsoa.flow.ui.components.Chip
import ai.bewsoa.flow.ui.components.GhostButton
import ai.bewsoa.flow.ui.components.PrimaryButton
import ai.bewsoa.flow.ui.components.SectionLabel
import ai.bewsoa.flow.ui.components.SegmentedToggle
import ai.bewsoa.flow.ui.components.appear
import ai.bewsoa.flow.ui.components.pressBounce
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The one-tap starters under the prompt — the four examples from the brief. */
private val AI_EXAMPLES = listOf(
    "Build a study-focused week",
    "Balance school, gym and study",
    "Create a YKS-focused schedule",
    "Make my week more realistic"
)

private const val AI_PLACEHOLDER =
    "I have school Monday–Friday 08:00–16:00, gym Monday Wednesday Friday at 18:00, " +
        "2 hours of YKS every evening, TYT Saturday morning and project work Sunday."

/**
 * Create / edit the standing weekly program: describe it, or build it by hand,
 * then preview, fix what the validator flags, read the diff, and save. This is
 * the only screen that writes a recurring program by hand, and it never touches
 * a one-time day edit.
 */
@Composable
fun ProgramBuilderScreen(
    start: BuilderStart = BuilderStart.MENU,
    onClose: () -> Unit = {},
    viewModel: ProgramBuilderViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<BlockEdit?>(null) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    var copyingDay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.begin(start) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, "Weekly program saved ✓", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    // Leaving with unsaved work asks first — both from the arrow and the system back.
    val leave: () -> Unit = {
        if (state.touched) confirmingDiscard = true else onClose()
    }
    BackHandler(enabled = !state.saved) {
        when {
            editing != null -> editing = null
            state.reviewing -> viewModel.keepEditing()
            else -> leave()
        }
    }

    val blockActions = remember(viewModel) {
        BlockEditorActions(
            onDismiss = { editing = null },
            onAdd = viewModel::addBlock,
            onUpdate = viewModel::updateBlock,
            onDelete = viewModel::deleteBlock,
            onDuplicate = viewModel::duplicateBlock,
            onMoveTo = viewModel::moveBlockToDay,
            onRepeatOn = viewModel::repeatBlockOn
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            BuilderHeader(
                editing = state.editingExisting,
                unsaved = state.touched,
                onBack = leave
            )
            SegmentedToggle(
                options = listOf("Build with AI", "Edit by hand"),
                selectedIndex = if (state.mode == BuilderMode.AI) 0 else 1,
                onSelect = {
                    viewModel.setMode(if (it == 0) BuilderMode.AI else BuilderMode.EDITOR)
                },
                modifier = Modifier.padding(horizontal = Space.l)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Space.l, end = Space.l, top = Space.l, bottom = Space.l
                ),
                verticalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                state.error?.let { message ->
                    item(key = "error") {
                        MessageCard(message, isError = true, onDismiss = viewModel::dismissNotice)
                    }
                }
                state.notice?.let { message ->
                    item(key = "notice") {
                        MessageCard(message, isError = false, onDismiss = viewModel::dismissNotice)
                    }
                }

                if (state.mode == BuilderMode.AI) {
                    item(key = "ai") {
                        AiBuildCard(
                            request = state.request,
                            busy = state.busy,
                            aiAvailable = state.aiAvailable,
                            hasDraft = !state.draft.isEmpty,
                            onRequestChange = viewModel::setRequest,
                            onGenerate = viewModel::generateWithAi,
                            modifier = Modifier.appear(0)
                        )
                    }
                } else {
                    item(key = "strip") {
                        Card(modifier = Modifier.appear(0)) {
                            SectionLabel("Your week · pick a day")
                            Spacer(Modifier.height(Space.s))
                            WeekDayStrip(
                                draft = state.draft,
                                selected = state.selectedDay,
                                onSelect = {
                                    copyingDay = false
                                    viewModel.selectDay(it)
                                },
                                daysWithErrors = state.validation.errors
                                    .mapNotNull { it.day }
                                    .toSet()
                            )
                            Spacer(Modifier.height(Space.m))
                            Text(
                                summaryLine(state),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalPalette.current.textDim
                            )
                        }
                    }

                    item(key = "day_head") {
                        DayHeaderRow(
                            day = state.selectedDay,
                            blocks = state.draft.blocksFor(state.selectedDay).size,
                            copying = copyingDay,
                            onCopyDay = { copyingDay = !copyingDay },
                            onClearDay = {
                                copyingDay = false
                                viewModel.clearDay(state.selectedDay)
                            },
                            onPickCopyTarget = { target ->
                                copyingDay = false
                                viewModel.copyDayTo(state.selectedDay, target)
                            }
                        )
                    }

                    val blocks = state.draft.blocksFor(state.selectedDay)
                    val dayIssues = state.validation.errorsForDay(state.selectedDay)
                        .mapNotNull { it.blockId }
                        .toSet()
                    dayBlockItems(blocks, dayIssues, state.selectedDay, viewModel) { block ->
                        editing = BlockEdit.Existing(state.selectedDay, block)
                    }

                    item(key = "add") {
                        AddBlockRow(onClick = { editing = BlockEdit.New(state.selectedDay) })
                    }
                }

                if (state.draft.isEmpty) {
                    item(key = "templates") {
                        TemplatesCard(
                            onTemplate = { viewModel.useTemplate(it) },
                            onCurrent = viewModel::useCurrentProgram
                        )
                    }
                }

                if (state.validation.issues.isNotEmpty() && !state.draft.isEmpty) {
                    item(key = "issues") { IssuesCard(state.validation.issues) }
                }

                if (state.changes.isNotEmpty() && state.touched && !state.draft.isEmpty) {
                    item(key = "changes") { ChangesCard(state.changes) }
                }

                item(key = "scope") { ScopeCard() }
            }

            SaveBar(
                state = state,
                onSave = viewModel::startReview
            )
        }

        editing?.let { target ->
            BlockEditorSheet(edit = target, actions = blockActions)
        }

        if (state.reviewing) {
            ReviewSheet(
                state = state,
                onConfirm = viewModel::save,
                onKeepEditing = viewModel::keepEditing
            )
        }

        if (confirmingDiscard) {
            DiscardDialog(
                onContinue = { confirmingDiscard = false },
                onDiscard = {
                    confirmingDiscard = false
                    onClose()
                }
            )
        }
    }
}

/**
 * The day's blocks. Kept as its own extension so the screen's LazyColumn stays
 * readable; the nudges are hidden at the ends of the list.
 */
private fun LazyListScope.dayBlockItems(
    blocks: List<TaskBlock>,
    issueIds: Set<String>,
    day: DayOfWeek,
    viewModel: ProgramBuilderViewModel,
    onClick: (TaskBlock) -> Unit
) {
    if (blocks.isEmpty()) {
        item(key = "empty_day") { EmptyDayCard(day) }
        return
    }
    blocks.forEachIndexed { index, block ->
        item(key = "block_${day.name}_${block.id}") {
            DraftBlockRow(
                block = block,
                hasError = block.id in issueIds,
                onClick = { onClick(block) },
                onMoveUp = if (index == 0) null else ({ viewModel.moveBlock(day, block.id, -1) }),
                onMoveDown = if (index == blocks.lastIndex) {
                    null
                } else {
                    ({ viewModel.moveBlock(day, block.id, 1) })
                }
            )
        }
    }
}

@Composable
private fun BuilderHeader(editing: Boolean, unsaved: Boolean, onBack: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.xs, end = Space.l, top = Space.s, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = palette.textBright
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (editing) "Edit your week" else "Build your week",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.textBright
            )
            Text(
                if (unsaved) {
                    "Unsaved draft · your program is unchanged"
                } else {
                    "Tell me what your week looks like."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (unsaved) palette.warn else palette.textDim
            )
        }
        if (unsaved) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.warn)
            )
        }
    }
}

@Composable
private fun AiBuildCard(
    request: String,
    busy: Boolean,
    aiAvailable: Boolean,
    hasDraft: Boolean,
    onRequestChange: (String) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Card(modifier = modifier, tone = CardTone.Accent(palette.accent)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                SectionLabel(if (hasDraft) "✦ rework this draft" else "✦ describe your week")
                Spacer(Modifier.height(2.dp))
                Text(
                    "Build your week",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        AppTextField(
            value = request,
            onValueChange = onRequestChange,
            placeholder = AI_PLACEHOLDER,
            singleLine = false,
            minLines = 4
        )
        Spacer(Modifier.height(Space.s))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            AI_EXAMPLES.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    row.forEach { example ->
                        Chip(
                            text = example,
                            onClick = { onRequestChange(example) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        if (aiAvailable) {
            PrimaryButton(
                text = when {
                    busy -> "Drafting your week…"
                    hasDraft -> "Redraft with AI"
                    else -> "Draft my week"
                },
                enabled = !busy,
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth()
            )
            if (busy) {
                Spacer(Modifier.height(Space.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = palette.accent
                    )
                    Spacer(Modifier.width(Space.s))
                    Text(
                        "A full week takes a moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim
                    )
                }
            }
        } else {
            Text(
                "Add a Claude or Gemini API key in Profile → Program to build with AI. " +
                    "You can still build your week by hand.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim
            )
        }
        Spacer(Modifier.height(Space.s))
        Text(
            "The AI writes a draft. Nothing changes until you save.",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textDim
        )
    }
}

@Composable
private fun DayHeaderRow(
    day: DayOfWeek,
    blocks: Int,
    copying: Boolean,
    onCopyDay: () -> Unit,
    onClearDay: () -> Unit,
    onPickCopyTarget: (DayOfWeek) -> Unit
) {
    val palette = LocalPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    day.getDisplayName(TextStyle.FULL, Locale.US),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
                Text(
                    "$blocks block${if (blocks == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            }
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Chip(
                text = if (copying) "Pick a day →" else "Copy this day",
                selected = copying,
                onClick = onCopyDay
            )
            if (blocks > 0) {
                Chip(text = "Clear day", onClick = onClearDay)
            }
        }
        if (copying) {
            Spacer(Modifier.height(Space.s))
            Text(
                "Copies every block here onto the day you pick, replacing it.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim
            )
            Spacer(Modifier.height(Space.s))
            DayPickerRow(
                selected = emptySet(),
                exclude = day,
                onToggle = onPickCopyTarget
            )
        }
    }
}

@Composable
private fun EmptyDayCard(day: DayOfWeek) {
    val palette = LocalPalette.current
    Card(tone = CardTone.Sunken) {
        Text(
            "${day.getDisplayName(TextStyle.FULL, Locale.US)} is empty.",
            style = MaterialTheme.typography.titleSmall,
            color = palette.textBright
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Add a block, or copy another day onto it.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
    }
}

@Composable
private fun TemplatesCard(
    onTemplate: (ProgramTemplate) -> Unit,
    onCurrent: () -> Unit
) {
    val palette = LocalPalette.current
    Card {
        SectionLabel("Start from")
        Spacer(Modifier.height(Space.s))
        Text(
            "A template is just a starting point — edit every block from here.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
        Spacer(Modifier.height(Space.m))
        ProgramTemplates.all.forEach { template ->
            TemplateRow(
                emoji = template.emoji,
                name = template.name,
                tagline = template.tagline,
                onClick = { onTemplate(template) }
            )
            Spacer(Modifier.height(Space.s))
        }
        TemplateRow(
            emoji = "🗓️",
            name = "Use my current program",
            tagline = "Your standing week, loaded as a draft to edit.",
            onClick = onCurrent
        )
    }
}

@Composable
private fun TemplateRow(
    emoji: String,
    name: String,
    tagline: String,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(interaction, 0.98f)
            .clip(RoundedCornerShape(Radius.row))
            .background(palette.surfaceHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textBright
            )
            Text(
                tagline,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim
            )
        }
    }
}

/** Errors first, then warnings — the exact messages the validator produced. */
@Composable
private fun IssuesCard(issues: List<ProgramIssue>) {
    val palette = LocalPalette.current
    val errors = issues.filter { it.level == IssueLevel.ERROR }
    val warnings = issues.filter { it.level == IssueLevel.WARNING }
    val tone = if (errors.isEmpty()) palette.warn else palette.danger
    Card(tone = CardTone.Accent(tone)) {
        SectionLabel(
            if (errors.isEmpty()) {
                "Worth a look"
            } else {
                "${errors.size} thing${if (errors.size == 1) "" else "s"} to fix"
            }
        )
        Spacer(Modifier.height(Space.s))
        (errors + warnings).take(6).forEach { issue ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    if (issue.level == IssueLevel.ERROR) "•" else "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (issue.level == IssueLevel.ERROR) palette.danger else palette.warn
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    issue.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textBright
                )
            }
        }
        if (errors.size + warnings.size > 6) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "…and ${errors.size + warnings.size - 6} more",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textDim
            )
        }
    }
}

@Composable
private fun ChangesCard(changes: List<String>) {
    val palette = LocalPalette.current
    Card {
        SectionLabel("What changes · vs your current program")
        Spacer(Modifier.height(Space.s))
        changes.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textBright,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

/** The standing-vs-one-time contract, said out loud where it is being decided. */
@Composable
private fun ScopeCard() {
    val palette = LocalPalette.current
    Card(tone = CardTone.Sunken) {
        Text(
            "This is your recurring week",
            style = MaterialTheme.typography.titleSmall,
            color = palette.textBright
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Saving here changes every Monday, every Tuesday, and so on. " +
                "For a one-off change to a single day, ask the assistant instead — " +
                "that stays a one-time edit.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val tone = if (isError) palette.danger else palette.success
    Card(tone = CardTone.Accent(tone)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textBright,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Space.s))
            Text(
                "Dismiss",
                style = MaterialTheme.typography.labelMedium,
                color = tone,
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }
    }
}

@Composable
private fun SaveBar(state: ProgramBuilderUiState, onSave: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .navigationBarsPadding()
            .padding(horizontal = Space.l, vertical = Space.m)
    ) {
        Text(
            summaryLine(state),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textDim
        )
        Spacer(Modifier.height(Space.s))
        PrimaryButton(
            text = "Save weekly program",
            enabled = state.canSave,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.validation.isSavable && !state.draft.isEmpty) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Fix what's flagged above to save.",
                style = MaterialTheme.typography.labelMedium,
                color = palette.danger
            )
        }
    }
}

/** The compact summary the brief asks for, in one line for the bars. */
private fun summaryLine(state: ProgramBuilderUiState): String {
    val summary = state.summary
    if (state.draft.isEmpty) return "Nothing scheduled yet"
    return "${summary.configuredDays}/7 days · ${formatHours(summary.totalMinutes)} scheduled · " +
        "${formatHours(summary.countedMinutes)} counted"
}

/**
 * The last stop before anything is written: the numbers, the human diff, the
 * warnings, and the week itself.
 */
@Composable
private fun ReviewSheet(
    state: ProgramBuilderUiState,
    onConfirm: () -> Unit,
    onKeepEditing: () -> Unit
) {
    val palette = LocalPalette.current
    val summary = state.summary
    BuilderSheet(onDismiss = onKeepEditing) {
        SectionLabel("Review · every week")
        Spacer(Modifier.height(Space.xs))
        Text(
            "Save this as your weekly program?",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.textBright
        )
        Spacer(Modifier.height(Space.l))

        SummaryLine("Days configured", "${summary.configuredDays} of 7")
        SummaryLine("Total scheduled", formatHours(summary.totalMinutes))
        SummaryLine("Counted toward progress", formatHours(summary.countedMinutes))
        SummaryLine("Study blocks", summary.studyBlocks.toString())
        SummaryLine("Gym blocks", summary.gymBlocks.toString())
        SummaryLine("Meals & free time", summary.freeBlocks.toString())

        Spacer(Modifier.height(Space.l))
        SectionLabel("What changes")
        Spacer(Modifier.height(Space.xs))
        state.changes.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textBright,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        if (state.validation.warnings.isNotEmpty()) {
            Spacer(Modifier.height(Space.l))
            SectionLabel("Worth a look")
            Spacer(Modifier.height(Space.xs))
            state.validation.warnings.take(4).forEach { issue ->
                Text(
                    issue.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.warn,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(Space.l))
        PrimaryButton(
            text = if (state.busy) "Saving…" else "Save weekly program",
            enabled = state.canSave,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Space.s))
        GhostButton(
            text = "Keep editing",
            onClick = onKeepEditing,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Space.l))
        SectionLabel("The week you're saving")
        WeeklyProgramPreview(state.draft)
        Spacer(Modifier.height(Space.l))
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textDim,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = palette.textBright
        )
    }
}

@Composable
private fun DiscardDialog(onContinue: () -> Unit, onDiscard: () -> Unit) {
    val palette = LocalPalette.current
    val scrim = remember { MutableInteractionSource() }
    val card = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.scrim)
            .clickable(interactionSource = scrim, indication = null, onClick = onContinue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(Space.xl)
                .clip(RoundedCornerShape(Radius.card))
                .background(palette.surface)
                .clickable(interactionSource = card, indication = null) {}
                .padding(Space.l)
        ) {
            Text(
                "Discard changes?",
                style = MaterialTheme.typography.titleLarge,
                color = palette.textBright
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "Your draft isn't saved yet, and your current program is still active.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                text = "Continue editing",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.s))
            GhostButton(
                text = "Discard",
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
