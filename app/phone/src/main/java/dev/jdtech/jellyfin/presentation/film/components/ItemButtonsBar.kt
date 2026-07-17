package dev.jdtech.jellyfin.presentation.film.components

import android.app.DownloadManager
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.displayNameWithContext
import dev.jdtech.jellyfin.utils.resolveDownloadStorageIndex

@Composable
fun ItemButtonsBar(
    item: FindroidItem,
    onPlayClick: (startFromBeginning: Boolean) -> Unit,
    onMarkAsPlayedClick: () -> Unit,
    onMarkAsFavoriteClick: () -> Unit,
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
    getSeasons: (suspend () -> List<FindroidSeason>)? = null,
    hasActiveDownloadOrRule: Boolean = false,
    onDeleteDownloads: (() -> Unit)? = null,
    onBulkDownload:
        (selection: DownloadSelection, alsoFollowNew: Boolean, onlyUnwatched: Boolean) -> Unit =
        { _, _, _ ->
        },
    downloadIconTint: Color? = null,
    onInfoClick: (() -> Unit)? = null,
    trailingContent: @Composable FlowRowScope.() -> Unit = {},
) {
    val context = LocalContext.current

    val trailerUri =
        when (item) {
            is FindroidMovie -> {
                item.trailer
            }
            is FindroidShow -> {
                item.trailer
            }
            else -> null
        }

    val downloadedSource =
        if (item.isDownloaded()) {
            item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
        } else {
            null
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

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            // One compact row of icon actions instead of a wrapping bar of labeled buttons:
            // toggles (played/favorite) use the standard checked container color rather than a
            // red icon tint, and the former overflow menu's actions are simply always visible.
            // Each icon carries a content description; destructive delete is error-tinted.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            ) {
                FilledTonalIconToggleButton(
                    checked = item.played,
                    onCheckedChange = { onMarkAsPlayedClick() },
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_check),
                        contentDescription =
                            stringResource(
                                if (item.played) CoreR.string.unmark_as_played
                                else CoreR.string.mark_as_played
                            ),
                    )
                }
                FilledTonalIconToggleButton(
                    checked = item.favorite,
                    onCheckedChange = { onMarkAsFavoriteClick() },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (item.favorite) CoreR.drawable.ic_heart_filled
                                else CoreR.drawable.ic_heart
                            ),
                        contentDescription =
                            stringResource(
                                if (item.favorite) CoreR.string.remove_from_favorites
                                else CoreR.string.add_to_favorites
                            ),
                    )
                }
                val canRestart = item.playbackPositionTicks.div(600000000) > 0
                if (canRestart) {
                    FilledTonalIconButton(onClick = { onPlayClick(true) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                            contentDescription =
                                stringResource(CoreR.string.restart_from_beginning),
                        )
                    }
                }
                trailerUri?.let { uri ->
                    FilledTonalIconButton(onClick = { onTrailerClick(uri) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_film),
                            contentDescription = stringResource(CoreR.string.watch_trailer),
                        )
                    }
                }
                onInfoClick?.let { infoClick ->
                    FilledTonalIconButton(onClick = infoClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_info),
                            contentDescription = stringResource(CoreR.string.info),
                        )
                    }
                }
                trailingContent()
                if (downloaderState != null && !downloaderState.isDownloading) {
                    if (item.isDownloaded()) {
                        // Size/path details live in the confirmation dialog this opens.
                        FilledTonalIconButton(
                            onClick = { deleteDownloadDialogOpen = true },
                            colors =
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription =
                                    downloadedSource?.size?.let { size ->
                                        stringResource(
                                            CoreR.string.delete_download_with_size,
                                            Formatter.formatFileSize(context, size),
                                        )
                                    } ?: stringResource(CoreR.string.delete_download),
                            )
                        }
                    } else if (item.canDownload || item is FindroidShow || item is FindroidSeason) {
                        FilledTonalIconButton(
                            onClick = {
                                if (enableDownloadDialog) {
                                    downloadScopeDialogOpen = true
                                } else {
                                    startDownload()
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_download),
                                contentDescription = stringResource(CoreR.string.download),
                                tint = downloadIconTint ?: LocalContentColor.current,
                            )
                        }
                    }
                }
            }
            if (downloaderState != null) {
                AnimatedVisibility(downloaderState.isDownloading) {
                    Column {
                        DownloaderCard(
                            state = downloaderState,
                            onCancelClick = { cancelDownloadDialogOpen = true },
                            onRetryClick = { onDownloadClick(selectedStorageIndex) },
                            onForceClick = onDownloadForceClick,
                            onPauseClick = onDownloadPauseClick,
                            onResumeClick = onDownloadResumeClick,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                    }
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
            var seasons by remember { mutableStateOf<List<FindroidSeason>?>(null) }
            LaunchedEffect(Unit) { seasons = getSeasons?.invoke() ?: emptyList() }
            DownloadScopeDialog(
                seasons = seasons,
                showEpisodeOption = showEpisodeDownloadOption,
                initialSelection = initialSelection,
                initialAlsoFollowNew = initialAlsoFollowNew,
                initialOnlyUnwatched = initialOnlyUnwatched,
                canDelete = hasActiveDownloadOrRule,
                onDelete =
                    onDeleteDownloads?.let {
                        {
                            downloadScopeDialogOpen = false
                            it()
                        }
                    },
                onConfirm = { selection, alsoFollowNew, onlyUnwatched ->
                    downloadScopeDialogOpen = false
                    if (selection.thisEpisodeOnly) {
                        startDownload()
                    } else {
                        onBulkDownload(selection, alsoFollowNew, onlyUnwatched)
                    }
                },
                onDismiss = { downloadScopeDialogOpen = false },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemButtonsBarPreview() {
    FindroidTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            onPlayClick = {},
            onMarkAsPlayedClick = {},
            onMarkAsFavoriteClick = {},
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
    FindroidTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            downloaderState =
                DownloaderState(status = DownloadManager.STATUS_RUNNING, progress = 0.3f),
            onPlayClick = {},
            onMarkAsPlayedClick = {},
            onMarkAsFavoriteClick = {},
            onDownloadClick = {},
            onDownloadCancelClick = {},
            onDownloadDeleteClick = {},
            onTrailerClick = {},
        )
    }
}
