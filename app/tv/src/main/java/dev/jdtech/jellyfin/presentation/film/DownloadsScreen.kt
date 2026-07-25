package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsState
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.isMarkedForAutoDeletion
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun DownloadsScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToPlayer: (itemId: UUID, itemKind: BaseItemKind) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.startObserving() }

    DownloadsScreenLayout(
        state = state,
        onMovieClick = { navigateToMovie(it) },
        onEpisodeClick = { navigateToPlayer(it, BaseItemKind.EPISODE) },
        onClearAllDownloads = { viewModel.clearAllDownloads(alsoRemoveRules = true) },
    )
}

@Composable
private fun DownloadsScreenLayout(
    state: DownloadsState,
    onMovieClick: (UUID) -> Unit = {},
    onEpisodeClick: (UUID) -> Unit = {},
    onClearAllDownloads: () -> Unit = {},
) {
    var clearAllDialogOpen by remember { mutableStateOf(false) }
    val itemsPadding = PaddingValues(horizontal = MaterialTheme.spacings.large)

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isEmpty) {
            Text(
                text = stringResource(id = CoreR.string.no_downloads),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = MaterialTheme.spacings.extraSmall,
                        bottom = MaterialTheme.spacings.large,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.large),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(itemsPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(id = CoreR.string.title_download),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Button(onClick = { clearAllDialogOpen = true }) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_trash),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(id = CoreR.string.clear_all_downloads))
                        }
                    }
                }
                if (state.movies.isNotEmpty()) {
                    item(key = "movies") {
                        Column {
                            Text(
                                text = stringResource(id = CoreR.string.movies_label),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(itemsPadding),
                            )
                            LazyRow(
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.default),
                                contentPadding = itemsPadding,
                            ) {
                                items(items = state.movies, key = { it.id }) { movie ->
                                    ItemCard(
                                        item = movie,
                                        direction = Direction.HORIZONTAL,
                                        onClick = { onMovieClick(movie.id) },
                                        downloadProgress = state.downloadProgress[movie.id],
                                    )
                                }
                            }
                        }
                    }
                }
                items(items = state.showGroups, key = { it.seriesId }) { group ->
                    Column {
                        Text(
                            text = group.seriesName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(itemsPadding),
                        )
                        LazyRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.default),
                            contentPadding = itemsPadding,
                        ) {
                            items(items = group.episodes, key = { it.id }) { episode ->
                                ItemCard(
                                    item = episode,
                                    direction = Direction.HORIZONTAL,
                                    onClick = { onEpisodeClick(episode.id) },
                                    downloadProgress = state.downloadProgress[episode.id],
                                    isMarkedForDeletion =
                                        state.autoDeleteWatchedEnabled &&
                                            episode.isMarkedForAutoDeletion(
                                                state.autoDeleteWatchedHours
                                            ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (clearAllDialogOpen) {
        AlertDialog(
            title = { Text(text = stringResource(id = CoreR.string.clear_all_downloads)) },
            text = { Text(text = stringResource(id = CoreR.string.clear_all_downloads_message)) },
            onDismissRequest = { clearAllDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllDownloads()
                        clearAllDialogOpen = false
                    }
                ) {
                    Text(text = stringResource(id = CoreR.string.delete_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearAllDialogOpen = false }) {
                    Text(text = stringResource(id = CoreR.string.cancel))
                }
            },
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun DownloadsScreenLayoutPreview() {
    FindroidTheme { DownloadsScreenLayout(state = DownloadsState()) }
}
