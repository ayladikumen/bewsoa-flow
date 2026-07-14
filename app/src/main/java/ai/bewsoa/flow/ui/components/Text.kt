package ai.bewsoa.flow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space

/**
 * The small wide label above a group. Uppercasing lives here so no call site
 * has to remember it, and so a translation can override it in one place.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalPalette.current.textDim,
        modifier = modifier
    )
}

/**
 * A number and what it means. The pairing of a big tight figure against a
 * small wide label is the core of the typographic hierarchy — it has to read
 * with all colour removed.
 */
@Composable
fun Metric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: Color? = null
) {
    val palette = LocalPalette.current
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = tone ?: palette.textBright
        )
        Spacer(Modifier.height(Space.xxs))
        SectionLabel(label)
    }
}

/**
 * What a tab shows before it has anything to show. Worth real care: this is
 * the first thing a new user sees on Focus and Chat, and an empty screen with
 * no affordance is where they leave.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = palette.textBright,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textDim,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(Space.l))
            action()
        }
    }
}
