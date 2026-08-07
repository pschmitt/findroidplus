package dev.pschmitt.jellyfin.presentation.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.setup.R as SetupR

/**
 * TV per-profile management screen - rename, set-as-main, delete, and per-service (Sonarr/
 * Radarr/Seerr) PVR overrides. Reached from [ProfilesScreen]'s "Manage" button, or from Settings >
 * Profiles > a profile. Drives the same [ProfileDetailViewModel]/[ProfileDetailState]/
 * [ProfileDetailAction] this app/tv module owns (mirrored from app/phone's equivalent - see
 * [ProfileDetailViewModel]'s doc for why), with a from-scratch `androidx.tv.material3` layout.
 */
@Composable
fun ProfileDetailScreen(
    profileId: String,
    navigateBack: () -> Unit,
    viewModel: ProfileDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(profileId) { viewModel.load(profileId) }

    LaunchedEffect(state.deleted) { if (state.deleted) navigateBack() }

    ProfileDetailScreenLayout(state = state, onAction = viewModel::onAction)
}

@Composable
private fun ProfileDetailScreenLayout(
    state: ProfileDetailState,
    onAction: (ProfileDetailAction) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = MaterialTheme.spacings.large,
                        vertical = MaterialTheme.spacings.default,
                    ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
        ) {
            ProfileHeaderCard(
                name = state.name,
                isMain = state.isMain,
                onRenameClick = { showRenameDialog = true },
                onSetAsMainClick = { onAction(ProfileDetailAction.OnSetAsMainClick) },
                onDeleteClick = { showDeleteDialog = true },
            )

            PvrServiceSection(
                nameRes = CoreR.string.integrations_sonarr,
                logoRes = CoreR.drawable.ic_sonarr,
                apiKeySettingsPath = "/settings/general",
                enabled = state.sonarr.enabled,
                baseUrl = state.sonarr.baseUrl,
                apiKey = state.sonarr.apiKey,
                httpHeaders = state.sonarr.httpHeaders,
                basicAuthUsername = state.sonarr.basicAuthUsername,
                basicAuthPassword = state.sonarr.basicAuthPassword,
                testState = state.sonarr.testState,
                showInheritToggle = !state.isMain,
                inheriting = !state.isMain && state.sonarr.inheriting,
                onInheritToggleChanged = {
                    onAction(ProfileDetailAction.OnToggleInherit(PvrService.SONARR, it))
                },
                onEnabledChanged = {
                    onAction(ProfileDetailAction.OnEnabledChanged(PvrService.SONARR, it))
                },
                onBaseUrlChanged = {
                    onAction(ProfileDetailAction.OnBaseUrlChanged(PvrService.SONARR, it))
                },
                onApiKeyChanged = {
                    onAction(ProfileDetailAction.OnApiKeyChanged(PvrService.SONARR, it))
                },
                onTestConnectionClick = {
                    onAction(ProfileDetailAction.OnTestConnectionClick(PvrService.SONARR))
                },
                onAdvancedSettingsChanged = { headers, username, password ->
                    onAction(
                        ProfileDetailAction.OnAdvancedSettingsChanged(
                            PvrService.SONARR,
                            headers,
                            username,
                            password,
                        )
                    )
                },
            )

            PvrServiceSection(
                nameRes = CoreR.string.integrations_radarr,
                logoRes = CoreR.drawable.ic_radarr,
                apiKeySettingsPath = "/settings/general",
                enabled = state.radarr.enabled,
                baseUrl = state.radarr.baseUrl,
                apiKey = state.radarr.apiKey,
                httpHeaders = state.radarr.httpHeaders,
                basicAuthUsername = state.radarr.basicAuthUsername,
                basicAuthPassword = state.radarr.basicAuthPassword,
                testState = state.radarr.testState,
                showInheritToggle = !state.isMain,
                inheriting = !state.isMain && state.radarr.inheriting,
                onInheritToggleChanged = {
                    onAction(ProfileDetailAction.OnToggleInherit(PvrService.RADARR, it))
                },
                onEnabledChanged = {
                    onAction(ProfileDetailAction.OnEnabledChanged(PvrService.RADARR, it))
                },
                onBaseUrlChanged = {
                    onAction(ProfileDetailAction.OnBaseUrlChanged(PvrService.RADARR, it))
                },
                onApiKeyChanged = {
                    onAction(ProfileDetailAction.OnApiKeyChanged(PvrService.RADARR, it))
                },
                onTestConnectionClick = {
                    onAction(ProfileDetailAction.OnTestConnectionClick(PvrService.RADARR))
                },
                onAdvancedSettingsChanged = { headers, username, password ->
                    onAction(
                        ProfileDetailAction.OnAdvancedSettingsChanged(
                            PvrService.RADARR,
                            headers,
                            username,
                            password,
                        )
                    )
                },
            )

            PvrServiceSection(
                nameRes = CoreR.string.integrations_seerr,
                logoRes = CoreR.drawable.ic_seerr,
                apiKeySettingsPath = "/settings",
                enabled = state.seerr.enabled,
                baseUrl = state.seerr.baseUrl,
                apiKey = state.seerr.apiKey,
                httpHeaders = state.seerr.httpHeaders,
                basicAuthUsername = state.seerr.basicAuthUsername,
                basicAuthPassword = state.seerr.basicAuthPassword,
                testState = state.seerr.testState,
                showInheritToggle = !state.isMain,
                inheriting = !state.isMain && state.seerr.inheriting,
                onInheritToggleChanged = {
                    onAction(ProfileDetailAction.OnToggleInherit(PvrService.SEERR, it))
                },
                onEnabledChanged = {
                    onAction(ProfileDetailAction.OnEnabledChanged(PvrService.SEERR, it))
                },
                onBaseUrlChanged = {
                    onAction(ProfileDetailAction.OnBaseUrlChanged(PvrService.SEERR, it))
                },
                onApiKeyChanged = {
                    onAction(ProfileDetailAction.OnApiKeyChanged(PvrService.SEERR, it))
                },
                onTestConnectionClick = {
                    onAction(ProfileDetailAction.OnTestConnectionClick(PvrService.SEERR))
                },
                onAdvancedSettingsChanged = { headers, username, password ->
                    onAction(
                        ProfileDetailAction.OnAdvancedSettingsChanged(
                            PvrService.SEERR,
                            headers,
                            username,
                            password,
                        )
                    )
                },
            )
        }
    }

    if (showRenameDialog) {
        RenameProfileDialog(
            currentName = state.name,
            onDismissRequest = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onAction(ProfileDetailAction.OnRenameConfirmed(newName))
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            title = { Text(text = stringResource(CoreR.string.profile_delete)) },
            text = {
                Text(text = stringResource(CoreR.string.profile_delete_confirm_text, state.name))
            },
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onAction(ProfileDetailAction.OnDeleteClick)
                    }
                ) {
                    Text(text = stringResource(CoreR.string.profile_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(SetupR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    name: String,
    isMain: Boolean,
    onRenameClick: () -> Unit,
    onSetAsMainClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                .padding(MaterialTheme.spacings.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge)
                if (isMain) {
                    Text(
                        text = stringResource(CoreR.string.profile_main_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier =
                            Modifier.background(
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
            OutlinedButton(onClick = onRenameClick) {
                Text(text = stringResource(CoreR.string.profile_rename))
            }
            if (!isMain) {
                OutlinedButton(onClick = onSetAsMainClick) {
                    Text(text = stringResource(CoreR.string.profile_set_as_main))
                }
            }
            OutlinedButton(onClick = onDeleteClick, enabled = !isMain) {
                Text(text = stringResource(CoreR.string.profile_delete))
            }
        }

        if (isMain) {
            Text(
                text = stringResource(CoreR.string.profile_delete_main_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RenameProfileDialog(
    currentName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.profile_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(CoreR.string.profile_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text(text = stringResource(SetupR.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(SetupR.string.cancel))
            }
        },
    )
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ProfileDetailScreenLayoutPreview() {
    JollyfinTheme {
        ProfileDetailScreenLayout(
            state =
                ProfileDetailState(
                    loading = false,
                    name = "kiddo",
                    isMain = false,
                    sonarr =
                        PvrSectionState(
                            inheriting = true,
                            enabled = true,
                            baseUrl = "https://sonarr.example.com",
                            apiKey = "abc123",
                        ),
                    radarr = PvrSectionState(inheriting = false, enabled = false),
                    seerr =
                        PvrSectionState(
                            inheriting = false,
                            enabled = true,
                            baseUrl = "https://seerr.example.com",
                            apiKey = "xyz789",
                            testState = PvrTestState.Success(3),
                        ),
                ),
            onAction = {},
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ProfileDetailScreenLayoutMainPreview() {
    JollyfinTheme {
        ProfileDetailScreenLayout(
            state =
                ProfileDetailState(
                    loading = false,
                    name = "jelly",
                    isMain = true,
                    sonarr =
                        PvrSectionState(enabled = true, baseUrl = "https://sonarr.example.com"),
                ),
            onAction = {},
        )
    }
}
