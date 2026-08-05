package dev.pschmitt.jellyfin.presentation.film.components

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.film.presentation.downloads.ManualImportEntry
import dev.pschmitt.jellyfin.film.presentation.downloads.ManualImportSheetState
import dev.pschmitt.jellyfin.models.ManualImportCandidate
import dev.pschmitt.jellyfin.models.PvrSource
import dev.pschmitt.jellyfin.presentation.components.MessageDetailsDialog
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize

/**
 * Reviews the individual files inside a download Sonarr/Radarr couldn't fully auto-import (e.g.
 * `trackedDownloadState=importBlocked`), each with the service's own guessed episode/quality
 * mapping and rejection reasons, letting the user pick which to actually import. Mirrors
 * [ReleasePickerSheet]'s structure (a state-driven [ModalBottomSheet] over a candidate list).
 *
 * [state.entries][ManualImportSheetState.entries] normally holds exactly one entry; when
 * Sonarr/Radarr have two duplicate queue rows for the same release still awaiting import (see
 * `PvrQueueEntry.duplicateGroupKey`), it holds one per duplicate and a small picker lets the user
 * choose which one to actually review/import from - the other(s) are removed automatically once the
 * chosen one is confirmed (see `ManualImportController.confirm`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualImportSheet(
    state: ManualImportSheetState,
    onSelectEntry: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onConfirm: () -> Unit,
    onReject: (removeFromClient: Boolean, blocklist: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    var showRejectConfirm by remember { mutableStateOf(false) }
    var showErrorDetails by remember { mutableStateOf(false) }

    val selected = state.entries.getOrNull(state.selectedEntryIndex) ?: state.entries.first()

    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        // The candidate list is wrapped in its own weighted, non-filling Box so it only claims
        // space up to what's left after the header/footer - without this, an unbounded LazyColumn
        // as a plain Column child greedily fills all remaining sheet height, pushing the "Import
        // selected" button below the visible viewport (the sheet itself doesn't scroll as a whole).
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = MaterialTheme.spacings.medium,
                        vertical = MaterialTheme.spacings.medium,
                    )
            ) {
                Text(
                    text = stringResource(CoreR.string.manual_import_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.entries.size > 1) {
                HorizontalDivider()
                ManualImportEntryPicker(
                    entries = state.entries,
                    selectedIndex = state.selectedEntryIndex,
                    onSelect = onSelectEntry,
                )
            }
            HorizontalDivider()
            Box(modifier = Modifier.weight(1f, fill = false)) {
                when {
                    selected.isLoading ->
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    selected.error != null && selected.candidates.isEmpty() ->
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.manual_import_loading_failed,
                                    selected.error.orEmpty(),
                                ),
                            modifier =
                                Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                            color = MaterialTheme.colorScheme.error,
                        )
                    selected.candidates.isEmpty() ->
                        Text(
                            text = stringResource(CoreR.string.manual_import_empty),
                            modifier =
                                Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        LazyColumn(
                            contentPadding =
                                PaddingValues(horizontal = MaterialTheme.spacings.medium)
                        ) {
                            itemsIndexed(
                                items = selected.candidates,
                                key = { _, candidate -> candidate.id },
                            ) { index, candidate ->
                                ManualImportRow(
                                    candidate = candidate,
                                    checked = candidate.id in selected.selectedIds,
                                    onToggle = { onToggleSelection(candidate.id) },
                                )
                                if (index != selected.candidates.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
                }
            }
            if (selected.candidates.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showRejectConfirm = true },
                        enabled = !state.isImporting && !state.isRejecting,
                    ) {
                        if (state.isRejecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                            Text(
                                text = stringResource(CoreR.string.manual_import_reject),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    val errorMessage = state.error
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { showErrorDetails = true },
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = onConfirm,
                        enabled =
                            selected.selectedIds.isNotEmpty() &&
                                !state.isImporting &&
                                !state.isRejecting,
                    ) {
                        if (state.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp)
                            )
                        } else {
                            Text(
                                text =
                                    stringResource(
                                        CoreR.string.manual_import_confirm,
                                        selected.selectedIds.size,
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRejectConfirm) {
        RejectReleaseDialog(
            title = state.title,
            onConfirm = { removeFromClient, blocklist ->
                showRejectConfirm = false
                onReject(removeFromClient, blocklist)
            },
            onDismiss = { showRejectConfirm = false },
        )
    }

    val detailsMessage = state.error
    if (showErrorDetails && detailsMessage != null) {
        MessageDetailsDialog(
            title = stringResource(CoreR.string.error_details_title),
            message = detailsMessage,
            onDismissRequest = { showErrorDetails = false },
        )
    }
}

/**
 * One row per duplicate queue entry, letting the user choose which release to actually review and
 * import from - shown only when [ManualImportSheetState.entries] has more than one. Labeled by
 * summed candidate size once loaded (the most useful thing to compare two competing grabs by),
 * falling back to a plain ordinal while still loading.
 */
