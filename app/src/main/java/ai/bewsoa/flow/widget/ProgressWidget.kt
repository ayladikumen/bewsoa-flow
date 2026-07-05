package ai.bewsoa.flow.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import java.time.LocalDate
import java.time.LocalTime

/**
 * "Today" widget: overall day progress as a bar, done/total count, and the
 * next unfinished blocks with their times.
 */
class ProgressWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ProgramRepository.get(context)
        val today = LocalDate.now()
        val counted = repo.blocksFor(today).filter { it.counted }
        val doneIds = repo.getDoneIds(today)
        val now = LocalTime.now()
        val palette = Widgets.palette(context)

        val doneCount = counted.count { it.id in doneIds }
        val upcoming = counted
            .filter { it.id !in doneIds && it.end > now }
            .take(3)
            .map { "${it.track.emoji} ${it.title} · ${it.start.format(widgetClock)}" }

        provideContent {
            ProgressContent(
                palette = palette,
                doneCount = doneCount,
                totalCount = counted.size,
                upcoming = upcoming
            )
        }
    }
}

class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

@Composable
private fun ProgressContent(
    palette: WidgetPalette,
    doneCount: Int,
    totalCount: Int,
    upcoming: List<String>
) {
    val allDone = totalCount > 0 && doneCount == totalCount
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.card)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TODAY",
                style = TextStyle(
                    color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "$doneCount / $totalCount",
                style = TextStyle(
                    color = palette.bright, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        LinearProgressIndicator(
            progress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = if (allDone) palette.success else palette.accent,
            backgroundColor = palette.outline
        )
        Spacer(GlanceModifier.height(10.dp))
        if (allDone) {
            Text(
                "Day complete. Never missed. 🎉",
                style = TextStyle(color = palette.success, fontSize = 12.sp)
            )
        } else if (upcoming.isEmpty()) {
            Text(
                "Nothing scheduled ahead today.",
                style = TextStyle(color = palette.dim, fontSize = 12.sp)
            )
        } else {
            upcoming.forEach { line ->
                Text(
                    line,
                    style = TextStyle(color = palette.dim, fontSize = 12.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
