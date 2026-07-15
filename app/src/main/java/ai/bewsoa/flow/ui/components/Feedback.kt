package ai.bewsoa.flow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ai.bewsoa.flow.data.Celebration
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/**
 * One piece of falling confetti. Physics is a pure function of elapsed time —
 * position is derived, never mutated — so a burst of 120 of these costs one
 * Canvas redraw per frame and nothing else.
 */
private class ConfettiPiece(
    val x0: Float,          // 0..1, fraction of width
    val vx: Float,          // horizontal drift, fractions/s
    val vy: Float,          // launch velocity, fractions/s (negative = up)
    val rot0: Float,
    val vr: Float,          // degrees/s
    val size: Float,        // px-ish, scaled by density at draw
    val colorIndex: Int
)

private const val GRAVITY = 1.1f       // fractions of height / s²
private const val CONFETTI_SECONDS = 2.8f

/**
 * A one-shot confetti cannon: pieces launch upward from the bottom corners,
 * arc, tumble and fall out of frame. Hand-rolled on one Canvas — no library,
 * no shimmer, gone in under three seconds. Motion budget well spent.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val pieces = remember {
        List(120) { i ->
            val fromLeft = i % 2 == 0
            ConfettiPiece(
                x0 = if (fromLeft) 0.05f else 0.95f,
                vx = (Random.nextFloat() * 0.55f + 0.1f) * (if (fromLeft) 1f else -1f),
                vy = -(Random.nextFloat() * 0.9f + 0.9f),
                rot0 = Random.nextFloat() * 360f,
                vr = (Random.nextFloat() - 0.5f) * 720f,
                size = Random.nextFloat() * 16f + 10f,
                colorIndex = i % 6
            )
        }
    }
    // Palette reads are composable — capture into a list before the draw lambda.
    val palette = LocalPalette.current
    val colors = remember(palette) {
        listOf(palette.primary, palette.accent, palette.success,
            palette.warn, palette.pink, palette.xp)
    }

    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (elapsed < CONFETTI_SECONDS) {
            withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
        }
    }

    Canvas(modifier) {
        val t = elapsed
        pieces.forEach { p ->
            val x = (p.x0 + p.vx * t) * size.width
            val y = (0.95f + p.vy * t + 0.5f * GRAVITY * t * t) * size.height
            if (y > size.height + 40f || x < -40f || x > size.width + 40f) return@forEach
            val alpha = (1f - (t / CONFETTI_SECONDS)).coerceIn(0f, 1f)
            rotate(degrees = p.rot0 + p.vr * t, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[p.colorIndex],
                    topLeft = Offset(x - p.size / 2f, y - p.size * 0.3f),
                    size = Size(p.size, p.size * 0.6f),
                    alpha = alpha
                )
            }
        }
    }
}

/**
 * The full-screen takeover for a [Celebration] — scrim, confetti, one big
 * card. Lives once in AppRoot above the Scaffold, so a level-up earned from a
 * widget tap or a chest on Progress celebrates identically. Celebrations
 * queue: a chest that also levels you up plays two moments back to back.
 */
@Composable
fun CelebrationHost(celebrations: Flow<Celebration>, modifier: Modifier = Modifier) {
    val queue = remember { mutableStateListOf<Celebration>() }
    var current by remember { mutableStateOf<Celebration?>(null) }

    LaunchedEffect(celebrations) {
        celebrations.collect { queue.add(it) }
    }
    LaunchedEffect(queue.size, current) {
        if (current == null && queue.isNotEmpty()) current = queue.removeAt(0)
    }

    val showing = current ?: return
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(showing) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(3_200)
        current = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(palette.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { current = null },
        contentAlignment = Alignment.Center
    ) {
        Confetti(Modifier.fillMaxSize())
        CelebrationCard(showing)
    }
}

@Composable
private fun CelebrationCard(celebration: Celebration) {
    val palette = LocalPalette.current
    val (emoji, label, headline, sub) = when (celebration) {
        is Celebration.LevelUp -> CelebrationCopy(
            emoji = "⚡",
            label = "LEVEL UP",
            headline = "Level ${celebration.level.index}",
            sub = "You are now “${celebration.level.title}”."
        )
        is Celebration.GoalHit -> CelebrationCopy(
            emoji = "🎯",
            label = "DAILY GOAL HIT",
            headline = "+${celebration.bonus} XP",
            sub = "Everything from here is pure bonus."
        )
        is Celebration.ChestOpened -> CelebrationCopy(
            emoji = "🎁",
            label = "WEEKLY CHEST",
            headline = "+${celebration.amount} XP",
            sub = "Paid for days you actually kept."
        )
        is Celebration.StreakMilestone -> CelebrationCopy(
            emoji = "🔥",
            label = "STREAK MILESTONE",
            headline = "${celebration.days} days",
            sub = "+${celebration.bonus} XP · never missing twice, made visible."
        )
    }
    Card(modifier = Modifier.padding(horizontal = Space.xxl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 56.sp)
            Spacer(Modifier.height(Space.m))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.xp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(Space.s))
            Text(
                headline,
                style = MaterialTheme.typography.displayMedium,
                color = palette.textBright,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.s))
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textDim,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class CelebrationCopy(
    val emoji: String,
    val label: String,
    val headline: String,
    val sub: String
)
