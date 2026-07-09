package ai.bewsoa.flow.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ai.bewsoa.flow.data.Track

// The legacy color names, now live lookups into the selected palette.
// Screens keep their existing imports; only the palette behind them changes.
// (Composable getters — usable anywhere inside composition, but capture them
// into locals before Canvas/draw lambdas.)

val DeepNavy: Color @Composable get() = LocalPalette.current.background
val SurfaceNavy: Color @Composable get() = LocalPalette.current.surface
val CardNavy: Color @Composable get() = LocalPalette.current.card
val Violet: Color @Composable get() = LocalPalette.current.primary
val VioletDeep: Color @Composable get() = LocalPalette.current.primaryDeep
val Cyan: Color @Composable get() = LocalPalette.current.accent
val Mint: Color @Composable get() = LocalPalette.current.success
val Amber: Color @Composable get() = LocalPalette.current.warn
val Coral: Color @Composable get() = LocalPalette.current.danger
val Pink: Color @Composable get() = LocalPalette.current.pink
val Orange: Color @Composable get() = LocalPalette.current.orange
val Gold: Color @Composable get() = LocalPalette.current.gold
val SlateBlue: Color @Composable get() = LocalPalette.current.slate
val Muted: Color @Composable get() = LocalPalette.current.muted
val TextBright: Color @Composable get() = LocalPalette.current.textBright
val TextDim: Color @Composable get() = LocalPalette.current.textDim
val Outline: Color @Composable get() = LocalPalette.current.outline
val Ink: Color @Composable get() = LocalPalette.current.ink

val BackgroundGradient: List<Color> @Composable get() = LocalPalette.current.backgroundGradient

@Composable
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
