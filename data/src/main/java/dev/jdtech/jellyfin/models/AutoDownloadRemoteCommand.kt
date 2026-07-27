package dev.jdtech.jellyfin.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A remote instruction addressed at [targetDeviceId], queued in the shared Jellyfin
 * `DisplayPreferences` bucket (see `RemoteConfigRepository`) until that device's next sync. IDs
 * travel as plain strings (not the project's usual
 * [dev.jdtech.jellyfin.backup.UUIDSerializer]) since this is an ephemeral wire format converted to
 * [java.util.UUID] at the repository boundary, not a persisted model. kotlinx.serialization
 * resolves the concrete subtype from a sealed hierarchy automatically (no `SerializersModule`
 * needed), so the shared queue can hold a mix of every command type below.
 */
@Serializable
sealed interface RemoteConfigCommand {
    val id: String
    val targetDeviceId: String
    val createdAt: Long
    val serverId: String

    /**
     * Persists an ongoing auto-download rule - replays [dev.jdtech.jellyfin.repository
     * .AutoDownloadRuleRepository.reconcileRules]'s own parameters verbatim on the target, rather
     * than modeling add/remove separately (reconcileRules already derives adds/removes from the
     * full [seasonIds] set).
     */
    @Serializable
    @SerialName("reconcile_rules")
    data class ReconcileRules(
        override val id: String,
        override val targetDeviceId: String,
        override val createdAt: Long,
        override val serverId: String,
        val userId: String,
        val seriesId: String,
        val seasonIds: List<String>,
        val alsoFutureSeasons: Boolean,
        val onlyNewEpisodes: Boolean,
        val onlyUnwatched: Boolean,
    ) : RemoteConfigCommand

    /**
     * One-time "download whatever currently matches this scope, right now" - no rule is
     * persisted on the target, mirroring a local bulk download made without "also download new
     * episodes". Applied by evaluating a transient (non-persisted)
     * [dev.jdtech.jellyfin.models.AutoDownloadRuleDto] per season on the target, same as
     * `ShowViewModel`/`SeasonViewModel`/`EpisodeViewModel`'s local `downloadWithScope` does today.
     */
    @Serializable
    @SerialName("evaluate_now")
    data class EvaluateNow(
        override val id: String,
        override val targetDeviceId: String,
        override val createdAt: Long,
        override val serverId: String,
        val userId: String,
        val seriesId: String,
        val seasonIds: List<String>,
        val onlyUnwatched: Boolean,
    ) : RemoteConfigCommand

    /**
     * One-time download of a single already-known item + media source, right now - the "this
     * episode" immediate-download case. The target resolves its own preferred storage index at
     * apply time ([dev.jdtech.jellyfin.utils.Downloader.resolvePreferredStorageIndex]) rather than
     * this carrying one, since the pushing device has no visibility into the target's storage
     * layout.
     */
    @Serializable
    @SerialName("download_item")
    data class DownloadItem(
        override val id: String,
        override val targetDeviceId: String,
        override val createdAt: Long,
        override val serverId: String,
        val itemId: String,
        val sourceId: String,
    ) : RemoteConfigCommand
}

/** Heartbeat entry advertising a device's presence to others sharing the same Jellyfin account. */
@Serializable
data class RemoteDeviceInfo(
    val id: String,
    val name: String,
    val lastSeenMillis: Long,
)
