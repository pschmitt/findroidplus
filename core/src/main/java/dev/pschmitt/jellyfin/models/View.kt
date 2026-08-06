package dev.pschmitt.jellyfin.models

import java.util.UUID

data class View(
    val id: UUID,
    val name: String,
    val items: List<JollyfinItem>,
    val type: CollectionType,
)
