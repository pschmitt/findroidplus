package dev.pschmitt.jellyfin.film.presentation.search

import dev.pschmitt.jellyfin.models.JollyfinItem
import dev.pschmitt.jellyfin.models.QueueStatus
import dev.pschmitt.jellyfin.models.SeerrSearchItem

data class SearchState(
    val items: List<JollyfinItem> = emptyList(),
    val seerrResults: List<SeerrSearchItem> = emptyList(),
    val loading: Boolean = false,
    val seerrSearching: Boolean = false,
    val radarrQueueStatus: Map<Int, QueueStatus> = emptyMap(),
)
