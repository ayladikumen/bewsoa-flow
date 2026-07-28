package ai.bewsoa.flow.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ai.bewsoa.flow.R

/**
 * The 3.0 voice, bundled as TTFs so the look is identical offline, on first
 * launch, and in release builds — no downloadable-font provider involved.
 *
 * Baloo 2 is the personality: big, chunky, rounded — the screen titles and hero
 * numbers. Nunito does the reading work everywhere else. The Focus countdown
 * deliberately stays on the system font (see Type.kt) for tabular digits.
 */
val DisplayFamily = FontFamily(
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold)
)

val BodyFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)
