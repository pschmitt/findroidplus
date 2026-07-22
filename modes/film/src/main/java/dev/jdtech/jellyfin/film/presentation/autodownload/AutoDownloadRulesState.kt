package dev.jdtech.jellyfin.film.presentation.autodownload

import dev.jdtech.jellyfin.models.UiText
import java.util.UUID

data class AutoDownloadShowRuleUiModel(
    val seriesId: UUID,
    // every rule id covering this show - a season-specific row per selected season, plus an
    // optional show-level (seasonId == null) row when alsoFutureSeasons is on. These coexist by
    // design; they're not alternatives to each other.
    val ruleIds: List<Long>,
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
    val error: Exception? = null,
)
