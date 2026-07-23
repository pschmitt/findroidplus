package dev.jdtech.jellyfin.presentation.film.components

import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSizeEstimate
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatBinaryFileSize
import dev.jdtech.jellyfin.utils.resolveDownloadStorageIndex
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Lets the user pick what to download: for an episode, either just that episode or a bulk
 * selection of seasons/whole show; for a season or show screen, only the bulk selection. "This
 * episode" is exclusive with everything else (it's a fundamentally different, single-item scope).
 * "Automatically download new episodes" covers both halves of staying current on a show: new
 * episodes airing in a season already picked below, and brand new seasons that don't exist yet -
 * there's no reason to make the user think about that distinction, since the intent ("keep this
 * show up to date") is the same either way. [seasons] is null while still loading.
 * [initialSelection]/[initialAlsoFollowNew]/[initialOnlyUnwatched] pre-populate the dialog from an
 * already-saved rule, if any, so reopening it reflects what's actually configured instead of
 * always starting blank.
 */
@Composable
fun DownloadScopeDialog(
    seasons: List<FindroidSeason>?,
    showEpisodeOption: Boolean,
    initialSelection: DownloadSelection = DownloadSelection(),
    initialAlsoFollowNew: Boolean = false,
    initialOnlyUnwatched: Boolean = false,
    canDelete: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onConfirm: (selection: DownloadSelection, alsoFollowNew: Boolean, onlyUnwatched: Boolean) -> Unit,
    onDismiss: () -> Unit,
    getSeasonSize: (suspend (seasonId: UUID, onlyUnwatched: Boolean) -> DownloadSizeEstimate)? =
        null,
    // Already known synchronously from the loaded item - no fetch needed the way season sizes
    // require one, so this covers the "this episode" scope instead of going through the cache.
    episodeSize: DownloadSizeEstimate? = null,
    downloadLocationPreference: String = "ask",
) {
    val hasExistingRule =
        initialSelection.seasonIds.isNotEmpty() || initialSelection.alsoFutureSeasons
    // Opened from an episode, the overwhelmingly common intent is "download this one" - so
    // preselect it even when the show already has an auto-download rule. The rule's selection is
    // kept in the (deselected) bulk section below for editing, and stays untouched when the user
    // confirms with "this episode" selected.
    var thisEpisodeOnly by remember { mutableStateOf(showEpisodeOption) }
    var selectedSeasonIds by remember { mutableStateOf(initialSelection.seasonIds) }
    // Either kind of previously-saved rule (future-seasons-only, or per-season-follow) means the
    // show already has ongoing tracking from the user's point of view - there's only one toggle
    // for that now, so either signal being true should show it as on.
    var alsoFollowNew by
        remember { mutableStateOf(initialAlsoFollowNew || initialSelection.alsoFutureSeasons) }
    var onlyUnwatched by remember { mutableStateOf(initialOnlyUnwatched) }
    var seasonsExpanded by remember { mutableStateOf(false) }

    val bulkModeSelected = !thisEpisodeOnly && (selectedSeasonIds.isNotEmpty() || alsoFollowNew)
    val canConfirm = thisEpisodeOnly || bulkModeSelected
    val allSeasonIds = seasons?.map { it.id }?.toSet().orEmpty()

    // Cached per (season id, onlyUnwatched) so toggling a season off and back on - or flipping
    // "only unwatched" back to what it was - doesn't re-hit the network; only ever grows for the
    // lifetime of this dialog.
    val seasonSizeCache = remember { mutableStateMapOf<Pair<UUID, Boolean>, DownloadSizeEstimate>() }
    LaunchedEffect(selectedSeasonIds, thisEpisodeOnly, onlyUnwatched) {
        if (thisEpisodeOnly || getSeasonSize == null) return@LaunchedEffect
        val missing = selectedSeasonIds.filter { (it to onlyUnwatched) !in seasonSizeCache }
        if (missing.isEmpty()) return@LaunchedEffect
        coroutineScope {
            val sizes =
                missing.map { seasonId ->
                    seasonId to async { getSeasonSize(seasonId, onlyUnwatched) }
                }
            sizes.forEach { (seasonId, deferred) ->
                seasonSizeCache[seasonId to onlyUnwatched] = deferred.await()
            }
        }
    }
    val showSizeEstimate =
        if (thisEpisodeOnly) episodeSize != null
        else getSeasonSize != null && selectedSeasonIds.isNotEmpty()
    val sizeLoading =
        !thisEpisodeOnly && selectedSeasonIds.any { (it to onlyUnwatched) !in seasonSizeCache }
    val totalEstimate =
        if (thisEpisodeOnly) {
            episodeSize ?: DownloadSizeEstimate()
        } else {
            selectedSeasonIds.fold(DownloadSizeEstimate()) { acc, id ->
                acc + (seasonSizeCache[id to onlyUnwatched] ?: DownloadSizeEstimate())
            }
        }

    val context = LocalContext.current
    val availableBytes: Long? = remember {
        val storageLocations = context.getExternalFilesDirs(null).filterNotNull()
        val preferredIndex = resolveDownloadStorageIndex(context, downloadLocationPreference)
        val preferredDir = storageLocations.getOrNull(preferredIndex)
        if (preferredDir != null) {
            StatFs(preferredDir.path).availableBytes
        } else {
            storageLocations.maxOfOrNull { StatFs(it.path).availableBytes }
        }
    }
    val showLowSpaceWarning =
        showSizeEstimate &&
            !sizeLoading &&
            availableBytes != null &&
            totalEstimate.sizeBytes > availableBytes

    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.download_scope_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                if (showEpisodeOption) {
                    ToggleOptionRow(
                        checked = thisEpisodeOnly,
                        label = stringResource(CoreR.string.download_scope_episode),
                        icon = CoreR.drawable.ic_play,
                        onToggle = {
                            thisEpisodeOnly = true
                            selectedSeasonIds = initialSelection.seasonIds
                        },
                    )
                    if (hasExistingRule && thisEpisodeOnly) {
                        Text(
                            text = stringResource(CoreR.string.download_scope_existing_rule_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (seasons == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (allSeasonIds.isNotEmpty()) {
                        ToggleOptionRow(
                            checked = !thisEpisodeOnly && selectedSeasonIds.containsAll(allSeasonIds),
                            label = stringResource(CoreR.string.download_scope_show),
                            icon = CoreR.drawable.ic_tv,
                            onToggle = { checked ->
                                thisEpisodeOnly = false
                                selectedSeasonIds = if (checked) allSeasonIds else emptySet()
                            },
                        )
                    }
                    if (seasons.isNotEmpty()) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { seasonsExpanded = !seasonsExpanded }
                                    .padding(vertical = MaterialTheme.spacings.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        CoreR.string.download_scope_seasons_header,
                                        seasons.size,
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                painter =
                                    painterResource(
                                        if (seasonsExpanded) CoreR.drawable.ic_chevron_up
                                        else CoreR.drawable.ic_chevron_down
                                    ),
                                contentDescription = null,
                                tint = LocalContentColor.current,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        if (seasonsExpanded) {
                            seasons.forEach { season ->
                                ToggleOptionRow(
                                    checked = !thisEpisodeOnly && season.id in selectedSeasonIds,
                                    label =
                                        stringResource(
                                            CoreR.string.auto_download_rule_season,
                                            season.indexNumber,
                                        ),
                                    icon = CoreR.drawable.ic_library,
                                    onToggle = { checked ->
                                        thisEpisodeOnly = false
                                        selectedSeasonIds =
                                            if (checked) selectedSeasonIds + season.id
                                            else selectedSeasonIds - season.id
                                    },
                                )
                            }
                        }
                    }
                }
                if (showSizeEstimate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.download_scope_estimated_size,
                                    pluralStringResource(
                                        CoreR.plurals.download_scope_estimated_items,
                                        totalEstimate.itemCount,
                                        totalEstimate.itemCount,
                                    ),
                                    formatBinaryFileSize(totalEstimate.sizeBytes),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (sizeLoading) {
                            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    if (showLowSpaceWarning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_alert_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                            Text(
                                text =
                                    stringResource(
                                        CoreR.string.download_scope_low_space_warning,
                                        formatBinaryFileSize(totalEstimate.sizeBytes),
                                        formatBinaryFileSize(availableBytes),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                HorizontalDivider()
                ToggleOptionRow(
                    checked = alsoFollowNew,
                    label = stringResource(CoreR.string.download_scope_also_new),
                    icon = CoreR.drawable.ic_refresh_cw,
                    onToggle = { checked ->
                        thisEpisodeOnly = false
                        alsoFollowNew = checked
                    },
                )
                if (bulkModeSelected) {
                    ToggleOptionRow(
                        checked = onlyUnwatched,
                        label = stringResource(CoreR.string.download_scope_only_unwatched),
                        icon = CoreR.drawable.ic_eye_off,
                        onToggle = { onlyUnwatched = it },
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        // A separate confirmButton + dismissButton (the "normal" AlertDialog API) renders as two
        // slots inside Material3's internal button FlowRow, which stacks them onto their own rows
        // as soon as the three buttons' combined width doesn't fit - on a phone that's every time,
        // since Remove/Cancel/Download all carry an icon and a label. Rendering all three as one
        // slot sidesteps that: a single child can't be split across rows, so it's a plain
        // full-width Row instead - Remove (icon-only, so it stays small) on one side, Cancel and
        // Download on the other, matching the FlowRow's fallback grouping.
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canDelete && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_trash),
                            contentDescription = stringResource(CoreR.string.download_scope_remove),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_x),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                        Text(text = stringResource(CoreR.string.cancel))
                    }
                    TextButton(
                        enabled = canConfirm,
                        onClick = {
                            onConfirm(
                                DownloadSelection(
                                    thisEpisodeOnly = thisEpisodeOnly,
                                    seasonIds = selectedSeasonIds,
                                    // Only one toggle now - "keep this show up to date" covers
                                    // both new episodes in the seasons picked above and brand new
                                    // seasons alike.
                                    alsoFutureSeasons = alsoFollowNew,
                                ),
                                alsoFollowNew,
                                onlyUnwatched,
                            )
                        },
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                        Text(text = stringResource(CoreR.string.download))
                    }
                }
            }
        },
    )
}

@Composable
internal fun ToggleOptionRow(
    checked: Boolean,
    label: String,
    onToggle: (Boolean) -> Unit,
    icon: Int? = null,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onToggle(!checked) }
                .padding(vertical = MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
@Preview
private fun DownloadScopeDialogPreview() {
    FindroidTheme {
        DownloadScopeDialog(
            seasons = emptyList(),
            showEpisodeOption = true,
            onConfirm = { _, _, _ -> },
            onDismiss = {},
        )
    }
}
