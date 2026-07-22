package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSizeEstimate
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisodes
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.presentation.film.components.DownloadScopeDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.EpisodeCard
import dev.jdtech.jellyfin.ui.components.UpcomingEpisodeCard
import java.util.UUID

@Composable
fun SeasonScreen(
    seasonId: UUID,
    navigateToPlayer: (itemId: UUID) -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(true) { viewModel.loadSeason(seasonId = seasonId) }

    SeasonScreenLayout(
        state = state,
        getSeasons = viewModel::getSeasons,
        getSeasonSize = viewModel::getUndownloadedEpisodeSize,
        onAction = { action ->
            when (action) {
                is SeasonAction.NavigateToItem -> navigateToPlayer(action.item.id)
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
    getSeasonSize: suspend (seasonId: UUID, onlyUnwatched: Boolean) -> DownloadSizeEstimate = { _, _ ->
        DownloadSizeEstimate()
    },
) {
    var downloadScopeDialogOpen by remember { mutableStateOf(false) }
    var clearDownloadsDialogOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        state.season?.let { season ->
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .padding(
                                start = MaterialTheme.spacings.extraLarge,
                                top = MaterialTheme.spacings.large,
                                end = MaterialTheme.spacings.large,
                            )
                ) {
                    Text(text = season.name, style = MaterialTheme.typography.displayMedium)
                    Text(text = season.seriesName, style = MaterialTheme.typography.headlineMedium)
                    Spacer(
                        modifier = Modifier.height(MaterialTheme.spacings.default)
                    )
                    if (state.hasDownloads) {
                        Button(onClick = { clearDownloadsDialogOpen = true }) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_trash),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(id = CoreR.string.clear_season_downloads))
                        }
                    } else {
                        Button(onClick = { downloadScopeDialogOpen = true }) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_download),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(id = CoreR.string.download))
                        }
                    }
                }
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = MaterialTheme.spacings.large,
                            bottom = MaterialTheme.spacings.large,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier = Modifier.weight(2f).padding(end = MaterialTheme.spacings.extraLarge),
                ) {
                    items(state.episodes) { episode ->
                        EpisodeCard(
                            episode = episode,
                            onClick = { onAction(SeasonAction.NavigateToItem(episode)) },
                            downloadProgress = state.downloadProgress[episode.id],
                        )
                    }
                    items(
                        items = state.upcomingEpisodes,
                        key = { episode -> "upcoming-${episode.episodeNumber}" },
                    ) { episode ->
                        UpcomingEpisodeCard(
                            episode = episode,
                            queued = state.queuedEpisodeNumbers.contains(episode.episodeNumber),
                            onToggleQueued = {
                                onAction(
                                    SeasonAction.ToggleEpisodeQueued(
                                        episodeNumber = episode.episodeNumber,
                                        sonarrEpisodeId = episode.episodeId,
                                    )
                                )
                            },
                        )
                    }
                }
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
    }

    if (downloadScopeDialogOpen) {
        var seasons by remember { mutableStateOf<List<FindroidSeason>?>(null) }
        LaunchedEffect(Unit) { seasons = getSeasons() }
        DownloadScopeDialog(
            seasons = seasons,
            initialSelection =
                DownloadSelection(
                    seasonIds = state.existingScope.seasonIds.ifEmpty { setOfNotNull(state.season?.id) },
                    alsoFutureSeasons = state.existingScope.alsoFutureSeasons,
                ),
            initialAlsoFollowNew = state.existingScope.alsoFollowNew,
            initialOnlyUnwatched = state.existingScope.onlyUnwatched,
            canDelete = state.hasDownloads || state.autoDownloadEnabled,
            getSeasonSize = getSeasonSize,
            onDelete = { downloadScopeDialogOpen = false; clearDownloadsDialogOpen = true },
            onConfirm = { selection, alsoFollowNew, onlyUnwatched ->
                onAction(SeasonAction.DownloadWithScope(selection, alsoFollowNew, onlyUnwatched))
                downloadScopeDialogOpen = false
            },
            onDismiss = { downloadScopeDialogOpen = false },
        )
    }

    if (clearDownloadsDialogOpen) {
        AlertDialog(
            title = { Text(text = stringResource(id = CoreR.string.clear_season_downloads)) },
            text = { Text(text = stringResource(id = CoreR.string.clear_season_downloads_message)) },
            onDismissRequest = { clearDownloadsDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(SeasonAction.DeleteSeasonDownloads(alsoRemoveRules = true))
                        clearDownloadsDialogOpen = false
                    }
                ) {
                    Text(text = stringResource(id = CoreR.string.delete_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDownloadsDialogOpen = false }) {
                    Text(text = stringResource(id = CoreR.string.cancel))
                }
            },
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeasonScreenLayoutPreview() {
    FindroidTheme {
        SeasonScreenLayout(
            state = SeasonState(season = dummySeason, episodes = dummyEpisodes),
            onAction = {},
        )
    }
}
