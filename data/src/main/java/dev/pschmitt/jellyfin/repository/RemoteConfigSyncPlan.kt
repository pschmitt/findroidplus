package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.models.RemoteActiveRuleSummary
import dev.pschmitt.jellyfin.models.RemoteConfigCommand
import dev.pschmitt.jellyfin.models.RemoteDeviceInfo

// Dead-letter TTL for a command nobody ever picked up (target uninstalled/reset, or never added
// the relevant server) - generous since this is a manual, low-frequency feature.
const val REMOTE_CONFIG_MAX_COMMAND_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

// How long a device's heartbeat is trusted before it's pruned from the registry (and any command
// still addressed to it dropped as dead-letter alongside it).
const val REMOTE_CONFIG_DEVICE_TTL_MILLIS = 90L * 24 * 60 * 60 * 1000

data class RemoteConfigSyncPlan(
    val commandsToApply: List<RemoteConfigCommand>,
    val remainingCommands: List<RemoteConfigCommand>,
    val newDevices: List<RemoteDeviceInfo>,
)

/**
 * Pure decision logic behind `RemoteConfigRepositoryImpl.syncNow` (lives in `core`, since applying
 * a command needs `Downloader`/`AutoDownloadRuleEvaluator`, which `data` can't depend on - see
 * that class's kdoc). Split out so it's unit-testable without faking
 * `JellyfinRepository`/`ServerDatabaseDao` - same "extract the matching/branching logic into a
 * plain function" approach as `matchSonarr`/`matchRadarr` in `QueueStatusRepositoryImpl`.
 * [hasServer] answers "has this device added this server locally yet" (backed by
 * `ServerDatabaseDao.get` in production). Deliberately agnostic to which [RemoteConfigCommand]
 * subtype it's handling - queueing/expiry/device-pruning is identical regardless of command type.
 */
fun planRemoteConfigSync(
    thisDeviceId: String,
    thisDeviceName: String,
    now: Long,
    allCommands: List<RemoteConfigCommand>,
    devices: List<RemoteDeviceInfo>,
    hasServer: (serverId: String) -> Boolean,
    thisDeviceActiveRules: List<RemoteActiveRuleSummary> = emptyList(),
): RemoteConfigSyncPlan {
    val applied = mutableSetOf<String>()
    val toApply = mutableListOf<RemoteConfigCommand>()
    allCommands
        .filter { it.targetDeviceId == thisDeviceId }
        .sortedBy { it.createdAt }
        .forEach { command ->
            when {
                now - command.createdAt > REMOTE_CONFIG_MAX_COMMAND_AGE_MILLIS -> applied += command.id
                !hasServer(command.serverId) -> Unit // stays queued - server not added here yet
                else -> {
                    toApply += command
                    applied += command.id
                }
            }
        }

    val prunedDevices =
        devices.filter { it.id == thisDeviceId || now - it.lastSeenMillis <= REMOTE_CONFIG_DEVICE_TTL_MILLIS }
    // Only devices *positively known* to be stale (present in the registry, past their TTL) get
    // their queued commands dead-lettered alongside them. A target simply absent from the
    // registry - e.g. it hasn't run its very first sync yet - is not the same thing as stale, so
    // its commands stay queued rather than being dropped on a technicality.
    val staleDeviceIds = (devices.map { it.id }.toSet() - prunedDevices.map { it.id }.toSet())
    val remaining =
        allCommands
            .filterNot { it.id in applied }
            .filterNot { now - it.createdAt > REMOTE_CONFIG_MAX_COMMAND_AGE_MILLIS }
            .filterNot { it.targetDeviceId in staleDeviceIds }

    val newDevices =
        prunedDevices.filterNot { it.id == thisDeviceId } +
            RemoteDeviceInfo(
                id = thisDeviceId,
                name = thisDeviceName,
                lastSeenMillis = now,
                activeRules = thisDeviceActiveRules,
            )

    return RemoteConfigSyncPlan(toApply, remaining, newDevices)
}
