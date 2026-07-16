package dev.jdtech.jellyfin.presentation.film

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderEvent
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderViewModel
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.core.presentation.search.SearchEvent
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeAction
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeState
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeViewModel
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.PvrSearchButton
import dev.jdtech.jellyfin.presentation.film.components.InfoDialog
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.PlayOverlayButton
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
fun EpisodeScreen(
    episodeId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToPerson: (personId: UUID) -> Unit,
    navigateToSeason: (seasonId: UUID) -> Unit,
    navigateToShow: (showId: UUID) -> Unit,
    navigateToSettings: () -> Unit,
    viewModel: EpisodeViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isOfflineMode = LocalOfflineMode.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadEpisode(episodeId = episodeId) }

    LaunchedEffect(state.episode) {
        state.episode?.let { episode -> downloaderViewModel.update(episode) }
    }

    ObserveAsEvents(downloaderViewModel.events) { event ->
        when (event) {
            is DownloaderEvent.Successful -> {
                viewModel.loadEpisode(episodeId = episodeId)
            }
            is DownloaderEvent.Deleted -> {
                if (isOfflineMode) {
                    navigateBack()
                } else {
                    viewModel.loadEpisode(episodeId = episodeId)
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

    EpisodeScreenLayout(
        state = state,
        downloaderState = downloaderState,
        downloadLocationPreference = downloaderViewModel.downloadLocationPreference,
        getSeasons = viewModel::getSeasons,
        onAction = { action ->
            when (action) {
                is EpisodeAction.Play -> {
                    val intent = Intent(context, PlayerActivity::class.java)
                    intent.putExtra("itemId", episodeId.toString())
                    intent.putExtra("itemKind", BaseItemKind.EPISODE.serialName)
                    intent.putExtra("startFromBeginning", action.startFromBeginning)
                    context.startActivity(intent)
                }
                is EpisodeAction.OnBackClick -> navigateBack()
                is EpisodeAction.OnHomeClick -> navigateHome()
                is EpisodeAction.OnSettingsClick -> navigateToSettings()
                is EpisodeAction.NavigateToPerson -> navigateToPerson(action.personId)
                is EpisodeAction.NavigateToSeason -> navigateToSeason(action.seasonId)
                is EpisodeAction.NavigateToShow -> navigateToShow(action.showId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onDownloaderAction = { action -> downloaderViewModel.onAction(action) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeScreenLayout(
    state: EpisodeState,
    downloaderState: DownloaderState,
    downloadLocationPreference: String = "ask",
    getSeasons: suspend () -> List<FindroidSeason> = { emptyList() },
    onAction: (EpisodeAction) -> Unit,
    onDownloaderAction: (DownloaderAction) -> Unit,
) {
    val androidContext = LocalContext.current
    val safePadding = rememberSafePadding()

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        state.episode?.let { episode ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                ItemHeader(
                    item = episode,
                    scrollState = scrollState,
                    content = {
                        PlayOverlayButton(
                            item = episode,
                            onClick = { onAction(EpisodeAction.Play(startFromBeginning = false)) },
                            enabled = episode.canPlay,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        Column(
                            modifier =
                                Modifier.align(Alignment.BottomStart)
                                    .padding(start = paddingStart, end = paddingEnd)
                        ) {
                            val seasonName =
                                episode.seasonName
                                    ?: run {
                                        stringResource(
                                            CoreR.string.season_number,
                                            episode.parentIndexNumber,
                                        )
                                    }
                            Text(
                                text = episode.seriesName,
                                modifier =
                                    Modifier.clickable {
                                        onAction(EpisodeAction.NavigateToShow(episode.seriesId))
                                    },
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text =
                                    "$seasonName - " +
                                        stringResource(
                                            id = CoreR.string.episode_number,
                                            episode.indexNumber,
                                        ),
                                modifier =
                                    Modifier.clickable {
                                        onAction(EpisodeAction.NavigateToSeason(episode.seasonId))
                                    },
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = episode.name,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 3,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    },
                )
                Column(modifier = Modifier.padding(start = paddingStart, end = paddingEnd)) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        episode.premiereDate?.let { premiereDate ->
                            Text(
                                text = premiereDate.format(state.dateFormat),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    episode.runtimeTicks.div(600000000),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        episode.communityRating?.let { communityRating ->
                            Row(verticalAlignment = Alignment.Bottom) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_star),
                                    contentDescription = null,
                                    tint = Color("#F2C94C".toColorInt()),
                                )
                                Spacer(Modifier.width(MaterialTheme.spacings.extraSmall))
                                Text(
                                    text = "%.1f".format(communityRating),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    val deleteDownload: () -> Unit = {
                        onDownloaderAction(DownloaderAction.DeleteDownload(episode))
                        Toast.makeText(
                                androidContext,
                                CoreR.string.download_deleted_toast,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                    val downloadedSource =
                        if (episode.isDownloaded()) {
                            episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        } else {
                            null
                        }
                    var infoDialogOpen by remember { mutableStateOf(false) }
                    ItemButtonsBar(
                        item = episode,
                        downloaderState = downloaderState,
                        downloadLocationPreference = downloadLocationPreference,
                        onPlayClick = { startFromBeginning ->
                            onAction(EpisodeAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onMarkAsPlayedClick = {
                            when (episode.played) {
                                true -> onAction(EpisodeAction.UnmarkAsPlayed)
                                false -> onAction(EpisodeAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (episode.favorite) {
                                true -> onAction(EpisodeAction.UnmarkAsFavorite)
                                false -> onAction(EpisodeAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = {},
                        onDownloadClick = { storageIndex ->
                            onDownloaderAction(DownloaderAction.Download(episode, storageIndex))
                        },
                        onDownloadCancelClick = {
                            onDownloaderAction(DownloaderAction.CancelDownload(episode))
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
                        modifier = Modifier.fillMaxWidth(),
                        enableDownloadDialog = true,
                        showEpisodeDownloadOption = true,
                        initialSelection =
                            DownloadSelection(
                                seasonIds = state.existingScope.seasonIds,
                                alsoFutureSeasons = state.existingScope.alsoFutureSeasons,
                            ),
                        initialAlsoFollowNew = state.existingScope.alsoFollowNew,
                        initialOnlyUnwatched = state.existingScope.onlyUnwatched,
                        getSeasons = getSeasons,
                        onBulkDownload = { selection, alsoFollowNew, onlyUnwatched ->
                            onAction(
                                EpisodeAction.DownloadWithScope(
                                    selection,
                                    alsoFollowNew,
                                    onlyUnwatched,
                                )
                            )
                        },
                        onInfoClick = state.videoMetadata?.let { { infoDialogOpen = true } },
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    if (infoDialogOpen && state.videoMetadata != null) {
                        InfoDialog(
                            videoMetadata = state.videoMetadata!!,
                            downloadedFilePath =
                                downloadedSource?.path?.takeUnless { it.endsWith(".download") },
                            onDismiss = { infoDialogOpen = false },
                        )
                    }
                    OverviewText(text = episode.overview)
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                }
                if (state.actors.isNotEmpty()) {
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(EpisodeAction.NavigateToPerson(personId))
                        },
                        contentPadding = PaddingValues(start = paddingStart, end = paddingEnd),
                    )
                }
                Spacer(Modifier.height(paddingBottom))
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }

        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            onBackClick = { onAction(EpisodeAction.OnBackClick) },
            onHomeClick = { onAction(EpisodeAction.OnHomeClick) },
            onSettingsClick = { onAction(EpisodeAction.OnSettingsClick) },
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            state.episode?.let { episode ->
                Button(
                    onClick = { onAction(EpisodeAction.NavigateToSeason(episode.seasonId)) },
                    modifier = Modifier.alpha(0.7f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White,
                        ),
                ) {
                    episode.seasonName?.let { seasonName -> Text(seasonName) }
                        ?: run {
                            Text(
                                stringResource(
                                    CoreR.string.season_number,
                                    episode.parentIndexNumber,
                                )
                            )
                        }
                }
                if (state.seriesTvdbId != null) {
                    PvrSearchButton(
                        onAutomaticSearch = { onAction(EpisodeAction.SearchEpisodeAutomatic) },
                        onManualSearch = { onAction(EpisodeAction.OpenReleasePicker) },
                    )
                }
            }
        }
    }

    state.releasePicker?.let { releasePicker ->
        ReleasePickerSheet(
            state = releasePicker,
            onGrab = { release -> onAction(EpisodeAction.GrabRelease(release)) },
            onDismissRequest = { onAction(EpisodeAction.DismissReleasePicker) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
private fun EpisodeScreenLayoutPreview() {
    FindroidTheme {
        EpisodeScreenLayout(
            state = EpisodeState(episode = dummyEpisode, videoMetadata = dummyVideoMetadata),
            downloaderState = DownloaderState(),
            onAction = {},
            onDownloaderAction = {},
        )
    }
}
