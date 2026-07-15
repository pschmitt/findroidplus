package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR

/** Formats a minute count as "15 minutes" / "2 hours" / "3 days", picking the coarsest exact unit. */
fun formatIntervalMinutes(minutes: Int): String =
    when {
        minutes % 1440 == 0 -> {
            val days = minutes / 1440
            if (days == 1) "1 day" else "$days days"
        }
        minutes % 60 == 0 -> {
            val hours = minutes / 60
            if (hours == 1) "1 hour" else "$hours hours"
        }
        else -> "$minutes minutes"
    }

/**
 * A list of preset intervals (radio buttons) plus a "Custom" option that reveals a numeric text
 * field. Used for both the backup interval and the auto-download check interval so both settings
 * share one picker UX instead of a bare number field.
 */
@Composable
fun IntervalPickerContent(
    value: Int,
    presetsMinutes: List<Int>,
    validRange: IntRange,
    onValueChange: (Int) -> Unit,
) {
    var customText by remember { mutableStateOf(if (value in presetsMinutes) "" else value.toString()) }
    val isCustomSelected = value !in presetsMinutes

    Column(modifier = Modifier.selectableGroup()) {
        presetsMinutes.forEach { preset ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .selectable(
                            selected = !isCustomSelected && value == preset,
                            onClick = { onValueChange(preset) },
                        )
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = !isCustomSelected && value == preset, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = formatIntervalMinutes(preset))
            }
        }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .selectable(
                        selected = isCustomSelected,
                        onClick = {
                            val parsed = customText.toIntOrNull()?.coerceIn(validRange)
                            onValueChange(parsed ?: validRange.first)
                        },
                    )
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isCustomSelected, onClick = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(CoreR.string.interval_custom))
        }
        if (isCustomSelected) {
            OutlinedTextField(
                value = customText,
                onValueChange = { text ->
                    customText = text
                    text.toIntOrNull()?.let { onValueChange(it.coerceIn(validRange)) }
                },
                label = { Text(text = stringResource(CoreR.string.interval_custom_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
            )
        }
    }
}
