package ai.bewsoa.flow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space

/**
 * The Duolingo-style momentum widgets: the streak flame, the week dot row,
 * the level badge and the day-pace bar. Together they translate the XP
 * economy's raw numbers into things that mean something at a glance.
 */

/** How one day of the current week went, for [WeekDots]. */
enum class DayMark { KEPT, PARTIAL, MISSED, TODAY, FUTURE }

data class WeekDot(val letter: String, val mark: DayMark)

/**
 * The streak, worn like a badge: flame + day count in a warm pill. The flame
 * breathes while today is still unkept — a nudge, not an alarm — and sits
 * still once the day is banked.
 */
@Composable
fun FlamePill(
    days: Int,
    todayKept: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val scale = pulseScale(active = !todayKept)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(palette.warn.copy(alpha = if (todayKept) 0.9f else 0.16f))
            .padding(horizontal = Space.m, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "🔥",
            fontSize = 14.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            "$days",
            style = MaterialTheme.typography.titleMedium,
            color = if (todayKept) palette.ink else palette.warn
        )
    }
}

/**
 * Mon..Sun as seven dots: kept days fill green with a tick, partial days go
 * amber, missed days stay hollow, today wears a breathing ring, the future
 * waits in outline. The whole week's story with zero numbers.
 */
@Composable
fun WeekDots(dots: List<WeekDot>, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dots.forEach { dot ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dot.letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dot.mark == DayMark.TODAY) palette.warn else palette.textDim
                )
                Spacer(Modifier.height(6.dp))
                val ringScale = pulseScale(active = dot.mark == DayMark.TODAY)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            if (dot.mark == DayMark.TODAY) {
                                scaleX = ringScale
                                scaleY = ringScale
                            }
                        }
                        .clip(CircleShape)
                        .background(
                            when (dot.mark) {
                                DayMark.KEPT -> palette.success
                                DayMark.PARTIAL -> palette.warn.copy(alpha = 0.8f)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = when (dot.mark) {
                                DayMark.KEPT -> palette.success
                                DayMark.PARTIAL -> palette.warn.copy(alpha = 0.8f)
                                DayMark.TODAY -> palette.warn
                                DayMark.MISSED -> palette.outline
                                DayMark.FUTURE -> palette.outline.copy(alpha = 0.6f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (dot.mark == DayMark.KEPT || dot.mark == DayMark.PARTIAL) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = palette.ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The level, styled like a game rank: a gold squircle with the number, the
 * title next to it, and the runway to the next level — always phrased as
 * "N XP to go", because a countdown means something and a total doesn't.
 */
@Composable
fun LevelBadge(
    level: Int,
    title: String,
    xpToNext: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.xp.copy(alpha = 0.16f))
                .border(2.dp, palette.xp.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$level",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.xp
            )
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = palette.textBright
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$xpToNext XP to the next level",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim
            )
            Spacer(Modifier.height(Space.s))
            StatBar(ratio = progress, color = palette.xp, height = 6.dp)
        }
    }
}

/**
 * Time-awareness without a timetable: one bar is the day passing (the knob),
 * the fill is the plan getting done. When the fill is ahead of the knob you
 * are beating the day; the copy above it does the talking. Wake window is
 * 07:00–24:00 — matching the program's real span, not midnight-to-midnight.
 */
@Composable
fun DayPaceBar(
    dayFraction: Float,
    planFraction: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val ahead = planFraction >= dayFraction
    val fill by animateFloatAsState(
        targetValue = planFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "paceFill"
    )
    val knob by animateFloatAsState(
        targetValue = dayFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "paceKnob"
    )
    var barWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .onSizeChanged { barWidth = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track + fill.
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(palette.outline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(10.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (ahead) palette.success else palette.warn)
            )
        }
        // The clock knob riding on top.
        val knobOffset = with(density) { ((barWidth * knob).toDp() - 9.dp).coerceAtLeast(0.dp) }
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .border(2.dp, palette.textBright.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.textBright.copy(alpha = 0.75f))
            )
        }
    }
}
