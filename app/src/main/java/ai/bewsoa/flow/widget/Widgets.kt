package ai.bewsoa.flow.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.ui.theme.Palettes
import kotlinx.coroutines.flow.first

/** The app palette translated for Glance, resolved from the saved theme. */
data class WidgetPalette(
    val card: ColorProvider,
    val chip: ColorProvider,
    val bright: ColorProvider,
    val dim: ColorProvider,
    val accent: ColorProvider,
    val success: ColorProvider,
    val warn: ColorProvider,
    val outline: ColorProvider
)

object Widgets {

    suspend fun palette(context: Context): WidgetPalette {
        val themeId = SettingsRepository.get(context).appTheme.first()
        val p = Palettes.byId(themeId)
        return WidgetPalette(
            card = ColorProvider(p.surface),
            chip = ColorProvider(p.primaryDeep),
            bright = ColorProvider(p.textBright),
            dim = ColorProvider(p.textDim),
            accent = ColorProvider(p.accent),
            success = ColorProvider(p.success),
            warn = ColorProvider(p.warn),
            outline = ColorProvider(p.outline)
        )
    }

    /** One call refreshes every widget type after data or theme changes. */
    suspend fun refreshAll(context: Context) {
        FlowWidget().updateAll(context)
        ProgressWidget().updateAll(context)
        StreakWidget().updateAll(context)
    }
}
