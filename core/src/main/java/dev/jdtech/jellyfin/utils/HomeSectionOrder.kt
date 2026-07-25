package dev.jdtech.jellyfin.utils

import java.util.UUID

/**
 * Stable identifiers for each independently-reorderable row on the Home screen. Per-library
 * ("Latest <library>") and per-Seerr-discover rows are dynamic, so they're keyed off the
 * underlying library id / string resource id rather than a fixed constant.
 */
object HomeSectionKeys {
    const val SUGGESTIONS = "suggestions"
    const val CONTINUE_WATCHING = "continue_watching"
    const val NEXT_UP = "next_up"
    const val FAVORITES = "favorites"
    const val ACTIVE_DOWNLOADS = "active_downloads"
    private const val VIEW_PREFIX = "view:"
    private const val DISCOVER_PREFIX = "discover:"

    fun view(id: UUID): String = "$VIEW_PREFIX$id"

    fun discover(titleRes: Int): String = "$DISCOVER_PREFIX$titleRes"
}

/**
 * Merges a persisted section-key order with the current "natural" (default) order: keys present
 * in [persisted] keep that relative order; any key in [natural] that [persisted] doesn't mention
 * (a library added since the order was last saved, a section just enabled, ...) is appended at
 * the end in its natural relative order, rather than disappearing or jumping to the front.
 */
fun resolveHomeSectionOrder(natural: List<String>, persisted: List<String>): List<String> {
    val naturalSet = natural.toSet()
    val fromPersisted = persisted.filter { it in naturalSet }.distinct()
    val missing = natural.filterNot { it in fromPersisted }
    return fromPersisted + missing
}

fun homeSectionOrderToString(order: List<String>): String = order.joinToString(",")

fun homeSectionOrderFromString(value: String?): List<String> =
    value?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
