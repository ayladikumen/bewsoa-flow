package ai.bewsoa.flow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Stroke

/**
 * Progress as an arc. Single-colour now rather than a violet→cyan gradient:
 * the gradient was chrome, and this ring's job is to report a number.
 *
 * Springs rather than tweens — the ring is one of the few places motion is
 * allowed, and it should feel like it has mass.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    strokeWidth: Dp = Stroke.ring,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ring"
    )
    // Palette reads are composable — capture before the draw lambda.
    val palette = LocalPalette.current
    val trackColor = palette.outline
    val ringColor = color ?: palette.primary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = DrawStroke(stroke, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = DrawStroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** The XP ring. Same shape as [ProgressRing], but always the XP identity colour. */
@Composable
fun XpRing(
    current: Int,
    goal: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = Stroke.ring,
    content: @Composable BoxScope.() -> Unit = {}
) {
    ProgressRing(
        progress = if (goal <= 0) 0f else current.toFloat() / goal,
        modifier = modifier,
        color = LocalPalette.current.xp,
        strokeWidth = strokeWidth,
        content = content
    )
}

/** A flat pill bar. The old horizontal gradient fill was decoration; it's gone. */
@Composable
fun StatBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val animated by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bar"
    )
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(LocalPalette.current.outline)
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(shape)
                    .background(color)
            )
        }
    }
}

/**
 * A tiny line chart — the 7-day trend on Focus. Deliberately axis-less and
 * label-less: it answers "is this going up or down", nothing more.
 */
@Composable
fun Sparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val palette = LocalPalette.current
    val lineColor = color ?: palette.accent
    val baseColor = palette.outline
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.max()).coerceAtLeast(1)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            // Inset by the stroke so the extremes aren't clipped at the edges.
            val y = size.height - (v.toFloat() / max) * (size.height - 4f) - 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(
            color = baseColor,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1f
        )
        drawPath(
            path = path,
            color = lineColor,
            style = DrawStroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
