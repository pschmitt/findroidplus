package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import dev.jdtech.jellyfin.film.presentation.autodownload.AutoDownloadRuleUiModel
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.UUID

@Composable
fun AutoDownloadRulesScreen(
    navigateBack: () -> Unit,
    viewModel: AutoDownloadRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadRules() }

    AutoDownloadRulesScreenLayout(
        state = state,
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
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.rules.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(CoreR.string.no_auto_download_rules),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = state.rules, key = { it.rule.id }) { rule ->
                    AutoDownloadRuleRow(rule = rule, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun AutoDownloadRuleRow(
    rule: AutoDownloadRuleUiModel,
    onAction: (AutoDownloadRulesAction) -> Unit,
) {
    Column {
        ListItem(
            headlineContent = { Text(text = rule.showName) },
            supportingContent = {
                Text(
                    text =
                        rule.seasonLabel?.asString()
                            ?: stringResource(CoreR.string.auto_download_rule_show)
                )
            },
            trailingContent = {
                Row {
                    Switch(
                        checked = rule.rule.enabled,
                        onCheckedChange = { enabled ->
                            onAction(AutoDownloadRulesAction.ToggleRule(rule.rule.id, enabled))
                        },
                    )
                    IconButton(
                        onClick = { onAction(AutoDownloadRulesAction.DeleteRule(rule.rule.id)) }
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_trash),
                            contentDescription = null,
                        )
                    }
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(CoreR.string.auto_download_only_new_episodes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = rule.rule.onlyNewEpisodes,
                onCheckedChange = { onlyNewEpisodes ->
                    onAction(
                        AutoDownloadRulesAction.ToggleRuleOnlyNewEpisodes(
                            rule.rule.id,
                            onlyNewEpisodes,
                        )
                    )
                },
            )
        }
        HorizontalDivider()
    }
}

@PreviewScreenSizes
@Composable
private fun AutoDownloadRulesScreenLayoutPreview() {
    FindroidTheme {
        AutoDownloadRulesScreenLayout(
            state =
                AutoDownloadRulesState(
                    rules =
                        listOf(
                            AutoDownloadRuleUiModel(
                                rule =
                                    AutoDownloadRuleDto(
                                        id = 1,
                                        serverId = "server",
                                        userId = UUID.randomUUID(),
                                        seriesId = UUID.randomUUID(),
                                        seasonId = null,
                                        enabled = true,
                                        createdAt = 0L,
                                    ),
                                showName = "Example Show",
                                seasonLabel = null,
                            )
                        )
                ),
            onAction = {},
        )
    }
}
