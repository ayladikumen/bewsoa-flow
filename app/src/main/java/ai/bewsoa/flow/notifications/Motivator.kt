package ai.bewsoa.flow.notifications

import ai.bewsoa.flow.data.Track
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * The voice of the app. Every line is tied to a real goal — Bewsoa AI,
 * Exact Hour, YKS, SAT, the university abroad — never generic filler.
 */
object Motivator {

    private val morning = listOf(
        "This morning block is 8 hours most people won't do. That's exactly why it works.",
        "Every net you add today is a door opening at a university abroad. Sit down and collect.",
        "YKS is the backbone. Protect the morning and the whole week stands.",
        "Future you is unpacking in a dorm room abroad. Today's block pays part of that rent.",
        "One focused morning beats three distracted ones. Phone in the other room, first question open.",
        "The acceptance letter is being written right now — by you, one solved question at a time.",
        "Nobody at that campus abroad will ask if the morning felt long. They'll only see that you made it."
    )

    private val study = listOf(
        "Active recall, not rereading. Close the book and prove you own the topic.",
        "The mistake log is your real teacher. Feed it today, and Saturday's TYT gets easier.",
        "Study session 2 is what converts this morning into retention. Don't donate 8 hours to forgetting.",
        "Weak topics don't fix themselves. Pick one tonight and make it boring.",
        "Every deneme is a rehearsal for the day that changes your address."
    )

    private val sat = listOf(
        "45 SAT minutes now = scholarship money later. Alternate, don't skip.",
        "Reading & Writing or Math — either way, wrong answers go into sat-mistakes.md tonight.",
        "The SAT is your ticket price for studying abroad. Tonight's session buys another piece of it.",
        "November's test date is coming whether you practice or not. Practice.",
        "A timed section on Sunday hits different when you did the weekday reps. Do the reps."
    )

    private val project = listOf(
        "Bewsoa AI grows one commit at a time. Tonight: one small task, done fully.",
        "Exact Hour won't ship itself. Sixty minutes, one task, one commit.",
        "The rocker pad is waiting. One OpenSCAD tweak tonight keeps the hardware weekend alive.",
        "\"Work on Exact Hour\" is not a task. Pick the one thing, finish it, commit it.",
        "Every repo you push is proof for every application you'll ever write. Build the evidence.",
        "Bewsoa AI is not a dream, it's a backlog. Close one item tonight.",
        "A WIP commit still beats a perfect plan. Push something."
    )

    private val gym = listOf(
        "A strong body carries the heavy weeks. Don't shrink the session two days in a row.",
        "YKS ends at five. The iron is the cleanest way to switch your brain off and back on.",
        "The gym block is where stress goes to die. Full program, no rushing out.",
        "Discipline transfers: every finished set makes the next study block easier to start."
    )

    private val streak = listOf(
        "Never miss twice. One slip is a life; two is a pattern; three is a new default.",
        "You don't have to be perfect. You have to be relentless about coming back.",
        "The streak isn't the point — the person who keeps it is. Be that person today.",
        "Missed yesterday? Fine. Today is the comeback, and comebacks are your specialty."
    )

    private val general = listOf(
        "Bewsoa AI, Exact Hour, a university abroad — three big things, one boring superpower: today's blocks, done.",
        "The plan already exists. You're not deciding anything today, just executing.",
        "Small daily wins compound into a different life. Log today's blocks and watch it happen.",
        "Somewhere there's a campus with your name on an acceptance letter. Earn it before dinner.",
        "You're building a company, a device, and a future abroad — from a weekly markdown file. Keep going."
    )

    /**
     * Picks a pool that fits the moment: study talk while YKS is on,
     * project talk in the late evening, streak talk on Sundays.
     */
    fun random(now: LocalDateTime): String {
        val pool = when {
            now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 17 -> streak + general
            now.hour < 12 -> morning + general
            now.hour < 17 -> morning + study
            now.hour < 19 -> gym + general
            now.hour < 22 -> study + sat + streak
            else -> project + general
        }
        return pool.random()
    }

    /** Second line of the task-end reminder, matched to the block's track. */
    fun taskEndLine(track: Track): String = when (track) {
        Track.YKS -> "Log it while it's fresh — active recall beats rereading."
        Track.TYT -> "Mark it now and review mistakes today, while they still sting."
        Track.SAT -> "Wrong answers go straight into sat-mistakes.md."
        Track.PROJECT -> "Did it end with a commit? Even WIP counts."
        Track.GYM -> "Session logged is session real. Check it off."
        Track.REVIEW -> "Twenty minutes that steer the whole week — done?"
        Track.MEAL, Track.FREE -> "Check it off and move on."
    }
}
