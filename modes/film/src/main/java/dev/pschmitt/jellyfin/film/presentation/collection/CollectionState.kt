package dev.pschmitt.jellyfin.film.presentation.collection

import dev.pschmitt.jellyfin.models.CollectionSection

data class CollectionState(
    val sections: List<CollectionSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
