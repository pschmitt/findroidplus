package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.RemoteDeviceInfo
import dev.jdtech.jellyfin.presentation.components.BaseDialog

/**
 * Row + dialog for picking which device a rule/download should apply to (FINDROID-44) - `null` =
 * this device (local, today's default behavior), or one of [otherDevices] to push to instead.
 * Shared between the auto-download rule editor (`AutoDownloadRulesScreen`) and the regular
 * download popup (`DownloadScopeDialog`), modeled 1:1 on `JellyfinServerUserPicker` from the QR
 * export screen (clickable summary row + `BaseDialog` + radio-button list).
 */
@Composable
fun RemoteDevicePicker(
    otherDevices: List<RemoteDeviceInfo>,
    selectedDeviceId: String?,
    onSelected: (String?) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val thisDeviceLabel = stringResource(CoreR.string.remote_config_this_device)
    val selectedLabel = otherDevices.find { it.id == selectedDeviceId }?.name ?: thisDeviceLabel

    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(CoreR.string.remote_config_target_device),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = selectedLabel, style = MaterialTheme.typography.bodySmall)
        }
        Icon(painter = painterResource(CoreR.drawable.ic_chevron_down), contentDescription = null)
    }

    if (showDialog) {
        BaseDialog(
            title = stringResource(CoreR.string.remote_config_target_device),
            onDismiss = { showDialog = false },
        ) { contentPadding ->
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                val options =
                    listOf<Pair<String?, String>>(null to thisDeviceLabel) +
                        otherDevices.map { it.id to it.name }
                for ((deviceId, label) in options) {
                    val isSelected = deviceId == selectedDeviceId
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onSelected(deviceId)
                                    showDialog = false
                                }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelected(deviceId)
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
