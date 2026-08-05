package dev.pschmitt.jellyfin.presentation.settings.localaccess

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

@Composable
fun LocalAccessScreen(navigateBack: () -> Unit, viewModel: LocalAccessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(true) { viewModel.load() }

    LocalAccessScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is LocalAccessAction.OnBackClick -> navigateBack()
                is LocalAccessAction.CopyToken -> {
                    clipboardManager.setText(AnnotatedString(state.token))
                    Toast.makeText(
                            context,
                            CoreR.string.local_access_token_copied_toast,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                is LocalAccessAction.CopyCliDownloadCommand -> {
                    clipboardManager.setText(AnnotatedString(state.cliDownloadCommand))
                    Toast.makeText(
                            context,
                            CoreR.string.local_access_cli_copied_toast,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                is LocalAccessAction.RegenerateToken -> {
                    Toast.makeText(
                            context,
                            CoreR.string.local_access_token_regenerated_toast,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                    viewModel.onAction(action)
                }
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalAccessScreenLayout(
    state: LocalAccessState,
    onAction: (LocalAccessAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.local_access_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(LocalAccessAction.OnBackClick) }) {
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LocalControlToggleRow(
                enabled = state.localControlEnabled,
                startFailed = state.startFailed,
                onToggle = { onAction(LocalAccessAction.SetLocalControlEnabled(it)) },
            )
            HorizontalDivider()
            TokenSection(
                token = state.token,
                onCopy = { onAction(LocalAccessAction.CopyToken) },
                onRegenerate = { onAction(LocalAccessAction.RegenerateToken) },
            )
            HorizontalDivider()
            CliDownloadSection(
                command = state.cliDownloadCommand,
                onCopy = { onAction(LocalAccessAction.CopyCliDownloadCommand) },
            )
        }
    }
}

@Composable
private fun LocalControlToggleRow(
    enabled: Boolean,
    startFailed: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacings.default,
                        vertical = MaterialTheme.spacings.small,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(CoreR.string.local_access_enable_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(CoreR.string.local_access_enable_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (startFailed) {
            Text(
                text = stringResource(CoreR.string.local_access_start_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier.padding(
                        horizontal = MaterialTheme.spacings.default,
                        vertical = MaterialTheme.spacings.small,
                    ),
            )
        }
    }
}

@Composable
private fun TokenSection(token: String, onCopy: () -> Unit, onRegenerate: () -> Unit) {
    var confirmRegenerateOpen by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(MaterialTheme.spacings.default)) {
        Text(
            text = stringResource(CoreR.string.local_access_token_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(CoreR.string.local_access_token_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.spacings.small),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Text(
                text = if (tokenVisible) token else "•".repeat(token.length),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                Icon(
                    painter =
                        painterResource(
                            if (tokenVisible) CoreR.drawable.ic_eye_off else CoreR.drawable.ic_eye
                        ),
                    contentDescription =
                        stringResource(
                            if (tokenVisible) {
                                CoreR.string.local_access_hide_token
                            } else {
                                CoreR.string.local_access_show_token
                            }
                        ),
                )
            }
            TextButton(onClick = onCopy) { Text(text = stringResource(CoreR.string.copy)) }
        }
        TextButton(
            onClick = { confirmRegenerateOpen = true },
            modifier = Modifier.padding(top = MaterialTheme.spacings.small),
        ) {
            Text(text = stringResource(CoreR.string.local_access_regenerate_token))
        }
    }

    if (confirmRegenerateOpen) {
        AlertDialog(
            onDismissRequest = { confirmRegenerateOpen = false },
            title = {
                Text(text = stringResource(CoreR.string.local_access_regenerate_confirm_title))
            },
            text = {
                Text(text = stringResource(CoreR.string.local_access_regenerate_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRegenerateOpen = false
                        onRegenerate()
                    }
                ) {
                    Text(text = stringResource(CoreR.string.local_access_regenerate_token))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerateOpen = false }) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CliDownloadSection(command: String, onCopy: () -> Unit) {
    Column(modifier = Modifier.padding(MaterialTheme.spacings.default)) {
        Text(
            text = stringResource(CoreR.string.local_access_cli_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(CoreR.string.local_access_cli_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.spacings.small),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCopy) { Text(text = stringResource(CoreR.string.copy)) }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun LocalAccessScreenLayoutPreview() {
    FindroidTheme {
        LocalAccessScreenLayout(
            state =
                LocalAccessState(
                    localControlEnabled = true,
                    token = "abcDEF123-example-token_xyz",
                    cliDownloadCommand =
                        "curl http://127.0.0.1:48411/cli -o findroid-cli && chmod +x findroid-cli",
                ),
            onAction = {},
        )
    }
}
