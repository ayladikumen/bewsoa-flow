package ai.bewsoa.flow.data.exacthour

import ai.bewsoa.flow.data.ActiveFocus
import ai.bewsoa.flow.data.CurrentBlock
import ai.bewsoa.flow.data.TaskBlock
import java.time.Duration
import java.time.LocalTime

enum class ClockSource { FOCUS, BLOCK }

/**
 * What the clock *should* be showing. Deliberately a description, not a set of
 * calls: deciding is pure and testable, pushing is [ClockMirror]'s problem.
 */
sealed interface ClockPlan {

    /**
     * A fingerprint of the desired state.
     *
     * Keyed on when the thing *ends*, never on how much is left — otherwise it
     * would change every second and the mirror would restart the countdown on
     * every tick, fighting the user instead of following them.
     */
    val key: String

    data class Countdown(
        val label: String,
        val minutes: Int,
        val seconds: Int,
        val source: ClockSource,
        val endsAtEpochSecond: Long
    ) : ClockPlan {
        override val key: String get() = "run:$source:$label:$endsAtEpochSecond"
    }

    /** Too long to count down, so say what it is instead. */
    data class Marquee(val text: String, val source: ClockSource) : ClockPlan {
        override val key: String get() = "text:$source:$text"
    }

    data object Clear : ClockPlan {
        override val key: String get() = "clear"
    }
}

/**
 * Decides what the LED matrix shows, from what the app knows.
 *
 * Pure — no Android, no I/O, no clock reads of its own. Everything it needs
 * arrives as a parameter so the precedence rules can be pinned down in tests
 * rather than discovered on hardware.
 */
object ClockIntent {

    fun resolve(
        focus: ActiveFocus?,
        blocks: List<TaskBlock>,
        doneIds: Set<String>,
        skippedIds: Set<String>,
        now: LocalTime,
        nowMillis: Long,
        mirrorFocus: Boolean,
        mirrorBlocks: Boolean,
        maxMinutes: Int = ClockLimits.DEFAULT_MAX_MINUTES
    ): ClockPlan {
        // 1. Deep Focus wins outright. It is the thing the user just explicitly
        //    committed to, where a block is only a standing plan — and a focus
        //    session is capped at 4h on the way in, so it always fits.
        if (mirrorFocus && focus != null && nowMillis < focus.endsAt) {
            val remaining = focus.endsAt - nowMillis
            return countdown(
                label = focus.label,
                remainingSeconds = remaining / 1_000L,
                source = ClockSource.FOCUS,
                endsAtEpochSecond = focus.endsAt / 1_000L,
                maxMinutes = maxMinutes
            )
        }

        // 2. Otherwise, whatever block is running. Unlike the reminders this
        //    doesn't filter on `counted`: "🍽️ Dinner 30:00" is exactly the kind
        //    of thing a wall clock is good at, and meals are tickable now.
        if (mirrorBlocks) {
            val block = CurrentBlock.at(blocks, now, doneIds, skippedIds)
            if (block != null) {
                val remainingSeconds = Duration.between(now, block.end).seconds
                if (remainingSeconds > 0) {
                    return countdown(
                        // Title only. The track emoji is an app affordance —
                        // the clock's 7-pixel font has no glyph for it and
                        // would scroll a gap where it should be.
                        label = block.title,
                        remainingSeconds = remainingSeconds,
                        source = ClockSource.BLOCK,
                        // No date here — blocks are wall-clock times, and the
                        // key only has to be stable within a day.
                        endsAtEpochSecond = block.end.toSecondOfDay().toLong(),
                        maxMinutes = maxMinutes
                    )
                }
            }
        }

        // 3. Nothing to show. A stale countdown on the wall is worse than a
        //    blank display.
        return ClockPlan.Clear
    }

    /**
     * A countdown, unless it won't fit. The device caps at 270 minutes and some
     * blocks are much longer than that — showing a truncated number would be a
     * lie, so those scroll their title instead.
     */
    private fun countdown(
        label: String,
        remainingSeconds: Long,
        source: ClockSource,
        endsAtEpochSecond: Long,
        maxMinutes: Int
    ): ClockPlan {
        if (remainingSeconds <= 0) return ClockPlan.Clear
        val minutes = (remainingSeconds / 60L).toInt()
        val seconds = (remainingSeconds % 60L).toInt()
        if (minutes > maxMinutes) return ClockPlan.Marquee(label, source)
        return ClockPlan.Countdown(
            label = label,
            minutes = minutes,
            seconds = seconds,
            source = source,
            endsAtEpochSecond = endsAtEpochSecond
        )
    }
}
