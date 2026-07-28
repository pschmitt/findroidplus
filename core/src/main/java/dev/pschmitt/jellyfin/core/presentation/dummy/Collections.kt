package dev.pschmitt.jellyfin.core.presentation.dummy

import dev.pschmitt.jellyfin.models.CollectionType
import dev.pschmitt.jellyfin.models.FindroidCollection
import dev.pschmitt.jellyfin.models.FindroidImages
import java.util.UUID

private val dummyMoviesCollection =
    FindroidCollection(
        id = UUID.randomUUID(),
        name = "Movies",
        type = CollectionType.Movies,
        images = FindroidImages(),
    )

private val dummyShowsCollection =
    FindroidCollection(
        id = UUID.randomUUID(),
        name = "Shows",
        type = CollectionType.TvShows,
        images = FindroidImages(),
    )

val dummyCollections = listOf(dummyMoviesCollection, dummyShowsCollection)
