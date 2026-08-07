package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.models.RemoteConfigCommand
import dev.pschmitt.jellyfin.models.RemoteDeviceInfo
import java.util.UUID

/**
 * Cross-device remote download/rule push (FINDROID-44): lets a device push work to another device
 * logged into the same Jellyfin account, using Jellyfin's own per-user `DisplayPreferences`
 * custom-data API as the transport - every instance already talks to this same account
 * continuously, so no dedicated relay/server is needed. See
 * [dev.pschmitt.jellyfin.models.RemoteConfigCommand] for the wire format.
 */
interface RemoteConfigRepository {
    /** Devices seen via heartbeat for the current account, excluding this device. */
    suspend fun listOtherDevices(): List<RemoteDeviceInfo>

    /**
     * Enqueues a command that replays [AutoDownloadRuleRepository.reconcileRules]'s exact
     * parameters on [targetDeviceId] the next time it syncs, instead of applying them to this
     * device's own Room database. Used by the dedicated auto-download rule editor, which only ever
     * manages persistent rules (no immediate download).
     */
    suspend fun pushRuleUpdate(
        targetDeviceId: String,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonIds: Set<UUID>,
        alsoFutureSeasons: Boolean,
        onlyNewEpisodes: Boolean,
        onlyUnwatched: Boolean,
        alsoDeleteDownloads: Boolean = false,
    )

    /**
     * Mirrors `ShowViewModel`/`SeasonViewModel`/`EpisodeViewModel`'s local `downloadWithScope`
     * exactly, just directed at [targetDeviceId] instead of this device's own Room/Downloader:
     * enqueues an immediate "download whatever currently matches [seasonIds], right now" command
     * when [seasonIds] isn't empty, and independently enqueues a persistent-rule command when
     * [alsoFollowNew] or [alsoFutureSeasons] is set - the two aren't mutually exclusive locally, so
     * they aren't here either.
     */
    suspend fun pushDownloadWithScope(
        targetDeviceId: String,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonIds: Set<UUID>,
        alsoFollowNew: Boolean,
        alsoFutureSeasons: Boolean,
        onlyUnwatched: Boolean,
    )

    /**
     * Enqueues an immediate single-item download on [targetDeviceId] - the "this episode"
     * immediate-download case, which has no season/rule scope at all.
     */
    suspend fun pushItemDownload(
        targetDeviceId: String,
        serverId: String,
        itemId: UUID,
        sourceId: String,
    )

    /**
     * Enqueues a command that clears every rule for [seriesId] on [targetDeviceId] - just
     * [pushRuleUpdate] with an empty scope, since `reconcileRules` already treats that as "delete
     * everything for this series." The management-focused counterpart to [pushRuleUpdate]: lets a
     * controller remove a rule it (or anyone else) previously pushed, without visiting the target
     * device. When [alsoDeleteDownloads] is set, the *target* device also deletes its own
     * already-downloaded episodes for [seriesId] once it applies the clear - mirrors the local
     * "also delete downloaded episodes" checkbox, just executed on the other device instead of this
     * one.
     */
    suspend fun pushRemoveRule(
        targetDeviceId: String,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        alsoDeleteDownloads: Boolean = false,
    )

    /**
     * Enqueues a command that pauses/resumes [seriesId]'s existing rule scope on [targetDeviceId]
     * without changing it - the remote counterpart to the local per-show enable/disable `Switch`.
     * See [dev.pschmitt.jellyfin.repository.AutoDownloadRuleRepository.setRulesEnabledForShow],
     * which the target applies this command with.
     */
    suspend fun pushSetRuleEnabled(
        targetDeviceId: String,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        enabled: Boolean,
    )

    /**
     * Commands in the shared queue that *this* device enqueued (via any of the `push*` methods
     * above) and that haven't been applied by their target yet - lets a controller see and cancel
     * its own still-pending pushes before they land.
     */
    suspend fun listPendingCommandsFromThisDevice(): List<RemoteConfigCommand>

    /**
     * Removes a still-pending command (by [commandId]) from the shared queue before it's applied.
     */
    suspend fun cancelPendingCommand(commandId: String)

    /**
     * Whether this device currently allows *other* devices to manage it (push to it, list it as a
     * target, see its active rules) - the per-device opt-out. Doesn't affect this device's own
     * ability to push to others, which is this device's own action rather than something done to it
     * without consent.
     */
    fun isRemoteManagementEnabled(): Boolean

    /**
     * Flips the opt-out. Turning it off takes effect immediately, not just on the next sync:
     * removes this device's own entry from the shared registry right away (rather than waiting up
     * to [dev.pschmitt.jellyfin.repository.REMOTE_CONFIG_DEVICE_TTL_MILLIS] for it to go stale) and
     * drops any commands still queued *for* this device (commands this device queued *for others*
     * are left alone - opting out is about not being managed, not about withdrawing pushes already
     * sent elsewhere). [syncNow] no-ops entirely while disabled, so this device stays absent from
     * the registry until re-enabled.
     */
    suspend fun setRemoteManagementEnabled(enabled: Boolean)

    /**
     * Forcibly removes [deviceId]'s registry entry, and any commands still queued *for* it, right
     * away - rather than waiting up to
     * [dev.pschmitt.jellyfin.repository.REMOTE_CONFIG_DEVICE_TTL_MILLIS] for [syncNow] to prune it
     * automatically. For a device that's gone for good (uninstalled, factory reset, or that had its
     * identity overwritten by restoring a backup taken on a different physical device) and so will
     * never sync again to prune or remove itself. Commands [deviceId] itself originated, targeting
     * *other* devices, are left alone - same reasoning as [setRemoteManagementEnabled].
     */
    suspend fun removeDevice(deviceId: String)

    /**
     * Fetches the shared bucket, applies every command addressed to this device (oldest first, so
     * the latest of several queued pushes for the same series wins), strips applied/expired
     * commands, prunes stale device heartbeats, refreshes this device's own heartbeat (including a
     * summary of its own currently-active auto-download rules, for the remote-devices management
     * screen), and writes the result back. Idempotent - safe to call from the periodic worker or a
     * manual retry. A no-op while [isRemoteManagementEnabled] is false.
     */
    suspend fun syncNow()
}
