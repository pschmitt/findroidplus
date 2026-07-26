package dev.jdtech.jellyfin.presentation.film

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderEvent
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderViewModel
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.search.SearchEvent
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.movie.MovieState
import dev.jdtech.jellyfin.film.presentation.movie.MovieViewModel
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.isDownloadBroken
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.InfoDialog
import dev.jdtech.jellyfin.presentation.film.components.InfoText
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemDetailScaffold
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemMetaRow
import dev.jdtech.jellyfin.presentation.film.components.LocalStorageIndicator
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.PlayOverlayButton
import dev.jdtech.jellyfin.presentation.film.components.PvrSearchButton
import dev.jdtech.jellyfin.presentation.film.components.QueueBadge
import dev.jdtech.jellyfin.presentation.film.components.ReleasePickerSheet
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.utils.format
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun MovieScreen(
    movieId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToPerson: (personId: UUID) -> Unit,
    navigateToSettings: () -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isOfflineMode = LocalOfflineMode.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadMovie(movieId = movieId) }

    LaunchedEffect(state.movie) { state.movie?.let { movie -> downloaderViewModel.update(movie) } }

    ObserveAsEvents(downloaderViewModel.events) { event ->
        when (event) {
            is DownloaderEvent.Successful -> {
                viewModel.loadMovie(movieId = movieId)
            }
            is DownloaderEvent.Deleted -> {
                if (isOfflineMode) {
                    navigateBack()
                } else {
                    viewModel.loadMovie(movieId = movieId)
                }
            }
        }
    }

    ObserveAsEvents(viewModel.searchEvents) { event ->
        val message =
            when (event) {
                is SearchEvent.SearchTriggered -> context.getString(CoreR.string.search_triggered_toast)
                is SearchEvent.ReleaseGrabbed -> context.getString(CoreR.string.release_grabbed_toast)
                is SearchEvent.Failed ->
                    context.getString(
                        CoreR.string.search_failed_toast,
                        event.message ?: context.getString(CoreR.string.unknown_error),
                    )
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    MovieScreenLayout(
        state = state,
        downloaderState = downloaderState,
        downloadLocationPreference = downloaderViewModel.downloadLocationPreference,
        onRefresh = { viewModel.loadMovie(movieId = movieId) },
        onAction = { action ->
            when (action) {
                is MovieAction.Play -> {
                    val intent = Intent(context, PlayerActivity::class.java)
                    intent.putExtra("itemId", movieId.toString())
                    intent.putExtra("itemKind", BaseItemKind.MOVIE.serialName)
                    intent.putExtra("startFromBeginning", action.startFromBeginning)
                    context.startActivity(intent)
                }
                is MovieAction.PlayTrailer -> {
                    try {
                        uriHandler.openUri(action.trailer)
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                is MovieAction.OnBackClick -> navigateBack()
                is MovieAction.OnHomeClick -> navigateHome()
                is MovieAction.OnSettingsClick -> navigateToSettings()
                is MovieAction.NavigateToPerson -> navigateToPerson(action.personId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onDownloaderAction = { action -> downloaderViewModel.onAction(action) },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MovieScreenLayout(
    state: MovieState,
    downloaderState: DownloaderState,
    downloadLocationPreference: String = "ask",
    onRefresh: () -> Unit = {},
    onAction: (MovieAction) -> Unit,
    onDownloaderAction: (DownloaderAction) -> Unit,
) {
    val androidContext = LocalContext.current
    val safePadding = rememberSafePadding()

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val scrollState = rememberScrollState()
    var infoDialogOpen by remember { mutableStateOf(false) }

    ItemDetailScaffold(
        hasBackButton = true,
        hasHomeButton = true,
        onBackClick = { onAction(MovieAction.OnBackClick) },
        onHomeClick = { onAction(MovieAction.OnHomeClick) },
        onSettingsClick = { onAction(MovieAction.OnSettingsClick) },
    ) {
        // Same default Material3 indicator as Downloads/Library/Home - one loading-feedback
        // language across the whole app instead of a screen-specific spinner.
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh) {
        state.movie?.let { movie ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                ItemHeader(
                    item = movie,
                    scrollState = scrollState,
                    content = {
                        PlayOverlayButton(
                            item = movie,
                            onClick = { onAction(MovieAction.Play(startFromBeginning = false)) },
                            enabled = movie.canPlay,
                            isDeleting = downloaderState.isDeleting,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        Column(
                            modifier =
                                Modifier.align(Alignment.BottomStart)
                                    .padding(start = paddingStart, end = paddingEnd)
                        ) {
                            Text(
                                text = movie.name,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 3,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            movie.originalTitle?.let { originalTitle ->
                                if (originalTitle != movie.name) {
                                    Text(
                                        text = originalTitle,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        if (state.videoMetadata != null) {
                            IconButton(
                                onClick = { infoDialogOpen = true },
                                modifier =
                                    Modifier.align(Alignment.BottomEnd)
                                        .padding(end = paddingEnd),
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.7f),
                                        contentColor = Color.White,
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_info),
                                    contentDescription = stringResource(CoreR.string.info),
                                )
                            }
                        }
                    },
                )
                Column(modifier = Modifier.padding(start = paddingStart, end = paddingEnd)) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    ItemMetaRow(
                        dateText = movie.premiereDate?.format(state.dateFormat),
                        runtimeTicks = movie.runtimeTicks,
                        officialRating = movie.officialRating,
                        communityRating = movie.communityRating,
                        modifier = Modifier.fillMaxWidth(),
                        played = movie.played,
                        favorite = movie.favorite,
                        onPlayedClick = {
                            if (movie.played) onAction(MovieAction.UnmarkAsPlayed)
                            else onAction(MovieAction.MarkAsPlayed)
                        },
                        onFavoriteClick = {
                            if (movie.favorite) onAction(MovieAction.UnmarkAsFavorite)
                            else onAction(MovieAction.MarkAsFavorite)
                        },
                    ) {
                        state.queueStatus?.let { queueStatus -> QueueBadge(status = queueStatus) }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    val deleteDownload: () -> Unit = {
                        onDownloaderAction(DownloaderAction.DeleteDownload(movie))
                        Toast.makeText(
                                androidContext,
                                CoreR.string.download_deleted_toast,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                    val downloadedSource =
                        if (movie.isDownloaded()) {
                            movie.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        } else {
                            null
                        }
                    ItemButtonsBar(
                        item = movie,
                        downloaderState = downloaderState,
                        downloadLocationPreference = downloadLocationPreference,
                        onPlayClick = { startFromBeginning ->
                            onAction(MovieAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onTrailerClick = { uri -> onAction(MovieAction.PlayTrailer(uri)) },
                        onDownloadClick = { storageIndex ->
                            onDownloaderAction(DownloaderAction.Download(movie, storageIndex))
                        },
                        onDownloadCancelClick = {
                            onDownloaderAction(DownloaderAction.CancelDownload(movie))
                        },
                        onDownloadForceClick = {
                            onDownloaderAction(DownloaderAction.ForceDownload)
                        },
                        onDownloadPauseClick = {
                            onDownloaderAction(DownloaderAction.PauseDownload)
                        },
                        onDownloadResumeClick = {
                            onDownloaderAction(DownloaderAction.ResumeDownload)
                        },
                        onDownloadDeleteClick = deleteDownload,
                        trailingContent = {
                            if (movie.tmdbId != null && state.radarrConfigured) {
                                PvrSearchButton(
                                    service = PvrSource.RADARR,
                                    onAutomaticSearch = {
                                        onAction(MovieAction.SearchMovieAutomatic)
                                    },
                                    onManualSearch = { onAction(MovieAction.OpenReleasePicker) },
                                    contentDescription =
                                        stringResource(CoreR.string.search_movie),
                                    label = stringResource(CoreR.string.search_movie),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    downloadedSource?.let { source ->
                        // Size lives on the "Delete download" tile above - only surface this
                        // caption for a broken (0-byte/missing) download.
                        if (!source.path.endsWith(".download") && movie.isDownloadBroken()) {
                            Spacer(Modifier.height(MaterialTheme.spacings.small))
                            LocalStorageIndicator(
                                path = source.path,
                                sizeBytes = source.size,
                                isBroken = true,
                                showSize = false,
                            )
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    if (infoDialogOpen && state.videoMetadata != null) {
                        InfoDialog(
                            videoMetadata = state.videoMetadata!!,
                            downloadedFilePath =
                                downloadedSource?.path?.takeUnless { it.endsWith(".download") },
                            onDismiss = { infoDialogOpen = false },
                        )
                    }
                    OverviewText(text = movie.overview, maxCollapsedLines = 3)
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    InfoText(
                        genres = movie.genres,
                        director = state.director,
                        writers = state.writers,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                }
                if (state.actors.isNotEmpty()) {
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(MovieAction.NavigateToPerson(personId))
                        },
                        contentPadding = PaddingValues(start = paddingStart, end = paddingEnd),
                    )
                }
                Spacer(Modifier.height(paddingBottom))
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
        }
    }

    state.releasePicker?.let { releasePicker ->
        ReleasePickerSheet(
            state = releasePicker,
            onGrab = { release -> onAction(MovieAction.GrabRelease(release)) },
            onDismissRequest = { onAction(MovieAction.DismissReleasePicker) },
        )
    }
}

@PreviewScreenSizes
@Composable
private fun EpisodeScreenLayoutPreview() {
    FindroidTheme {
        MovieScreenLayout(
            state = MovieState(movie = dummyMovie, videoMetadata = dummyVideoMetadata),
            downloaderState = DownloaderState(),
            onAction = {},
            onDownloaderAction = {},
        )
    }
}
