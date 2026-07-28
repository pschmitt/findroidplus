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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize

@Composable
fun ClearDownloadsDialog(
    title: String,
    message: String,
    onConfirm: (checkboxChecked: Boolean) -> Unit,
    onDismiss: () -> Unit,
    name: String? = null,
    sizeBytes: Long? = null,
    checkboxLabel: String = stringResource(CoreR.string.also_remove_auto_download_rules),
    checkboxSummary: String = stringResource(CoreR.string.also_remove_auto_download_rules_summary),
    checkboxDefault: Boolean = true,
) {
    var checkboxChecked by remember { mutableStateOf(checkboxDefault) }

    AlertDialog(
        // Not AlertDialog's own `icon` slot - Material3 always renders that centered *above* the
        // title, not inline with it. Building the title as an icon+text Row instead keeps them on
        // the same line.
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = title)
            }
        },
        text = {
            Column {
                Text(text = message)
                if (name != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                    Text(text = name, style = MaterialTheme.typography.bodyMedium)
                }
                if (sizeBytes != null) {
                    Text(
                        text = formatBinaryFileSize(sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                Row(
                    modifier = Modifier.clickable { checkboxChecked = !checkboxChecked },
                ) {
                    Checkbox(
                        checked = checkboxChecked,
                        onCheckedChange = { checkboxChecked = it },
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                    Column {
                        Text(text = checkboxLabel)
                        Text(text = checkboxSummary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(checkboxChecked) }) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.delete_download),
                    color = MaterialTheme.colorScheme.error,
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
private fun ClearDownloadsDialogPreview() {
    FindroidTheme {
        ClearDownloadsDialog(
            title = stringResource(CoreR.string.clear_all_downloads),
            message = stringResource(CoreR.string.clear_all_downloads_message),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
