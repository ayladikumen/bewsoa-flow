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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ai.bewsoa.flow.MainActivity
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.StreakInfo
import java.time.LocalDate

/**
 * "Streak" widget: the flame, the day count, and the never-miss-twice status —
 * the one number that has to survive.
 */
class StreakWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ProgramRepository.get(context)
        val streak = repo.computeStreak(LocalDate.now())
        val palette = Widgets.palette(context)
        provideContent {
            StreakContent(palette = palette, streak = streak)
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

@Composable
private fun StreakContent(palette: WidgetPalette, streak: StreakInfo) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.card)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔥", style = TextStyle(fontSize = 26.sp))
        Spacer(GlanceModifier.width(10.dp))
        Column {
            Text(
                "${streak.current} day${if (streak.current == 1) "" else "s"}",
                style = TextStyle(
                    color = palette.bright, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            )
            Text(
                when {
                    streak.todayKept -> "Today counts. Keep stacking."
                    !streak.yesterdayKept -> "Never miss twice — today is the comeback."
                    else -> "Yesterday held. Today decides."
                },
                style = TextStyle(
                    color = when {
                        streak.todayKept -> palette.success
                        !streak.yesterdayKept -> palette.warn
                        else -> palette.dim
                    },
                    fontSize = 11.sp
                ),
                maxLines = 2
            )
        }
    }
}
