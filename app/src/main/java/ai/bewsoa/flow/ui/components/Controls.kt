package ai.bewsoa.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space

/**
 * The one loud action on a screen. If a screen has two of these, one of them
 * is wrong — the accent colour is meant to appear at most twice per screen,
 * counting the active nav item.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(Radius.row),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.primary,
            contentColor = Color.White,
            disabledContainerColor = palette.outline,
            disabledContentColor = palette.textDim
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary action: reads as a button, doesn't compete for attention. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(Radius.row),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.surfaceHigh,
            contentColor = palette.textBright,
            disabledContainerColor = palette.surfaceHigh,
            disabledContentColor = palette.textDim
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** For the irreversible ones. Colour is the warning. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(Radius.row),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.danger.copy(alpha = 0.16f),
            contentColor = palette.danger,
            disabledContainerColor = palette.surfaceHigh,
            disabledContentColor = palette.textDim
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A pill. [color] is only ever a track or a state — never decoration.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalPalette.current
    val tint = color ?: palette.accent
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) tint.copy(alpha = 0.22f) else palette.surfaceHigh)
            .then(
                if (selected) Modifier.border(1.dp, tint.copy(alpha = 0.5f), shape) else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.m, vertical = Space.s)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.textBright else palette.textDim
        )
    }
}

/** Day/Week, provider pickers, intensity — anything mutually exclusive and short. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(palette.surfaceSunken)
            .padding(Space.xs)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (selected) palette.surfaceHigh else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = Space.s),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) palette.textBright else palette.textDim
                )
            }
        }
    }
}

/** The single text field definition for the whole app. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val palette = LocalPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(Radius.row),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        label = label?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.textDim) }
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.textBright,
            unfocusedTextColor = palette.textBright,
            focusedBorderColor = palette.accent.copy(alpha = 0.6f),
            unfocusedBorderColor = palette.outline,
            focusedLabelColor = palette.accent,
            unfocusedLabelColor = palette.textDim,
            cursorColor = palette.accent,
            focusedContainerColor = palette.surfaceHigh,
            unfocusedContainerColor = palette.surfaceHigh
        )
    )
}

/** Minus / value / plus. Used for capacity and any other coarse number. */
@Composable
fun NumberStepper(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color? = null
) {
    val palette = LocalPalette.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StepperButton(Icons.Rounded.Remove, "Decrease", onDecrease)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = tone ?: palette.textBright,
            modifier = Modifier.padding(horizontal = Space.m)
        )
        StepperButton(Icons.Rounded.Add, "Increase", onIncrease)
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(palette.surfaceHigh)
    ) {
        Icon(icon, contentDescription = description, tint = palette.textBright)
    }
}
