package ai.bewsoa.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ai.bewsoa.flow.ui.theme.LocalPalette

// Transitional shims. The redesign replaced these with AppBackground (Surfaces.kt),
// Card (Surfaces.kt) and SectionLabel (Text.kt); they stay only until the last
// screen has migrated, then this file goes away.
//
// ProgressRing and StatBar moved to Progress.kt — same package, so call sites
// were untouched.

@Deprecated(
    "The full-screen gradient is gone; use AppBackground.",
    ReplaceWith("AppBackground(content)")
)
@Composable
fun GradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalPalette.current.background),
        content = content
    )
}

@Deprecated(
    "Glow borders are gone; use Card, with CardTone.Accent where a colour carries meaning.",
    ReplaceWith("Card(modifier, content = content)")
)
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        tone = accent?.let { CardTone.Accent(it) } ?: CardTone.Plain,
        content = content
    )
}

@Deprecated("Renamed for consistency with the other text helpers.", ReplaceWith("SectionLabel(text)"))
@Composable
fun SectionHeader(text: String) = SectionLabel(text)
