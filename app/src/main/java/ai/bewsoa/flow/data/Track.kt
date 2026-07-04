package ai.bewsoa.flow.data

/**
 * The tracks from the weekly program. Every block on the schedule belongs to one.
 */
enum class Track(val emoji: String, val label: String) {
    YKS("📚", "YKS"),
    TYT("📝", "TYT"),
    SAT("🇺🇸", "SAT"),
    PROJECT("💻", "Projects"),
    GYM("🏋️", "Gym"),
    MEAL("🍽️", "Fuel"),
    REVIEW("🗓️", "Review"),
    FREE("🔋", "Free time")
}
