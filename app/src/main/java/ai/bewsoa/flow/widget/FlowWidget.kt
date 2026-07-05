package ai.bewsoa.flow.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ai.bewsoa.flow.MainActivity
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.TaskBlock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val widgetClock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * "Now" widget: the block that should be running right now, minutes left,
 * today's progress, and a one-tap Done action.
 */
class FlowWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ProgramRepository.get(context)
        val today = LocalDate.now()
        val counted = repo.blocksFor(today).filter { it.counted }
        val doneIds = repo.getDoneIds(today)
        val now = LocalTime.now()
        val palette = Widgets.palette(context)

        val current = counted.firstOrNull {
            now >= it.start && now < it.end && it.id !in doneIds
        }
        val next = counted.firstOrNull { it.start > now && it.id !in doneIds }
        val doneCount = counted.count { it.id in doneIds }

        provideContent {
            NowContent(
                palette = palette,
                date = today,
                current = current,
                next = next,
                doneCount = doneCount,
                totalCount = counted.size,
                now = now
            )
        }
    }
}

class FlowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FlowWidget()
}

/** Marks the given block done and refreshes every widget. */
class WidgetMarkDoneAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[PARAM_TASK] ?: return
        val date = parameters[PARAM_DATE] ?: return
        ProgramRepository.get(context).setDone(LocalDate.parse(date), taskId, true)
        Widgets.refreshAll(context)
    }

    companion object {
        val PARAM_TASK = ActionParameters.Key<String>("task_id")
        val PARAM_DATE = ActionParameters.Key<String>("date")
    }
}

@Composable
private fun NowContent(
    palette: WidgetPalette,
    date: LocalDate,
    current: TaskBlock?,
    next: TaskBlock?,
    doneCount: Int,
    totalCount: Int,
    now: LocalTime
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.card)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "BEWSOA FLOW",
                style = TextStyle(
                    color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "$doneCount/$totalCount done",
                style = TextStyle(
                    color = if (doneCount == totalCount) palette.success else palette.dim,
                    fontSize = 10.sp
                )
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        when {
            current != null -> {
                Text(
                    "${current.track.emoji} ${current.title}",
                    style = TextStyle(
                        color = palette.bright, fontSize = 15.sp, fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(2.dp))
                val left = Duration.between(now, current.end).toMinutes()
                Text(
                    "${current.start.format(widgetClock)}–${current.end.format(widgetClock)}" +
                        " · $left min left",
                    style = TextStyle(color = palette.dim, fontSize = 12.sp)
                )
                Spacer(GlanceModifier.height(10.dp))
                Text(
                    "  Mark done ✓  ",
                    style = TextStyle(
                        color = palette.bright, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier
                        .background(palette.chip)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(
                            actionRunCallback<WidgetMarkDoneAction>(
                                actionParametersOf(
                                    WidgetMarkDoneAction.PARAM_TASK to current.id,
                                    WidgetMarkDoneAction.PARAM_DATE to date.toString()
                                )
                            )
                        )
                )
            }
            next != null -> {
                Text(
                    "Next: ${next.track.emoji} ${next.title}",
                    style = TextStyle(
                        color = palette.bright, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "starts at ${next.start.format(widgetClock)}",
                    style = TextStyle(color = palette.dim, fontSize = 12.sp)
                )
            }
            totalCount > 0 && doneCount == totalCount -> Text(
                "All blocks done. Legend behavior. 🎉",
                style = TextStyle(
                    color = palette.bright, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
            )
            else -> Text(
                "Nothing left on today's program.",
                style = TextStyle(color = palette.dim, fontSize = 13.sp)
            )
        }
    }
}
