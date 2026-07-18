package dev.jdtech.jellyfin.presentation.film.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.downloads.ManualImportSheetState
import dev.jdtech.jellyfin.models.ManualImportCandidate
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatBinaryFileSize

/**
 * Reviews the individual files inside a download Sonarr/Radarr couldn't fully auto-import (e.g.
 * `trackedDownloadState=importBlocked`), each with the service's own guessed episode/quality
 * mapping and rejection reasons, letting the user pick which to actually import. Mirrors
 * [ReleasePickerSheet]'s structure (a state-driven [ModalBottomSheet] over a candidate list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualImportSheet(
    state: ManualImportSheetState,
    onToggleSelection: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacings.medium,
                    vertical = MaterialTheme.spacings.medium,
                )
        ) {
            Text(text = stringResource(CoreR.string.manual_import_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider()
        when {
            state.isLoading ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            state.error != null && state.candidates.isEmpty() ->
                Text(
                    text = stringResource(CoreR.string.manual_import_loading_failed, state.error.orEmpty()),
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                    color = MaterialTheme.colorScheme.error,
                )
            state.candidates.isEmpty() ->
                Text(
                    text = stringResource(CoreR.string.manual_import_empty),
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else ->
                LazyColumn(contentPadding = PaddingValues(horizontal = MaterialTheme.spacings.medium)) {
                    itemsIndexed(items = state.candidates, key = { _, candidate -> candidate.id }) { index, candidate ->
                        ManualImportRow(
                            candidate = candidate,
                            checked = candidate.id in state.selectedIds,
                            onToggle = { onToggleSelection(candidate.id) },
                        )
                        if (index != state.candidates.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
        }
        if (state.candidates.isNotEmpty()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(MaterialTheme.spacings.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val errorMessage = state.error
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Button(onClick = onConfirm, enabled = state.selectedIds.isNotEmpty() && !state.isImporting) {
                    if (state.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                    } else {
                        Text(text = stringResource(CoreR.string.manual_import_confirm, state.selectedIds.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualImportRow(candidate: ManualImportCandidate, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = candidate.canImport, onClick = onToggle)
                .padding(vertical = MaterialTheme.spacings.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        ) {
            Text(
                text = candidate.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            val details =
                listOfNotNull(candidate.episodeLabel, candidate.qualityName, formatBinaryFileSize(candidate.sizeBytes))
                    .joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (candidate.rejections.isNotEmpty()) {
                Text(
                    text = candidate.rejections.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!candidate.canImport) {
                Text(
                    text = stringResource(CoreR.string.manual_import_cannot_import),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        Checkbox(checked = checked, enabled = candidate.canImport, onCheckedChange = { onToggle() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun ManualImportSheetLoadingPreview() {
    FindroidTheme {
        ManualImportSheet(
            state =
                ManualImportSheetState(
                    source = PvrSource.SONARR,
                    downloadId = "abc",
                    title = "Some Show - Season 1",
                    isLoading = true,
                ),
            onToggleSelection = {},
            onConfirm = {},
            onDismissRequest = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun ManualImportSheetContentPreview() {
    FindroidTheme {
        ManualImportSheet(
            state =
                ManualImportSheetState(
                    source = PvrSource.SONARR,
                    downloadId = "abc",
                    title = "Some Show - Season 1",
                    isLoading = false,
                    candidates =
                        listOf(
                            ManualImportCandidate(
                                id = 1,
                                name = "S01E06-A Day Off in Roa.mkv",
                                sizeBytes = 914_265_058L,
                                qualityName = "Bluray-1080p",
                                episodeLabel = "S1E6",
                                canImport = true,
                                rejections = listOf("Episode file already imported"),
                            ),
                            ManualImportCandidate(
                                id = 2,
                                name = "Unrecognized.File.mkv",
                                sizeBytes = 500_000_000L,
                                qualityName = null,
                                episodeLabel = null,
                                canImport = false,
                                rejections = emptyList(),
                            ),
                        ),
                    selectedIds = setOf(1),
                ),
            onToggleSelection = {},
            onConfirm = {},
            onDismissRequest = {},
        )
    }
}
