package dev.jdtech.jellyfin.presentation.settings.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun IntegrationsSettingsScreen(
    navigateBack: () -> Unit,
    viewModel: IntegrationsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.load() }

    IntegrationsSettingsScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is IntegrationsSettingsAction.OnBackClick -> navigateBack()
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntegrationsSettingsScreenLayout(
    state: IntegrationsSettingsState,
    onAction: (IntegrationsSettingsAction) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.integrations_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(IntegrationsSettingsAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PvrServiceSection(
                nameRes = CoreR.string.integrations_sonarr,
                enableLabelRes = CoreR.string.integrations_enable_sonarr,
                enabled = state.sonarrEnabled,
                baseUrl = state.sonarrBaseUrl,
                apiKey = state.sonarrApiKey,
                testState = state.sonarrTestState,
                onEnabledChanged = {
                    onAction(IntegrationsSettingsAction.OnSonarrEnabledChanged(it))
                },
                onBaseUrlChanged = {
                    onAction(IntegrationsSettingsAction.OnSonarrBaseUrlChanged(it))
                },
                onApiKeyChanged = {
                    onAction(IntegrationsSettingsAction.OnSonarrApiKeyChanged(it))
                },
                onTestConnectionClick = {
                    onAction(IntegrationsSettingsAction.OnTestSonarrConnection)
                },
            )

            HorizontalDivider()

            PvrServiceSection(
                nameRes = CoreR.string.integrations_radarr,
                enableLabelRes = CoreR.string.integrations_enable_radarr,
                enabled = state.radarrEnabled,
                baseUrl = state.radarrBaseUrl,
                apiKey = state.radarrApiKey,
                testState = state.radarrTestState,
                onEnabledChanged = {
                    onAction(IntegrationsSettingsAction.OnRadarrEnabledChanged(it))
                },
                onBaseUrlChanged = {
                    onAction(IntegrationsSettingsAction.OnRadarrBaseUrlChanged(it))
                },
                onApiKeyChanged = {
                    onAction(IntegrationsSettingsAction.OnRadarrApiKeyChanged(it))
                },
                onTestConnectionClick = {
                    onAction(IntegrationsSettingsAction.OnTestRadarrConnection)
                },
            )

            HorizontalDivider()

            Column {
                Text(
                    text = stringResource(CoreR.string.integrations_poll_interval),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(CoreR.string.integrations_poll_interval_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.pvrPollIntervalMinutes.toString(),
                    onValueChange = { value ->
                        val minutes = value.toIntOrNull()
                        if (minutes != null) {
                            onAction(IntegrationsSettingsAction.OnPollIntervalChanged(minutes))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PvrServiceSection(
    nameRes: Int,
    enableLabelRes: Int,
    enabled: Boolean,
    baseUrl: String,
    apiKey: String,
    testState: PvrTestState,
    onEnabledChanged: (Boolean) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onTestConnectionClick: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(nameRes), style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(enableLabelRes), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }

        if (enabled) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text(text = stringResource(CoreR.string.integrations_server_url)) },
                placeholder = {
                    Text(text = stringResource(CoreR.string.integrations_server_url_hint))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text(text = stringResource(CoreR.string.integrations_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { pasted ->
                                onApiKeyChanged(pasted.trim())
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_clipboard_paste),
                            contentDescription =
                                stringResource(CoreR.string.integrations_paste_api_key),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onTestConnectionClick,
                enabled = testState !is PvrTestState.Testing && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            ) {
                Text(text = stringResource(CoreR.string.integrations_test_connection))
            }

            when (testState) {
                is PvrTestState.Success -> {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.integrations_test_connection_success,
                                testState.itemCount,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is PvrTestState.Error -> {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.integrations_test_connection_error,
                                testState.message,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }
        }
    }
}
