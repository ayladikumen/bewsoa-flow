package ai.bewsoa.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.Stroke

/**
 * A flat, solid canvas. The old full-screen gradient is gone: when every
 * surface glows, nothing reads as important. The gradient survives only as a
 * deliberate moment (see the celebration aurora), never as wallpaper.
 */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalPalette.current.background),
        content = content
    )
}

/** How a [Card] separates itself from what's behind it. */
sealed interface CardTone {
    /** The default: one tonal step above the background. */
    data object Plain : CardTone

    /** A step *down* — wells, gutters, anything that should read as inset. */
    data object Sunken : CardTone

    /** Carries a track or state colour. Used sparingly; colour here means data. */
    data class Accent(val color: Color) : CardTone
}

/**
 * The workhorse surface. Replaces GlowCard: elevation is a tonal step rather
 * than a border and a glow, which is the only thing that reads correctly on
 * the AMOLED palette where shadows are invisible.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Plain,
    padding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPalette.current
    val background = when (tone) {
        CardTone.Plain -> palette.surface
        CardTone.Sunken -> palette.surfaceSunken
        is CardTone.Accent -> palette.surface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(background)
            .then(
                if (tone is CardTone.Accent) {
                    Modifier.background(tone.color.copy(alpha = 0.10f))
                } else {
                    Modifier
                }
            )
            .then(if (padding) Modifier.padding(Space.l) else Modifier),
        content = content
    )
}

/**
 * The signature of the redesign: a full-height bar in the track colour on the
 * leading edge of a block, task, cell or card. This one motif is what replaced
 * the glow, and it is the only place track colour is allowed to appear.
 */
@Composable
fun Rail(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(Stroke.rail)
            .fillMaxHeight()
            .clip(RoundedCornerShape(Radius.pill))
            .background(color)
    )
}
