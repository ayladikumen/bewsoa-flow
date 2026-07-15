package ai.bewsoa.flow.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.bewsoa.flow.data.ChestState
import ai.bewsoa.flow.data.Xp
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.CardTone
import ai.bewsoa.flow.ui.components.PrimaryButton
import ai.bewsoa.flow.ui.components.XpRing
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space

/**
 * Where the account stands: level ring, title, and this week's earnings.
 * The ring shows progress *into the current level* — the same number the
 * Today card's thin bar tracks, drawn big.
 */
@Composable
fun LevelCard(xp: XpProgress) {
    val palette = LocalPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XpRing(
                current = xp.level.xpIntoLevel,
                goal = xp.level.xpForNext,
                modifier = Modifier.size(72.dp),
                strokeWidth = 7.dp
            ) {
                Text(
                    "${xp.level.index}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.xp
                )
            }
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                Text(
                    "LEVEL ${xp.level.index}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.xp,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    xp.level.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${xp.totalXp} XP total · " +
                        "${xp.level.xpForNext - xp.level.xpIntoLevel} to next level",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            }
            Spacer(Modifier.width(Space.s))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "+${xp.weekXp}",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.xp
                )
                Text(
                    "THIS WEEK",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textDim
                )
            }
        }
    }
}

/**
 * The weekly chest, Duolingo-style: earn it by keeping days, open it by hand —
 * the tap is the payoff. Locked shows honest progress; claimable turns loud;
 * opened goes quiet until Monday.
 */
@Composable
fun ChestCard(chest: ChestState, onOpen: () -> Unit) {
    val palette = LocalPalette.current
    when {
        chest.claimable -> Card(tone = CardTone.Accent(palette.xp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 36.sp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Chest unlocked!",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textBright
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${chest.keptDays} kept days · +${chest.reward} XP inside",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            PrimaryButton(
                text = "Open chest",
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            )
        }

        chest.opened -> Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 28.sp)
                Spacer(Modifier.width(Space.l))
                Column {
                    Text(
                        "Chest collected ✓",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textBright
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "A new one starts building Monday.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim
                    )
                }
            }
        }

        else -> Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 28.sp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Weekly chest",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textBright
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Keep ${Xp.CHEST_UNLOCK_DAYS} days to unlock — " +
                            "${chest.keptDays} of ${Xp.CHEST_UNLOCK_DAYS} so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            ChestProgress(chest.keptDays)
        }
    }
}

/** Five fat segments, one per required kept day. Filled = earned. */
@Composable
private fun ChestProgress(keptDays: Int) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        repeat(Xp.CHEST_UNLOCK_DAYS) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (i < keptDays) palette.xp else palette.outline)
            )
        }
    }
}

/** Last week's unopened chest — a second chance, not a nag. Claimable only. */
@Composable
fun LastChestCard(chest: ChestState, onOpen: () -> Unit) {
    val palette = LocalPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", fontSize = 24.sp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    "Last week's chest is still waiting",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textBright
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "+${chest.reward} XP · expires " +
                        "${Xp.chestExpiry(chest.weekStart)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            }
            androidx.compose.material3.TextButton(onClick = onOpen) {
                Text(
                    "Open",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.xp
                )
            }
        }
    }
}
