package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.autodownload.AutoDownloadRulesAction
import dev.jdtech.jellyfin.film.presentation.autodownload.AutoDownloadRulesState
import dev.jdtech.jellyfin.film.presentation.autodownload.AutoDownloadRulesViewModel
import dev.jdtech.jellyfin.film.presentation.autodownload.AutoDownloadShowRuleUiModel
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.film.components.ClearDownloadsDialog
import dev.jdtech.jellyfin.presentation.film.components.LocalStorageIndicator
import dev.jdtech.jellyfin.presentation.film.components.ToggleOptionRow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

@Composable
fun AutoDownloadRulesScreen(
    navigateBack: () -> Unit,
    navigateToDownloadSettings: () -> Unit,
    viewModel: AutoDownloadRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadRules() }

    AutoDownloadRulesScreenLayout(
        state = state,
        onNavigateToDownloadSettings = navigateToDownloadSettings,
        getSeasons = viewModel::getSeasons,
        onAction = { action ->
            when (action) {
                is AutoDownloadRulesAction.OnBackClick -> navigateBack()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoDownloadRulesScreenLayout(
    state: AutoDownloadRulesState,
    onAction: (AutoDownloadRulesAction) -> Unit,
    onNavigateToDownloadSettings: () -> Unit = {},
    getSeasons: suspend (UUID) -> List<FindroidSeason> = { emptyList() },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.auto_download_rules)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(AutoDownloadRulesAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDownloadSettings) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_settings),
                            contentDescription = stringResource(CoreR.string.download_settings),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.shows.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(CoreR.string.no_auto_download_rules),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = state.shows, key = { it.seriesId }) { show ->
                    AutoDownloadShowRuleRow(show = show, onAction = onAction, getSeasons = getSeasons)
                }
            }
        }
    }
}

