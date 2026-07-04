package ai.bewsoa.flow.ui.theme

import androidx.compose.ui.graphics.Color
import ai.bewsoa.flow.data.Track

val DeepNavy = Color(0xFF0B1020)
val SurfaceNavy = Color(0xFF131A31)
val CardNavy = Color(0xFF182042)
val Violet = Color(0xFF7C5CFF)
val VioletDeep = Color(0xFF3A2B7A)
val Cyan = Color(0xFF22D3EE)
val Mint = Color(0xFF34D399)
val Amber = Color(0xFFF59E0B)
val Coral = Color(0xFFFB7185)
val Pink = Color(0xFFEC4899)
val Orange = Color(0xFFFB923C)
val Gold = Color(0xFFFBBF24)
val SlateBlue = Color(0xFF64748B)
val Muted = Color(0xFF475569)
val TextBright = Color(0xFFE8ECF8)
val TextDim = Color(0xFF9AA5C4)
val Outline = Color(0xFF2A3554)
val Ink = Color(0xFF0B1020)

val BackgroundGradient = listOf(
    Color(0xFF0B1020),
    Color(0xFF17123A),
    Color(0xFF0B1020)
)

fun Track.color(): Color = when (this) {
    Track.YKS -> Violet
    Track.TYT -> Pink
    Track.SAT -> Cyan
    Track.PROJECT -> Mint
    Track.GYM -> Orange
    Track.MEAL -> SlateBlue
    Track.REVIEW -> Gold
    Track.FREE -> Muted
}
