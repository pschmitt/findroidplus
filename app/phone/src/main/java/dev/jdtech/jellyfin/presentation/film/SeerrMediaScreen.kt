package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.seerr.SeerrMediaAction
import dev.jdtech.jellyfin.film.presentation.seerr.SeerrMediaEvent
import dev.jdtech.jellyfin.film.presentation.seerr.SeerrMediaState
import dev.jdtech.jellyfin.film.presentation.seerr.SeerrMediaViewModel
import dev.jdtech.jellyfin.models.SeerrMediaDetail
import dev.jdtech.jellyfin.models.SeerrMediaStatus
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.ReleasePickerSheet
import dev.jdtech.jellyfin.presentation.film.components.PvrQueueDownloadCard
import dev.jdtech.jellyfin.presentation.film.components.SeerrStatusChip
import dev.jdtech.jellyfin.presentation.film.components.seerrMediaTypeLabel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID

/**
 * Detail view for a Seerr search result that is not (fully) in the library yet - metadata plus
 * the request/unrequest actions. Identified by TMDB id instead of a Jellyfin item id.
 */
@Composable
fun SeerrMediaScreen(
    tmdbId: Int,
    mediaType: SeerrMediaType,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    sonarrEpisodeId: Int? = null,
    navigateToShow: (UUID?) -> Unit = {},
    navigateToSeason: (Int, UUID?) -> Unit = { _, _ -> },
    navigateBack: () -> Unit,
    viewModel: SeerrMediaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Request and cancel share their failure event, so remember which label to show for it.
    var lastActionWasCancel by remember { mutableStateOf(false) }

    LaunchedEffect(tmdbId, mediaType, seasonNumber, episodeNumber, sonarrEpisodeId) {
        viewModel.loadDetail(tmdbId, mediaType, seasonNumber, episodeNumber, sonarrEpisodeId)
    }

    ObserveAsEvents(viewModel.events) { event ->
        val message =
            when (event) {
                is SeerrMediaEvent.Requested ->
                    context.getString(CoreR.string.discover_requested_toast, event.title)
                is SeerrMediaEvent.RequestCancelled ->
                    context.getString(CoreR.string.seerr_request_cancelled_toast, event.title)
                is SeerrMediaEvent.SearchTriggered ->
                    context.getString(
                        when (event.source) {
                            dev.jdtech.jellyfin.models.PvrSource.SONARR ->
                                CoreR.string.sonarr_search_started_toast
                            dev.jdtech.jellyfin.models.PvrSource.RADARR ->
                                CoreR.string.radarr_search_started_toast
                        }
                    )
                is SeerrMediaEvent.SearchFailed ->
                    context.getString(
                        when (event.source) {
                            dev.jdtech.jellyfin.models.PvrSource.SONARR ->
                                CoreR.string.sonarr_search_failed_toast
                            dev.jdtech.jellyfin.models.PvrSource.RADARR ->
                                CoreR.string.radarr_search_failed_toast
                        },
                        event.message ?: context.getString(CoreR.string.unknown_error),
                    )
                is SeerrMediaEvent.ReleaseGrabbed ->
                    context.getString(CoreR.string.release_grabbed_toast)
                is SeerrMediaEvent.ActionFailed ->
                    context.getString(
                        if (lastActionWasCancel) {
                            CoreR.string.seerr_cancel_failed_toast
                        } else {
                            CoreR.string.discover_request_failed_toast
                        },
                        event.message ?: context.getString(CoreR.string.unknown_error),
                    )
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    SeerrMediaScreenLayout(
        state = state,
        navigateToShow = navigateToShow,
        navigateToSeason = navigateToSeason,
        onAction = { action ->
            when (action) {
                is SeerrMediaAction.OnRequest -> lastActionWasCancel = false
                is SeerrMediaAction.OnCancelRequest -> lastActionWasCancel = true
                is SeerrMediaAction.OnBackClick -> navigateBack()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SeerrMediaScreenLayout(
    state: SeerrMediaState,
    navigateToShow: (UUID?) -> Unit,
    navigateToSeason: (Int, UUID?) -> Unit,
    onAction: (SeerrMediaAction) -> Unit,
) {
    val safePadding = rememberSafePadding()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val scrollState = rememberScrollState()

    var showCancelDialog by remember { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.detail != null -> {
                val detail = state.detail!!
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                    SeerrBackdrop(detail = detail)
                    Column(modifier = Modifier.padding(start = paddingStart, end = paddingEnd)) {
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                        Text(
                            text = detail.episode?.title ?: detail.title,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 3,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                        Text(
                            text = seerrMetaLine(detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        detail.episode?.let { episode ->
                            Spacer(Modifier.height(MaterialTheme.spacings.small))
                            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                                TextButton(onClick = { navigateToShow(state.jellyfinShowId) }) {
                                    Text(detail.title)
                                }
                                TextButton(
                                    onClick = {
                                        navigateToSeason(episode.seasonNumber, state.jellyfinSeasonId)
                                    }
                                ) {
                                    Text(detail.season?.title ?: "Season ${episode.seasonNumber}")
                                }
                            }
                        } ?: detail.season?.let {
                            Spacer(Modifier.height(MaterialTheme.spacings.small))
                            TextButton(onClick = { navigateToShow(state.jellyfinShowId) }) {
                                Text(detail.title)
                            }
                        }
                        // Seerr only tracks request/availability status at the season/show level,
                        // so AVAILABLE/PARTIALLY_AVAILABLE don't mean anything precise for a
                        // single episode - an episode is either in the library or it isn't. Once
                        // there's an episode in view, resolve the chip from that binary fact
                        // instead of projecting the season's aggregate status onto it; the
                        // request-lifecycle states (NOT_REQUESTED/PENDING/PROCESSING) still
                        // describe the season accurately either way, so those pass through as-is.
                        val displayStatus =
                            when {
                                detail.episode == null -> detail.status
                                state.jellyfinEpisodeId != null -> SeerrMediaStatus.AVAILABLE
                                detail.status == SeerrMediaStatus.AVAILABLE ||
                                    detail.status == SeerrMediaStatus.PARTIALLY_AVAILABLE -> null
                                else -> detail.status
                            }
                        // The chip renders NOT_REQUESTED as "Requested" (its just-requested
                        // marker), so only show it once there actually is a request or status.
                        if (
                            displayStatus != null &&
                                (displayStatus != SeerrMediaStatus.NOT_REQUESTED ||
                                    detail.cancellableRequestIds.isNotEmpty())
                        ) {
                            Spacer(Modifier.height(MaterialTheme.spacings.small))
                            SeerrStatusChip(status = displayStatus)
                        }
                        state.queueStatus?.let { queueStatus ->
                            Spacer(Modifier.height(MaterialTheme.spacings.small))
                            PvrQueueDownloadCard(status = queueStatus)
                        }
                        (detail.episode?.overview ?: detail.season?.overview ?: detail.overview)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { overview ->
                            Spacer(Modifier.height(MaterialTheme.spacings.medium))
                            OverviewText(text = overview, maxCollapsedLines = 5)
                        }
                        Spacer(Modifier.height(MaterialTheme.spacings.medium))
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.medium)
                        ) {
                            if (
                                detail.status == SeerrMediaStatus.NOT_REQUESTED &&
                                    detail.cancellableRequestIds.isEmpty()
                            ) {
                                Button(
                                    onClick = { onAction(SeerrMediaAction.OnRequest) },
                                    enabled = !state.isSubmitting,
                                ) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_seerr),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                                    // Seerr has no per-episode requesting, so a season or episode
                                    // view always ends up requesting the season - make that
                                    // explicit rather than implying the whole show is requested.
                                    Text(
                                        text =
                                            stringResource(
                                                if (detail.season != null) {
                                                    CoreR.string.discover_request_season
                                                } else {
                                                    CoreR.string.discover_request
                                                }
                                            )
                                    )
                                }
                            }
                            if (detail.cancellableRequestIds.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    enabled = !state.isSubmitting,
                                ) {
                                    Row(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(MaterialTheme.spacings.small)
                                    ) {
                                        Icon(
                                            painter = painterResource(CoreR.drawable.ic_seerr),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(text = stringResource(CoreR.string.seerr_cancel_request))
                                    }
                                }
                            }
                            // Not in the library yet - there's nothing to play, but TMDB usually
                            // has a trailer, so offer that instead while the request works its
                            // way through Sonarr/Radarr.
                            detail.trailerUrl?.let { trailerUrl ->
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            uriHandler.openUri(trailerUrl)
                                        } catch (e: IllegalArgumentException) {
                                            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Row(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(MaterialTheme.spacings.small)
                                    ) {
                                        Icon(
                                            painter = painterResource(CoreR.drawable.ic_film),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(text = stringResource(CoreR.string.trailer))
                                    }
                                }
                            }
                        }
                        if (state.pvrSearchConfigured) {
                            Spacer(Modifier.height(MaterialTheme.spacings.small))
                            OutlinedButton(
                                onClick = { onAction(SeerrMediaAction.OnAutomaticSearchInPvr) },
                                enabled = !state.isSubmitting,
                            ) {
                                PvrSearchButtonLabel(mediaType = detail.mediaType, manual = false)
                            }
                        }
                        if (state.manualPvrSearchAvailable) {
                            OutlinedButton(
                                onClick = { onAction(SeerrMediaAction.OnOpenReleasePicker) },
                                enabled = !state.isSubmitting,
                            ) {
                                PvrSearchButtonLabel(mediaType = detail.mediaType, manual = true)
                            }
                        }
                        // Show-level view only - a season/episode view is already scoped to one
                        // season, so there's nothing to list. This is the only way to reach a
                        // season-scoped Seerr view directly from the show (the other path is via
                        // an episode's "back to season" link, further downstream).
                        if (detail.mediaType == SeerrMediaType.TV && detail.season == null && detail.episode == null) {
                            detail.numberOfSeasons?.takeIf { it > 0 }?.let { numberOfSeasons ->
                                Spacer(Modifier.height(MaterialTheme.spacings.medium))
                                Text(
                                    text = stringResource(CoreR.string.seasons),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(MaterialTheme.spacings.small))
                                Column {
                                    for (seasonNumber in 1..numberOfSeasons) {
                                        val seasonStatus =
                                            detail.seasons
                                                .firstOrNull { it.seasonNumber == seasonNumber }
                                                ?.status
                                        SeerrSeasonRow(
                                            seasonNumber = seasonNumber,
                                            status = seasonStatus,
                                            // No per-row Jellyfin season id is resolved here (the
                                            // show-level view only resolves one show/season pair
                                            // total, not all seasons at once) - always route
                                            // through a fresh season-scoped SeerrMediaRoute load,
                                            // same as navigateToSeason already does when it has no
                                            // Jellyfin season id to jump to directly.
                                            onClick = { navigateToSeason(seasonNumber, null) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(paddingBottom))
                }
            }
            state.error != null -> {
                ErrorCard(
                    onShowStacktrace = { showErrorDialog = true },
                    onRetryClick = { onAction(SeerrMediaAction.OnRetryClick) },
                    modifier =
                        Modifier.align(Alignment.Center)
                            .padding(start = paddingStart, end = paddingEnd),
                )
                if (showErrorDialog) {
                    ErrorDialog(
                        exception = state.error!!,
                        onDismissRequest = { showErrorDialog = false },
                    )
                }
            }
            else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // ItemTopBar-style floating back button - the screen has no top app bar of its own.
        IconButton(
            onClick = { onAction(SeerrMediaAction.OnBackClick) },
            modifier =
                Modifier.padding(
                        start = safePadding.start + MaterialTheme.spacings.small,
                        top = safePadding.top + MaterialTheme.spacings.small,
                    )
                    .alpha(0.7f),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                ),
        ) {
            Icon(painter = painterResource(CoreR.drawable.ic_arrow_left), contentDescription = null)
        }
    }

    if (showCancelDialog) {
        state.detail?.let { detail ->
            AlertDialog(
                title = { Text(text = stringResource(CoreR.string.seerr_cancel_request)) },
                text = {
                    Text(
                        text =
                            stringResource(CoreR.string.seerr_cancel_request_message, detail.title)
                    )
                },
                onDismissRequest = { showCancelDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onAction(SeerrMediaAction.OnCancelRequest)
                            showCancelDialog = false
                        }
                    ) {
                        Text(
                            text = stringResource(CoreR.string.seerr_cancel_request),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text(text = stringResource(CoreR.string.close))
                    }
                },
            )
        }
    }

    state.releasePicker?.let { releasePicker ->
        ReleasePickerSheet(
            state = releasePicker,
            onGrab = { onAction(SeerrMediaAction.GrabRelease(it)) },
            onDismissRequest = { onAction(SeerrMediaAction.DismissReleasePicker) },
        )
    }
}

@Composable
private fun PvrSearchButtonLabel(mediaType: SeerrMediaType, manual: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        Icon(
            painter =
                painterResource(
                    if (mediaType == SeerrMediaType.TV) CoreR.drawable.ic_sonarr
                    else CoreR.drawable.ic_radarr
                ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text =
                stringResource(
                    when (mediaType) {
                        SeerrMediaType.MOVIE ->
                            if (manual) CoreR.string.search_movie_in_radarr_manual
                            else CoreR.string.search_movie_in_radarr_automatic
                        SeerrMediaType.TV ->
                            if (manual) CoreR.string.search_episode_in_sonarr_manual
                            else CoreR.string.search_episode_in_sonarr_automatic
                    }
                )
        )
    }
}

/**
 * One row in the show-level season list: "Season N" plus its status chip (omitted when the
 * season has never been touched - matching how the rest of this screen only shows a chip once
 * there's an actual request/status to report, see the show-level chip above). Tapping always
 * navigates into a season-scoped Seerr view, letting that screen resolve the Jellyfin ids itself.
 */
@Composable
private fun SeerrSeasonRow(seasonNumber: Int, status: SeerrMediaStatus?, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(vertical = MaterialTheme.spacings.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(CoreR.string.season_number, seasonNumber),
            style = MaterialTheme.typography.bodyLarge,
        )
        status?.let { SeerrStatusChip(status = it) }
    }
}

/** 16:9 backdrop, falling back to the poster or a plain surface when there's no image at all. */
@Composable
private fun SeerrBackdrop(detail: SeerrMediaDetail) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        val imageUrl = detail.episode?.stillUrl ?: detail.backdropUrl ?: detail.season?.posterUrl ?: detail.posterUrl
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** "2014 · Movie · 1h 49m · Comedy, Drama" - skips whatever the payload doesn't have. */
@Composable
private fun seerrMetaLine(detail: SeerrMediaDetail): String {
    detail.episode?.let { episode ->
        return listOf(
                detail.season?.title ?: detail.title,
                stringResource(
                    CoreR.string.episode_name_extended,
                    episode.seasonNumber,
                    episode.episodeNumber,
                    episode.title,
                ),
                episode.airDate?.take(10),
            )
            .filterNotNull()
            .joinToString(" · ")
    }
    detail.season?.let { season ->
        return listOf(detail.title, season.title).joinToString(" · ")
    }
    val runtimeOrSeasons =
        when (detail.mediaType) {
            SeerrMediaType.MOVIE ->
                detail.runtimeMinutes?.takeIf { it > 0 }?.let { minutes ->
                    val hours = minutes / 60
                    if (hours > 0) {
                        stringResource(CoreR.string.runtime_hours_minutes, hours, minutes % 60)
                    } else {
                        stringResource(CoreR.string.runtime_minutes_short, minutes)
                    }
                }
            SeerrMediaType.TV ->
                detail.numberOfSeasons?.takeIf { it > 0 }?.let { seasons ->
                    pluralStringResource(CoreR.plurals.seerr_seasons, seasons, seasons)
                }
        }
    return listOfNotNull(
            detail.year?.toString(),
            seerrMediaTypeLabel(detail.mediaType),
            runtimeOrSeasons,
            detail.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
        )
        .joinToString(" · ")
}

@PreviewScreenSizes
@Composable
private fun SeerrMediaScreenLayoutPreview() {
    FindroidTheme {
        SeerrMediaScreenLayout(
            state =
                SeerrMediaState(
                    detail =
                        SeerrMediaDetail(
                            tmdbId = 157336,
                            mediaType = SeerrMediaType.MOVIE,
                            title = "Interstellar",
                            year = 2014,
                            overview =
                                "The adventures of a group of explorers who make use of a " +
                                    "newly discovered wormhole to surpass the limitations on " +
                                    "human space travel and conquer the vast distances involved " +
                                    "in an interstellar voyage.",
                            posterUrl = null,
                            backdropUrl = null,
                            genres = listOf("Adventure", "Drama", "Science Fiction"),
                            runtimeMinutes = 169,
                            numberOfSeasons = null,
                            status = SeerrMediaStatus.NOT_REQUESTED,
                            cancellableRequestIds = emptyList(),
                        )
                ),
            navigateToShow = {},
            navigateToSeason = { _, _ -> },
            onAction = {},
        )
    }
}
