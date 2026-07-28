package dev.pschmitt.jellyfin.core.presentation.downloader

import java.util.UUID

/**
 * What to download, chosen from [dev.pschmitt.jellyfin.presentation.film.components.DownloadScopeDialog].
 * [thisEpisodeOnly] triggers the normal single-item download flow instead of a bulk queue, and is
 * exclusive with [seasonIds]/[alsoFutureSeasons]. The download-scope dialog itself only exposes a
 * single "automatically download new episodes" toggle that drives both [alsoFutureSeasons] and
 * the separate `alsoFollowNew` flag together - from the user's point of view "keep this show up to
 * date" shouldn't require understanding the season/episode distinction. The more advanced
 * per-rule editor (`AutoDownloadRulesScreen`) still lets the two be configured independently,
 * which is why they remain separate fields here rather than being collapsed into one.
 */
data class DownloadSelection(
    val thisEpisodeOnly: Boolean = false,
    val seasonIds: Set<UUID> = emptySet(),
    // Watch for and auto-download brand new seasons of this show as they're released. Implies
    // persisting a rule - a one-off "future seasons" download is a no-op by definition, since
    // there's nothing to download yet.
    val alsoFutureSeasons: Boolean = false,
)
