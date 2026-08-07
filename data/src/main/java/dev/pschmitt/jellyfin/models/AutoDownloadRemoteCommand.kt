package dev.pschmitt.jellyfin.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A remote instruction addressed at [targetDeviceId], queued in the shared Jellyfin
 * `DisplayPreferences` bucket (see `RemoteConfigRepository`) until that device's next sync. IDs
 * travel as plain strings (not the project's usual [dev.pschmitt.jellyfin.backup.UUIDSerializer])
 * since this is an ephemeral wire format converted to [java.util.UUID] at the repository boundary,
 * not a persisted model. kotlinx.serialization resolves the concrete subtype from a sealed
 * hierarchy automatically (no `SerializersModule` needed), so the shared queue can hold a mix of
 * every command type below.
 *
 * [originDeviceId] identifies which device enqueued this - lets that same device later list and
 * cancel its own still-pending pushes (see
 * `RemoteConfigRepository.listPendingCommandsFromThisDevice` /`cancelPendingCommand`).
 * [displayName] is a human-readable label (show or item name) resolved once at push time by the
 * repository, not re-resolved by whoever renders a pending-commands list later - avoids extra
 * Jellyfin API calls just to render management UI, and survives the referenced item being
 * renamed/deleted server-side in the meantime.
 */
@Serializable
sealed interface RemoteConfigCommand {
    val id: String
    val targetDeviceId: String
    val originDeviceId: String
    val createdAt: Long
    val serverId: String
    val displayName: String

    /**
     * Persists an ongoing auto-download rule - replays
     * [dev.pschmitt.jellyfin.repository .AutoDownloadRuleRepository.reconcileRules]'s own
     * parameters verbatim on the target, rather than modeling add/remove separately (reconcileRules
     * already derives adds/removes from the full [seasonIds] set - an empty [seasonIds] with
     * [alsoFutureSeasons] false clears the rule entirely, which is also how a remote "remove rule"
     * push works - `RemoteConfigRepository.pushRemoveRule`).
     */
    @Serializable
    @SerialName("reconcile_rules")
    data class ReconcileRules(
        override val id: String,
        override val targetDeviceId: String,
        override val originDeviceId: String,
        override val createdAt: Long,
        override val serverId: String,
        override val displayName: String,
        val userId: String,
        val seriesId: String,
        val seasonIds: List<String>,
        val alsoFutureSeasons: Boolean,
        val onlyNewEpisodes: Boolean,
        val onlyUnwatched: Boolean,
        // FINDROID-59: only meaningful when this command clears the rule entirely (empty
        // [seasonIds] and [alsoFutureSeasons] false) - tells the *applying* device to also delete
        // its own already-downloaded episodes for [seriesId], mirroring the local "also delete
        // downloaded episodes" checkbox. Defaults to false so a command already queued by (or
        // decoded on) a not-yet-upgraded device still round-trips fine.
        val alsoDeleteDownloads: Boolean = false,
    ) : RemoteConfigCommand

    /**
     * One-time "download whatever currently matches this scope, right now" - no rule is persisted
     * on the target, mirroring a local bulk download made without "also download new episodes".
     * Applied by evaluating a transient (non-persisted)
     * [dev.pschmitt.jellyfin.models.AutoDownloadRuleDto] per season on the target, same as
     * `ShowViewModel`/`SeasonViewModel`/`EpisodeViewModel`'s local `downloadWithScope` does today.
     */
    @Serializable
    @SerialName("evaluate_now")
    data class EvaluateNow(
        override val id: String,
        override val targetDeviceId: String,
        override val originDeviceId: String,
        override val createdAt: Long,
        override val serverId: String,
        override val displayName: String,
        val userId: String,
        val seriesId: String,
        val seasonIds: List<String>,
        val onlyUnwatched: Boolean,
    ) : RemoteConfigCommand

    /**
     * One-time download of a single already-known item + media source, right now - the "this
     * episode" immediate-download case. The target resolves its own preferred storage index at
     * apply time ([dev.pschmitt.jellyfin.utils.Downloader.resolvePreferredStorageIndex]) rather
     * than this carrying one, since the pushing device has no visibility into the target's storage
     * layout.
     */
    @Serializable
    @SerialName("download_item")
    data class DownloadItem(
        override val id: String,
        override val targetDeviceId: String,
        override val originDeviceId: String,
        override val createdAt: Long,
        override val serverId: String,
        override val displayName: String,
        val itemId: String,
        val sourceId: String,
    ) : RemoteConfigCommand

    /**
     * Pauses/resumes [seriesId]'s existing rule scope on [targetDeviceId] without changing it - the
     * remote counterpart to the local per-show enable/disable `Switch`
     * (`AutoDownloadRulesViewModel.toggleShowRule` /
     * `AutoDownloadRuleRepository.setRulesEnabledForShow`).
     */
    @Serializable
    @SerialName("set_rule_enabled")
    data class SetRuleEnabled(
        override val id: String,
        override val targetDeviceId: String,
        override val originDeviceId: String,
        override val createdAt: Long,
        override val serverId: String,
        override val displayName: String,
        val userId: String,
        val seriesId: String,
        val enabled: Boolean,
    ) : RemoteConfigCommand
}

/**
 * A currently-active auto-download rule on the publishing device, for a "what's live on device X"
 * management view - published as part of that device's own [RemoteDeviceInfo] heartbeat, refreshed
 * on every `syncNow()`. [seasonCount] and [alsoFutureSeasons] mirror the same summary
 * `AutoDownloadRuleRepository.toExistingScope()` already computes for the local rules screen.
 */
@Serializable
data class RemoteActiveRuleSummary(
    val serverId: String,
    val seriesId: String,
    val showName: String,
    val seasonCount: Int,
    val alsoFutureSeasons: Boolean,
    // Whether this show's rule scope is currently enabled (vs paused) - defaults true so a
    // summary published by an older, not-yet-upgraded device still round-trips as "on" instead of
    // silently rendering a remote toggle as off.
    val enabled: Boolean = true,
)

/** Heartbeat entry advertising a device's presence to others sharing the same Jellyfin account. */
@Serializable
data class RemoteDeviceInfo(
    val id: String,
    val name: String,
    val lastSeenMillis: Long,
    val activeRules: List<RemoteActiveRuleSummary> = emptyList(),
)
