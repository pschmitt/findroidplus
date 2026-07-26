package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.models.VideoMetadata

data class MovieState(
    val movie: FindroidMovie? = null,
    val videoMetadata: VideoMetadata? = null,
    val actors: List<FindroidItemPerson> = emptyList(),
    val director: FindroidItemPerson? = null,
    val writers: List<FindroidItemPerson> = emptyList(),
    val dateFormat: String = "system",
    val releasePicker: ReleasePickerState? = null,
    val queueStatus: QueueStatus? = null,
    // Gates the search button - no point offering a Radarr search that can only fail with a
    // toast when Radarr isn't (fully) configured.
    val radarrConfigured: Boolean = false,
    // Drives the pull-to-refresh spinner - separate from `movie == null` (first load, full-screen
    // spinner instead) since a refresh keeps showing the existing content underneath.
    val isRefreshing: Boolean = false,
    val error: Exception? = null,
)
