package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * Sonarr's manual/interactive search - lists candidate releases (from
 * [dev.jdtech.jellyfin.repository.SonarrSearchRepository.getReleases]) for the user to grab one
 * themselves, as opposed to [PvrSearchButton]'s automatic-search option where Sonarr picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasePickerSheet(
    state: ReleasePickerState,
    onGrab: (PvrRelease) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Text(
            text = stringResource(CoreR.string.release_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacings.medium,
                    vertical = MaterialTheme.spacings.medium,
                ),
        )
        HorizontalDivider()
        when {
            state.isLoading ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            state.releases.isEmpty() ->
                Text(
                    text = stringResource(CoreR.string.release_picker_empty),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(MaterialTheme.spacings.medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else ->
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacings.medium)
                ) {
                    itemsIndexed(items = state.releases, key = { _, release -> release.guid }) { index, release ->
                        ReleaseRow(release = release, onGrab = { onGrab(release) })
                        if (index != state.releases.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
        }
    }
}

@Composable
private fun ReleaseRow(release: PvrRelease, onGrab: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onGrab)
                .padding(vertical = MaterialTheme.spacings.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        ) {
            Text(
                text = release.title ?: release.guid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            val seedersText = stringResource(CoreR.string.release_picker_seeders, release.seeders ?: 0)
            val details =
                listOfNotNull(
                        release.quality?.quality?.name,
                        Formatter.formatShortFileSize(context, release.size),
                        release.seeders?.let { seedersText },
                        release.indexer,
                    )
                    .joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (release.rejected) {
                Text(
                    text =
                        stringResource(
                            CoreR.string.release_picker_rejected,
                            release.rejections.joinToString(", "),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        IconButton(onClick = onGrab) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_download),
                contentDescription = stringResource(CoreR.string.release_picker_grab),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun ReleasePickerSheetLoadingPreview() {
    FindroidTheme { ReleasePickerSheet(state = ReleasePickerState(isLoading = true), onGrab = {}, onDismissRequest = {}) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun ReleasePickerSheetContentPreview() {
    FindroidTheme {
        ReleasePickerSheet(
            state =
                ReleasePickerState(
                    isLoading = false,
                    releases =
                        listOf(
                            PvrRelease(
                                guid = "1",
                                indexerId = 1,
                                indexer = "Indexer A",
                                title = "Show.S01E01.1080p.WEB-DL",
                                size = 1_500_000_000L,
                                seeders = 42,
                            ),
                            PvrRelease(
                                guid = "2",
                                indexerId = 2,
                                indexer = "Indexer B",
                                title = "Show.S01E01.720p.WEB-DL",
                                size = 800_000_000L,
                                rejected = true,
                                rejections = listOf("Not a preferred word"),
                            ),
                        )
                ),
            onGrab = {},
            onDismissRequest = {},
        )
    }
}
