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
const val GUIDE_VERSION = 9

private data class Release(val version: String, val changes: List<String>)

private data class GuideSection(val emoji: String, val title: String, val points: List<String>)

private val RELEASES = listOf(
    Release(
        "3.1 — build your own week",
        listOf(
            "New: the weekly program builder. Describe your week in plain words — \"school Mon–Fri 08:00–16:00, gym Mon/Wed/Fri at 18:00, TYT Saturday morning\" — and it drafts the whole Monday–Sunday program for you to edit.",
            "Or build it by hand: pick a day, add blocks, tap one to change its title, track, times and note. Duplicate a block, repeat it on other days, move it to another day, copy or clear a whole day.",
            "Six starter templates — Study Focus, Balanced, Gym + Study, Exam Prep, Project Focus, Empty Week — or load your current week and edit that.",
            "Nothing is saved while you edit: overlapping or impossible times get flagged, a plain-English diff shows exactly what changes, and only \"Save weekly program\" makes it real.",
            "Reach it from Week (\"Your weekly program\"), the Assistant's calendar button, or Profile → Program. It always edits the recurring week — one-off changes are still the Assistant's job."
        )
    ),
    Release(
        "3.0 — the new start",
        listOf(
            "Out of beta. Reorder got honest: dragging a block means \"do this next\" — every block keeps its own length, NOW moves to whatever you put on top, and the times reflow from this moment.",
            "Meals and free blocks are tickable now. They stay out of the streak and XP maths — checking off dinner is just free satisfaction.",
            "A brand-new look: warm paper, big rounded type, pastel checklists and a floating five-button bar with the Week — the real goal — in the middle.",
            "Days are checklists now, not timetables. Blocks show as \"~2h · evening\"; the pace bar keeps you honest about the clock without a single hour grid.",
            "The Assistant is alive: chat about your plan, and it drafts changes. Say \"tonight I'm out\" and it edits just today; only \"every week / from now on\" touches the standing program. Every draft shows Apply buttons — nothing changes silently.",
            "One-time day edits live in their own layer, so alarms, widgets, streak and XP follow them automatically and the weekly program stays untouched.",
            "XP now speaks human: your level as a badge, the runway to the next one, the week as seven dots, and the chest as a moment. Numbers only where they mean something.",
            "Home is a dashboard: greeting, one honest ring, routine/task tiles, focus launcher and a peek at the week.",
            "Focus moved behind Home's play button; the timer, live notification and weekly stats are unchanged."
        )
    ),
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
        "🏠", "Home",
        listOf(
            "Greeting, the big ring (today's plan), routine & task tiles, the XP daily goal, the focus launcher and a peek at the week.",
            "The flame pill is your streak — it breathes until today is kept, then settles.",
            "Everything on Home is a door: tap the ring for Today, the bars for Week, the play button for Focus."
        )
    ),
    GuideSection(
        "✅", "Today",
        listOf(
            "The day as a checklist, not a timetable: every block shows as \"~2h · evening\" — durations and dayparts, no hour grid.",
            "The pace bar: the knob is the day passing (7:00–24:00), the fill is your plan getting done. Fill ahead of knob = you're beating the day.",
            "The goal is the week — ease any single day (skip, reorder, move a task to tomorrow) and let the week absorb it.",
            "A day is \"kept\" at 60% of counted blocks. Never miss twice.",
            "Hold & drag rows to match the order life actually happened — every block keeps its own length, the clock times just reflow. Catch-up collects unlogged blocks behind one line.",
            "My tasks: the + button (or the composer at the bottom) adds in plain words; AI sizes, schedules and splits. Tap a task for its quadrant, steps, tomorrow and delete."
        )
    ),
    GuideSection(
        "🗓️", "Week — the centre button",
        listOf(
            "The week ring and pace line: ahead of pace means a light Sunday; behind means small daily wins, not a weekend cram.",
            "The streak as seven dots, your level badge, and the weekly chest — keep 5 days to unlock it, open it yourself.",
            "The plan: browse any day of any week — ‹ › moves weeks, the strip picks the day. Log or skip right there.",
            "AI drafts (the Sunday coach, and week-wide chat changes) land here as cards you accept or dismiss."
        )
    ),
    GuideSection(
        "🛠️", "Weekly program builder",
        listOf(
            "Reachable from Week (\"Your weekly program\"), the Assistant's calendar button, or Profile → Program.",
            "Build with AI: describe the week in plain words — \"school Mon–Fri 08:00–16:00, gym Mon/Wed/Fri at 18:00, TYT Saturday morning\" — and it drafts the whole Monday–Sunday program.",
            "Or build it by hand: pick a day, add blocks, tap one to edit its title, track, times and note. Duplicate it, repeat it on other days, move it to another day, copy or clear a whole day.",
            "Templates (Study Focus, Balanced, Gym + Study, Exam Prep, Project Focus, Empty Week) are starting points — or load your current week and edit that.",
            "Nothing is saved while you edit: overlaps and impossible times are flagged, the diff shows exactly what changes, and only \"Save weekly program\" makes it real.",
            "It always edits the recurring program. A change for one day only is still the Assistant's job."
        )
    ),
    GuideSection(
        "✨", "Assistant",
        listOf(
            "Chat about your plan: what's left, how the week looks, what to do next.",
            "Ask for a change and it drafts one. \"Tonight I'm at a wedding\" edits only tonight; \"move gym to 6pm every week\" rewrites the program — and it will only do that when you clearly say it's permanent.",
            "Drafts are never auto-applied: every one shows what changes and waits for your Apply.",
            "It can also add tasks — \"add: 40 soru paragraf yarına\" becomes a sized, scheduled task.",
            "Uses your own Claude or Gemini key from Profile; the conversation stays on this phone."
        )
    ),
    GuideSection(
        "🧠", "Focus",
        listOf(
            "Start from Home's play button. Commit to one thing and a length; the countdown runs here and in the notification shade.",
            "When time's up: Completed logs it to today at that moment and into the weekly total. Discard logs nothing.",
            "Finish now credits the minutes you actually did; Abandon drops the session — the streak never depended on it."
        )
    ),
    GuideSection(
        "⚡", "XP & levels",
        listOf(
            "Every finished block pays XP — longer and more mission-critical pays more. Tasks and focus sessions pay a little too.",
            "The daily goal is 80% of a perfect day, computed from your actual plan. Crossing it banks a +25 bonus.",
            "Keep 5 days in a week and the chest unlocks — open it yourself on Week, that tap is the reward.",
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
        "👤", "Profile",
        listOf(
            "Themes — Sunrise is the new default; the old dark looks are still there, and widgets recolor with your pick.",
            "Program: edit the week as markdown and let Claude or Gemini rebuild it, or open the builder for the guided version. API keys never leave this phone.",
            "The Sunday coach's draft appears on Week — you always accept or dismiss it yourself.",
            "Your data: export everything as CSV, JSON or Markdown."
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
                contentColor = androidx.compose.ui.graphics.Color.White
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
