package dev.pschmitt.jellyfin.presentation.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.settings.R as SettingsR

/**
 * Shown right after the user switches the download-location setting between internal/external
 * storage. Dismissing (back/tap-outside) is the implicit "skip" option - existing downloads are
 * left untouched and this dialog won't reappear until the setting changes again.
 */
@Composable
fun DownloadLocationChangeDialog(
    from: String,
    to: String,
    onMove: () -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val fromLabel =
        stringResource(
            if (from == "internal") SettingsR.string.download_location_internal
            else SettingsR.string.download_location_external
        )
    val toLabel =
        stringResource(
            if (to == "internal") SettingsR.string.download_location_internal
            else SettingsR.string.download_location_external
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(SettingsR.string.download_location_changed_title)) },
        text = {
            Text(
                text =
                    stringResource(
                        SettingsR.string.download_location_changed_message,
                        fromLabel,
                        toLabel,
                    )
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = onMove) {
                    Text(text = stringResource(SettingsR.string.download_location_move))
                }
                TextButton(onClick = onClear) {
                    Text(text = stringResource(SettingsR.string.download_location_clear))
                }
            }
        },
    )
}

@Composable
@Preview
private fun DownloadLocationChangeDialogPreview() {
    FindroidTheme {
        DownloadLocationChangeDialog(
            from = "internal",
            to = "external",
            onMove = {},
            onClear = {},
            onDismissRequest = {},
        )
    }
}
