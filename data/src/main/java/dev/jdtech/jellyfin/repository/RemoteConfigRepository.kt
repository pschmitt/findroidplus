package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.RemoteDeviceInfo
import java.util.UUID

/**
 * Cross-device remote download/rule push (FINDROID-44): lets a device push work to another device
 * logged into the same Jellyfin account, using Jellyfin's own per-user `DisplayPreferences`
 * custom-data API as the transport - every instance already talks to this same account
 * continuously, so no dedicated relay/server is needed. See
 * [dev.jdtech.jellyfin.models.RemoteConfigCommand] for the wire format.
 */
interface RemoteConfigRepository {
    /** Devices seen via heartbeat for the current account, excluding this device. */
    suspend fun listOtherDevices(): List<RemoteDeviceInfo>

    /**
     * Enqueues a command that replays [AutoDownloadRuleRepository.reconcileRules]'s exact
     * parameters on [targetDeviceId] the next time it syncs, instead of applying them to this
     * device's own Room database. Used by the dedicated auto-download rule editor, which only
     * ever manages persistent rules (no immediate download).
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
    )

    /**
     * Mirrors `ShowViewModel`/`SeasonViewModel`/`EpisodeViewModel`'s local `downloadWithScope`
     * exactly, just directed at [targetDeviceId] instead of this device's own Room/Downloader:
     * enqueues an immediate "download whatever currently matches [seasonIds], right now" command
     * when [seasonIds] isn't empty, and independently enqueues a persistent-rule command when
     * [alsoFollowNew] or [alsoFutureSeasons] is set - the two aren't mutually exclusive locally,
     * so they aren't here either.
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
     * Fetches the shared bucket, applies every command addressed to this device (oldest first, so
     * the latest of several queued pushes for the same series wins), strips applied/expired
     * commands, prunes stale device heartbeats, refreshes this device's own heartbeat, and writes
     * the result back. Idempotent - safe to call from the periodic worker or a manual retry.
     */
    suspend fun syncNow()
}
