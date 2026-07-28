package ai.bewsoa.flow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Motion
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space

/**
 * The pastel checklist row — the visual signature of 3.0. A track-tinted
 * wash over the surface colour gives the candy-paper look on Sunrise and a
 * subtle glow-less tint on the dark palettes. Rows are the colour carriers;
 * cards stay white.
 */
@Composable
fun PastelRow(
    tint: Color,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val wash by animateColorAsState(
        targetValue = tint.copy(alpha = if (dimmed) 0.07f else if (palette.isLight) 0.16f else 0.14f),
        animationSpec = tween(Motion.BASE),
        label = "wash"
    )
    val shape = RoundedCornerShape(Radius.card)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressBounce(interaction, 0.98f) else Modifier)
            .clip(shape)
            .background(palette.surface)
            .background(wash)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Space.l, vertical = verticalPadding)
            .graphicsLayer { alpha = if (dimmed) 0.62f else 1f },
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * The round outline checkbox from the reference design. Unchecked it is a
 * quiet ring in the row's colour; checking it fills the circle and pops a
 * white tick in with a spring — the core loop should feel like a little win.
 */
@Composable
fun RoundCheck(
    checked: Boolean,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (checked) color else Color.Transparent,
        animationSpec = tween(Motion.FAST),
        label = "checkFill"
    )
    Box(
        modifier = modifier
            .size(size)
            .pressBounce(interaction, 0.85f)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, if (checked) color else color.copy(alpha = 0.55f), CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                if (!checked) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = if (checked) "Mark as not done" else "Mark as done",
            tint = palette.ink,
            modifier = Modifier
                .size(size * 0.6f)
                .popIn(checked)
        )
    }
}

/**
 * A small loud pill — "3 left", "NOW", "8 PENDING". Filled means solid colour
 * with knockout text (the reference app's orange pending badge); unfilled is a
 * quiet tint.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .padding(horizontal = Space.m, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) palette.ink else color
        )
    }
}

/** The tiny coloured dot that keys a row to its track (the timeline look). */
@Composable
fun TrackDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
