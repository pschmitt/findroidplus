package dev.pschmitt.jellyfin.ui

import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloaderAction
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloaderEvent
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloaderState
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloaderViewModel
import dev.pschmitt.jellyfin.core.presentation.dummy.dummyMovie
import dev.pschmitt.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.pschmitt.jellyfin.core.presentation.theme.Yellow
import dev.pschmitt.jellyfin.film.presentation.movie.MovieAction
import dev.pschmitt.jellyfin.film.presentation.movie.MovieState
import dev.pschmitt.jellyfin.film.presentation.movie.MovieViewModel
import dev.pschmitt.jellyfin.models.isDownloaded
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.DownloadProgress
import dev.pschmitt.jellyfin.utils.ObserveAsEvents
import dev.pschmitt.jellyfin.utils.format
import dev.pschmitt.jellyfin.utils.formatDownloadSpeed
import dev.pschmitt.jellyfin.utils.formatEta
import dev.pschmitt.jellyfin.utils.resolveDownloadStorageIndex
import java.util.UUID

@Composable
fun MovieScreen(
    movieId: UUID,
    navigateToPlayer: (itemId: UUID) -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadMovie(movieId = movieId) }

    LaunchedEffect(state.movie) { state.movie?.let { movie -> downloaderViewModel.update(movie) } }

    ObserveAsEvents(downloaderViewModel.events) { event ->
        when (event) {
            is DownloaderEvent.Successful,
            is DownloaderEvent.Deleted -> viewModel.loadMovie(movieId = movieId)
        }
    }

    MovieScreenLayout(
        state = state,
        downloaderState = downloaderState,
        downloadLocationPreference = downloaderViewModel.downloadLocationPreference,
        onAction = { action ->
            when (action) {
                is MovieAction.Play -> {
                    navigateToPlayer(movieId)
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onDownloaderAction = downloaderViewModel::onAction,
    )
}

@Composable
private fun MovieScreenLayout(
    state: MovieState,
    onAction: (MovieAction) -> Unit,
    downloaderState: DownloaderState? = null,
    downloadLocationPreference: String = "ask",
    onDownloaderAction: (DownloaderAction) -> Unit = {},
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales.get(0)

    var storageSelectionDialogOpen by remember { mutableStateOf(false) }
    var cancelDownloadDialogOpen by remember { mutableStateOf(false) }
    var deleteDownloadDialogOpen by remember { mutableStateOf(false) }
    var selectedStorageIndex by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        state.movie?.let { movie ->
            var size by remember { mutableStateOf(Size.Zero) }
            Box(
                modifier =
                    Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                        size = coordinates.size.toSize()
                    }
            ) {
                AsyncImage(
                    model = movie.images.backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (size != Size.Zero) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color.Black.copy(alpha = .2f), Color.Black),
                                        center = Offset(size.width, 0f),
                                        radius = size.width * .8f,
                                    )
                                )
                    )
                }
                Column(
                    modifier =
                        Modifier.padding(
                            start = MaterialTheme.spacings.default * 2,
                            end = MaterialTheme.spacings.default * 2,
                        )
                ) {
                    Spacer(modifier = Modifier.height(112.dp))
                    Text(text = movie.name, style = MaterialTheme.typography.displayMedium)
                    if (movie.originalTitle != movie.name) {
                        movie.originalTitle?.let { originalTitle ->
                            Text(text = originalTitle, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)
                    ) {
                        movie.premiereDate?.let { premiereDate ->
                            Text(
                                text = premiereDate.format(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    movie.runtimeTicks.div(600000000),
                                ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        movie.officialRating?.let {
                            Text(text = it, style = MaterialTheme.typography.labelMedium)
                        }
                        movie.communityRating?.let {
                            Row {
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_star),
                                    contentDescription = null,
                                    tint = Yellow,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(MaterialTheme.spacings.extraSmall))
                                Text(
                                    text = String.format(locale, "%.1f", it),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    Text(
                        text = movie.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(640.dp),
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium)
                    ) {
                        Button(
                            onClick = { onAction(MovieAction.Play(startFromBeginning = false)) },
                            modifier = Modifier.focusRequester(focusRequester),
                        ) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_play),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(id = CoreR.string.play))
                        }
                        movie.trailer?.let { trailerUri ->
                            Button(onClick = { onAction(MovieAction.PlayTrailer(trailerUri)) }) {
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_film),
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(id = CoreR.string.watch_trailer))
                            }
                        }
                        Button(
                            onClick = {
                                when (movie.played) {
                                    true -> onAction(MovieAction.UnmarkAsPlayed)
                                    false -> onAction(MovieAction.MarkAsPlayed)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_check),
                                contentDescription = null,
                                tint = if (movie.played) Color.Red else LocalContentColor.current,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text =
                                    stringResource(
                                        id =
                                            if (movie.played) CoreR.string.unmark_as_played
                                            else CoreR.string.mark_as_played
                                    )
                            )
                        }
                        Button(
                            onClick = {
                                when (movie.favorite) {
                                    true -> onAction(MovieAction.UnmarkAsFavorite)
                                    false -> onAction(MovieAction.MarkAsFavorite)
                                }
                            }
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (movie.favorite) CoreR.drawable.ic_heart_filled
                                            else CoreR.drawable.ic_heart
                                    ),
                                contentDescription = null,
                                tint = if (movie.favorite) Color.Red else LocalContentColor.current,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text =
                                    stringResource(
                                        id =
                                            if (movie.favorite) CoreR.string.remove_from_favorites
                                            else CoreR.string.add_to_favorites
                                    )
                            )
                        }
                        if (downloaderState != null && !downloaderState.isDownloading) {
                            if (movie.isDownloaded()) {
                                Button(onClick = { deleteDownloadDialogOpen = true }) {
                                    Icon(
                                        painter = painterResource(id = CoreR.drawable.ic_trash),
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(id = CoreR.string.delete_download))
                                }
                            } else if (movie.canDownload) {
                                Button(
                                    onClick = {
                                        val storageLocations = context.getExternalFilesDirs(null)
                                        val preferredIndex =
                                            resolveDownloadStorageIndex(
                                                context,
                                                downloadLocationPreference,
                                            )
                                        when {
                                            preferredIndex >= 0 -> {
                                                onDownloaderAction(
                                                    DownloaderAction.Download(movie, preferredIndex)
                                                )
                                            }
                                            storageLocations.size > 1 -> {
                                                storageSelectionDialogOpen = true
                                            }
                                            else -> {
                                                onDownloaderAction(
                                                    DownloaderAction.Download(movie, 0)
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = CoreR.drawable.ic_download),
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(id = CoreR.string.download))
                                }
                            }
                        }
                    }
                    if (downloaderState != null && downloaderState.isDownloading) {
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                        ) {
                            val statusText =
                                when {
                                    downloaderState.status == android.app.DownloadManager.STATUS_PENDING ->
                                        stringResource(id = CoreR.string.download_queued)
                                    downloaderState.status == android.app.DownloadManager.STATUS_PAUSED ->
                                        stringResource(id = CoreR.string.download_paused)
                                    downloaderState.status == DownloadProgress.STATUS_AWAITING_FOREGROUND ->
                                        stringResource(id = CoreR.string.download_awaiting_foreground)
                                    downloaderState.status == DownloadProgress.STATUS_VERIFYING ->
                                        stringResource(id = CoreR.string.download_verifying)
                                    downloaderState.progress > 0f ->
                                        stringResource(
                                            id = CoreR.string.download_progress_status,
                                            (downloaderState.progress * 100).toInt(),
                                            formatDownloadSpeed(downloaderState.speedBytesPerSecond),
                                            formatEta(downloaderState.etaSeconds),
                                        )
                                    else -> stringResource(id = CoreR.string.download_downloading)
                                }
                            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { cancelDownloadDialogOpen = true }) {
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_x),
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(id = CoreR.string.download_action_cancel))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.large)
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = CoreR.string.genres),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = .5f),
                            )
                            Text(
                                text = movie.genres.joinToString(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        state.director?.let { director ->
                            Column {
                                Text(
                                    text = stringResource(id = CoreR.string.director),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = .5f),
                                )
                                Text(
                                    text = director.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(id = CoreR.string.writers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = .5f),
                            )
                            Text(
                                text = state.writers.joinToString { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    //                    Spacer(modifier =
                    // Modifier.height(MaterialTheme.spacings.large))
                    //                    Text(
                    //                        text = stringResource(id =
                    // CoreR.string.cast_amp_crew),
                    //                        style = MaterialTheme.typography.headlineMedium,
                    //                    )
                }
            }

            LaunchedEffect(true) { focusRequester.requestFocus() }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
    }

    if (storageSelectionDialogOpen) {
        val storageLocations = remember { context.getExternalFilesDirs(null) }
        AlertDialog(
            title = { Text(text = stringResource(id = CoreR.string.select_storage_location)) },
            text = {
                Column {
                    storageLocations.forEachIndexed { index, dir ->
                        val locationStringRes =
                            if (Environment.isExternalStorageRemovable(dir)) CoreR.string.external
                            else CoreR.string.internal
                        val availableMegaBytes = StatFs(dir.path).availableBytes.div(1000000)
                        TextButton(
                            onClick = {
                                onDownloaderAction(DownloaderAction.Download(state.movie!!, index))
                                storageSelectionDialogOpen = false
                            }
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id = CoreR.string.storage_name,
                                        stringResource(id = locationStringRes),
                                        availableMegaBytes,
                                    )
                            )
                        }
                    }
                }
            },
            onDismissRequest = { storageSelectionDialogOpen = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { storageSelectionDialogOpen = false }) {
                    Text(text = stringResource(id = CoreR.string.cancel))
                }
            },
        )
    }

    if (cancelDownloadDialogOpen) {
        AlertDialog(
            title = { Text(text = stringResource(id = CoreR.string.cancel_download)) },
            text = { Text(text = stringResource(id = CoreR.string.cancel_download_message)) },
            onDismissRequest = { cancelDownloadDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDownloaderAction(DownloaderAction.CancelDownload(state.movie!!))
                        cancelDownloadDialogOpen = false
                    }
                ) {
                    Text(text = stringResource(id = CoreR.string.stop_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDownloadDialogOpen = false }) {
                    Text(text = stringResource(id = CoreR.string.cancel))
                }
            },
        )
    }

    if (deleteDownloadDialogOpen) {
        AlertDialog(
            title = { Text(text = stringResource(id = CoreR.string.delete_download)) },
            text = { state.movie?.let { movie -> Text(text = movie.name) } },
            onDismissRequest = { deleteDownloadDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDownloaderAction(DownloaderAction.DeleteDownload(state.movie!!))
                        deleteDownloadDialogOpen = false
                    }
                ) {
                    Text(text = stringResource(id = CoreR.string.delete_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDownloadDialogOpen = false }) {
                    Text(text = stringResource(id = CoreR.string.cancel))
                }
            },
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MovieScreenLayoutPreview() {
    FindroidTheme {
        MovieScreenLayout(
            state = MovieState(movie = dummyMovie, videoMetadata = dummyVideoMetadata),
            onAction = {},
        )
    }
}
