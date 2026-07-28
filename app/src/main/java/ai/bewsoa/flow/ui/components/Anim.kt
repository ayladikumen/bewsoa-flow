package ai.bewsoa.flow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import ai.bewsoa.flow.ui.theme.Motion

/**
 * The motion vocabulary of the redesign, in one place. Three moves, used
 * everywhere, so the app feels consistent rather than busy:
 *  - [pressBounce]: anything tappable squishes slightly under the finger.
 *  - [appear]: list content floats up and fades in, staggered by position.
 *  - [popIn]: a state change (check fill, badge, NOW pill) lands with a spring.
 */

/** Squish-on-press. The rounded design language invites it — everything is a button. */
fun Modifier.pressBounce(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressBounce"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * First-composition entrance: fade + float up, delayed by [index] so a screen
 * builds itself top-to-bottom instead of blinking on. One-shot — recompositions
 * and scrolling never replay it.
 */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = Motion.SLOW,
            delayMillis = (index * 45).coerceAtMost(360)
        ),
        label = "appear"
    )
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 28f
    }
}

/** Spring-scale from 0 whenever [key] flips true — the "it landed" move. */
fun Modifier.popIn(visible: Boolean): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "popIn"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = scale.coerceIn(0f, 1f)
    }
}

/** A slow, gentle pulse for living things (the streak flame, the NOW dot). */
@Composable
fun pulseScale(active: Boolean): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    return scale
}
