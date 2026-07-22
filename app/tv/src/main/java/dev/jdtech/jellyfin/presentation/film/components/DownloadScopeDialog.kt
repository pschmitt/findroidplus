package dev.jdtech.jellyfin.presentation.film.components

import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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
 * TV equivalent of phone's `DownloadScopeDialog` for Show/Season screens. There's no per-episode
 * detail screen on TV (episodes navigate straight to the player), so unlike phone this dialog
 * only ever offers season/show/future-seasons scope - no "this episode only" option.
 */
@Composable
fun DownloadScopeDialog(
    seasons: List<FindroidSeason>?,
    initialSelection: DownloadSelection = DownloadSelection(),
    initialAlsoFollowNew: Boolean = false,
    initialOnlyUnwatched: Boolean = false,
    canDelete: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onConfirm: (selection: DownloadSelection, alsoFollowNew: Boolean, onlyUnwatched: Boolean) -> Unit,
    onDismiss: () -> Unit,
    getSeasonSize: (suspend (seasonId: UUID, onlyUnwatched: Boolean) -> DownloadSizeEstimate)? =
        null,
    downloadLocationPreference: String = "ask",
) {
    var selectedSeasonIds by remember { mutableStateOf(initialSelection.seasonIds) }
    // Either kind of previously-saved rule (future-seasons-only, or per-season-follow) means the
    // show already has ongoing tracking from the user's point of view - there's only one toggle
    // for that now, so either signal being true should show it as on.
    var alsoFollowNew by
        remember { mutableStateOf(initialAlsoFollowNew || initialSelection.alsoFutureSeasons) }
    var onlyUnwatched by remember { mutableStateOf(initialOnlyUnwatched) }
    var seasonsExpanded by remember { mutableStateOf(false) }

    val bulkModeSelected = selectedSeasonIds.isNotEmpty() || alsoFollowNew
    val allSeasonIds = seasons?.map { it.id }?.toSet().orEmpty()

    // Cached per (season id, onlyUnwatched) so toggling a season off and back on - or flipping
    // "only unwatched" back to what it was - doesn't re-hit the network; only ever grows for the
    // lifetime of this dialog.
    val seasonSizeCache = remember { mutableStateMapOf<Pair<UUID, Boolean>, DownloadSizeEstimate>() }
    LaunchedEffect(selectedSeasonIds, onlyUnwatched) {
        if (getSeasonSize == null) return@LaunchedEffect
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
    val showSizeEstimate = getSeasonSize != null && selectedSeasonIds.isNotEmpty()
    val sizeLoading = selectedSeasonIds.any { (it to onlyUnwatched) !in seasonSizeCache }
    val totalEstimate =
        selectedSeasonIds.fold(DownloadSizeEstimate()) { acc, id ->
            acc + (seasonSizeCache[id to onlyUnwatched] ?: DownloadSizeEstimate())
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                Text(
                    text = stringResource(CoreR.string.download_scope_title),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (seasons == null) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.large),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        if (allSeasonIds.isNotEmpty()) {
                            item {
                                ScopeToggleRow(
                                    checked = selectedSeasonIds.containsAll(allSeasonIds),
                                    label = stringResource(CoreR.string.download_scope_show),
                                    icon = CoreR.drawable.ic_tv,
                                    onToggle = { checked ->
                                        selectedSeasonIds = if (checked) allSeasonIds else emptySet()
                                    },
                                )
                            }
                        }
                        if (seasons.isNotEmpty()) {
                            item {
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .clickable { seasonsExpanded = !seasonsExpanded }
                                            .padding(
                                                horizontal = MaterialTheme.spacings.default,
                                                vertical = MaterialTheme.spacings.small,
                                            ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text =
                                            stringResource(
                                                CoreR.string.download_scope_seasons_header,
                                                seasons.size,
                                            ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        painter =
                                            painterResource(
                                                if (seasonsExpanded) CoreR.drawable.ic_chevron_up
                                                else CoreR.drawable.ic_chevron_down
                                            ),
                                        contentDescription = null,
                                    )
                                }
                            }
                            if (seasonsExpanded) {
                                items(seasons) { season ->
                                    ScopeToggleRow(
                                        checked = season.id in selectedSeasonIds,
                                        label =
                                            stringResource(
                                                CoreR.string.auto_download_rule_season,
                                                season.indexNumber,
                                            ),
                                        icon = CoreR.drawable.ic_library,
                                        onToggle = { checked ->
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
                        item {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            horizontal = MaterialTheme.spacings.default,
                                            vertical = MaterialTheme.spacings.small,
                                        ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                                        )
                                )
                                if (sizeLoading) {
                                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                        if (showLowSpaceWarning) {
                            item {
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(
                                                horizontal = MaterialTheme.spacings.default,
                                                vertical = MaterialTheme.spacings.small,
                                            ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_alert_circle),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                                    Text(
                                        text =
                                            stringResource(
                                                CoreR.string.download_scope_low_space_warning,
                                                formatBinaryFileSize(totalEstimate.sizeBytes),
                                                formatBinaryFileSize(availableBytes),
                                            ),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    item { HorizontalDivider() }
                    item {
                        ScopeToggleRow(
                            checked = alsoFollowNew,
                            label = stringResource(CoreR.string.download_scope_also_new),
                            icon = CoreR.drawable.ic_refresh_cw,
                            onToggle = { alsoFollowNew = it },
                        )
                    }
                    if (bulkModeSelected) {
                        item {
                            ScopeToggleRow(
                                checked = onlyUnwatched,
                                label = stringResource(CoreR.string.download_scope_only_unwatched),
                                icon = CoreR.drawable.ic_eye_off,
                                onToggle = { onlyUnwatched = it },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.default),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                ) {
                    if (canDelete && onDelete != null) {
                        Button(onClick = onDelete) {
                            Icon(painter = painterResource(CoreR.drawable.ic_trash), contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(CoreR.string.download_scope_remove))
                        }
                    }
                    Button(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
                    Button(
                        enabled = bulkModeSelected,
                        onClick = { onConfirm(DownloadSelection(seasonIds = selectedSeasonIds, alsoFutureSeasons = alsoFollowNew), alsoFollowNew, onlyUnwatched) },
                    ) {
                        Icon(painter = painterResource(CoreR.drawable.ic_download), contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(CoreR.string.download))
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
            }
        }
    }
}

@Composable
private fun ScopeToggleRow(
    checked: Boolean,
    label: String,
    onToggle: (Boolean) -> Unit,
    icon: Int? = null,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onToggle(!checked) }
                .padding(
                    horizontal = MaterialTheme.spacings.default,
                    vertical = MaterialTheme.spacings.small,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = checked, onClick = { onToggle(!checked) })
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null)
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        }
        Text(text = label, modifier = Modifier.weight(1f))
    }
}

@Composable
@Preview
private fun DownloadScopeDialogPreview() {
    FindroidTheme { DownloadScopeDialog(seasons = emptyList(), onConfirm = { _, _, _ -> }, onDismiss = {}) }
}
