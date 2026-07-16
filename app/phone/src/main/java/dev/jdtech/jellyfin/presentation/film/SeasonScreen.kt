package dev.jdtech.jellyfin.presentation.film

import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.film.components.ClearDownloadsDialog
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.EpisodeCard
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.film.components.PlayOverlayButton
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.displayNameWithContext
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun SeasonScreen(
    seasonId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToItem: (item: FindroidItem) -> Unit,
    navigateToSeries: (seriesId: UUID) -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
) {
    val androidContext = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadSeason(seasonId = seasonId) }

    SeasonScreenLayout(
        state = state,
        getSeasons = viewModel::getSeasons,
        onAction = { action ->
            when (action) {
                is SeasonAction.Play -> {
                    val intent = Intent(androidContext, PlayerActivity::class.java)
                    intent.putExtra("itemId", seasonId.toString())
                    intent.putExtra("itemKind", BaseItemKind.SEASON.serialName)
                    androidContext.startActivity(intent)
                }
                is SeasonAction.OnBackClick -> navigateBack()
                is SeasonAction.OnHomeClick -> navigateHome()
                is SeasonAction.NavigateToItem -> navigateToItem(action.item)
                is SeasonAction.NavigateToSeries -> navigateToSeries(action.seriesId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun SeasonScreenLayout(
    state: SeasonState,
    onAction: (SeasonAction) -> Unit,
    getSeasons: suspend () -> List<FindroidSeason> = { emptyList() },
) {
    val androidContext = LocalContext.current
    val safePadding = rememberSafePadding()
    var clearSeasonDownloadsDialogOpen by remember { mutableStateOf(false) }

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        state.season?.let { season ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = lazyListState,
                contentPadding = PaddingValues(bottom = paddingBottom),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            ) {
                item {
                    ItemHeader(
                        item = season,
                        lazyListState = lazyListState,
                        content = {
                            PlayOverlayButton(
                                item = season,
                                onClick = {
                                    onAction(SeasonAction.Play(startFromBeginning = false))
                                },
                                enabled = season.canPlay && state.episodes.isNotEmpty(),
                                modifier = Modifier.align(Alignment.Center),
                            )
                            Row(
                                modifier =
                                    Modifier.align(Alignment.BottomStart)
                                        .padding(start = paddingStart, end = paddingEnd),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                ItemPoster(
                                    item = season,
                                    direction = Direction.VERTICAL,
                                    modifier =
                                        Modifier.width(120.dp).clip(MaterialTheme.shapes.small),
                                )
                                Spacer(Modifier.width(MaterialTheme.spacings.medium))
                                Column(modifier = Modifier) {
                                    Text(
                                        text = season.seriesName,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = season.name,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 3,
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.default.div(2)))
                    ItemButtonsBar(
                        item = season,
                        onPlayClick = { startFromBeginning ->
                            onAction(SeasonAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onMarkAsPlayedClick = {
                            when (season.played) {
                                true -> onAction(SeasonAction.UnmarkAsPlayed)
                                false -> onAction(SeasonAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (season.favorite) {
                                true -> onAction(SeasonAction.UnmarkAsFavorite)
                                false -> onAction(SeasonAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = {},
                        onDownloadClick = {},
                        onDownloadCancelClick = {},
                        onDownloadDeleteClick = {},
                        modifier =
                            Modifier.padding(start = paddingStart, end = paddingEnd).fillMaxWidth(),
                        downloaderState = DownloaderState(),
                        enableDownloadDialog = true,
                        getSeasons = getSeasons,
                        initialSelection =
                            DownloadSelection(
                                seasonIds = state.existingScope.seasonIds.ifEmpty { setOf(season.id) },
                                alsoFutureSeasons = state.existingScope.alsoFutureSeasons,
                            ),
                        initialAlsoFollowNew = state.existingScope.alsoFollowNew,
                        initialOnlyUnwatched = state.existingScope.onlyUnwatched,
                        hasActiveDownloadOrRule = state.hasDownloads || state.autoDownloadEnabled,
                        onDeleteDownloads = { clearSeasonDownloadsDialogOpen = true },
                        downloadIconTint =
                            if (state.autoDownloadEnabled) Color("#F2C94C".toColorInt()) else null,
                        onBulkDownload = { selection, alsoFollowNew, onlyUnwatched ->
                            onAction(
                                SeasonAction.DownloadWithScope(
                                    selection,
                                    alsoFollowNew,
                                    onlyUnwatched,
                                )
                            )
                            Toast.makeText(
                                    androidContext,
                                    if (alsoFollowNew) {
                                        CoreR.string.auto_download_enabled_toast
                                    } else {
                                        CoreR.string.download_queued_toast
                                    },
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        },
                        trailingContent = {
                            if (state.hasDownloads || state.autoDownloadEnabled) {
                                FilledTonalButton(onClick = { clearSeasonDownloadsDialogOpen = true }) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_trash),
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                                    Text(text = stringResource(CoreR.string.clear_season_downloads))
                                }
                            }
                        },
                    )
                    if (state.downloadsSizeBytes > 0) {
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.downloads_disk_usage,
                                    Formatter.formatFileSize(androidContext, state.downloadsSizeBytes),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier.padding(start = paddingStart, end = paddingEnd),
                        )
                    }
                }
                items(items = state.episodes, key = { episode -> episode.id }) { episode ->
                    EpisodeCard(
                        episode = episode,
                        onClick = { onAction(SeasonAction.NavigateToItem(episode)) },
                        modifier = Modifier.padding(start = paddingStart, end = paddingEnd),
                        downloadProgress = state.downloadProgress[episode.id],
                        queueStatus = state.queueStatus[episode.id],
                    )
                }
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }

        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            onBackClick = { onAction(SeasonAction.OnBackClick) },
            onHomeClick = { onAction(SeasonAction.OnHomeClick) },
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            state.season?.let { season ->
                Button(
                    onClick = { onAction(SeasonAction.NavigateToSeries(season.seriesId)) },
                    modifier = Modifier.alpha(0.7f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White,
                        ),
                ) {
                    Text(text = season.seriesName, overflow = TextOverflow.Ellipsis, maxLines = 1)
                }
            }
        }
    }

    if (clearSeasonDownloadsDialogOpen) {
        ClearDownloadsDialog(
            title = stringResource(CoreR.string.clear_season_downloads),
            message = stringResource(CoreR.string.clear_season_downloads_message),
            name = state.season?.displayNameWithContext(),
            sizeBytes = state.downloadsSizeBytes,
            onConfirm = { alsoRemoveRules ->
                onAction(SeasonAction.DeleteSeasonDownloads(alsoRemoveRules))
                Toast.makeText(
                        androidContext,
                        CoreR.string.downloads_deleted_toast,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                clearSeasonDownloadsDialogOpen = false
            },
            onDismiss = { clearSeasonDownloadsDialogOpen = false },
        )
    }
}

@PreviewScreenSizes
@Composable
private fun SeasonScreenLayoutPreview() {
    FindroidTheme { SeasonScreenLayout(state = SeasonState(season = dummySeason), onAction = {}) }
}
