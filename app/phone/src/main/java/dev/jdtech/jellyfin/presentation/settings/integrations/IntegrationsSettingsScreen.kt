package dev.jdtech.jellyfin.presentation.settings.integrations

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.api.pvr.PvrService
import dev.jdtech.jellyfin.presentation.components.TopBarTitle
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.setup.R as SetupR

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
                title = {
                    TopBarTitle(
                        text = stringResource(SettingsR.string.settings_category_connections),
                        iconRes = SettingsR.drawable.ic_network,
                    )
                },
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
            JellyfinConnectionSection(
                state = state,
                onServerSelected = {
                    onAction(IntegrationsSettingsAction.OnJellyfinServerSelected(it))
                },
                onUserSelected = { onAction(IntegrationsSettingsAction.OnJellyfinUserSelected(it)) },
                onAddServer = { onAction(IntegrationsSettingsAction.OnAddJellyfinServer(it)) },
                onDeleteServer = { onAction(IntegrationsSettingsAction.OnDeleteJellyfinServer(it)) },
                onLogin = { username, password ->
                    onAction(IntegrationsSettingsAction.OnLoginJellyfinUser(username, password))
                },
                onDeleteUser = { onAction(IntegrationsSettingsAction.OnDeleteJellyfinUser(it)) },
            )

            HorizontalDivider()

            PvrServiceSection(
                nameRes = CoreR.string.integrations_sonarr,
                logoRes = CoreR.drawable.ic_sonarr,
                apiKeySettingsPath = "/settings/general",
                enabled = state.sonarrEnabled,
                baseUrl = state.sonarrBaseUrl,
                apiKey = state.sonarrApiKey,
                httpHeaders = state.sonarrHttpHeaders,
                basicAuthUsername = state.sonarrBasicAuthUsername,
                basicAuthPassword = state.sonarrBasicAuthPassword,
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
                onAdvancedSettingsChanged = { headers, username, password ->
                    onAction(
                        IntegrationsSettingsAction.OnAdvancedSettingsChanged(
                            PvrService.SONARR,
                            headers,
                            username,
                            password,
                        )
                    )
                },
            )

            HorizontalDivider()

            PvrServiceSection(
                nameRes = CoreR.string.integrations_radarr,
                logoRes = CoreR.drawable.ic_radarr,
                apiKeySettingsPath = "/settings/general",
                enabled = state.radarrEnabled,
                baseUrl = state.radarrBaseUrl,
                apiKey = state.radarrApiKey,
                httpHeaders = state.radarrHttpHeaders,
                basicAuthUsername = state.radarrBasicAuthUsername,
                basicAuthPassword = state.radarrBasicAuthPassword,
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
                onAdvancedSettingsChanged = { headers, username, password ->
                    onAction(
                        IntegrationsSettingsAction.OnAdvancedSettingsChanged(
                            PvrService.RADARR,
                            headers,
                            username,
                            password,
                        )
                    )
                },
            )

            HorizontalDivider()

            PvrServiceSection(
                nameRes = CoreR.string.integrations_seerr,
                logoRes = CoreR.drawable.ic_seerr,
                apiKeySettingsPath = "/settings",
                enabled = state.seerrEnabled,
                baseUrl = state.seerrBaseUrl,
                apiKey = state.seerrApiKey,
                httpHeaders = state.seerrHttpHeaders,
                basicAuthUsername = state.seerrBasicAuthUsername,
                basicAuthPassword = state.seerrBasicAuthPassword,
                testState = state.seerrTestState,
                onEnabledChanged = {
                    onAction(IntegrationsSettingsAction.OnSeerrEnabledChanged(it))
                },
                onBaseUrlChanged = {
                    onAction(IntegrationsSettingsAction.OnSeerrBaseUrlChanged(it))
                },
                onApiKeyChanged = {
                    onAction(IntegrationsSettingsAction.OnSeerrApiKeyChanged(it))
                },
                onTestConnectionClick = {
                    onAction(IntegrationsSettingsAction.OnTestSeerrConnection)
                },
                onAdvancedSettingsChanged = { headers, username, password ->
                    onAction(
                        IntegrationsSettingsAction.OnAdvancedSettingsChanged(
                            PvrService.SEERR,
                            headers,
                            username,
                            password,
                        )
                    )
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

            Column {
                Text(
                    text = stringResource(CoreR.string.integrations_release_cache),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(CoreR.string.integrations_release_cache_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.pvrReleaseCacheMinutes.toString(),
                    onValueChange = { value ->
                        val minutes = value.toIntOrNull()
                        if (minutes != null) {
                            onAction(IntegrationsSettingsAction.OnReleaseCacheChanged(minutes))
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
private fun JellyfinConnectionSection(
    state: IntegrationsSettingsState,
    onServerSelected: (String) -> Unit,
    onUserSelected: (java.util.UUID) -> Unit,
    onAddServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onLogin: (String, String) -> Unit,
    onDeleteUser: (java.util.UUID) -> Unit,
) {
    var serverAddress by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var serverToDelete by remember { mutableStateOf<ServerWithAddresses?>(null) }
    var userToDelete by remember { mutableStateOf<dev.jdtech.jellyfin.models.User?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(CoreR.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(CoreR.string.integrations_jellyfin),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.jellyfinServers.isEmpty()) {
            Text(
                text = stringResource(CoreR.string.integrations_no_jellyfin_server),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            state.jellyfinServers.forEach { server ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onServerSelected(server.server.id) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.jellyfinOperationInProgress,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.server.name)
                            server.addresses.firstOrNull()?.let { address ->
                                Text(address.address, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (state.currentServerId == server.server.id) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_check),
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(
                        onClick = { serverToDelete = server },
                        enabled = !state.jellyfinOperationInProgress,
                    ) {
                        Icon(painter = painterResource(CoreR.drawable.ic_trash), contentDescription = null)
                    }
                }
            }
        }
        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            label = { Text(stringResource(SetupR.string.edit_text_server_address_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAddServer(serverAddress) },
            enabled = serverAddress.isNotBlank() && !state.jellyfinOperationInProgress,
        ) {
            Text(stringResource(SetupR.string.add_server_btn_connect))
        }
        if (state.currentServerId != null) {
            if (state.jellyfinUsers.isNotEmpty()) {
                Text(
                    text = stringResource(SettingsR.string.users),
                    style = MaterialTheme.typography.titleSmall,
                )
                state.jellyfinUsers.forEach { user ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onUserSelected(user.id) },
                            enabled = !state.jellyfinOperationInProgress,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_user),
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(user.name, modifier = Modifier.weight(1f))
                            if (state.currentUserId == user.id.toString()) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        }
                        IconButton(
                            onClick = { userToDelete = user },
                            enabled = !state.jellyfinOperationInProgress,
                        ) {
                            Icon(painter = painterResource(CoreR.drawable.ic_trash), contentDescription = null)
                        }
                    }
                }
            }
            Text(
                text = stringResource(SetupR.string.login),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(SetupR.string.edit_text_username_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(SetupR.string.edit_text_password_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onLogin(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank() && !state.jellyfinOperationInProgress,
            ) {
                Text(stringResource(SetupR.string.login_btn_login))
            }
        }
        state.jellyfinError?.let { error ->
            Text(text = error.asString(), color = MaterialTheme.colorScheme.error)
        }
    }

    serverToDelete?.let { server ->
        AlertDialog(
            title = { Text(stringResource(SetupR.string.remove_server_dialog)) },
            text = { Text(stringResource(SetupR.string.remove_server_dialog_text, server.server.name)) },
            onDismissRequest = { serverToDelete = null },
            confirmButton = {
                TextButton(onClick = { onDeleteServer(server.server.id); serverToDelete = null }) {
                    Text(stringResource(SetupR.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text(stringResource(SetupR.string.cancel))
                }
            },
        )
    }
    userToDelete?.let { user ->
        AlertDialog(
            title = { Text(stringResource(SetupR.string.remove_user_dialog)) },
            text = { Text(stringResource(SetupR.string.remove_user_dialog_text, user.name)) },
            onDismissRequest = { userToDelete = null },
            confirmButton = {
                TextButton(onClick = { onDeleteUser(user.id); userToDelete = null }) {
                    Text(stringResource(SetupR.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text(stringResource(SetupR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PvrServiceSection(
    nameRes: Int,
    @DrawableRes logoRes: Int,
    // Path under the service's base URL where its web UI shows the API key, e.g.
    // "/settings/general" for Sonarr/Radarr - linked from the section for easier setup.
    apiKeySettingsPath: String,
    enabled: Boolean,
    baseUrl: String,
    apiKey: String,
    httpHeaders: String,
    basicAuthUsername: String,
    basicAuthPassword: String,
    testState: PvrTestState,
    onEnabledChanged: (Boolean) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onTestConnectionClick: () -> Unit,
    onAdvancedSettingsChanged: (headers: String, username: String, password: String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Single header line: logo, name, and the enable toggle right-aligned.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(nameRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
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

            PvrAdvancedHttpFields(
                headers = httpHeaders,
                basicAuthUsername = basicAuthUsername,
                basicAuthPassword = basicAuthPassword,
                onChanged = onAdvancedSettingsChanged,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onTestConnectionClick,
                    enabled =
                        testState !is PvrTestState.Testing &&
                            baseUrl.isNotBlank() &&
                            apiKey.isNotBlank(),
                ) {
                    Text(text = stringResource(CoreR.string.integrations_test_connection))
                }
                TextButton(
                    onClick = {
                        val url = baseUrl.trim().trimEnd('/')
                        uriHandler.openUri(url + apiKeySettingsPath)
                    },
                    enabled = baseUrl.isNotBlank(),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_globe),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(CoreR.string.integrations_get_api_key))
                }
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

@Composable
private fun PvrAdvancedHttpFields(
    headers: String,
    basicAuthUsername: String,
    basicAuthPassword: String,
    onChanged: (headers: String, username: String, password: String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(CoreR.string.integrations_advanced_http),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter =
                    painterResource(
                        if (expanded) CoreR.drawable.ic_chevron_up
                        else CoreR.drawable.ic_chevron_down
                    ),
                contentDescription =
                    stringResource(if (expanded) CoreR.string.collapse else CoreR.string.expand),
            )
        }
        if (expanded) {
            OutlinedTextField(
                value = headers,
                onValueChange = { onChanged(it, basicAuthUsername, basicAuthPassword) },
                label = { Text(stringResource(CoreR.string.integrations_custom_headers)) },
                placeholder = { Text(stringResource(CoreR.string.integrations_custom_headers_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = basicAuthUsername,
                onValueChange = { onChanged(headers, it, basicAuthPassword) },
                label = { Text(stringResource(CoreR.string.integrations_basic_auth_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = basicAuthPassword,
                onValueChange = { onChanged(headers, basicAuthUsername, it) },
                label = { Text(stringResource(CoreR.string.integrations_basic_auth_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
