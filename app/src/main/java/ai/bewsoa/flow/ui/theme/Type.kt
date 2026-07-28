package ai.bewsoa.flow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tabular figures. Any number that animates or has to line up in a column gets
 * this — without it, proportional digits change width and the Focus countdown
 * jitters on every tick.
 */
private const val Tnum = "tnum"

/**
 * Two families with clear jobs: Baloo 2 (rounded, chunky) owns display,
 * headlines and card titles — the "poster voice" of the redesign — while
 * Nunito carries body and labels. Line heights are taller than the old scale
 * because Baloo's rounded ascenders need the air.
 */
val AppTypography = Typography(
    // Focus countdown. Stays on the system font: it ticks every second and
    // needs tabular digits, which the display face doesn't guarantee.
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 66.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = Tnum
    ),
    // Hero numbers: completion percent, streak count, celebration headline.
    displayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    // The big indigo screen title ("Good evening", "Tasks", "Your week").
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp
    ),
    // Card title.
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),
    // Row title.
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontSize = 12.sp, lineHeight = 17.sp),
    // Buttons.
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    // Times, chips, small numbers.
    labelMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = Tnum
    ),
    // SECTION HEADERS — always uppercased by SectionLabel, never by hand.
    labelSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.2.sp
    )
)
