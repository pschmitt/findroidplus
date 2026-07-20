package dev.jdtech.jellyfin.presentation.settings.backup

import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.components.TopBarTitle
import dev.jdtech.jellyfin.presentation.settings.components.IntervalPickerContent
import dev.jdtech.jellyfin.presentation.settings.components.formatIntervalMinutes
import dev.jdtech.jellyfin.setup.presentation.backup.BackupSettingsAction
import dev.jdtech.jellyfin.setup.presentation.backup.BackupSettingsEvent
import dev.jdtech.jellyfin.setup.presentation.backup.BackupSettingsState
import dev.jdtech.jellyfin.setup.presentation.backup.BackupSettingsViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun BackupSettingsScreen(
    navigateBack: () -> Unit,
    navigateToRestore: () -> Unit,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(true) { viewModel.load() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is BackupSettingsEvent.BackupNowSuccess -> {
                viewModel.load()
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(CoreR.string.backup_now_success))
                }
            }
            is BackupSettingsEvent.BackupNowError -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(CoreR.string.backup_now_error, event.message ?: "")
                    )
                }
            }
        }
    }

    val chooseFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.onAction(BackupSettingsAction.OnFolderPicked(uri))
            }
        }

    var pendingPassword by rememberSaveable { mutableStateOf<String?>(null) }
    val createBackupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                viewModel.onAction(BackupSettingsAction.OnBackupNow(uri, pendingPassword))
            }
            pendingPassword = null
        }

    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }

    BackupSettingsScreenLayout(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                is BackupSettingsAction.OnBackClick -> navigateBack()
                else -> viewModel.onAction(action)
            }
        },
        onChooseFolderClick = { chooseFolderLauncher.launch(null) },
        onBackupNowClick = { showPasswordDialog = true },
        onRestoreClick = navigateToRestore,
    )

    if (showPasswordDialog) {
        // Pre-fill with the auto-backup encryption password (if set) - the user is likely
        // encrypting this manual backup the same way, and re-typing it here would be redundant.
        var password by rememberSaveable { mutableStateOf(state.autoBackupPassword.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(text = stringResource(CoreR.string.backup_now_password_title)) },
            text = {
                Column {
                    Text(text = stringResource(CoreR.string.backup_now_password_summary))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = stringResource(CoreR.string.backup_now_password_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        pendingPassword = if (password.isEmpty()) null else password
                        // Same human-friendly timestamp format as AutoBackupWorker's filenames.
                        createBackupLauncher.launch(
                            "findroid-backup-${
                                ZonedDateTime.now()
                                    .truncatedTo(ChronoUnit.SECONDS)
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            }.frb"
                        )
                    }
                ) {
                    Text(text = stringResource(CoreR.string.backup_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupSettingsScreenLayout(
    state: BackupSettingsState,
    snackbarHostState: SnackbarHostState,
    onAction: (BackupSettingsAction) -> Unit,
    onChooseFolderClick: () -> Unit,
    onBackupNowClick: () -> Unit,
    onRestoreClick: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    TopBarTitle(
                        text = stringResource(CoreR.string.backup_and_restore),
                        iconRes = CoreR.drawable.ic_save,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(BackupSettingsAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        var showIntervalDialog by rememberSaveable { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(CoreR.string.backup_section_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_refresh_cw),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(CoreR.string.backup_auto_enable),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(CoreR.string.backup_auto_enable_summary),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.autoBackupEnabled,
                    onCheckedChange = {
                        onAction(BackupSettingsAction.OnAutoBackupEnabledChanged(it))
                    },
                )
            }

            if (state.autoBackupEnabled) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable { showIntervalDialog = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painter = painterResource(CoreR.drawable.ic_gauge), contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(CoreR.string.backup_auto_interval),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = formatIntervalMinutes(state.autoBackupIntervalMinutes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_database),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(CoreR.string.backup_folder),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                state.autoBackupFolderUri
                                    ?: stringResource(CoreR.string.backup_folder_not_set),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedButton(onClick = onChooseFolderClick) {
                    Text(text = stringResource(CoreR.string.backup_choose_folder))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painter = painterResource(CoreR.drawable.ic_lock), contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    var passwordText by
                        rememberSaveable(state.autoBackupPassword) {
                            mutableStateOf(state.autoBackupPassword.orEmpty())
                        }
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { value ->
                            passwordText = value
                            onAction(BackupSettingsAction.OnAutoBackupPasswordChanged(value))
                        },
                        label = { Text(text = stringResource(CoreR.string.backup_auto_password)) },
                        supportingText = {
                            Text(text = stringResource(CoreR.string.backup_auto_password_summary))
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(CoreR.drawable.ic_check), contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(CoreR.string.backup_last),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text =
                            if (state.lastBackupTimestamp > 0) {
                                DateFormat.getDateFormat(context)
                                    .format(Date(state.lastBackupTimestamp))
                            } else {
                                stringResource(CoreR.string.backup_last_never)
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(onClick = onBackupNowClick) {
                Icon(painter = painterResource(CoreR.drawable.ic_save), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(CoreR.string.backup_now))
            }

            HorizontalDivider()

            Text(
                text = stringResource(CoreR.string.restore_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(CoreR.string.restore_section_summary),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onRestoreClick) {
                Icon(painter = painterResource(CoreR.drawable.ic_rotate_ccw), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(CoreR.string.restore_backup))
            }
        }

        if (showIntervalDialog) {
            var pendingInterval by remember { mutableStateOf(state.autoBackupIntervalMinutes) }
            BaseDialog(
                title = stringResource(CoreR.string.backup_auto_interval),
                onDismiss = { showIntervalDialog = false },
                negativeButton = {
                    TextButton(onClick = { showIntervalDialog = false }) {
                        Text(text = stringResource(CoreR.string.cancel))
                    }
                },
                positiveButton = {
                    TextButton(
                        onClick = {
                            onAction(
                                BackupSettingsAction.OnAutoBackupIntervalChanged(pendingInterval)
                            )
                            showIntervalDialog = false
                        }
                    ) {
                        Text(text = stringResource(CoreR.string.save))
                    }
                },
            ) { contentPadding ->
                Column(modifier = Modifier.padding(contentPadding)) {
                    Text(
                        text = stringResource(CoreR.string.backup_auto_interval_summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IntervalPickerContent(
                        value = pendingInterval,
                        presetsMinutes = listOf(60, 360, 720, 1440, 4320, 10080),
                        validRange = 15..(30 * 24 * 60),
                        onValueChange = { pendingInterval = it },
                    )
                }
            }
        }
    }
}
