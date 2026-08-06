package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize
import dev.pschmitt.jellyfin.utils.isPathOnRemovableStorage

/**
 * The same "icon + size" caption used for a downloaded item's row on the Downloads screen (see
 * `StorageIconFor`/`DownloadRow` there), reused on the movie/episode detail page so a downloaded
 * item's on-disk footprint and location are visible without a trip to that screen. [isBroken]
 * mirrors `JollyfinItem.isDownloadBroken()` - an error-tinted warning icon instead of the storage
 * icon, since a 0 B reading here means the same "file's actually missing" thing it does there.
 * [showSize] is false on the movie/episode detail page - the size lives on the "Delete download"
 * tile there instead (see ItemButtonsBar), so this only needs to surface the
 * broken/marked-for-deletion states; the size line is still shown when neither of those apply
 * everywhere else that doesn't merge size into a button.
 */
@Composable
fun LocalStorageIndicator(
    path: String,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
    isBroken: Boolean = false,
    isMarkedForDeletion: Boolean = false,
    showSize: Boolean = true,
) {
    val context = LocalContext.current
    val isRemovable = remember(path) { isPathOnRemovableStorage(context, path) }
    val showLeadingSegment = isBroken || showSize

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showLeadingSegment) {
            Icon(
                painter =
                    painterResource(
                        when {
                            isBroken -> CoreR.drawable.ic_alert_circle
                            isRemovable == true -> CoreR.drawable.ic_sd_card
                            else -> CoreR.drawable.ic_smartphone
                        }
                    ),
                contentDescription = null,
                tint =
                    if (isBroken) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.extraSmall))
            Text(
                text =
                    if (isBroken) stringResource(CoreR.string.download_row_broken)
                    else formatBinaryFileSize(sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isBroken) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isMarkedForDeletion && !isBroken) {
            if (showLeadingSegment) {
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
            }
            Icon(
                painter = painterResource(CoreR.drawable.ic_trash),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.extraSmall))
            Text(
                text = stringResource(CoreR.string.download_row_marked_for_deletion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalStorageIndicatorPreview() {
    JollyfinTheme {
        LocalStorageIndicator(path = "/storage/emulated/0/downloads/x", sizeBytes = 1_400_000_000L)
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalStorageIndicatorBrokenPreview() {
    JollyfinTheme {
        LocalStorageIndicator(
            path = "/storage/emulated/0/downloads/x",
            sizeBytes = 0L,
            isBroken = true,
        )
    }
}
