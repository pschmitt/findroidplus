package dev.jdtech.jellyfin.presentation.settings.localaccess

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.localcontrol.PairedClient
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatRelativeTime

@Composable
fun LocalAccessScreen(navigateBack: () -> Unit, viewModel: LocalAccessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(true) { viewModel.load() }

    LocalAccessScreenLayout(
        state = state,
        onAction = { action ->
            if (action is LocalAccessAction.OnBackClick) {
                navigateBack()
            } else {
                if (action is LocalAccessAction.RevokeClient) {
                    Toast.makeText(
                            context,
                            CoreR.string.local_access_revoke_toast,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalAccessScreenLayout(state: LocalAccessState, onAction: (LocalAccessAction) -> Unit) {
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    LocalControlToggleRow(
                        enabled = state.localControlEnabled,
                        onToggle = { onAction(LocalAccessAction.SetLocalControlEnabled(it)) },
                    )
                    HorizontalDivider()
                }
                if (state.pairedClients.isEmpty() && !state.isLoading) {
                    item {
                        Text(
                            text = stringResource(CoreR.string.local_access_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(MaterialTheme.spacings.default),
                        )
                    }
                }
                items(items = state.pairedClients, key = { it.clientId }) { client ->
                    PairedClientRow(client = client, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun LocalControlToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacings.default, vertical = MaterialTheme.spacings.small),
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
}

@Composable
private fun PairedClientRow(client: PairedClient, onAction: (LocalAccessAction) -> Unit) {
    var confirmOpen by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacings.default, vertical = MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = client.packageName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(CoreR.string.local_access_paired_at, formatRelativeTime(client.pairedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { confirmOpen = true }) {
            Icon(painter = painterResource(CoreR.drawable.ic_trash), contentDescription = null)
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text(text = stringResource(CoreR.string.local_access_revoke_confirm_title)) },
            text = {
                Text(
                    text =
                        stringResource(
                            CoreR.string.local_access_revoke_confirm_message,
                            client.packageName,
                        )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOpen = false
                        onAction(LocalAccessAction.RevokeClient(client.clientId))
                    }
                ) {
                    Text(text = stringResource(CoreR.string.download_scope_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            },
        )
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
                    pairedClients =
                        listOf(
                            PairedClient(
                                clientId = "1",
                                uid = 10123,
                                packageName = "com.termux",
                                tokenHash = "",
                                pairedAt = System.currentTimeMillis(),
                            )
                        ),
                ),
            onAction = {},
        )
    }
}
