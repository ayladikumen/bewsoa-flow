package ai.bewsoa.flow.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The measurements the whole app is built from. The redesign leans on a strict
 * rhythm instead of per-screen literals: screens gutter at [Space.l], cards pad
 * at [Space.l], and stack at [Space.m].
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** One shape family. Anything rounder or squarer than these is a mistake. */
object Radius {
    val cell = 6.dp
    val row = 12.dp
    val card = 20.dp
    val sheet = 28.dp
    val pill = 999.dp
}

object Stroke {
    /** The track rail — the motif that replaced the old glow border. */
    val rail = 3.dp
    val hairline = 1.dp
    val ring = 10.dp
}

/** Durations in ms. Motion is scarce here: rings, XP and levels only. */
object Motion {
    const val FAST = 140
    const val BASE = 240
    const val SLOW = 420
}