@Composable
private fun AutoDownloadShowRuleRow(
    show: AutoDownloadShowRuleUiModel,
    onAction: (AutoDownloadRulesAction) -> Unit,
    getSeasons: suspend (UUID) -> List<FindroidSeason>,
) {
    val context = LocalContext.current
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var editDialogOpen by remember { mutableStateOf(false) }

    Column {
        ListItem(
            modifier = Modifier.clickable { editDialogOpen = true },
            leadingContent = {
                Icon(painter = painterResource(CoreR.drawable.ic_tv), contentDescription = null)
            },
            headlineContent = { Text(text = show.showName) },
            supportingContent = {
                Column {
                    Text(text = show.scopeLabel.asString())
                    if (show.seasonIds.isNotEmpty() && show.alsoFutureSeasons) {
                        Text(
                            text = stringResource(CoreR.string.download_scope_future_seasons),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val samplePath = show.downloadedSamplePath
                    if (show.downloadedSizeBytes > 0 && samplePath != null) {
                        LocalStorageIndicator(
                            path = samplePath,
                            sizeBytes = show.downloadedSizeBytes,
                        )
                    }
                }
            },
            trailingContent = {
                Row {
                    Switch(
                        checked = show.enabled,
                        onCheckedChange = { enabled ->
                            onAction(
                                AutoDownloadRulesAction.ToggleShowRule(show.seriesId, enabled)
                            )
                            Toast.makeText(
                                    context,
                                    if (enabled) {
                                        CoreR.string.auto_download_rule_enabled_toast
                                    } else {
                                        CoreR.string.auto_download_rule_disabled_toast
                                    },
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        },
                    )
                    IconButton(onClick = { deleteDialogOpen = true }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_trash),
                            contentDescription = null,
                        )
                    }
                }
            },
        )
        HorizontalDivider()
    }

    if (editDialogOpen) {
        EditRuleDialog(
            show = show,
            getSeasons = getSeasons,
            onConfirm = { seasonIds, alsoFutureSeasons, onlyNewEpisodes, onlyUnwatched ->
                onAction(
                    AutoDownloadRulesAction.UpdateShowRule(
                        show.seriesId,
                        seasonIds,
                        alsoFutureSeasons,
                        onlyNewEpisodes,
                        onlyUnwatched,
                    )
                )
                editDialogOpen = false
            },
            onDismiss = { editDialogOpen = false },
        )
    }

    if (deleteDialogOpen) {
        ClearDownloadsDialog(
            title = stringResource(CoreR.string.delete_auto_download_rule),
            message = stringResource(CoreR.string.delete_auto_download_rule_message),
            name = show.showName,
            checkboxLabel = stringResource(CoreR.string.also_delete_downloaded_episodes),
            checkboxSummary = stringResource(CoreR.string.also_delete_downloaded_episodes_summary),
            checkboxDefault = false,
            onConfirm = { alsoDeleteDownloads ->
                onAction(
                    AutoDownloadRulesAction.DeleteShowRule(show.seriesId, alsoDeleteDownloads)
                )
                Toast.makeText(
                        context,
                        CoreR.string.auto_download_rule_deleted_toast,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                deleteDialogOpen = false
            },
            onDismiss = { deleteDialogOpen = false },
        )
    }
}

@Composable
private fun EditRuleDialog(
    show: AutoDownloadShowRuleUiModel,
    getSeasons: suspend (UUID) -> List<FindroidSeason>,
    onConfirm:
        (
            seasonIds: Set<UUID>,
            alsoFutureSeasons: Boolean,
            onlyNewEpisodes: Boolean,
            onlyUnwatched: Boolean,
        ) -> Unit,
    onDismiss: () -> Unit,
) {
    var seasons by remember { mutableStateOf<List<FindroidSeason>?>(null) }
    var selectedSeasonIds by remember { mutableStateOf(show.seasonIds) }
    var alsoFutureSeasons by remember { mutableStateOf(show.alsoFutureSeasons) }
    var onlyNewEpisodes by remember { mutableStateOf(show.onlyNewEpisodes) }
    var onlyUnwatched by remember { mutableStateOf(show.onlyUnwatched) }
    var seasonsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(show.seriesId) { seasons = getSeasons(show.seriesId) }

    val canConfirm = selectedSeasonIds.isNotEmpty() || alsoFutureSeasons

    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.edit_auto_download_rule)) },
        text = {
            val currentSeasons = seasons
            if (currentSeasons == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val allSeasonIds = currentSeasons.map { it.id }.toSet()
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                    if (allSeasonIds.isNotEmpty()) {
                        ToggleOptionRow(
                            checked = selectedSeasonIds.containsAll(allSeasonIds),
                            label = stringResource(CoreR.string.edit_rule_scope_entire_show),
                            icon = CoreR.drawable.ic_tv,
                            onToggle = { checked ->
                                selectedSeasonIds = if (checked) allSeasonIds else emptySet()
                            },
                        )
                    }
                    if (currentSeasons.isNotEmpty()) {
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
                                        currentSeasons.size,
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
                            currentSeasons.forEach { season ->
                                ToggleOptionRow(
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
                    HorizontalDivider()
                    ToggleOptionRow(
                        checked = alsoFutureSeasons,
                        label = stringResource(CoreR.string.download_scope_future_seasons),
                        icon = CoreR.drawable.ic_sparkles,
                        onToggle = { alsoFutureSeasons = it },
                    )
                    ToggleOptionRow(
                        checked = onlyUnwatched,
                        label = stringResource(CoreR.string.download_scope_only_unwatched),
                        icon = CoreR.drawable.ic_eye_off,
                        onToggle = { onlyUnwatched = it },
                    )
                    if (selectedSeasonIds.isNotEmpty()) {
                        ToggleOptionRow(
                            checked = onlyNewEpisodes,
                            label = stringResource(CoreR.string.auto_download_only_new_episodes),
                            icon = CoreR.drawable.ic_refresh_cw,
                            onToggle = { onlyNewEpisodes = it },
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = seasons != null && canConfirm,
                onClick = {
                    onConfirm(selectedSeasonIds, alsoFutureSeasons, onlyNewEpisodes, onlyUnwatched)
                },
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = stringResource(CoreR.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_x),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                Text(text = stringResource(CoreR.string.cancel))
            }
        },
    )
}

@PreviewScreenSizes
@Composable
private fun AutoDownloadRulesScreenLayoutPreview() {
    FindroidTheme {
        AutoDownloadRulesScreenLayout(
            state =
                AutoDownloadRulesState(
                    shows =
                        listOf(
                            AutoDownloadShowRuleUiModel(
                                seriesId = UUID.randomUUID(),
                                ruleIds = listOf(1L),
                                showName = "Example Show",
                                enabled = true,
                                seasonIds = emptySet(),
                                alsoFutureSeasons = true,
                                scopeLabel =
                                    UiText.StringResource(CoreR.string.auto_download_rule_future_seasons),
                                onlyNewEpisodes = false,
                                onlyUnwatched = false,
                            )
                        )
                ),
            onAction = {},
        )
    }
}
