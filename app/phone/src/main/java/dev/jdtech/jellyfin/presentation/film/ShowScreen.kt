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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import dev.jdtech.jellyfin.film.presentation.show.ShowViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.UpcomingSeason
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.ClearDownloadsDialog
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.InfoText
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.PlayOverlayButton
import dev.jdtech.jellyfin.presentation.film.components.UpcomingSeasonCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatBinaryFileSize
import dev.jdtech.jellyfin.utils.formatCalendarDate
import dev.jdtech.jellyfin.utils.formatCalendarTime
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.getShowDateString
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun ShowScreen(
    showId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToItem: (item: FindroidItem) -> Unit,
    navigateToPerson: (personId: UUID) -> Unit,
    navigateToSeerr: (tmdbId: Int, seasonNumber: Int) -> Unit,
    navigateToSettings: () -> Unit,
    viewModel: ShowViewModel = hiltViewModel(),
) {
    val androidContext = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadShow(showId = showId) }

    ShowScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is ShowAction.Play -> {
                    val intent = Intent(androidContext, PlayerActivity::class.java)
                    intent.putExtra("itemId", showId.toString())
                    intent.putExtra("itemKind", BaseItemKind.SERIES.serialName)
                    androidContext.startActivity(intent)
                }
                is ShowAction.PlayTrailer -> {
                    try {
                        uriHandler.openUri(action.trailer)
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(androidContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                is ShowAction.OnBackClick -> navigateBack()
                is ShowAction.OnHomeClick -> navigateHome()
                is ShowAction.OnSettingsClick -> navigateToSettings()
                is ShowAction.NavigateToItem -> navigateToItem(action.item)
                is ShowAction.NavigateToPerson -> navigateToPerson(action.personId)
                is ShowAction.NavigateToSeerr -> navigateToSeerr(action.tmdbId, action.seasonNumber)
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun ShowScreenLayout(state: ShowState, onAction: (ShowAction) -> Unit) {
    val androidContext = LocalContext.current
    val safePadding = rememberSafePadding()

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val scrollState = rememberScrollState()
    var clearShowDownloadsDialogOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        state.show?.let { show ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                ItemHeader(
                    item = show,
                    scrollState = scrollState,
                    content = {
                        PlayOverlayButton(
                            item = show,
                            onClick = { onAction(ShowAction.Play(startFromBeginning = false)) },
                            enabled = show.canPlay && state.seasons.isNotEmpty(),
                            modifier = Modifier.align(Alignment.Center),
                        )
                        Column(
                            modifier =
                                Modifier.align(Alignment.BottomStart)
                                    .padding(start = paddingStart, end = paddingEnd)
                        ) {
                            Text(
                                text = show.name,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 3,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            show.originalTitle?.let { originalTitle ->
                                if (originalTitle != show.name) {
                                    Text(
                                        text = originalTitle,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
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
                        Text(
                            text = getShowDateString(show),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    show.runtimeTicks.div(600000000),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        show.officialRating?.let { officialRating ->
                            Text(text = officialRating, style = MaterialTheme.typography.bodyMedium)
                        }
                        show.communityRating?.let { communityRating ->
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
                    ItemButtonsBar(
                        item = show,
                        onPlayClick = { startFromBeginning ->
                            onAction(ShowAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onMarkAsPlayedClick = {
                            when (show.played) {
                                true -> onAction(ShowAction.UnmarkAsPlayed)
                                false -> onAction(ShowAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (show.favorite) {
                                true -> onAction(ShowAction.UnmarkAsFavorite)
                                false -> onAction(ShowAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = { uri -> onAction(ShowAction.PlayTrailer(uri)) },
                        onDownloadClick = {},
                        onDownloadCancelClick = {},
                        onDownloadDeleteClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        downloaderState = DownloaderState(),
                        enableDownloadDialog = true,
                        initialSelection =
                            DownloadSelection(
                                seasonIds = state.existingScope.seasonIds,
                                alsoFutureSeasons = state.existingScope.alsoFutureSeasons,
                            ),
                        initialAlsoFollowNew = state.existingScope.alsoFollowNew,
                        initialOnlyUnwatched = state.existingScope.onlyUnwatched,
                        getSeasons = { state.seasons },
                        hasActiveDownloadOrRule = state.hasDownloads || state.autoDownloadEnabled,
                        onDeleteDownloads = { clearShowDownloadsDialogOpen = true },
                        downloadIconTint =
                            if (state.autoDownloadEnabled) Color("#F2C94C".toColorInt()) else null,
                        onBulkDownload = { selection, alsoFollowNew, onlyUnwatched ->
                            onAction(
                                ShowAction.DownloadWithScope(selection, alsoFollowNew, onlyUnwatched)
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
                                FilledTonalButton(onClick = { clearShowDownloadsDialogOpen = true }) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_trash),
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                                    Text(text = stringResource(CoreR.string.clear_show_downloads))
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    if (state.downloadsSizeBytes > 0) {
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.downloads_disk_usage,
                                    formatBinaryFileSize(state.downloadsSizeBytes),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                    }
                    OverviewText(text = show.overview, maxCollapsedLines = 3)
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    InfoText(
                        genres = show.genres,
                        director = state.director,
                        writers = state.writers,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    state.nextUp?.let { nextUp ->
                        Text(
                            text = stringResource(CoreR.string.next_up),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                        Column(
                            modifier =
                                Modifier.widthIn(max = 420.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onAction(ShowAction.NavigateToItem(nextUp)) }
                        ) {
                            ItemPoster(
                                item = nextUp,
                                direction = Direction.HORIZONTAL,
                                modifier = Modifier.clip(MaterialTheme.shapes.medium),
                            )
                            Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                            Text(
                                text =
                                    stringResource(
                                        id = CoreR.string.episode_name_extended,
                                        nextUp.parentIndexNumber,
                                        nextUp.indexNumber,
                                        nextUp.name,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    }
                    if (state.nextUp == null) {
                        state.nextAiring?.let { nextAiring ->
                            Text(
                                text =
                                    nextAiring.airTime?.let { airTime ->
                                        stringResource(
                                            CoreR.string.next_episode_airs_time,
                                            nextAiring.subtitle.orEmpty(),
                                            formatCalendarDate(nextAiring.date),
                                            formatCalendarTime(airTime),
                                        )
                                    }
                                        ?: stringResource(
                                            CoreR.string.next_episode_airs,
                                            nextAiring.subtitle.orEmpty(),
                                            formatCalendarDate(nextAiring.date),
                                        ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(MaterialTheme.spacings.medium))
                        }
                    }
                }

                if (state.seasons.isNotEmpty() || state.missingSeasons.isNotEmpty()) {
                    Column(modifier = Modifier.padding(start = paddingStart, end = paddingEnd)) {
                        Text(
                            text = stringResource(CoreR.string.seasons),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                    }
                    val seasonRowItems =
                        (state.seasons.map { SeasonRowItem.Real(it) } +
                                state.missingSeasons.map { SeasonRowItem.Missing(it) })
                            .sortedBy { it.seasonNumber }
                    LazyRow(
                        contentPadding = PaddingValues(start = paddingStart, end = paddingEnd),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                    ) {
                        items(
                            items = seasonRowItems,
                            key = { item ->
                                when (item) {
                                    is SeasonRowItem.Real -> item.season.id
                                    is SeasonRowItem.Missing -> "missing-${item.season.seasonNumber}"
                                }
                            },
                        ) { item ->
                            when (item) {
                                is SeasonRowItem.Real ->
                                    ItemCard(
                                        item = item.season,
                                        direction = Direction.VERTICAL,
                                        onClick = { onAction(ShowAction.NavigateToItem(item.season)) },
                                    )
                                is SeasonRowItem.Missing ->
                                    UpcomingSeasonCard(
                                        season = item.season,
                                        onClick =
                                            state.seriesTmdbId?.let { tmdbId ->
                                                {
                                                    onAction(
                                                        ShowAction.NavigateToSeerr(
                                                            tmdbId = tmdbId,
                                                            seasonNumber = item.season.seasonNumber,
                                                        )
                                                    )
                                                }
                                            },
                                    )
                            }
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                }

                if (state.actors.isNotEmpty()) {
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(ShowAction.NavigateToPerson(personId))
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
            onBackClick = { onAction(ShowAction.OnBackClick) },
            onHomeClick = { onAction(ShowAction.OnHomeClick) },
            onSettingsClick = { onAction(ShowAction.OnSettingsClick) },
        )
    }

    if (clearShowDownloadsDialogOpen) {
        ClearDownloadsDialog(
            title = stringResource(CoreR.string.clear_show_downloads),
            message = stringResource(CoreR.string.clear_show_downloads_message),
            name = state.show?.name,
            sizeBytes = state.downloadsSizeBytes,
            onConfirm = { alsoRemoveRules ->
                onAction(ShowAction.DeleteShowDownloads(alsoRemoveRules))
                Toast.makeText(
                        androidContext,
                        CoreR.string.downloads_deleted_toast,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                clearShowDownloadsDialogOpen = false
            },
            onDismiss = { clearShowDownloadsDialogOpen = false },
        )
    }
}

@PreviewScreenSizes
@Composable
private fun EpisodeScreenLayoutPreview() {
    FindroidTheme { ShowScreenLayout(state = ShowState(show = dummyShow), onAction = {}) }
}

/**
 * Merges real [FindroidSeason]s and Sonarr-known [UpcomingSeason] placeholders into one list so
 * the seasons row can be sorted by season number - rendering real seasons first and missing ones
 * appended at the end (as two separate `items()` blocks previously did) put a show's e.g. season 4
 * placeholder after 1-3 but ahead of a real season 5, wherever one existed.
 */
private sealed interface SeasonRowItem {
    val seasonNumber: Int

    data class Real(val season: FindroidSeason) : SeasonRowItem {
        override val seasonNumber: Int
            get() = season.indexNumber
    }

    data class Missing(val season: UpcomingSeason) : SeasonRowItem {
        override val seasonNumber: Int
            get() = season.seasonNumber
    }
}
