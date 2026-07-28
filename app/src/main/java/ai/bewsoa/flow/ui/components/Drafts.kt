package ai.bewsoa.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space

/**
 * How an AI draft asks for a verdict — the "new drafts UI". A sparkle-tinted
 * card that names its scope loudly (TODAY ONLY vs EVERY WEEK vs NEW TASK),
 * lists the concrete changes behind little rails, and offers exactly two
 * honest buttons. Nothing applies silently; this card is the contract.
 */
@Composable
fun DraftCard(
    scopeLabel: String,
    title: String,
    changes: List<String>,
    applyText: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    dismissText: String = "Not now"
) {
    val palette = LocalPalette.current
    Card(modifier = modifier, tone = CardTone.Accent(palette.accent)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                SectionLabel(scopeLabel)
                Spacer(Modifier.height(2.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
            }
        }
        if (note != null && note.isNotBlank()) {
            Spacer(Modifier.height(Space.s))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textDim
            )
        }
        if (changes.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            changes.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Rail(palette.accent.copy(alpha = 0.6f), Modifier.fillMaxHeight())
                    Spacer(Modifier.width(Space.m))
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textBright
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PrimaryButton(
                text = applyText,
                onClick = onApply,
                modifier = Modifier.weight(1.4f)
            )
            GhostButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
