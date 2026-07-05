package ai.bewsoa.flow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One selectable look for the whole app. Field names are semantic; the legacy
 * color names in Color.kt (Cyan, Violet, CardNavy…) map onto these so every
 * screen re-themes without rewrites. Widgets read the same palettes through
 * [Palettes.byId].
 */
data class Palette(
    val id: String,
    val label: String,
    val isLight: Boolean,
    val background: Color,
    val surface: Color,
    val card: Color,
    val primary: Color,      // Violet slot — YKS, buttons, ring
    val primaryDeep: Color,  // VioletDeep slot — chips, indicators
    val accent: Color,       // Cyan slot — labels, highlights
    val success: Color,      // Mint slot
    val warn: Color,         // Amber slot — streak
    val danger: Color,       // Coral slot — errors
    val pink: Color,         // TYT
    val orange: Color,       // GYM
    val gold: Color,         // REVIEW
    val slate: Color,        // MEAL
    val muted: Color,        // FREE
    val textBright: Color,
    val textDim: Color,
    val outline: Color,
    val ink: Color,          // text on accent/warn chips
    val backgroundGradient: List<Color>
)

object Palettes {

    /** The original look — deep navy, violet and cyan neon. */
    val NeonNight = Palette(
        id = "neon_night",
        label = "Neon Night",
        isLight = false,
        background = Color(0xFF0B1020),
        surface = Color(0xFF131A31),
        card = Color(0xFF182042),
        primary = Color(0xFF7C5CFF),
        primaryDeep = Color(0xFF3A2B7A),
        accent = Color(0xFF22D3EE),
        success = Color(0xFF34D399),
        warn = Color(0xFFF59E0B),
        danger = Color(0xFFFB7185),
        pink = Color(0xFFEC4899),
        orange = Color(0xFFFB923C),
        gold = Color(0xFFFBBF24),
        slate = Color(0xFF64748B),
        muted = Color(0xFF475569),
        textBright = Color(0xFFE8ECF8),
        textDim = Color(0xFF9AA5C4),
        outline = Color(0xFF2A3554),
        ink = Color(0xFF0B1020),
        backgroundGradient = listOf(
            Color(0xFF0B1020), Color(0xFF17123A), Color(0xFF0B1020)
        )
    )

    /** Warm dark — maroon night, fire oranges and gold. */
    val Ember = Palette(
        id = "ember",
        label = "Ember",
        isLight = false,
        background = Color(0xFF190E0C),
        surface = Color(0xFF241412),
        card = Color(0xFF2E1A16),
        primary = Color(0xFFFF6B4A),
        primaryDeep = Color(0xFF6E2A1A),
        accent = Color(0xFFFFB454),
        success = Color(0xFF7CE0A3),
        warn = Color(0xFFFBBF24),
        danger = Color(0xFFFB7185),
        pink = Color(0xFFF472B6),
        orange = Color(0xFFFB923C),
        gold = Color(0xFFFCD34D),
        slate = Color(0xFF8A7268),
        muted = Color(0xFF6B554C),
        textBright = Color(0xFFF8ECE6),
        textDim = Color(0xFFC7A99C),
        outline = Color(0xFF4C2E24),
        ink = Color(0xFF190E0C),
        backgroundGradient = listOf(
            Color(0xFF190E0C), Color(0xFF3A1712), Color(0xFF190E0C)
        )
    )

    /** Light theme — paper white with the violet/cyan identity kept readable. */
    val Daylight = Palette(
        id = "daylight",
        label = "Daylight",
        isLight = true,
        background = Color(0xFFF3F5FC),
        surface = Color(0xFFFFFFFF),
        card = Color(0xFFFFFFFF),
        primary = Color(0xFF6D4AFF),
        primaryDeep = Color(0xFFE3DCFF),
        accent = Color(0xFF0E7490),
        success = Color(0xFF059669),
        warn = Color(0xFFD97706),
        danger = Color(0xFFE11D48),
        pink = Color(0xFFDB2777),
        orange = Color(0xFFEA580C),
        gold = Color(0xFFCA8A04),
        slate = Color(0xFF64748B),
        muted = Color(0xFF94A3B8),
        textBright = Color(0xFF1A2340),
        textDim = Color(0xFF5A6486),
        outline = Color(0xFFD6DCEF),
        ink = Color(0xFFFFFFFF),
        backgroundGradient = listOf(
            Color(0xFFF3F5FC), Color(0xFFE9E3FF), Color(0xFFF3F5FC)
        )
    )

    /** AMOLED black — pure black, mint and cyan glow. */
    val PitchBlack = Palette(
        id = "pitch_black",
        label = "Pitch Black",
        isLight = false,
        background = Color(0xFF000000),
        surface = Color(0xFF0A0D14),
        card = Color(0xFF0F1420),
        primary = Color(0xFF8B5CF6),
        primaryDeep = Color(0xFF2E1065),
        accent = Color(0xFF22D3EE),
        success = Color(0xFF34D399),
        warn = Color(0xFFF59E0B),
        danger = Color(0xFFFB7185),
        pink = Color(0xFFEC4899),
        orange = Color(0xFFFB923C),
        gold = Color(0xFFFBBF24),
        slate = Color(0xFF64748B),
        muted = Color(0xFF3F4A5C),
        textBright = Color(0xFFEDF1FA),
        textDim = Color(0xFF8B95AD),
        outline = Color(0xFF1E2634),
        ink = Color(0xFF000000),
        backgroundGradient = listOf(
            Color(0xFF000000), Color(0xFF0A0F1E), Color(0xFF000000)
        )
    )

    val all = listOf(NeonNight, Ember, Daylight, PitchBlack)

    const val DEFAULT_ID = "neon_night"

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: NeonNight
}

val LocalPalette = compositionLocalOf { Palettes.NeonNight }

/**
 * The theme id read synchronously at app start (alongside the program), so the
 * first frame already uses the saved theme instead of flashing the default.
 */
object ThemeCache {
    @Volatile
    var initialId: String = Palettes.DEFAULT_ID
}

fun Palette.toColorScheme(): ColorScheme = if (isLight) {
    lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryDeep,
        onPrimaryContainer = textBright,
        secondary = accent,
        onSecondary = ink,
        tertiary = warn,
        onTertiary = ink,
        background = background,
        onBackground = textBright,
        surface = surface,
        onSurface = textBright,
        surfaceVariant = card,
        onSurfaceVariant = textDim,
        outline = outline,
        error = danger,
        onError = Color.White
    )
} else {
    darkColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryDeep,
        onPrimaryContainer = textBright,
        secondary = accent,
        onSecondary = ink,
        tertiary = warn,
        onTertiary = ink,
        background = background,
        onBackground = textBright,
        surface = surface,
        onSurface = textBright,
        surfaceVariant = card,
        onSurfaceVariant = textDim,
        outline = outline,
        error = danger,
        onError = ink
    )
}
