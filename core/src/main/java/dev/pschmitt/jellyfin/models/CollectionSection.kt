package dev.pschmitt.jellyfin.models

data class CollectionSection(val id: Int, val name: UiText, var items: List<JollyfinItem>)
