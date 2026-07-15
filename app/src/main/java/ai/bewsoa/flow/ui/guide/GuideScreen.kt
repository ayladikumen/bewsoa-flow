package ai.bewsoa.flow.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet

/**
 * The user-facing map of the app. Two doors in:
 * - [WhatsNewOverlay] takes over the first launch after an update (gated on
 *   [GUIDE_VERSION] vs the seen_version_code setting),
 * - [GuideScreen] is always reachable from Settings.
 *
 * Screens stay quiet; the "how does this work" prose lives here instead.
 */

/** Bump alongside versionCode so the What's new overlay shows once per release. */
const val GUIDE_VERSION = 6

private data class Release(val version: String, val changes: List<String>)

private data class GuideSection(val emoji: String, val title: String, val points: List<String>)

private val RELEASES = listOf(
    Release(
        "2.0 (beta)",
        listOf(
            "A whole new look: flat surfaces instead of glowing cards, so the numbers are the loudest thing on screen.",
            "XP and levels: blocks, tasks and focus sessions all pay XP. Hit the daily goal for a bonus, keep 5 days for the weekly chest, and watch the confetti.",
            "The plan, browsable: Progress now shows any day of any week — times, notes, and what actually happened.",
            "Five tabs instead of six. Review, Alerts and this Guide now live inside the screens they belong to.",
            "Skip a block for today: it's excused, not missed — it leaves the maths entirely, so it can't dent your streak. Three a week, resets Monday.",
            "Export everything from Settings → Your data: CSV, JSON or Markdown, straight to the share sheet.",
            "Still being built: the drag-to-edit weekly grid and the AI chat tab."
        )
    ),
    Release(
        "1.4",
        listOf(
            "Hold & drag a block on Today to reorder the day — studied before the gym? Put it first, times re-map for today only.",
            "The Focus timer now counts down live in your notification shade.",
            "Quieter Today: catch-up folds into one line, long hints moved into this Guide.",
            "This Guide — every feature in one place, anytime via Settings."
        )
    ),
    Release(
        "1.3",
        listOf(
            "Focus tab: commit to one thing + a length, confirm at the end — only real work gets logged.",
            "Eisenhower chips on tasks: Do first / Schedule / Quick win / Later. Tap to change.",
            "Task layer: add tasks in plain words, AI sizes, schedules and splits them; memorization repeats at 1d/3d/1w/1mo."
        )
    ),
    Release(
        "1.2",
        listOf(
            "Three home-screen widgets: current block, today's progress, streak.",
            "Themes (widgets follow), insights from your history, and a Sunday AI coach that drafts next week."
        )
    )
)

private val SECTIONS = listOf(
    GuideSection(
        "⚡", "Today",
        listOf(
            "The ring is today's progress. A day is \"kept\" at 60% of counted blocks — the streak's one rule is never miss twice.",
            "Tick blocks as you finish them. Hold & drag to match the order life actually happened.",
            "Catch up gathers unlogged blocks from today and yesterday behind one line.",
            "Deep work sums focused blocks plus confirmed Focus sessions against a soft 6h goal.",
            "My tasks: write plainly and Add — or Add with AI to size, schedule and split it (uses your Settings API key).",
            "On a task: tap the colored chip to change its priority quadrant, break it into steps, or push it to tomorrow guilt-free.",
            "Tasks marked as memorization come back for review at +1, +3, +7 and +30 days.",
            "Day load compares planned task minutes to your capacity — adjust it with the ± buttons."
        )
    ),
    GuideSection(
        "🧠", "Focus",
        listOf(
            "Commit to one thing and a length, then start. The countdown runs here and as a live notification.",
            "When time's up: Completed logs it to today at that moment and into the weekly total. Discard logs nothing.",
            "Finish now credits the minutes you actually did; Abandon drops the session — the streak never depended on it."
        )
    ),
    GuideSection(
        "📈", "Progress",
        listOf(
            "The plan: browse any day of any week — ‹ › moves weeks, the strip picks the day. Log or skip right there.",
            "Streak, insights computed from your history, this week's day bars and per-track totals.",
            "Deep focus: the week's confirmed session time, day by day."
        )
    ),
    GuideSection(
        "⚡", "XP & levels",
        listOf(
            "Every finished block pays XP — longer and more mission-critical pays more. Tasks and focus sessions pay a little too.",
            "The daily goal is 80% of a perfect day, computed from your actual plan. Crossing it banks a +25 bonus.",
            "Keep 5 days in a week and the chest unlocks — open it yourself on Progress, that tap is the reward.",
            "Un-checking a block takes its XP back. Skipped blocks never pay and never cost.",
            "Streak freezes: one earned per 7 kept days (hold max 2). A freeze spends itself to save yesterday when a streak of 3+ would break."
        )
    ),
    GuideSection(
        "📝", "Review",
        listOf(
            "The weekly report writes itself from your logs — you only add three notes: TYT score, what slowed you down, next week's one task.",
            "Past weeks stay expandable with their stats recomputed."
        )
    ),
    GuideSection(
        "🔔", "Alerts",
        listOf(
            "End-of-block reminders (offset adjustable), motivation pings tied to your actual goals, and a history of everything sent."
        )
    ),
    GuideSection(
        "⚙️", "Settings",
        listOf(
            "Theme picker — widgets recolor with it.",
            "Program: edit the week as markdown and let Claude or Gemini rebuild it. API keys never leave this phone.",
            "The Sunday coach's draft appears on Today — you always accept or dismiss it yourself."
        )
    ),
    GuideSection(
        "📱", "Widgets",
        listOf(
            "Now (current block + one-tap done), Today's progress, and Streak. Long-press your home screen → Widgets → Bewsoa Flow."
        )
    )
)

/** Full-screen takeover shown once after an update. */
@Composable
fun WhatsNewOverlay(onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        GuideList(
            headline = "What's new",
            subtitle = "Fresh in this update, then the full map of the app.",
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Violet,
                contentColor = TextBright
            )
        ) {
            Text("Got it — let's go")
        }
    }
}

/** The same content as a regular destination, opened from Settings. */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Cyan) }
        }
        GuideList(
            headline = "Guide",
            subtitle = "Everything the app does, in one place.",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GuideList(headline: String, subtitle: String, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(headline, style = MaterialTheme.typography.headlineLarge, color = TextBright)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }

        item { SectionHeader("New in ${RELEASES.first().version}") }
        item { ReleaseCard(RELEASES.first(), highlight = true) }

        item { SectionHeader("The full map") }
        items(SECTIONS, key = { it.title }) { section -> SectionCard(section) }

        item { SectionHeader("Earlier updates") }
        items(RELEASES.drop(1), key = { it.version }) { release ->
            ReleaseCard(release, highlight = false)
        }
    }
}

@Composable
private fun ReleaseCard(release: Release, highlight: Boolean) {
    GlowCard(accent = if (highlight) Cyan else null) {
        Text(
            "v${release.version}",
            style = MaterialTheme.typography.titleSmall,
            color = if (highlight) Cyan else TextBright
        )
        Spacer(Modifier.height(6.dp))
        release.changes.forEach { Bullet(it) }
    }
}

@Composable
private fun SectionCard(section: GuideSection) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(section.title, style = MaterialTheme.typography.titleMedium, color = TextBright)
        }
        Spacer(Modifier.height(8.dp))
        section.points.forEach { Bullet(it) }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("·", style = MaterialTheme.typography.bodySmall, color = Cyan)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
            modifier = Modifier.weight(1f)
        )
    }
}
