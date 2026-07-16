package dev.jdtech.jellyfin.presentation.film

import android.app.DownloadManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.IntOffset
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
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.film.components.ClearDownloadsDialog
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.DeleteProgress
import dev.jdtech.jellyfin.utils.DownloadProgress
import dev.jdtech.jellyfin.utils.formatDownloadSpeed
import dev.jdtech.jellyfin.utils.formatEta
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    onSettingsClick: () -> Unit,
    onShowClick: (UUID) -> Unit = {},
    onMoviesClick: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val androidContext = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.startObserving() }

    var clearAllDialogOpen by remember { mutableStateOf(false) }
    var deleteSelectedDialogOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingDownloadDelete?>(null) }

    val allItems = state.movies + state.showGroups.flatMap { it.episodes }
    val totalSizeBytes =
        remember(allItems) {
            allItems.sumOf { it.sources.firstOrNull { s -> s.type == FindroidSourceType.LOCAL }?.size ?: 0L }
        }
    val selectedSizeBytes =
        remember(allItems, state.selectedIds) {
            allItems
                .filter { it.id in state.selectedIds }
                .sumOf { it.sources.firstOrNull { s -> s.type == FindroidSourceType.LOCAL }?.size ?: 0L }
        }

    DownloadsScreenLayout(
        state = state,
        onSettingsClick = onSettingsClick,
        onTrashClick = {
            if (state.selectedIds.isNotEmpty()) deleteSelectedDialogOpen = true
            else clearAllDialogOpen = true
        },
        onClearSelection = { viewModel.toggleSelectAll(false) },
        onItemClick = onItemClick,
        onToggleSelection = viewModel::toggleSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onToggleGroupSelection = viewModel::setGroupSelected,
        onDownloadAction = viewModel::onDownloadAction,
        onSwipeDeleteRequest = { id, title, path, sizeBytes ->
            pendingDelete = PendingDownloadDelete(id, title, path, sizeBytes)
        },
        onPauseAllClick = viewModel::pauseAll,
        onResumeAllClick = viewModel::resumeAll,
        onShowClick = onShowClick,
        onMoviesClick = onMoviesClick,
        onForceGroup = viewModel::forceGroup,
    )

    if (clearAllDialogOpen) {
        ClearDownloadsDialog(
            title = stringResource(CoreR.string.clear_all_downloads),
            message = stringResource(CoreR.string.clear_all_downloads_message),
            sizeBytes = totalSizeBytes,
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
            sizeBytes = selectedSizeBytes,
            onConfirm = {
                viewModel.deleteSelected()
                deleteSelectedDialogOpen = false
            },
            onDismiss = { deleteSelectedDialogOpen = false },
        )
    }

    pendingDelete?.let { pending ->
        DeleteSingleDownloadDialog(
            title = pending.title,
            path = pending.path,
            sizeBytes = pending.sizeBytes,
            onConfirm = {
                viewModel.deleteItem(pending.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private data class PendingDownloadDelete(
    val id: UUID,
    val title: String,
    val path: String?,
    val sizeBytes: Long?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: DownloadsState,
    onSettingsClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onItemClick: (FindroidItem) -> Unit = {},
    onToggleSelection: (UUID) -> Unit = {},
    onToggleSelectAll: (Boolean) -> Unit = {},
    onToggleGroupSelection: (Set<UUID>, Boolean) -> Unit = { _, _ -> },
    onDownloadAction: (UUID, DownloadAction) -> Unit = { _, _ -> },
    onSwipeDeleteRequest: (UUID, String, String?, Long?) -> Unit = { _, _, _, _ -> },
    onPauseAllClick: () -> Unit = {},
    onResumeAllClick: () -> Unit = {},
    onShowClick: (UUID) -> Unit = {},
    onMoviesClick: () -> Unit = {},
    onForceGroup: (List<UUID>) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val allIds =
        remember(state.movies, state.showGroups) {
            (state.movies.map { it.id } + state.showGroups.flatMap { it.episodes }.map { it.id })
                .toSet()
        }
    val allSelected = allIds.isNotEmpty() && state.selectedIds.containsAll(allIds)
    val selectionMode = state.selectedIds.isNotEmpty()

    var moviesCollapsed by remember { mutableStateOf(false) }
    var collapsedGroupIds by remember { mutableStateOf(emptySet<UUID>()) }

    // Reset whenever there's no active batch, so the next delete shows the card fresh even if
    // the user dismissed a previous one.
    var deleteProgressDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleteProgress == null) {
        if (state.deleteProgress == null) deleteProgressDismissed = false
    }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        bottomBar = {
            state.deleteProgress?.let { progress ->
                if (!deleteProgressDismissed) {
                    DeleteProgressCard(
                        progress = progress,
                        onDismiss = { deleteProgressDismissed = true },
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_download)) },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = stringResource(CoreR.string.cancel),
                            )
                        }
                    }
                },
                actions = {
                    if (!selectionMode && state.downloadProgress.isNotEmpty()) {
                        val allPaused =
                            state.downloadProgress.values.all {
                                it.status == DownloadManager.STATUS_PAUSED
                            }
                        IconButton(onClick = if (allPaused) onResumeAllClick else onPauseAllClick) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (allPaused) CoreR.drawable.ic_play
                                        else CoreR.drawable.ic_pause
                                    ),
                                contentDescription =
                                    stringResource(
                                        if (allPaused) CoreR.string.resume_all_downloads
                                        else CoreR.string.pause_all_downloads
                                    ),
                            )
                        }
                    }
                    if (!state.isEmpty) {
                        IconButton(onClick = onTrashClick) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription =
                                    if (selectionMode) {
                                        stringResource(CoreR.string.delete_selected_downloads)
                                    } else {
                                        stringResource(CoreR.string.clear_all_downloads)
                                    },
                            )
                        }
                    }
                    if (!selectionMode) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_settings),
                                contentDescription = stringResource(CoreR.string.title_settings),
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
                if (selectionMode) {
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
                    stickyHeader {
                        SectionHeader(
                            text = stringResource(CoreR.string.movies_label),
                            onClick = onMoviesClick,
                            collapsed = moviesCollapsed,
                            onToggleCollapsed = { moviesCollapsed = !moviesCollapsed },
                        )
                    }
                    if (!moviesCollapsed) {
                        items(items = state.movies, key = { it.id }) { movie ->
                            DownloadRow(
                                item = movie,
                                title = movie.name,
                                checked = movie.id in state.selectedIds,
                                selectionMode = selectionMode,
                                progress = state.downloadProgress[movie.id],
                                onClick = { onItemClick(movie) },
                                onLongClick = { onToggleSelection(movie.id) },
                                onToggleSelection = { onToggleSelection(movie.id) },
                                onDownloadAction = { onDownloadAction(movie.id, it) },
                                onSwipeDeleteRequest = {
                                    val source =
                                        movie.sources.firstOrNull {
                                            it.type == FindroidSourceType.LOCAL
                                        }
                                    onSwipeDeleteRequest(
                                        movie.id,
                                        movie.name,
                                        source?.path,
                                        source?.size,
                                    )
                                },
                            )
                        }
                    }
                }
                state.showGroups.forEach { group ->
                    val groupIds = group.episodes.map { it.id }.toSet()
                    val groupSelected = groupIds.isNotEmpty() && state.selectedIds.containsAll(groupIds)
                    val hasQueuedEpisode =
                        group.episodes.any {
                            state.downloadProgress[it.id]?.status == DownloadManager.STATUS_PENDING
                        }
                    val groupCollapsed = group.seriesId in collapsedGroupIds
                    stickyHeader {
                        ShowGroupHeader(
                            group = group,
                            checked = groupSelected,
                            selectionMode = selectionMode,
                            onToggle = { onToggleGroupSelection(groupIds, !groupSelected) },
                            onLongClick = { onToggleGroupSelection(groupIds, !groupSelected) },
                            onClick = { onShowClick(group.seriesId) },
                            canForce = hasQueuedEpisode,
                            onForceClick = { onForceGroup(group.episodes.map { it.id }) },
                            collapsed = groupCollapsed,
                            onToggleCollapsed = {
                                collapsedGroupIds =
                                    if (groupCollapsed) collapsedGroupIds - group.seriesId
                                    else collapsedGroupIds + group.seriesId
                            },
                        )
                    }
                    if (groupCollapsed) return@forEach
                    items(items = group.episodes, key = { it.id }) { episode ->
                        val episodeTitle =
                            stringResource(
                                CoreR.string.episode_name_extended,
                                episode.parentIndexNumber,
                                episode.indexNumber,
                                episode.name,
                            )
                        DownloadRow(
                            item = episode,
                            title = episodeTitle,
                            checked = episode.id in state.selectedIds,
                            selectionMode = selectionMode,
                            progress = state.downloadProgress[episode.id],
                            onClick = { onItemClick(episode) },
                            onLongClick = { onToggleSelection(episode.id) },
                            onToggleSelection = { onToggleSelection(episode.id) },
                            onDownloadAction = { onDownloadAction(episode.id, it) },
                            onSwipeDeleteRequest = {
                                val source =
                                    episode.sources.firstOrNull {
                                        it.type == FindroidSourceType.LOCAL
                                    }
                                onSwipeDeleteRequest(
                                    episode.id,
                                    episodeTitle,
                                    source?.path,
                                    source?.size,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteProgressCard(progress: DeleteProgress, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.default)) {
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        stringResource(
                            CoreR.string.delete_downloads_progress,
                            progress.done,
                            progress.total,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (progress.total > 0) progress.done / progress.total.toFloat() else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_x),
                    contentDescription = stringResource(CoreR.string.cancel),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    onClick: () -> Unit = {},
    collapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier =
                    Modifier.weight(1f)
                        .padding(
                            horizontal = MaterialTheme.spacings.medium,
                            vertical = MaterialTheme.spacings.medium,
                        ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onToggleCollapsed) {
                Icon(
                    painter =
                        painterResource(
                            if (collapsed) CoreR.drawable.ic_chevron_down
                            else CoreR.drawable.ic_chevron_up
                        ),
                    contentDescription =
                        stringResource(
                            if (collapsed) CoreR.string.expand else CoreR.string.collapse
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShowGroupHeader(
    group: DownloadShowGroup,
    checked: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    canForce: Boolean = false,
    onForceClick: () -> Unit = {},
    collapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
) {
    val context = LocalContext.current
    val downloadedSizeBytes =
        remember(group.episodes) {
            group.episodes.sumOf { episode ->
                episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }?.size ?: 0L
            }
        }

    Card {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggle() else onClick() },
                        onLongClick = onLongClick,
                    )
                    .padding(
                        horizontal = MaterialTheme.spacings.default,
                        vertical = MaterialTheme.spacings.small,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(32.dp).clip(MaterialTheme.shapes.extraSmall)) {
                ItemPoster(item = group.episodes.first(), direction = Direction.VERTICAL)
            }
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.seriesName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (downloadedSizeBytes > 0) {
                    Text(
                        text = Formatter.formatFileSize(context, downloadedSizeBytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!selectionMode && canForce) {
                IconButton(onClick = onForceClick) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_fast_forward),
                        contentDescription = stringResource(CoreR.string.download_action_force),
                    )
                }
            }
            if (!selectionMode) {
                IconButton(onClick = onToggleCollapsed) {
                    Icon(
                        painter =
                            painterResource(
                                if (collapsed) CoreR.drawable.ic_chevron_down
                                else CoreR.drawable.ic_chevron_up
                            ),
                        contentDescription =
                            stringResource(
                                if (collapsed) CoreR.string.expand else CoreR.string.collapse
                            ),
                    )
                }
            }
            if (selectionMode) {
                Checkbox(checked = checked, onCheckedChange = { onToggle() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
    item: FindroidItem,
    title: String,
    checked: Boolean,
    selectionMode: Boolean,
    progress: DownloadProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
    onSwipeDeleteRequest: () -> Unit,
) {
    val context = LocalContext.current
    val activeProgress = progress?.takeIf { it.status != DownloadManager.STATUS_SUCCESSFUL }
    val isPending = activeProgress?.status == DownloadManager.STATUS_PENDING
    val isPaused = activeProgress?.status == DownloadManager.STATUS_PAUSED
    val isVerifying = activeProgress?.status == DownloadProgress.STATUS_VERIFYING
    val sizeBytes = item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }?.size ?: 0L
    val swipeEnabled = activeProgress == null && !selectionMode

    val content: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelection() else onClick() },
                        onLongClick = onLongClick,
                    )
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
                                isVerifying -> stringResource(CoreR.string.download_verifying)
                                activeProgress.percent >= 0 ->
                                    stringResource(
                                        CoreR.string.download_progress_status,
                                        activeProgress.percent,
                                        formatDownloadSpeed(
                                            context,
                                            activeProgress.speedBytesPerSecond,
                                        ),
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
            when {
                selectionMode -> {
                    Checkbox(checked = checked, onCheckedChange = { onToggleSelection() })
                }
                activeProgress != null -> {
                    if (isPending) {
                        IconButton(onClick = { onDownloadAction(DownloadAction.Force) }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_fast_forward),
                                contentDescription = stringResource(CoreR.string.download_action_force),
                            )
                        }
                    } else if (!isVerifying) {
                        IconButton(
                            onClick = {
                                onDownloadAction(
                                    if (isPaused) DownloadAction.Resume else DownloadAction.Pause
                                )
                            }
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (isPaused) CoreR.drawable.ic_play
                                        else CoreR.drawable.ic_pause
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
                }
                item.isDownloaded() -> {
                    IconButton(onClick = onClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_play),
                            contentDescription = stringResource(CoreR.string.download_action_play),
                        )
                    }
                }
            }
        }
    }

    if (swipeEnabled) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val offsetX = remember { Animatable(0f) }
        val maxSwipePx = with(density) { 96.dp.toPx() }
        val thresholdPx = maxSwipePx / 2f

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier.matchParentSize().padding(horizontal = MaterialTheme.spacings.default),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = stringResource(CoreR.string.delete_download),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Box(
                modifier =
                    Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .background(MaterialTheme.colorScheme.background)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state =
                                rememberDraggableState { delta ->
                                    scope.launch {
                                        offsetX.snapTo((offsetX.value + delta).coerceIn(-maxSwipePx, 0f))
                                    }
                                },
                            onDragStopped = {
                                if (offsetX.value < -thresholdPx) {
                                    onSwipeDeleteRequest()
                                }
                                scope.launch { offsetX.animateTo(0f) }
                            },
                        )
            ) {
                content()
            }
        }
    } else {
        content()
    }
}

@Composable
private fun DeleteSelectedDownloadsDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    sizeBytes: Long? = null,
) {
    val context = LocalContext.current
    AlertDialog(
        icon = {
            Icon(
                painter = painterResource(CoreR.drawable.ic_trash),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = stringResource(CoreR.string.delete_selected_downloads)) },
        text = {
            Column {
                Text(text = stringResource(CoreR.string.delete_selected_downloads_message, count))
                if (sizeBytes != null) {
                    Text(
                        text = Formatter.formatFileSize(context, sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
private fun DeleteSingleDownloadDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    path: String? = null,
    sizeBytes: Long? = null,
) {
    val context = LocalContext.current
    AlertDialog(
        icon = {
            Icon(
                painter = painterResource(CoreR.drawable.ic_trash),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = stringResource(CoreR.string.delete_download)) },
        text = {
            Column {
                Text(text = title)
                if (path != null) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sizeBytes != null) {
                    Text(
                        text = Formatter.formatFileSize(context, sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
