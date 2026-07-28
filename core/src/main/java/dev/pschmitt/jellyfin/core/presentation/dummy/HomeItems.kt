package dev.pschmitt.jellyfin.core.presentation.dummy

import dev.pschmitt.jellyfin.models.CollectionType
import dev.pschmitt.jellyfin.models.HomeItem
import dev.pschmitt.jellyfin.models.HomeSection
import dev.pschmitt.jellyfin.models.UiText
import dev.pschmitt.jellyfin.models.View
import java.util.UUID

val dummyHomeSuggestions = HomeItem.Suggestions(id = UUID.randomUUID(), items = dummyMovies)

val dummyHomeSection =
    HomeItem.Section(
        HomeSection(
            id = UUID.randomUUID(),
            name = UiText.DynamicString("Continue watching"),
            items = dummyMovies + dummyEpisodes,
        )
    )

val dummyHomeView =
    HomeItem.ViewItem(
        View(
            id = UUID.randomUUID(),
            name = "Movies",
            items = dummyMovies,
            type = CollectionType.Movies,
        )
    )
