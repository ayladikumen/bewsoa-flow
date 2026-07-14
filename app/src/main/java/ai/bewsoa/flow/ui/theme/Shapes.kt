package ai.bewsoa.flow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * Maps the [Radius] family onto Material's slots so stock M3 components
 * (sheets, menus, dialogs) pick up the same shapes as our own surfaces.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.cell),
    small = RoundedCornerShape(Radius.row),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.sheet)
)
