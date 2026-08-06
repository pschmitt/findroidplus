package dev.pschmitt.jellyfin.presentation.film.components

import android.app.DownloadManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloadSelection
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloadSizeEstimate
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloaderState
import dev.pschmitt.jellyfin.core.presentation.dummy.dummyEpisode
import dev.pschmitt.jellyfin.models.JollyfinItem
import dev.pschmitt.jellyfin.models.JollyfinMovie
import dev.pschmitt.jellyfin.models.JollyfinSeason
import dev.pschmitt.jellyfin.models.JollyfinShow
import dev.pschmitt.jellyfin.models.JollyfinSourceType
import dev.pschmitt.jellyfin.models.RemoteDeviceInfo
import dev.pschmitt.jellyfin.models.isDownloaded
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.displayNameWithContext
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize
import dev.pschmitt.jellyfin.utils.resolveDownloadStorageIndex
import java.util.UUID

@Composable
fun ItemButtonsBar(
    item: JollyfinItem,
    onPlayClick: (startFromBeginning: Boolean) -> Unit,
    onDownloadClick: (storageIndex: Int) -> Unit,
    onDownloadCancelClick: () -> Unit,
    onDownloadDeleteClick: () -> Unit,
    onDownloadForceClick: () -> Unit = {},
    onDownloadPauseClick: () -> Unit = {},
    onDownloadResumeClick: () -> Unit = {},
    onTrailerClick: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    downloaderState: DownloaderState? = null,
    downloadLocationPreference: String = "ask",
    enableDownloadDialog: Boolean = false,
    showEpisodeDownloadOption: Boolean = false,
    initialSelection: DownloadSelection = DownloadSelection(),
    initialAlsoFollowNew: Boolean = false,
    initialOnlyUnwatched: Boolean = false,
    getSeasons: (suspend () -> List<JollyfinSeason>)? = null,
    getSeasonSize: (suspend (seasonId: UUID, onlyUnwatched: Boolean) -> DownloadSizeEstimate)? =
        null,
    hasActiveDownloadOrRule: Boolean = false,
    onDeleteDownloads: (() -> Unit)? = null,
    // FINDROID-44: devices other than this one seen via heartbeat, for DownloadScopeDialog's
    // device picker - null hides the picker entirely (same gating as a null getSeasons).
    getOtherDevices: (suspend () -> List<RemoteDeviceInfo>)? = null,
    onBulkDownload:
        (
            selection: DownloadSelection,
            alsoFollowNew: Boolean,
            onlyUnwatched: Boolean,
            targetDeviceId: String?,
        ) -> Unit =
        { _, _, _, _ ->
        },
    // Only reachable when showEpisodeDownloadOption is true (Episode screen) and a target device
    // was picked - the local "this episode" immediate download instead goes through
    // onDownloadClick/startDownload as always.
    onPushEpisodeDownload: (targetDeviceId: String) -> Unit = {},
    downloadIconTint: Color? = null,
    trailingContent: @Composable FlowRowScope.() -> Unit = {},
    // Rendered alongside whichever of the download progress card/Delete/Download button is
    // currently showing below the tile row (e.g. ItemOverflowMenu) - unlike [trailingContent],
    // which is just another tile in the same wrapping FlowRow as everything else.
    overflowContent: @Composable () -> Unit = {},
    // Set when this item's own PVR queue entry has an import warning/failure to resolve - makes
    // the download progress card tappable to open the manage-import sheet.
    onDownloadCardClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    val trailerUri =
        when (item) {
            is JollyfinMovie -> {
                item.trailer
            }
            is JollyfinShow -> {
                item.trailer
            }
            else -> null
        }

    val downloadedSource =
        if (item.isDownloaded()) {
            item.sources.firstOrNull { it.type == JollyfinSourceType.LOCAL }
        } else {
            null
        }

    // Already known synchronously from the loaded item - the "this episode" scope in
    // DownloadScopeDialog doesn't need a network fetch the way bulk season sizes do.
    val singleItemSize =
        remember(item) {
            DownloadSizeEstimate(sizeBytes = item.sources.firstOrNull()?.size ?: 0, itemCount = 1)
        }

    var storageSelectionDialogOpen by remember { mutableStateOf(false) }
    var cancelDownloadDialogOpen by remember { mutableStateOf(false) }
    var deleteDownloadDialogOpen by remember { mutableStateOf(false) }
    var downloadScopeDialogOpen by remember { mutableStateOf(false) }

    var selectedStorageIndex by remember { mutableIntStateOf(0) }
    var storageLocations = remember { context.getExternalFilesDirs(null) }

    val startDownload: () -> Unit = {
        storageLocations = context.getExternalFilesDirs(null)
        val preferredIndex = resolveDownloadStorageIndex(context, downloadLocationPreference)
        when {
            preferredIndex >= 0 -> {
                selectedStorageIndex = preferredIndex
                onDownloadClick(selectedStorageIndex)
            }
            storageLocations.size > 1 -> {
                storageSelectionDialogOpen = true
            }
            else -> {
                selectedStorageIndex = 0
                onDownloadClick(selectedStorageIndex)
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        // One row of uniform labeled action tiles ([ItemActionButton]): every action - including
        // anything injected via [trailingContent], like the "delete downloads" tile - shares the
        // same icon-above-label silhouette. Played/favorite toggles only appear here when the
        // caller wires them up (Show/Season); Episode/Movie surface those on their meta line
        // instead. [overflowContent] (e.g. ItemOverflowMenu) sits below this FlowRow, alongside
        // whichever of the download progress card/Delete/Download button is showing - see there.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            val canRestart = item.playbackPositionTicks.div(600000000) > 0
            if (canRestart) {
                ItemActionButton(
                    icon = painterResource(CoreR.drawable.ic_rotate_ccw),
                    label = stringResource(CoreR.string.restart_from_beginning),
                    onClick = { onPlayClick(true) },
                )
            }
            trailerUri?.let { uri ->
                ItemActionButton(
                    icon = painterResource(CoreR.drawable.ic_film),
                    label = stringResource(CoreR.string.trailer),
                    onClick = { onTrailerClick(uri) },
                )
            }
            trailingContent()
        }
        // Exactly one of these three renders at a time, each sharing its row with
        // [overflowContent]: the live progress card while downloading, a large "Delete download"
        // button once downloaded, or a large "Download" button when neither. While isDeleting,
        // neither button shows (a second tap could queue another delete of a file that's already
        // going away) - the overflow menu still needs a place, so it renders alone on its own row.
        if (downloaderState != null) {
            AnimatedVisibility(downloaderState.isDownloading) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DownloaderCard(
                            state = downloaderState,
                            onCancelClick = { cancelDownloadDialogOpen = true },
                            onRetryClick = { onDownloadClick(selectedStorageIndex) },
                            onForceClick = onDownloadForceClick,
                            onPauseClick = onDownloadPauseClick,
                            onResumeClick = onDownloadResumeClick,
                            onCardClick = onDownloadCardClick,
                            modifier = Modifier.weight(1f),
                        )
                        overflowContent()
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                }
            }
            if (!downloaderState.isDownloading) {
                if (item.isDownloaded() && !downloaderState.isDeleting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The label doubles as the on-disk size so it doesn't need its own
                        // separate caption elsewhere on the screen - falls back to the generic
                        // label for a broken (0-byte/missing) download, where a size reading
                        // would be misleading. Path details still live in the confirmation dialog
                        // this opens.
                        FilledTonalButton(
                            onClick = { deleteDownloadDialogOpen = true },
                            modifier = Modifier.weight(1f),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(MaterialTheme.spacings.small))
                            Text(
                                downloadedSource
                                    ?.takeIf { it.size > 0L }
                                    ?.let { formatBinaryFileSize(it.size) }
                                    ?: stringResource(CoreR.string.delete_download)
                            )
                        }
                        overflowContent()
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                } else if (
                    !downloaderState.isDeleting &&
                        (item.canDownload || item is JollyfinShow || item is JollyfinSeason)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                if (enableDownloadDialog) {
                                    downloadScopeDialogOpen = true
                                } else {
                                    startDownload()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_download),
                                contentDescription = null,
                                tint = downloadIconTint ?: LocalContentColor.current,
                            )
                            Spacer(Modifier.width(MaterialTheme.spacings.small))
                            Text(stringResource(CoreR.string.download))
                        }
                        overflowContent()
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                } else {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        overflowContent()
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                overflowContent()
            }
        }
    }
    if (storageSelectionDialogOpen) {
        val locations = remember {
            storageLocations.map { dir ->
                val locationStringRes =
                    if (Environment.isExternalStorageRemovable(dir)) CoreR.string.external
                    else CoreR.string.internal
                val locationString = context.getString(locationStringRes)

                val stat = StatFs(dir.path)
                val availableMegaBytes = stat.availableBytes.div(1000000)
                context.getString(CoreR.string.storage_name, locationString, availableMegaBytes)
            }
        }
        StorageSelectionDialog(
            storageLocations = locations,
            onSelect = { storageIndex ->
                selectedStorageIndex = storageIndex
                onDownloadClick(selectedStorageIndex)
                storageSelectionDialogOpen = false
            },
            onDismiss = { storageSelectionDialogOpen = false },
        )
    }
    if (cancelDownloadDialogOpen) {
        CancelDownloadDialog(
            onCancel = {
                onDownloadCancelClick()
                cancelDownloadDialogOpen = false
            },
            onDismiss = { cancelDownloadDialogOpen = false },
        )
    }
    if (deleteDownloadDialogOpen) {
        DeleteDownloadDialog(
            onDelete = {
                onDownloadDeleteClick()
                deleteDownloadDialogOpen = false
            },
            onDismiss = { deleteDownloadDialogOpen = false },
            name = item.displayNameWithContext(),
            path = downloadedSource?.path,
            sizeBytes = downloadedSource?.size,
        )
    }
    if (downloadScopeDialogOpen) {
        var seasons by remember { mutableStateOf<List<JollyfinSeason>?>(null) }
        var otherDevices by remember { mutableStateOf<List<RemoteDeviceInfo>>(emptyList()) }
        LaunchedEffect(Unit) { seasons = getSeasons?.invoke() ?: emptyList() }
        LaunchedEffect(Unit) { otherDevices = getOtherDevices?.invoke() ?: emptyList() }
        DownloadScopeDialog(
            seasons = seasons,
            showEpisodeOption = showEpisodeDownloadOption,
            initialSelection = initialSelection,
            initialAlsoFollowNew = initialAlsoFollowNew,
            initialOnlyUnwatched = initialOnlyUnwatched,
            canDelete = hasActiveDownloadOrRule,
            getSeasonSize = getSeasonSize,
            episodeSize = singleItemSize,
            downloadLocationPreference = downloadLocationPreference,
            otherDevices = otherDevices,
            onDelete =
                onDeleteDownloads?.let {
                    {
                        downloadScopeDialogOpen = false
                        it()
                    }
                },
            onConfirm = { selection, alsoFollowNew, onlyUnwatched, targetDeviceId ->
                downloadScopeDialogOpen = false
                if (targetDeviceId != null && selection.thisEpisodeOnly) {
                    // The immediate single-item case has no season/rule scope at all, so it's a
                    // wholly separate remote path from onBulkDownload below.
                    onPushEpisodeDownload(targetDeviceId)
                } else if (selection.thisEpisodeOnly) {
                    startDownload()
                }
                // Not mutually exclusive: "this episode" is an immediate single download,
                // "also download new episodes" is a forward-looking rule - both can be selected
                // at once and both should happen. The rule branch only needs to fire on top of a
                // single download when it's actually configured (matches the non-episode
                // seasons/show flows, where this always ran because thisEpisodeOnly is never true
                // there).
                if (
                    !selection.thisEpisodeOnly || alsoFollowNew || selection.seasonIds.isNotEmpty()
                ) {
                    onBulkDownload(selection, alsoFollowNew, onlyUnwatched, targetDeviceId)
                }
            },
            onDismiss = { downloadScopeDialogOpen = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemButtonsBarPreview() {
    JollyfinTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            onPlayClick = {},
            onDownloadClick = {},
            onDownloadCancelClick = {},
            onDownloadDeleteClick = {},
            onTrailerClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemButtonsBarDownloadingPreview() {
    JollyfinTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            downloaderState =
                DownloaderState(status = DownloadManager.STATUS_RUNNING, progress = 0.3f),
            onPlayClick = {},
            onDownloadClick = {},
            onDownloadCancelClick = {},
            onDownloadDeleteClick = {},
            onTrailerClick = {},
        )
    }
}
