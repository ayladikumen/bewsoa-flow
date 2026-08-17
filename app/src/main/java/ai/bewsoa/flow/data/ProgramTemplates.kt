package ai.bewsoa.flow.data

import java.time.DayOfWeek

/**
 * A starter week. Templates are data, not a second scheduling system: each one
 * builds the same `Map<DayOfWeek, List<TaskBlock>>` everything else uses, hands
 * it back as a [WeeklyProgramDraft], and is then edited like any other draft.
 */
data class ProgramTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    private val blocks: Map<DayOfWeek, List<TaskBlock>>
) {
    fun draft(): WeeklyProgramDraft = WeeklyProgramDraft.of(blocks)
}

/**
 * The starter templates offered when a week is still blank.
 *
 * Ids follow the repository convention — `wd_` for a block identical on every
 * weekday, `sa_`/`su_` for the weekend — and deliberately reuse the built-in
 * program's ids where a block means the same thing, so a user who starts from a
 * template keeps the completion history those blocks already have.
 */
object ProgramTemplates {

    private val WEEKDAYS = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )

    /**
     * "Use current program as template": the standing recurring program — the
     * custom program when one is active, otherwise the built-in week. Day
     * overrides are deliberately not consulted; a one-time edit must never leak
     * into a recurring program.
     */
    fun fromStandingProgram(): WeeklyProgramDraft =
        WeeklyProgramDraft.of(WeeklyProgram.standingWeekMap())

    val studyFocus = ProgramTemplate(
        id = "study_focus",
        name = "Study Focus",
        emoji = "📚",
        tagline = "Exam work is the backbone; everything else fits around it.",
        blocks = week(
            weekdays(
                slot("wd_yks_morning", "YKS — deep work", Track.YKS, "09:00", "13:00", "The backbone of the day."),
                slot("wd_lunch", "Lunch", Track.MEAL, "13:00", "14:00", counted = false),
                slot("wd_yks_afternoon", "YKS — problem sets", Track.YKS, "14:00", "17:00"),
                slot("wd_gym", "Gym", Track.GYM, "17:30", "18:45"),
                slot("wd_dinner", "Dinner", Track.MEAL, "19:00", "19:30", counted = false),
                slot("wd_yks_review", "Review & mistake log", Track.YKS, "19:30", "21:30", "Active recall on today's topics."),
                slot("wd_sat", "SAT — 45 focused minutes", Track.SAT, "21:30", "22:15"),
                slot("wd_free", "Free time", Track.FREE, "22:15", "23:59", counted = false)
            ),
            DayOfWeek.SATURDAY to listOf(
                slot("sa_tyt", "TYT exam — full & timed", Track.TYT, "09:00", "14:00", "Marked immediately."),
                slot("sa_lunch", "Lunch + decompress", Track.MEAL, "14:00", "15:00", counted = false),
                slot("sa_yks", "TYT review + weak topics", Track.YKS, "15:00", "18:00"),
                slot("sa_free", "Free time", Track.FREE, "18:00", "23:59", counted = false)
            ),
            DayOfWeek.SUNDAY to listOf(
                slot("su_sat_deep", "SAT — deep session", Track.SAT, "10:30", "13:00"),
                slot("su_lunch", "Lunch + break", Track.MEAL, "13:00", "14:00", counted = false),
                slot("su_yks", "Weak-topic drills", Track.YKS, "14:00", "17:00"),
                slot("su_review", "Weekly review", Track.REVIEW, "18:00", "18:30", "Score the week, name one change."),
                slot("su_free", "Free time", Track.FREE, "18:30", "23:59", counted = false)
            )
        )
    )

    val balanced = ProgramTemplate(
        id = "balanced",
        name = "Balanced",
        emoji = "⚖️",
        tagline = "School, study, gym and a project — with real free time.",
        blocks = week(
            weekdays(
                slot("wd_school", "School", Track.YKS, "08:00", "16:00", "Lessons and breaks live inside this block."),
                slot("wd_gym", "Gym", Track.GYM, "17:00", "18:15"),
                slot("wd_dinner", "Dinner", Track.MEAL, "18:30", "19:00", counted = false),
                slot("wd_yks_review", "YKS — 2h study", Track.YKS, "19:00", "21:00"),
                slot("wd_project", "Project — one small task", Track.PROJECT, "21:00", "22:00", "End with a commit, even WIP."),
                slot("wd_free", "Free time", Track.FREE, "22:00", "23:59", counted = false)
            ),
            DayOfWeek.SATURDAY to listOf(
                slot("sa_tyt", "TYT practice", Track.TYT, "10:00", "13:00"),
                slot("sa_lunch", "Lunch", Track.MEAL, "13:00", "14:00", counted = false),
                slot("sa_gym", "Gym — long session", Track.GYM, "15:00", "16:30"),
                slot("sa_project", "Project block", Track.PROJECT, "17:00", "19:00"),
                slot("sa_free", "Free time", Track.FREE, "19:00", "23:59", counted = false)
            ),
            DayOfWeek.SUNDAY to listOf(
                slot("su_yks", "YKS — catch up", Track.YKS, "11:00", "13:00"),
                slot("su_lunch", "Lunch", Track.MEAL, "13:00", "14:00", counted = false),
                slot("su_project", "Project — close the week", Track.PROJECT, "14:00", "16:30"),
                slot("su_review", "Weekly review", Track.REVIEW, "18:00", "18:30"),
                slot("su_free", "Free time", Track.FREE, "18:30", "23:59", counted = false)
            )
        )
    )

    val gymAndStudy = ProgramTemplate(
        id = "gym_study",
        name = "Gym + Study",
        emoji = "🏋️",
        tagline = "Six sessions a week, study built around training.",
        blocks = week(
            weekdays(
                slot("wd_yks_morning", "YKS — deep work", Track.YKS, "09:30", "12:30"),
                slot("wd_lunch", "Lunch", Track.MEAL, "12:30", "13:30", counted = false),
                slot("wd_gym", "Gym — full program", Track.GYM, "14:00", "16:00", "Warm up properly, no rushing out."),
                slot("wd_yks_review", "YKS — review & drills", Track.YKS, "16:30", "18:30"),
                slot("wd_dinner", "Dinner", Track.MEAL, "18:30", "19:15", counted = false),
                slot("wd_sat", "SAT — 45 minutes", Track.SAT, "19:15", "20:00"),
                slot("wd_free", "Free time", Track.FREE, "20:00", "23:59", counted = false)
            ),
            DayOfWeek.SATURDAY to listOf(
                slot("sa_gym", "Gym — 2h session", Track.GYM, "10:00", "12:00", "Longest session of the week."),
                slot("sa_lunch", "Lunch", Track.MEAL, "12:00", "13:00", counted = false),
                slot("sa_tyt", "TYT exam", Track.TYT, "14:00", "17:00"),
                slot("sa_free", "Free time", Track.FREE, "17:00", "23:59", counted = false)
            ),
            DayOfWeek.SUNDAY to listOf(
                slot("su_gym", "Gym — light / mobility", Track.GYM, "11:00", "12:00"),
                slot("su_lunch", "Lunch", Track.MEAL, "12:30", "13:30", counted = false),
                slot("su_yks", "YKS — weak topics", Track.YKS, "14:00", "16:30"),
                slot("su_review", "Weekly review", Track.REVIEW, "18:00", "18:30"),
                slot("su_free", "Free time", Track.FREE, "18:30", "23:59", counted = false)
            )
        )
    )

    val examPrep = ProgramTemplate(
        id = "exam_prep",
        name = "Exam Prep",
        emoji = "📝",
        tagline = "Sprint mode: practice exams and immediate review.",
        blocks = week(
            weekdays(
                slot("wd_yks_morning", "YKS — timed sections", Track.YKS, "08:30", "12:00"),
                slot("wd_lunch", "Lunch", Track.MEAL, "12:00", "13:00", counted = false),
                slot("wd_yks_afternoon", "YKS — mistake review", Track.YKS, "13:00", "16:00", "Every wrong answer gets a reason."),
                slot("wd_sat", "SAT — drills", Track.SAT, "16:15", "17:30"),
                slot("wd_gym", "Gym — short session", Track.GYM, "18:00", "19:00", "Movement protects the brain."),
                slot("wd_dinner", "Dinner", Track.MEAL, "19:00", "19:45", counted = false),
                slot("wd_yks_review", "Recall drills", Track.YKS, "19:45", "21:45"),
                slot("wd_free", "Wind down", Track.FREE, "21:45", "23:59", counted = false)
            ),
            DayOfWeek.SATURDAY to listOf(
                slot("sa_tyt", "TYT exam — full & timed", Track.TYT, "09:00", "14:00"),
                slot("sa_lunch", "Lunch + decompress", Track.MEAL, "14:00", "15:00", counted = false),
                slot("sa_yks", "Exam review", Track.YKS, "15:00", "18:30", "Go over it while it's fresh."),
                slot("sa_free", "Free time", Track.FREE, "18:30", "23:59", counted = false)
            ),
            DayOfWeek.SUNDAY to listOf(
                slot("su_sat_deep", "SAT — full practice set", Track.SAT, "09:30", "13:00"),
                slot("su_lunch", "Lunch", Track.MEAL, "13:00", "14:00", counted = false),
                slot("su_yks", "Weak-topic rebuild", Track.YKS, "14:00", "17:30"),
                slot("su_review", "Weekly review", Track.REVIEW, "18:00", "18:30"),
                slot("su_free", "Free time", Track.FREE, "18:30", "23:59", counted = false)
            )
        )
    )

    val projectFocus = ProgramTemplate(
        id = "project_focus",
        name = "Project Focus",
        emoji = "💻",
        tagline = "Ship something every day, keep study steady.",
        blocks = week(
            weekdays(
                slot("wd_project", "Project — deep work", Track.PROJECT, "09:00", "13:00", "One task, finished fully."),
                slot("wd_lunch", "Lunch", Track.MEAL, "13:00", "14:00", counted = false),
                slot("wd_yks_morning", "YKS — study block", Track.YKS, "14:00", "17:00"),
                slot("wd_gym", "Gym", Track.GYM, "17:30", "18:45"),
                slot("wd_dinner", "Dinner", Track.MEAL, "19:00", "19:30", counted = false),
                slot("wd_project_evening", "Project — polish & commit", Track.PROJECT, "19:30", "21:30"),
                slot("wd_free", "Free time", Track.FREE, "21:30", "23:59", counted = false)
            ),
            DayOfWeek.SATURDAY to listOf(
                slot("sa_project", "Project — hardware / big task", Track.PROJECT, "10:00", "14:00", "Table space and daylight."),
                slot("sa_lunch", "Lunch", Track.MEAL, "14:00", "15:00", counted = false),
                slot("sa_gym", "Gym", Track.GYM, "15:00", "16:30"),
                slot("sa_yks", "YKS — study", Track.YKS, "17:00", "19:00"),
                slot("sa_free", "Free time", Track.FREE, "19:00", "23:59", counted = false)
            ),
            DayOfWeek.SUNDAY to listOf(
                slot("su_project", "Project — close the week", Track.PROJECT, "11:00", "15:00", "Push so the week ends clean."),
                slot("su_lunch", "Late lunch", Track.MEAL, "15:00", "16:00", counted = false),
                slot("su_yks", "YKS — light review", Track.YKS, "16:00", "18:00"),
                slot("su_review", "Weekly review", Track.REVIEW, "18:00", "18:30"),
                slot("su_free", "Free time", Track.FREE, "18:30", "23:59", counted = false)
            )
        )
    )

    val emptyWeek = ProgramTemplate(
        id = "empty",
        name = "Empty Week",
        emoji = "🗒️",
        tagline = "Seven blank days — build it block by block.",
        blocks = emptyMap()
    )

    val all: List<ProgramTemplate> =
        listOf(studyFocus, balanced, gymAndStudy, examPrep, projectFocus, emptyWeek)

    fun byId(id: String): ProgramTemplate? = all.firstOrNull { it.id == id }

    // Builders -----------------------------------------------------------------

    private fun slot(
        id: String,
        title: String,
        track: Track,
        start: String,
        end: String,
        note: String = "",
        counted: Boolean = true
    ) = TaskBlock(
        id = id,
        title = title,
        track = track,
        start = BlockCodec.parseTime(start),
        end = BlockCodec.parseTime(end),
        note = note,
        counted = counted
    )

    /** The same block list on Monday–Friday, ids included — the wd_ convention. */
    private fun weekdays(vararg blocks: TaskBlock): List<Pair<DayOfWeek, List<TaskBlock>>> =
        WEEKDAYS.map { it to blocks.toList() }

    private fun week(
        weekdays: List<Pair<DayOfWeek, List<TaskBlock>>>,
        vararg rest: Pair<DayOfWeek, List<TaskBlock>>
    ): Map<DayOfWeek, List<TaskBlock>> = (weekdays + rest).toMap()
}
