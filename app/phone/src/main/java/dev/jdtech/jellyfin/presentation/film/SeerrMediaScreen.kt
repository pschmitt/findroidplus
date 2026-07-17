package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
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
import dev.jdtech.jellyfin.presentation.film.components.SeerrStatusChip
import dev.jdtech.jellyfin.presentation.film.components.seerrMediaTypeLabel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents

/**
 * Detail view for a Seerr search result that is not (fully) in the library yet - metadata plus
 * the request/unrequest actions. Identified by TMDB id instead of a Jellyfin item id.
 */
@Composable
fun SeerrMediaScreen(
    tmdbId: Int,
    mediaType: SeerrMediaType,
    navigateBack: () -> Unit,
    viewModel: SeerrMediaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The viewmodel's ActionFailed event doesn't say which action failed - remember it here so
    // the failure toast can match ("Request failed" vs "Cancel failed").
    var lastActionWasCancel by remember { mutableStateOf(false) }

    LaunchedEffect(true) { viewModel.loadDetail(tmdbId = tmdbId, mediaType = mediaType) }

    ObserveAsEvents(viewModel.events) { event ->
        val message =
            when (event) {
                is SeerrMediaEvent.Requested ->
                    context.getString(CoreR.string.discover_requested_toast, event.title)
                is SeerrMediaEvent.RequestCancelled ->
                    context.getString(CoreR.string.seerr_request_cancelled_toast, event.title)
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
private fun SeerrMediaScreenLayout(state: SeerrMediaState, onAction: (SeerrMediaAction) -> Unit) {
    val safePadding = rememberSafePadding()

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
                            text = detail.title,
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
                        // The chip renders NOT_REQUESTED as "Requested" (its just-requested
                        // marker), so only show it once there actually is a request or status.
                        if (
                            detail.status != SeerrMediaStatus.NOT_REQUESTED ||
                                detail.cancellableRequestIds.isNotEmpty()
                        ) {
                            Spacer(Modifier.height(MaterialTheme.spacings.small))
                            SeerrStatusChip(status = detail.status)
                        }
                        detail.overview?.takeIf { it.isNotBlank() }?.let { overview ->
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
                                    Text(text = stringResource(CoreR.string.discover_request))
                                }
                            }
                            if (detail.cancellableRequestIds.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    enabled = !state.isSubmitting,
                                ) {
                                    Text(text = stringResource(CoreR.string.seerr_cancel_request))
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
        val imageUrl = detail.backdropUrl ?: detail.posterUrl
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
            onAction = {},
        )
    }
}
