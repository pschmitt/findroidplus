package dev.jdtech.jellyfin.presentation.setup.restore

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.setup.components.RootLayout
import dev.jdtech.jellyfin.setup.presentation.restore.RestoreBackupAction
import dev.jdtech.jellyfin.setup.presentation.restore.RestoreBackupState
import dev.jdtech.jellyfin.setup.presentation.restore.RestoreBackupViewModel
import dev.jdtech.jellyfin.utils.restartProcess

@Composable
fun RestoreBackupScreen(
    onBackClick: () -> Unit,
    viewModel: RestoreBackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var pickedOnce by remember { mutableStateOf(false) }

    val pickFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickedOnce = true
            if (uri != null) {
                viewModel.onAction(RestoreBackupAction.OnFilePicked(uri))
            }
        }

    LaunchedEffect(Unit) { pickFileLauncher.launch(arrayOf("*/*")) }

    RestoreBackupScreenLayout(
        state = state,
        pickedOnce = pickedOnce,
        onChooseFileClick = { pickFileLauncher.launch(arrayOf("*/*")) },
        onAction = { action ->
            when (action) {
                is RestoreBackupAction.OnBackClick -> onBackClick()
                else -> viewModel.onAction(action)
            }
        },
        // A full process restart (not just an Activity restart) - currentServer/currentUser were
        // just restored (prefs + DB), so a fresh cold start lands directly on HomeRoute via
        // MainViewModel's checks, without making the user re-pick the server/user they just
        // restored. A process restart is required (not just recreating the Activity) because
        // JellyfinApi is a @Singleton built once from the current server/user at process
        // startup - restarting only the Activity would leave it permanently unconfigured.
        onContinueClick = { (context as Activity).restartProcess() },
    )
}

@Composable
private fun RestoreBackupScreenLayout(
    state: RestoreBackupState,
    pickedOnce: Boolean,
    onChooseFileClick: () -> Unit,
    onAction: (RestoreBackupAction) -> Unit,
    onContinueClick: () -> Unit,
) {
    RootLayout {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                contentDescription = null,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(CoreR.string.restore_backup),
                style = MaterialTheme.typography.headlineSmall,
            )

            val summary = state.summary

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }
                summary != null -> {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.restore_backup_summary,
                                summary.serversRestored,
                                summary.usersRestored,
                                summary.rulesRestored,
                            )
                    )

                    if (summary.downloadedItems.isNotEmpty() && !state.downloadPromptAnswered) {
                        Text(
                            text = stringResource(CoreR.string.restore_backup_redownload_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.restore_backup_redownload_text,
                                    summary.downloadedItems.size,
                                )
                        )
                        Button(
                            onClick = { onAction(RestoreBackupAction.OnRedownloadYes) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_check),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(CoreR.string.restore_backup_redownload_yes))
                        }
                        OutlinedButton(
                            onClick = { onAction(RestoreBackupAction.OnRedownloadNo) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(CoreR.string.restore_backup_redownload_no))
                        }
                    } else {
                        Button(onClick = onContinueClick, modifier = Modifier.fillMaxWidth()) {
                            Text(text = stringResource(CoreR.string.restore_backup_continue))
                        }
                    }
                }
                state.error != null -> {
                    Text(text = stringResource(CoreR.string.restore_backup_error, state.error!!))
                    OutlinedButton(onClick = onChooseFileClick, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(CoreR.string.restore_backup_choose_file))
                    }
                    TextButton(onClick = { onAction(RestoreBackupAction.OnBackClick) }) {
                        Text(text = stringResource(CoreR.string.cancel))
                    }
                }
                pickedOnce -> {
                    OutlinedButton(onClick = onChooseFileClick, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(CoreR.string.restore_backup_choose_file))
                    }
                    TextButton(onClick = { onAction(RestoreBackupAction.OnBackClick) }) {
                        Text(text = stringResource(CoreR.string.cancel))
                    }
                }
            }
        }
    }

    if (state.needsPassword) {
        var password by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onAction(RestoreBackupAction.OnBackClick) },
            title = { Text(text = stringResource(CoreR.string.restore_backup_password_title)) },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(text = stringResource(CoreR.string.restore_backup_password_hint)) },
                    isError = state.wrongPassword,
                    supportingText = {
                        if (state.wrongPassword) {
                            Text(text = stringResource(CoreR.string.restore_backup_wrong_password))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(RestoreBackupAction.OnPasswordSubmit(password)) }
                ) {
                    Text(text = stringResource(CoreR.string.restore_backup_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(RestoreBackupAction.OnBackClick) }) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}
