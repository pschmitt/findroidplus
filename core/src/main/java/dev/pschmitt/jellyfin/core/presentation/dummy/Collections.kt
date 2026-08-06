package dev.pschmitt.jellyfin.core.presentation.dummy

import dev.pschmitt.jellyfin.models.CollectionType
import dev.pschmitt.jellyfin.models.JollyfinCollection
import dev.pschmitt.jellyfin.models.JollyfinImages
import java.util.UUID

private val dummyMoviesCollection =
    JollyfinCollection(
        id = UUID.randomUUID(),
        name = "Movies",
        type = CollectionType.Movies,
        images = JollyfinImages(),
    )

private val dummyShowsCollection =
    JollyfinCollection(
        id = UUID.randomUUID(),
        name = "Shows",
        type = CollectionType.TvShows,
        images = JollyfinImages(),
    )

val dummyCollections = listOf(dummyMoviesCollection, dummyShowsCollection)
