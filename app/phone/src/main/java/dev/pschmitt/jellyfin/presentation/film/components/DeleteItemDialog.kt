package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

/**
 * Confirmation for permanently deleting an item from the Jellyfin server - a much heavier action
 * than [ClearDownloadsDialog] (which only ever touches this device's local copy), so it asks for
 * two independent points of friction rather than one: an explicit opt-in cascade checkbox (default
 * on by default, so a plain delete doesn't quietly leave the item to be re-grabbed on the next
 * Sonarr/Radarr scan) and a text field that must contain exactly "YES" before the Delete button
 * enables. Not localized ("YES" stays literal) - it's a deliberate low-level confirmation gesture,
 * not user-facing copy.
 */
@Composable
fun DeleteItemDialog(
    message: String,
    onConfirm: (cascadeToPvr: Boolean) -> Unit,
    onDismiss: () -> Unit,
    // Non-null only when this item has a resolvable Sonarr/Radarr identity and that service is
    // configured - otherwise there's nothing to cascade to.
    pvrCascadeLabel: String? = null,
    pvrCascadeSummary: String? = null,
) {
    var confirmText by remember { mutableStateOf("") }
    var cascadeChecked by remember { mutableStateOf(true) }
    val confirmEnabled = confirmText == "YES"

    AlertDialog(
        // Not AlertDialog's own `icon` slot - Material3 always renders that centered *above* the
        // title, not inline with it. Building the title as an icon+text Row instead keeps them on
        // the same line, same as ClearDownloadsDialog.
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = stringResource(CoreR.string.delete_from_jellyfin))
            }
        },
        text = {
            Column {
                Text(text = message)
                if (pvrCascadeLabel != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    Row(modifier = Modifier.clickable { cascadeChecked = !cascadeChecked }) {
                        Checkbox(checked = cascadeChecked, onCheckedChange = { cascadeChecked = it })
                        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                        Column {
                            Text(text = pvrCascadeLabel)
                            pvrCascadeSummary?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text(text = stringResource(CoreR.string.delete_item_confirm_label)) },
                    singleLine = true,
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = { onConfirm(cascadeChecked) }) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = null,
                    tint = if (confirmEnabled) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.delete),
                    color = if (confirmEnabled) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(painter = painterResource(CoreR.drawable.ic_x), contentDescription = null)
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = stringResource(CoreR.string.cancel))
            }
        },
    )
}

@Composable
@Preview
private fun DeleteItemDialogPreview() {
    FindroidTheme {
        DeleteItemDialog(
            message = stringResource(CoreR.string.delete_movie_message),
            onConfirm = {},
            onDismiss = {},
            pvrCascadeLabel = stringResource(CoreR.string.also_remove_from_radarr),
            pvrCascadeSummary = stringResource(CoreR.string.also_remove_from_radarr_summary),
        )
    }
}
