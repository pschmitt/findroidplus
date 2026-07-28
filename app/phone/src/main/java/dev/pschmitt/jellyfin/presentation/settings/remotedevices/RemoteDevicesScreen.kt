package dev.pschmitt.jellyfin.presentation.settings.remotedevices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.models.FindroidShow
import dev.pschmitt.jellyfin.models.RemoteActiveRuleSummary
import dev.pschmitt.jellyfin.models.RemoteConfigCommand
import dev.pschmitt.jellyfin.models.RemoteDeviceInfo
import dev.pschmitt.jellyfin.presentation.film.components.ClearDownloadsDialog
import dev.pschmitt.jellyfin.presentation.film.components.Direction
import dev.pschmitt.jellyfin.presentation.film.components.ItemPoster
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.formatRelativeTime

// This file used to also hold a standalone RemoteDevicesScreen/RemoteDevicesViewModel-backed
// layout - it's now folded into AutoDownloadRulesScreen (FINDROID-54: the two screens were "a
// show + season scope + toggle/remove" for different devices, so one merged screen replaces
// both). What's left here are the reusable, non-private row/section composables the merged
// screen imports; RemoteDevicesViewModel/RemoteDevicesState/RemoteDevicesAction are unchanged and
// still live alongside this file.

@Composable
fun RemoteManagementToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacings.default, vertical = MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(CoreR.string.remote_devices_allow_management_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(CoreR.string.remote_devices_allow_management_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun DeviceSection(
    device: RemoteDeviceInfo,
    showsBySeriesId: Map<String, FindroidShow>,
    onAction: (RemoteDevicesAction) -> Unit,
) {
    Column(
        modifier =
            Modifier.padding(
                horizontal = MaterialTheme.spacings.default,
                vertical = MaterialTheme.spacings.small,
            )
    ) {
        Text(text = device.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text =
                stringResource(
                    CoreR.string.remote_devices_last_seen,
                    formatRelativeTime(device.lastSeenMillis),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (device.activeRules.isEmpty()) {
            Text(
                text = stringResource(CoreR.string.remote_devices_no_active_rules),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = MaterialTheme.spacings.small),
            )
        } else {
            device.activeRules.forEach { rule ->
                ActiveRuleRow(
                    device = device,
                    rule = rule,
                    show = showsBySeriesId[rule.seriesId],
                    onAction = onAction,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = MaterialTheme.spacings.small))
    }
}

@Composable
private fun ActiveRuleRow(
    device: RemoteDeviceInfo,
    rule: RemoteActiveRuleSummary,
    show: FindroidShow?,
    onAction: (RemoteDevicesAction) -> Unit,
) {
    var confirmOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (show != null) {
            ItemPoster(
                item = show,
                direction = Direction.VERTICAL,
                modifier = Modifier.width(40.dp).clip(RoundedCornerShape(MaterialTheme.spacings.small)),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = rule.showName, style = MaterialTheme.typography.bodyLarge)
            val scopeParts = mutableListOf<String>()
            if (rule.seasonCount > 0) {
                scopeParts +=
                    pluralStringResource(
                        CoreR.plurals.remote_devices_season_count,
                        rule.seasonCount,
                        rule.seasonCount,
                    )
            }
            if (rule.alsoFutureSeasons) {
                scopeParts +=
                    if (rule.seasonCount > 0) {
                        stringResource(CoreR.string.remote_devices_also_future_seasons)
                    } else {
                        stringResource(CoreR.string.remote_devices_future_seasons_only)
                    }
            }
            Text(
                text = scopeParts.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { confirmOpen = true }) {
            Icon(painter = painterResource(CoreR.drawable.ic_trash), contentDescription = null)
        }
    }

    // FINDROID-59: reuses the same ClearDownloadsDialog the local "delete auto-download rule"
    // flow uses (AutoDownloadRulesScreen's AutoDownloadShowRuleRow), so removing a rule pushed to
    // another device gets the same "also delete downloaded episodes" choice - it just executes on
    // the target device once it applies the resulting clear command, rather than on this one.
    if (confirmOpen) {
        ClearDownloadsDialog(
            title = stringResource(CoreR.string.remote_devices_remove_rule_confirm_title),
            message =
                stringResource(
                    CoreR.string.remote_devices_remove_rule_confirm_message,
                    rule.showName,
                    device.name,
                ),
            checkboxLabel = stringResource(CoreR.string.also_delete_downloaded_episodes),
            checkboxSummary =
                stringResource(
                    CoreR.string.remote_devices_also_delete_downloaded_episodes_summary,
                    device.name,
                ),
            checkboxDefault = false,
            onConfirm = { alsoDeleteDownloads ->
                confirmOpen = false
                onAction(
                    RemoteDevicesAction.RemoveActiveRule(
                        targetDeviceId = device.id,
                        serverId = rule.serverId,
                        seriesId = rule.seriesId,
                        alsoDeleteDownloads = alsoDeleteDownloads,
                    )
                )
            },
            onDismiss = { confirmOpen = false },
        )
    }
}

@Composable
fun PendingCommandRow(
    command: RemoteConfigCommand,
    devices: List<RemoteDeviceInfo>,
    onAction: (RemoteDevicesAction) -> Unit,
) {
    val targetName = devices.find { it.id == command.targetDeviceId }?.name ?: command.targetDeviceId
    val suffixRes =
        when (command) {
            is RemoteConfigCommand.ReconcileRules -> CoreR.string.remote_devices_command_reconcile_suffix
            is RemoteConfigCommand.EvaluateNow -> CoreR.string.remote_devices_command_evaluate_suffix
            is RemoteConfigCommand.DownloadItem -> CoreR.string.remote_devices_command_download_suffix
        }
    val suffixText = stringResource(suffixRes)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacings.default, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = command.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(CoreR.string.remote_devices_pending_to, suffixText, targetName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onAction(RemoteDevicesAction.CancelPendingCommand(command.id)) }
        ) {
            Icon(painter = painterResource(CoreR.drawable.ic_x), contentDescription = null)
        }
    }
}
