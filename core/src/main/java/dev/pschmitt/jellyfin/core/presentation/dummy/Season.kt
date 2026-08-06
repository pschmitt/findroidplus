package dev.pschmitt.jellyfin.core.presentation.dummy

import dev.pschmitt.jellyfin.models.JollyfinImages
import dev.pschmitt.jellyfin.models.JollyfinSeason
import java.util.UUID

val dummySeason =
    JollyfinSeason(
        id = UUID.randomUUID(),
        name = "Season 1",
        seriesId = UUID.randomUUID(),
        seriesName = "Attack on Titan",
        originalTitle = null,
        overview = "",
        sources = emptyList(),
        indexNumber = 0,
        episodes = emptyList(),
        played = false,
        favorite = false,
        canPlay = true,
        canDownload = false,
        unplayedItemCount = null,
        images = JollyfinImages(),
    )
