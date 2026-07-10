package ai.bewsoa.flow.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The weekly program from docs/weekly_program.md, encoded as data.
 *
 * Times are anchors, not handcuffs — what matters is the order of blocks
 * and the weekly totals. YKS is the backbone; everything else is built
 * around that skeleton.
 */
object WeeklyProgram {

    fun blocksFor(date: LocalDate): List<TaskBlock> {
        val base = CustomProgram.current?.get(date.dayOfWeek)?.takeIf { it.isNotEmpty() }
            ?: builtIn(date)
        // A drag on Today may have re-slotted this date's blocks.
        return DayBlockOrder.applyTo(base, date)
    }

    private fun builtIn(date: LocalDate): List<TaskBlock> = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> saturday
        DayOfWeek.SUNDAY -> sunday
        else -> weekday(date.dayOfWeek)
    }

    fun blockById(date: LocalDate, id: String): TaskBlock? =
        blocksFor(date).firstOrNull { it.id == id }

    /** The active program (custom or built-in) as a per-weekday map, for diffing. */
    fun weekMap(): Map<DayOfWeek, List<TaskBlock>> {
        val monday = LocalDate.now().with(
            java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        )
        return DayOfWeek.entries.associateWith { day ->
            blocksFor(monday.plusDays((day.value - 1).toLong()))
        }
    }

    fun dayLabel(date: LocalDate): String = when {
        CustomProgram.current != null -> "My Program"
        date.dayOfWeek == DayOfWeek.SATURDAY -> "TYT Saturday"
        date.dayOfWeek == DayOfWeek.SUNDAY -> "Reset & Build Sunday"
        else -> "Weekday Grind"
    }

    private fun weekday(day: DayOfWeek): List<TaskBlock> = listOf(
        TaskBlock(
            "wd_yks_morning", "YKS — deep work", Track.YKS,
            LocalTime.of(9, 0), LocalTime.of(17, 0),
            "The backbone. Breaks and lunch live inside this block."
        ),
        TaskBlock(
            "wd_gym", "Gym — long session", Track.GYM,
            LocalTime.of(17, 0), LocalTime.of(18, 45),
            "~1.5h+. Proper warm-up, full program, no rushing out."
        ),
        TaskBlock(
            "wd_dinner", "Dinner", Track.MEAL,
            LocalTime.of(19, 0), LocalTime.of(19, 30),
            "Eat, don't linger.",
            counted = false
        ),
        TaskBlock(
            "wd_yks_review", "Study session 2 — YKS review", Track.YKS,
            LocalTime.of(19, 30), LocalTime.of(21, 15),
            "Active recall, mistake log, drills on today's topics. This makes the morning stick."
        ),
        TaskBlock(
            "wd_sat", "SAT — ${satFocus(day)}", Track.SAT,
            LocalTime.of(21, 15), LocalTime.of(22, 0),
            "45 focused minutes. Wrong answers go into sat-mistakes.md."
        ),
        TaskBlock(
            "wd_project", "Project — ${projectFocus(day)}", Track.PROJECT,
            LocalTime.of(22, 0), LocalTime.of(23, 0),
            "One small task, done fully. End with a commit — even WIP."
        ),
        TaskBlock(
            "wd_free", "Free time", Track.FREE,
            LocalTime.of(23, 0), LocalTime.of(23, 59),
            "Yours. Guilt-free.",
            counted = false
        ),
    )

    private fun satFocus(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY -> "Reading & Writing"
        else -> "Math"
    }

    private fun projectFocus(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Exact Hour firmware"
        DayOfWeek.TUESDAY -> "Exact Hour OpenSCAD design"
        DayOfWeek.WEDNESDAY -> "Bewsoa AI Clock feature"
        DayOfWeek.THURSDAY -> "Exact Hour — finish the week's task"
        else -> "docs, cleanup, plan Saturday hardware"
    }

    private val saturday = listOf(
        TaskBlock(
            "sa_tyt", "TYT exam", Track.TYT,
            LocalTime.of(9, 0), LocalTime.of(14, 0),
            "Full, timed, marked immediately. Fixed."
        ),
        TaskBlock(
            "sa_lunch", "Lunch + decompress", Track.MEAL,
            LocalTime.of(14, 0), LocalTime.of(15, 0),
            "You earned it.",
            counted = false
        ),
        TaskBlock(
            "sa_gym", "Gym — 2h session", Track.GYM,
            LocalTime.of(15, 0), LocalTime.of(17, 0),
            "Longest session of the week. No time pressure."
        ),
        TaskBlock(
            "sa_project", "Exact Hour — hardware", Track.PROJECT,
            LocalTime.of(17, 15), LocalTime.of(19, 0),
            "Rocker pad, wiring, printing, physical testing. Table space + daylight."
        ),
        TaskBlock(
            "sa_dinner", "Quick dinner", Track.MEAL,
            LocalTime.of(19, 0), LocalTime.of(19, 15),
            counted = false
        ),
        TaskBlock(
            "sa_yks", "YKS — TYT review + weak topics", Track.YKS,
            LocalTime.of(19, 15), LocalTime.of(21, 45),
            "Go over the TYT while it's fresh, then drill what's weak."
        ),
        TaskBlock(
            "sa_free", "Free time", Track.FREE,
            LocalTime.of(21, 45), LocalTime.of(23, 59),
            counted = false
        ),
    )

    private val sunday = listOf(
        TaskBlock(
            "su_sat_deep", "SAT — deep session", Track.SAT,
            LocalTime.of(10, 30), LocalTime.of(13, 0),
            "Timed section or full practice set. Mark and review immediately."
        ),
        TaskBlock(
            "su_lunch", "Lunch + break", Track.MEAL,
            LocalTime.of(13, 0), LocalTime.of(14, 30),
            counted = false
        ),
        TaskBlock(
            "su_project", "Project block — close the week", Track.PROJECT,
            LocalTime.of(14, 30), LocalTime.of(17, 30),
            "Finish what Saturday left open. Push to GitHub so the week ends clean."
        ),
        TaskBlock(
            "su_gym", "Gym — optional / light", Track.GYM,
            LocalTime.of(17, 30), LocalTime.of(18, 30),
            "Only if you're at 4 sessions or fewer this week."
        ),
        TaskBlock(
            "su_review", "Weekly review", Track.REVIEW,
            LocalTime.of(18, 30), LocalTime.of(19, 0),
            "The report builds itself from your logs — just add notes in the Review tab."
        ),
        TaskBlock(
            "su_free", "Free time", Track.FREE,
            LocalTime.of(19, 0), LocalTime.of(23, 59),
            "Buffer + recovery. Week ends clean.",
            counted = false
        ),
    )
}
