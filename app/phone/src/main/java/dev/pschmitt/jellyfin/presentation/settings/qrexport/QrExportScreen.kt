package dev.pschmitt.jellyfin.presentation.settings.qrexport

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.common.BitMatrix
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.components.BaseDialog
import dev.pschmitt.jellyfin.presentation.components.TopBarTitle
import dev.pschmitt.jellyfin.qrsetup.QrCodec
import dev.pschmitt.jellyfin.setup.presentation.qrexport.QrExportAction
import dev.pschmitt.jellyfin.setup.presentation.qrexport.QrExportState
import dev.pschmitt.jellyfin.setup.presentation.qrexport.QrExportViewModel
import java.util.UUID

@Composable
fun QrExportScreen(navigateBack: () -> Unit, viewModel: QrExportViewModel = hiltViewModel()) {
    val activity = LocalContext.current as FragmentActivity
    val state by viewModel.state.collectAsStateWithLifecycle()

    // This screen renders real credentials (Jellyfin session token, PVR API keys) as a QR code -
    // gate it behind biometric/PIN auth so a picked-up unlocked phone can't reach it, same
    // rationale as the FLAG_SECURE screenshot block below.
    var authState by rememberSaveable { mutableStateOf(AuthState.PENDING) }

    LaunchedEffect(Unit) {
        if (authState != AuthState.PENDING) return@LaunchedEffect
        val canAuthenticate =
            BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            authState = AuthState.FAILED
            return@LaunchedEffect
        }
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(CoreR.string.qr_export_biometric_title))
                .setSubtitle(activity.getString(CoreR.string.qr_export_biometric_subtitle))
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        authState = AuthState.SUCCEEDED
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authState = AuthState.FAILED
                    }
                },
            )
            .authenticate(promptInfo)
    }

    if (authState == AuthState.FAILED) {
        LaunchedEffect(Unit) { navigateBack() }
    }

    if (authState != AuthState.SUCCEEDED) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    LaunchedEffect(Unit) { viewModel.onAction(QrExportAction.OnLoad) }

    // Blocks screenshots of the generated code while it's on screen - same bearer-credential
    // rationale as the biometric gate above.
    DisposableEffect(state.payload) {
        if (state.payload != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    QrExportScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is QrExportAction.OnBackClick -> navigateBack()
                else -> viewModel.onAction(action)
            }
        },
    )
}

private enum class AuthState {
    PENDING,
    SUCCEEDED,
    FAILED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrExportScreenLayout(state: QrExportState, onAction: (QrExportAction) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    TopBarTitle(
                        text = stringResource(CoreR.string.qr_export_title),
                        iconRes = CoreR.drawable.ic_smartphone,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(QrExportAction.OnBackClick) }) {
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(CoreR.string.qr_export_summary),
                style = MaterialTheme.typography.bodySmall,
            )

            SectionCheckbox(
                labelRes = CoreR.string.qr_export_include_jellyfin,
                summaryRes = CoreR.string.qr_export_include_jellyfin_summary,
                available = state.jellyfinAvailable,
                checked = state.includeJellyfin,
                onCheckedChange = { onAction(QrExportAction.OnIncludeJellyfinChanged(it)) },
            )

