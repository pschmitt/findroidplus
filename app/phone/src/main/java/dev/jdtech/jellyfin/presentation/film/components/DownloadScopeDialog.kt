package dev.jdtech.jellyfin.presentation.film.components

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

/**
 * Lets the user pick what to download: for an episode, either just that episode or a bulk
 * selection of seasons/whole show; for a season or show screen, only the bulk selection. Checking
 * "Entire show" or "This episode" clears the other choices since they're mutually exclusive with
 * picking individual seasons. [seasons] is null while still loading.
 */
@Composable
fun DownloadScopeDialog(
    seasons: List<FindroidSeason>?,
    showEpisodeOption: Boolean,
    defaultSeasonId: UUID? = null,
    canDelete: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onConfirm: (selection: DownloadSelection, alsoFollowNew: Boolean, onlyUnwatched: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var thisEpisodeOnly by remember { mutableStateOf(showEpisodeOption) }
    var entireShow by remember { mutableStateOf(false) }
    var futureSeasonsOnly by remember { mutableStateOf(false) }
    var selectedSeasonIds by remember {
        mutableStateOf(defaultSeasonId?.let { setOf(it) } ?: emptySet())
    }
    var alsoFollowNew by remember { mutableStateOf(false) }
    var onlyUnwatched by remember { mutableStateOf(false) }

    val bulkModeSelected = !thisEpisodeOnly && (entireShow || selectedSeasonIds.isNotEmpty())
    val canConfirm = thisEpisodeOnly || bulkModeSelected || futureSeasonsOnly

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
                            entireShow = false
                            futureSeasonsOnly = false
                            selectedSeasonIds = emptySet()
                        },
                    )
                }
                ToggleOptionRow(
                    checked = entireShow,
                    label = stringResource(CoreR.string.download_scope_show),
                    icon = CoreR.drawable.ic_tv,
                    onToggle = {
                        thisEpisodeOnly = false
                        entireShow = true
                        futureSeasonsOnly = false
                        selectedSeasonIds = emptySet()
                    },
                )
                ToggleOptionRow(
                    checked = futureSeasonsOnly,
                    label = stringResource(CoreR.string.download_scope_future_seasons),
                    icon = CoreR.drawable.ic_sparkles,
                    onToggle = {
                        thisEpisodeOnly = false
                        entireShow = false
                        futureSeasonsOnly = true
                        selectedSeasonIds = emptySet()
                    },
                )
                if (seasons == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    seasons.forEach { season ->
                        ToggleOptionRow(
                            checked =
                                !thisEpisodeOnly &&
                                    !entireShow &&
                                    !futureSeasonsOnly &&
                                    season.id in selectedSeasonIds,
                            label =
                                stringResource(CoreR.string.auto_download_rule_season, season.indexNumber),
                            icon = CoreR.drawable.ic_library,
                            onToggle = { checked ->
                                thisEpisodeOnly = false
                                entireShow = false
                                futureSeasonsOnly = false
                                selectedSeasonIds =
                                    if (checked) selectedSeasonIds + season.id
                                    else selectedSeasonIds - season.id
                            },
                        )
                    }
                }
                HorizontalDivider()
                if (bulkModeSelected || futureSeasonsOnly) {
                    ToggleOptionRow(
                        checked = onlyUnwatched,
                        label = stringResource(CoreR.string.download_scope_only_unwatched),
                        icon = CoreR.drawable.ic_eye_off,
                        onToggle = { onlyUnwatched = it },
                    )
                    ToggleOptionRow(
                        checked = alsoFollowNew || futureSeasonsOnly,
                        label = stringResource(CoreR.string.download_scope_also_new),
                        icon = CoreR.drawable.ic_refresh_cw,
                        onToggle = { if (!futureSeasonsOnly) alsoFollowNew = it },
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        DownloadSelection(
                            thisEpisodeOnly = thisEpisodeOnly,
                            entireShow = entireShow,
                            seasonIds = selectedSeasonIds,
                            futureSeasonsOnly = futureSeasonsOnly,
                        ),
                        alsoFollowNew || futureSeasonsOnly,
                        onlyUnwatched,
                    )
                },
            ) {
                Text(text = stringResource(CoreR.string.download))
            }
        },
        dismissButton = {
            Row {
                if (canDelete && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_trash),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                        Text(
                            text = stringResource(CoreR.string.download_scope_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
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
