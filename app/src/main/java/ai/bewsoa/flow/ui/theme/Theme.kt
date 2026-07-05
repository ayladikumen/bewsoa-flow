package ai.bewsoa.flow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.bewsoa.flow.data.SettingsRepository

/** Applies the palette the user picked in Settings, live. */
@Composable
fun BewsoaFlowTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val themeId by settings.appTheme.collectAsStateWithLifecycle(
        initialValue = ThemeCache.initialId
    )
    val palette = Palettes.byId(themeId)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = AppTypography,
            content = content
        )
    }
}
