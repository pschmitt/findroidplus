package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun ClearDownloadsDialog(
    title: String,
    message: String,
    onConfirm: (alsoRemoveRules: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var alsoRemoveRules by remember { mutableStateOf(true) }

    AlertDialog(
        title = { Text(text = title) },
        text = {
            Column {
                Text(text = message)
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                Row(
                    modifier = Modifier.clickable { alsoRemoveRules = !alsoRemoveRules },
                ) {
                    Checkbox(checked = alsoRemoveRules, onCheckedChange = { alsoRemoveRules = it })
                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                    Column {
                        Text(text = stringResource(CoreR.string.also_remove_auto_download_rules))
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.also_remove_auto_download_rules_summary
                                ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoRemoveRules) }) {
                Text(text = stringResource(CoreR.string.delete_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
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
