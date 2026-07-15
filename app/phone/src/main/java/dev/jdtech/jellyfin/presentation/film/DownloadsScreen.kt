package dev.jdtech.jellyfin.presentation.film

import android.app.DownloadManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadAction
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadShowGroup
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsState
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.presentation.film.components.ClearDownloadsDialog
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.DownloadProgress
import dev.jdtech.jellyfin.utils.formatDownloadSpeed
import dev.jdtech.jellyfin.utils.formatEta
import java.util.UUID

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    onAutoDownloadRulesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val androidContext = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.startObserving() }

    var clearAllDialogOpen by remember { mutableStateOf(false) }
    var deleteSelectedDialogOpen by remember { mutableStateOf(false) }

    DownloadsScreenLayout(
        state = state,
        onAutoDownloadRulesClick = onAutoDownloadRulesClick,
        onSettingsClick = onSettingsClick,
        onTrashClick = {
            if (state.selectedIds.isNotEmpty()) deleteSelectedDialogOpen = true
            else clearAllDialogOpen = true
        },
        onItemClick = onItemClick,
        onToggleSelection = viewModel::toggleSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onToggleGroupSelection = viewModel::setGroupSelected,
        onDownloadAction = viewModel::onDownloadAction,
    )

    if (clearAllDialogOpen) {
        ClearDownloadsDialog(
            title = stringResource(CoreR.string.clear_all_downloads),
            message = stringResource(CoreR.string.clear_all_downloads_message),
            onConfirm = { alsoRemoveRules ->
                viewModel.clearAllDownloads(alsoRemoveRules)
                Toast.makeText(
                        androidContext,
                        CoreR.string.downloads_deleted_toast,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                clearAllDialogOpen = false
            },
            onDismiss = { clearAllDialogOpen = false },
        )
    }

    if (deleteSelectedDialogOpen) {
        DeleteSelectedDownloadsDialog(
            count = state.selectedIds.size,
            onConfirm = {
                viewModel.deleteSelected()
                deleteSelectedDialogOpen = false
            },
            onDismiss = { deleteSelectedDialogOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: DownloadsState,
    onAutoDownloadRulesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onItemClick: (FindroidItem) -> Unit = {},
    onToggleSelection: (UUID) -> Unit = {},
    onToggleSelectAll: (Boolean) -> Unit = {},
    onToggleGroupSelection: (Set<UUID>, Boolean) -> Unit = { _, _ -> },
    onDownloadAction: (UUID, DownloadAction) -> Unit = { _, _ -> },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val allIds =
        remember(state.movies, state.showGroups) {
            (state.movies.map { it.id } + state.showGroups.flatMap { it.episodes }.map { it.id })
                .toSet()
        }
    val allSelected = allIds.isNotEmpty() && state.selectedIds.containsAll(allIds)

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_download)) },
                actions = {
                    IconButton(onClick = onAutoDownloadRulesClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_refresh_cw),
                            contentDescription = stringResource(CoreR.string.auto_download_rules),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_settings),
                            contentDescription = stringResource(CoreR.string.title_settings),
                        )
                    }
                    if (!state.isEmpty) {
                        IconButton(onClick = onTrashClick) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription =
                                    if (state.selectedIds.isNotEmpty()) {
                                        stringResource(CoreR.string.delete_selected_downloads)
                                    } else {
                                        stringResource(CoreR.string.clear_all_downloads)
                                    },
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isEmpty && !state.isLoading) {
                Text(
                    text = stringResource(CoreR.string.no_downloads),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (allIds.isNotEmpty()) {
                    item {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { onToggleSelectAll(!allSelected) }
                                    .padding(
                                        horizontal = MaterialTheme.spacings.default,
                                        vertical = MaterialTheme.spacings.small,
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = allSelected, onCheckedChange = onToggleSelectAll)
                            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                            Text(text = stringResource(CoreR.string.manage_downloads_select_all))
                        }
                    }
                }
                if (state.movies.isNotEmpty()) {
                    stickyHeader { SectionHeader(text = stringResource(CoreR.string.movies_label)) }
                    items(items = state.movies, key = { it.id }) { movie ->
                        DownloadRow(
                            item = movie,
                            title = movie.name,
                            checked = movie.id in state.selectedIds,
                            progress = state.downloadProgress[movie.id],
                            onClick = { onItemClick(movie) },
                            onToggleSelection = { onToggleSelection(movie.id) },
                            onDownloadAction = { onDownloadAction(movie.id, it) },
                        )
                    }
                }
                state.showGroups.forEach { group ->
                    val groupIds = group.episodes.map { it.id }.toSet()
                    val groupSelected = groupIds.isNotEmpty() && state.selectedIds.containsAll(groupIds)
                    stickyHeader {
                        ShowGroupHeader(
                            group = group,
                            checked = groupSelected,
                            onToggle = { onToggleGroupSelection(groupIds, !groupSelected) },
                        )
                    }
                    items(items = group.episodes, key = { it.id }) { episode ->
                        DownloadRow(
                            item = episode,
                            title =
                                stringResource(
                                    CoreR.string.episode_name_extended,
                                    episode.parentIndexNumber,
                                    episode.indexNumber,
                                    episode.name,
                                ),
                            checked = episode.id in state.selectedIds,
                            progress = state.downloadProgress[episode.id],
                            onClick = { onItemClick(episode) },
                            onToggleSelection = { onToggleSelection(episode.id) },
                            onDownloadAction = { onDownloadAction(episode.id, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Card {
        Text(
            text = text,
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacings.medium,
                    vertical = MaterialTheme.spacings.medium,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ShowGroupHeader(group: DownloadShowGroup, checked: Boolean, onToggle: () -> Unit) {
    Card {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(
                        horizontal = MaterialTheme.spacings.medium,
                        vertical = MaterialTheme.spacings.small,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(32.dp).clip(MaterialTheme.shapes.extraSmall)) {
                ItemPoster(item = group.episodes.first(), direction = Direction.VERTICAL)
            }
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
            Text(
                text = group.seriesName,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun DownloadRow(
    item: FindroidItem,
    title: String,
    checked: Boolean,
    progress: DownloadProgress?,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
) {
    val context = LocalContext.current
    val activeProgress = progress?.takeIf { it.status != DownloadManager.STATUS_SUCCESSFUL }
    val isPending = activeProgress?.status == DownloadManager.STATUS_PENDING
    val isPaused = activeProgress?.status == DownloadManager.STATUS_PAUSED
    val sizeBytes = item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }?.size ?: 0L

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = MaterialTheme.spacings.default,
                    vertical = MaterialTheme.spacings.small,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(96.dp).clip(MaterialTheme.shapes.small)) {
            ItemPoster(item = item, direction = Direction.HORIZONTAL)
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.default))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (activeProgress != null) {
                Text(
                    text =
                        when {
                            isPending -> stringResource(CoreR.string.download_queued)
                            isPaused -> stringResource(CoreR.string.download_paused)
                            activeProgress.percent >= 0 ->
                                stringResource(
                                    CoreR.string.download_progress_status,
                                    activeProgress.percent,
                                    formatDownloadSpeed(context, activeProgress.speedBytesPerSecond),
                                    formatEta(activeProgress.etaSeconds),
                                )
                            else -> stringResource(CoreR.string.download_downloading)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isPending) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { activeProgress.percent.coerceAtLeast(0) / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            } else {
                Text(
                    text = Formatter.formatFileSize(context, sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        if (activeProgress != null) {
            if (!isPending) {
                IconButton(
                    onClick = {
                        onDownloadAction(if (isPaused) DownloadAction.Resume else DownloadAction.Pause)
                    }
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isPaused) CoreR.drawable.ic_play else CoreR.drawable.ic_pause
                            ),
                        contentDescription =
                            stringResource(
                                if (isPaused) CoreR.string.download_action_resume
                                else CoreR.string.download_action_pause
                            ),
                    )
                }
            }
            IconButton(onClick = { onDownloadAction(DownloadAction.Cancel) }) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_x),
                    contentDescription = stringResource(CoreR.string.download_action_cancel),
                )
            }
        } else {
            Checkbox(checked = checked, onCheckedChange = { onToggleSelection() })
        }
    }
}

@Composable
private fun DeleteSelectedDownloadsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.delete_selected_downloads)) },
        text = { Text(text = stringResource(CoreR.string.delete_selected_downloads_message, count)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(CoreR.string.delete_download)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

@PreviewScreenSizes
@Composable
private fun DownloadsScreenLayoutPreview() {
    FindroidTheme {
        DownloadsScreenLayout(
            state =
                DownloadsState(
                    movies = listOf(dummyMovie),
                    showGroups =
                        listOf(
                            DownloadShowGroup(
                                seriesId = dummyEpisode.seriesId,
                                seriesName = dummyEpisode.seriesName,
                                episodes = listOf(dummyEpisode),
                            )
                        ),
                )
        )
    }
}
