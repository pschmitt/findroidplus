package dev.jdtech.jellyfin.presentation.settings.homelayout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.homelayout.HomeLayoutRow
import dev.jdtech.jellyfin.presentation.film.components.SectionServiceIcons
import dev.jdtech.jellyfin.film.presentation.homelayout.HomeLayoutSettingsAction
import dev.jdtech.jellyfin.film.presentation.homelayout.HomeLayoutSettingsState
import dev.jdtech.jellyfin.film.presentation.homelayout.HomeLayoutSettingsViewModel
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.components.TopBarTitle
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR

@Composable
fun HomeLayoutSettingsScreen(
    navigateBack: () -> Unit,
    viewModel: HomeLayoutSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.load() }

    HomeLayoutSettingsScreenLayout(
        state = state,
        onAction = viewModel::onAction,
        navigateBack = navigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeLayoutSettingsScreenLayout(
    state: HomeLayoutSettingsState,
    onAction: (HomeLayoutSettingsAction) -> Unit,
    navigateBack: () -> Unit,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    TopBarTitle(
                        text = stringResource(SettingsR.string.settings_category_home_layout),
                        iconRes = CoreR.drawable.ic_arrow_down_up,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                            contentDescription = stringResource(CoreR.string.home_layout_reset),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(state.rows, key = { it.key }) { row ->
                val index = state.rows.indexOf(row)
                HomeLayoutRowItem(
                    row = row,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.rows.lastIndex,
                    onMoveUp = { onAction(HomeLayoutSettingsAction.OnMoveUp(index)) },
                    onMoveDown = { onAction(HomeLayoutSettingsAction.OnMoveDown(index)) },
                    trailingIcon = CoreR.drawable.ic_eye_off,
                    trailingIconDescription = stringResource(CoreR.string.home_layout_hide_section),
                    onTrailingClick = { onAction(HomeLayoutSettingsAction.OnHide(row.key)) },
                )
            }
            if (state.hiddenRows.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(CoreR.string.home_layout_hidden_sections_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                horizontal = MaterialTheme.spacings.default,
                                vertical = MaterialTheme.spacings.small,
                            ),
                    )
                }
                items(state.hiddenRows, key = { "hidden:${it.key}" }) { row ->
                    HomeLayoutRowItem(
                        row = row,
                        canMoveUp = false,
                        canMoveDown = false,
                        onMoveUp = {},
                        onMoveDown = {},
                        showMoveButtons = false,
                        trailingIcon = CoreR.drawable.ic_plus,
                        trailingIconDescription = stringResource(CoreR.string.home_layout_show_section),
                        onTrailingClick = { onAction(HomeLayoutSettingsAction.OnRestore(row.key)) },
                        titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(text = stringResource(CoreR.string.home_layout_reset_confirm_title)) },
            text = { Text(text = stringResource(CoreR.string.home_layout_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onAction(HomeLayoutSettingsAction.OnResetLayout)
                    }
                ) {
                    Text(text = stringResource(CoreR.string.home_layout_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun HomeLayoutRowItem(
    row: HomeLayoutRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    trailingIcon: Int,
    trailingIconDescription: String,
    onTrailingClick: () -> Unit,
    modifier: Modifier = Modifier,
    showMoveButtons: Boolean = true,
    titleColor: Color = Color.Unspecified,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacings.default, vertical = MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionServiceIcons(row.serviceIcons)
            Text(text = row.label.asString(), style = MaterialTheme.typography.bodyLarge, color = titleColor)
        }
        Row {
            if (showMoveButtons) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_chevron_up),
                        contentDescription = stringResource(CoreR.string.move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_chevron_down),
                        contentDescription = stringResource(CoreR.string.move_down),
                    )
                }
            }
            IconButton(onClick = onTrailingClick) {
                Icon(painter = painterResource(trailingIcon), contentDescription = trailingIconDescription)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeLayoutSettingsScreenLayoutPreview() {
    FindroidTheme {
        HomeLayoutSettingsScreenLayout(
            state =
                HomeLayoutSettingsState(
                    rows =
                        listOf(
                            HomeLayoutRow("suggestions", UiText.DynamicString("Suggestions")),
                            HomeLayoutRow(
                                "continue_watching",
                                UiText.DynamicString("Continue Watching"),
                            ),
                            HomeLayoutRow("next_up", UiText.DynamicString("Next Up")),
                        )
                ),
            onAction = {},
            navigateBack = {},
        )
    }
}
