package ai.bewsoa.flow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletDeep,
    onPrimaryContainer = TextBright,
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Amber,
    onTertiary = Ink,
    background = DeepNavy,
    onBackground = TextBright,
    surface = SurfaceNavy,
    onSurface = TextBright,
    surfaceVariant = CardNavy,
    onSurfaceVariant = TextDim,
    outline = Outline,
    error = Coral,
    onError = Ink
)

/** Bewsoa Flow is dark-first by design — the app always uses the night palette. */
@Composable
fun BewsoaFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
