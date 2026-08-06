package dev.pschmitt.jellyfin.film.presentation.person

import dev.pschmitt.jellyfin.models.JollyfinMovie
import dev.pschmitt.jellyfin.models.JollyfinPerson
import dev.pschmitt.jellyfin.models.JollyfinShow

data class PersonState(
    val person: JollyfinPerson? = null,
    val starredInMovies: List<JollyfinMovie> = emptyList(),
    val starredInShows: List<JollyfinShow> = emptyList(),
    // Drives the pull-to-refresh spinner - separate from `person == null` (first load, full-
    // screen spinner instead) since a refresh keeps showing the existing content underneath.
    val isRefreshing: Boolean = false,
    val error: Exception? = null,
)
