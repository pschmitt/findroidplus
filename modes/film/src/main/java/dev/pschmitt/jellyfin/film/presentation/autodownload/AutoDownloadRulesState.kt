package dev.pschmitt.jellyfin.film.presentation.autodownload

import dev.pschmitt.jellyfin.models.JollyfinShow
import dev.pschmitt.jellyfin.models.UiText
import java.util.UUID

data class AutoDownloadShowRuleUiModel(
    val seriesId: UUID,
    // every rule id covering this show - a season-specific row per selected season, plus an
    // optional show-level (seasonId == null) row when alsoFutureSeasons is on. These coexist by
    // design; they're not alternatives to each other.
    val ruleIds: List<Long>,
    val show: JollyfinShow,
    val showName: String,
    val enabled: Boolean,
    val seasonIds: Set<UUID>,
    val alsoFutureSeasons: Boolean,
    val scopeLabel: UiText,
    val onlyNewEpisodes: Boolean,
    val onlyUnwatched: Boolean,
    // Local disk usage of the episodes this rule's scope covers - the whole show's downloads for
    // a future-seasons-only rule (seasonIds empty), otherwise just the tracked seasons'.
    val downloadedSizeBytes: Long = 0,
    // Path of one of the local sources contributing to downloadedSizeBytes, purely so the UI can
    // show the right internal/removable-storage icon; null when downloadedSizeBytes is 0.
    val downloadedSamplePath: String? = null,
)

data class AutoDownloadRulesState(
    val shows: List<AutoDownloadShowRuleUiModel> = emptyList(),
    val isLoading: Boolean = false,
    // Pull-to-refresh spinner, distinct from isLoading (the first full-screen load) - set while a
    // manual refresh drives an immediate RemoteConfigRepository.syncNow() (rather than waiting on
    // RemoteConfigWorker's 15-minute WorkManager floor) before reloading local rules.
    val isRefreshing: Boolean = false,
    val error: Exception? = null,
)

// One-shot feedback for pushing a rule to another device (FINDROID-44) - unlike a local save,
// there's otherwise no signal that anything actually went out over the network, so this is shown
// as a toast, same channel-based one-shot pattern as SearchEvent/DeleteItemEvent.
sealed interface AutoDownloadRuleEvent {
    data class RuleSentToDevice(val deviceName: String) : AutoDownloadRuleEvent
}