            val serverUserOptions =
                state.availableServers.flatMap { s ->
                    s.users.map { u -> Triple(s.server.id, u.id, "${s.server.name} - ${u.name}") }
                }
            if (state.includeJellyfin && state.jellyfinAvailable) {
                if (serverUserOptions.size > 1) {
                    JellyfinServerUserPicker(
                        options = serverUserOptions,
                        selectedServerId = state.selectedServerId,
                        selectedUserId = state.selectedUserId,
                        onSelected = { serverId, userId ->
                            onAction(QrExportAction.OnServerSelected(serverId))
                            onAction(QrExportAction.OnUserSelected(userId))
                        },
                    )
                }
                OutlinedTextField(
                    value = state.jellyfinUsername,
                    onValueChange = { onAction(QrExportAction.OnJellyfinUsernameChanged(it)) },
                    label = {
                        Text(text = stringResource(CoreR.string.qr_export_jellyfin_username))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.jellyfinPassword,
                    onValueChange = { onAction(QrExportAction.OnJellyfinPasswordChanged(it)) },
                    label = {
                        Text(text = stringResource(CoreR.string.qr_export_jellyfin_password))
                    },
                    supportingText = {
                        Text(
                            text = stringResource(CoreR.string.qr_export_jellyfin_password_summary)
                        )
                    },
                    singleLine = true,
                    isError = state.jellyfinLoginError != null,
                    visualTransformation =
                        if (state.jellyfinPasswordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onAction(QrExportAction.OnToggleJellyfinPasswordVisibility)
                            }
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (state.jellyfinPasswordVisible) CoreR.drawable.ic_eye_off
                                        else CoreR.drawable.ic_eye
                                    ),
                                contentDescription =
                                    stringResource(CoreR.string.qr_export_toggle_password),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                val jellyfinLoginError = state.jellyfinLoginError
                if (jellyfinLoginError != null) {
                    Text(
                        text = jellyfinLoginError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (state.isVerifyingJellyfinLogin) {
                    Text(
                        text = stringResource(CoreR.string.qr_export_jellyfin_verifying),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val advancedAvailable =
                state.sonarrAvailable || state.radarrAvailable || state.seerrAvailable
            if (advancedAvailable) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            onAction(QrExportAction.OnAdvancedToggle)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(CoreR.string.qr_export_advanced),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painter =
                            painterResource(
                                if (state.advancedExpanded) CoreR.drawable.ic_chevron_up
                                else CoreR.drawable.ic_chevron_down
                            ),
                        contentDescription = null,
                    )
                }
                AnimatedVisibility(visible = state.advancedExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionCheckbox(
                            labelRes = CoreR.string.qr_export_include_sonarr,
                            summaryRes = null,
                            available = state.sonarrAvailable,
                            checked = state.includeSonarr,
                            onCheckedChange = {
                                onAction(QrExportAction.OnIncludeSonarrChanged(it))
                            },
                        )
                        if (state.includeSonarr && state.sonarrAvailable) {
                            PvrOverrideFields(
                                baseUrl = state.sonarrBaseUrl,
                                apiKey = state.sonarrApiKey,
                                onBaseUrlChange = {
                                    onAction(QrExportAction.OnSonarrBaseUrlChanged(it))
                                },
                                onApiKeyChange = {
                                    onAction(QrExportAction.OnSonarrApiKeyChanged(it))
                                },
                            )
                        }
                        SectionCheckbox(
                            labelRes = CoreR.string.qr_export_include_radarr,
                            summaryRes = null,
                            available = state.radarrAvailable,
                            checked = state.includeRadarr,
                            onCheckedChange = {
                                onAction(QrExportAction.OnIncludeRadarrChanged(it))
                            },
                        )
                        if (state.includeRadarr && state.radarrAvailable) {
                            PvrOverrideFields(
                                baseUrl = state.radarrBaseUrl,
                                apiKey = state.radarrApiKey,
                                onBaseUrlChange = {
                                    onAction(QrExportAction.OnRadarrBaseUrlChanged(it))
                                },
                                onApiKeyChange = {
                                    onAction(QrExportAction.OnRadarrApiKeyChanged(it))
                                },
                            )
                        }
                        SectionCheckbox(
                            labelRes = CoreR.string.qr_export_include_seerr,
                            summaryRes = null,
                            available = state.seerrAvailable,
                            checked = state.includeSeerr,
                            onCheckedChange = {
                                onAction(QrExportAction.OnIncludeSeerrChanged(it))
                            },
                        )
                        if (state.includeSeerr && state.seerrAvailable) {
                            PvrOverrideFields(
                                baseUrl = state.seerrBaseUrl,
                                apiKey = state.seerrApiKey,
                                onBaseUrlChange = {
                                    onAction(QrExportAction.OnSeerrBaseUrlChanged(it))
                                },
                                onApiKeyChange = {
                                    onAction(QrExportAction.OnSeerrApiKeyChanged(it))
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.password,
                onValueChange = {},
                readOnly = true,
                label = { Text(text = stringResource(CoreR.string.qr_export_password)) },
                supportingText = {
                    Text(text = stringResource(CoreR.string.qr_export_password_summary))
                },
                singleLine = true,
                visualTransformation =
                    if (state.passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = { onAction(QrExportAction.OnTogglePasswordVisibility) }
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (state.passwordVisible) CoreR.drawable.ic_eye_off
                                        else CoreR.drawable.ic_eye
                                    ),
                                contentDescription =
                                    stringResource(CoreR.string.qr_export_toggle_password),
                            )
                        }
                        IconButton(onClick = { onAction(QrExportAction.OnRegeneratePassword) }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_refresh_cw),
                                contentDescription =
                                    stringResource(CoreR.string.qr_export_regenerate_password),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val nothingSelected =
                !(state.includeJellyfin && state.jellyfinAvailable) &&
                    !(state.includeSonarr && state.sonarrAvailable) &&
                    !(state.includeRadarr && state.radarrAvailable) &&
                    !(state.includeSeerr && state.seerrAvailable)

            if (nothingSelected) {
                Text(
                    text = stringResource(CoreR.string.qr_export_nothing_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.isGenerating) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            val error = state.error
            if (error != null) {
                Text(
                    text = stringResource(CoreR.string.qr_export_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val payload = state.payload
            if (payload != null) {
                Crossfade(
                    targetState = payload,
                    animationSpec = tween(durationMillis = 300),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    label = "qr-code",
                ) { crossfadedPayload ->
                    val bitmap =
                        remember(crossfadedPayload) { QrCodec.encode(crossfadedPayload).toBitmap() }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCheckbox(
    labelRes: Int,
    summaryRes: Int?,
    available: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked && available,
            onCheckedChange = onCheckedChange,
            enabled = available,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    if (available) {
                        summaryRes?.let { stringResource(it) } ?: ""
                    } else {
                        stringResource(CoreR.string.qr_export_not_configured)
                    },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PvrOverrideFields(
    baseUrl: String,
    apiKey: String,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text(text = stringResource(CoreR.string.integrations_server_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(text = stringResource(CoreR.string.integrations_api_key)) },
            supportingText = {
                Text(text = stringResource(CoreR.string.qr_export_api_key_summary))
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun JellyfinServerUserPicker(
    options: List<Triple<String, UUID, String>>,
    selectedServerId: String?,
    selectedUserId: UUID?,
    onSelected: (serverId: String, userId: UUID) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val selectedLabel =
        options.find { it.first == selectedServerId && it.second == selectedUserId }?.third

    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(CoreR.string.qr_export_jellyfin_account),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = selectedLabel.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
        Icon(painter = painterResource(CoreR.drawable.ic_chevron_down), contentDescription = null)
    }

    if (showDialog) {
        BaseDialog(
            title = stringResource(CoreR.string.qr_export_jellyfin_account),
            onDismiss = { showDialog = false },
        ) { contentPadding ->
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                for ((serverId, userId, label) in options) {
                    val isSelected = serverId == selectedServerId && userId == selectedUserId
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onSelected(serverId, userId)
                                    showDialog = false
                                }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelected(serverId, userId)
                                showDialog = false
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        }
    }
}

private fun BitMatrix.toBitmap(): Bitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels[y * width + x] = if (get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565)
}