@Composable
private fun ManualImportEntryPicker(
    entries: List<ManualImportEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = MaterialTheme.spacings.small)) {
        entries.forEachIndexed { index, entry ->
            val label =
                if (entry.isLoading) {
                    stringResource(CoreR.string.manual_import_release_ordinal, index + 1)
                } else {
                    stringResource(
                        CoreR.string.manual_import_release_size,
                        index + 1,
                        formatBinaryFileSize(entry.candidates.sumOf { it.sizeBytes }),
                    )
                }
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(
                            horizontal = MaterialTheme.spacings.medium,
                            vertical = MaterialTheme.spacings.small,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) })
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Confirms discarding the whole release the manual-import sheet is reviewing - e.g. one
 * Sonarr/Radarr itself flagged as suspicious (a disguised executable, wrong language, ...), or
 * where none of the files are worth keeping. Mirrors the queue row's own remove confirmation (same
 * flags, same defaults), just reachable from within the review sheet instead of requiring the user
 * to back out to the queue row's trash icon first.
 */
@Composable
private fun RejectReleaseDialog(
    title: String,
    onConfirm: (removeFromClient: Boolean, blocklist: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var removeFromClient by remember { mutableStateOf(true) }
    var blocklist by remember { mutableStateOf(true) }

    AlertDialog(
        // Not AlertDialog's own `icon` slot - Material3 always renders that centered *above* the
        // title, not inline with it. Building the title as an icon+text Row instead keeps them on
        // the same line, matching DeleteSelectedDownloadsDialog/RemovePvrQueueItemDialog.
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = stringResource(CoreR.string.pvr_queue_remove_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                Text(text = stringResource(CoreR.string.pvr_queue_remove_message, title))
                ToggleOptionRow(
                    checked = removeFromClient,
                    label = stringResource(CoreR.string.pvr_queue_remove_from_client),
                    onToggle = { removeFromClient = it },
                )
                ToggleOptionRow(
                    checked = blocklist,
                    label = stringResource(CoreR.string.pvr_queue_blocklist),
                    onToggle = { blocklist = it },
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(removeFromClient, blocklist) }) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(painter = painterResource(CoreR.drawable.ic_x), contentDescription = null)
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = stringResource(CoreR.string.cancel))
            }
        },
    )
}

@Composable
private fun ManualImportRow(
    candidate: ManualImportCandidate,
    checked: Boolean,
    onToggle: () -> Unit,
) {
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
                listOfNotNull(
                        candidate.episodeLabel,
                        candidate.qualityName,
                        formatBinaryFileSize(candidate.sizeBytes),
                    )
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
                    title = "Some Show - Season 1",
                    entries =
                        listOf(
                            ManualImportEntry(
                                source = PvrSource.SONARR,
                                downloadId = "abc",
                                queueItemId = 1,
                                isLoading = true,
                            )
                        ),
                ),
            onSelectEntry = {},
            onToggleSelection = {},
            onConfirm = {},
            onReject = { _, _ -> },
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
                    title = "Some Show - Season 1",
                    entries =
                        listOf(
                            ManualImportEntry(
                                source = PvrSource.SONARR,
                                downloadId = "abc",
                                queueItemId = 1,
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
                            ManualImportEntry(
                                source = PvrSource.SONARR,
                                downloadId = "def",
                                queueItemId = 2,
                                isLoading = false,
                                candidates =
                                    listOf(
                                        ManualImportCandidate(
                                            id = 1,
                                            name = "S01E06-A.Day.Off.mkv",
                                            sizeBytes = 902_000_000L,
                                            qualityName = "WEBDL-1080p",
                                            episodeLabel = "S1E6",
                                            canImport = true,
                                            rejections = emptyList(),
                                        )
                                    ),
                                selectedIds = setOf(1),
                            ),
                        ),
                ),
            onSelectEntry = {},
            onToggleSelection = {},
            onConfirm = {},
            onReject = { _, _ -> },
            onDismissRequest = {},
        )
    }
}
