package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.RadarrApi
import dev.pschmitt.jellyfin.api.pvr.SonarrApi
import dev.pschmitt.jellyfin.api.pvr.SonarrCalendarEntry
import dev.pschmitt.jellyfin.models.CalendarResult
import dev.pschmitt.jellyfin.models.JollyfinMovie
import dev.pschmitt.jellyfin.models.JollyfinShow
import dev.pschmitt.jellyfin.models.PvrFetchError
import dev.pschmitt.jellyfin.models.PvrSource
import dev.pschmitt.jellyfin.models.SeerrMediaType
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

/**
 * [resolveSonarrConfig]/[resolveRadarrConfig] resolve the active profile's effective Sonarr/Radarr
 * config from `core`'s `PvrConfigResolver` - passed in as plain lambdas (rather than depending on
 * `PvrConfigResolver` directly) because that type lives in `core`, which depends on `data`, not the
 * other way around. Same pattern as `QueueStatusRepositoryImpl`.
 *
 * Constructed via [dev.pschmitt.jellyfin.di.CalendarModule] (a Hilt `@Provides`) rather than an
 * `@Inject` constructor, since `data` has no Hilt plugin.
 *
 * Simpler than `QueueStatusRepositoryImpl`: Sonarr/Radarr's calendar entries already embed the
 * provider id per entry (`includeSeries=true` for Sonarr, full movie object for Radarr), so there's
 * no separate series/movie-list fetch+join needed, and there's no polling loop - see
 * `CalendarRepository`'s doc for why.
 *
 * [loadJellyfinShows]/[loadJellyfinMovies] fetch the *live* library via
 * [JellyfinRepository.getItems] rather than the local Room cache: that cache only holds items the
 * user has actually downloaded, which is a poor match candidate set for "what's coming up" - most
 * upcoming episodes/movies won't be downloaded yet, so matching against it would leave nearly every
 * entry's [CalendarEntry.itemId] (and thus poster/click-through) unresolved. Confirmed this was
 * really happening on a fresh install (empty local `shows`/`movies` tables). Note
 * [QueueStatusRepositoryImpl] still has this bug - it wasn't in scope to fix here, but shares the
 * exact same local-cache pattern and should get the same treatment.
 */
class CalendarRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val jellyfinRepository: JellyfinRepository,
    private val seerrRepository: SeerrRepository,
    private val resolveSonarrConfig: () -> PvrClientConfig?,
    private val resolveRadarrConfig: () -> PvrClientConfig?,
) : CalendarRepository {

    override suspend fun getUpcoming(daysBack: Int, daysForward: Int): CalendarResult =
        coroutineScope {
            val today = LocalDate.now()
            val start = today.minusDays(daysBack.toLong())
            val end = today.plusDays(daysForward.toLong())

            // Each service is independently try/caught inside its own fetch function - a failure
            // in one must never blank out or crash the other's contribution to the merged list.
            val sonarrDeferred = async { fetchSonarrCalendar(start, end) }
            val radarrDeferred = async { fetchRadarrCalendar(start, end) }
            val sonarr = sonarrDeferred.await()
            val radarr = radarrDeferred.await()
            CalendarResult(
                entries = enrichPosters((sonarr.entries + radarr.entries).sortedBy { it.date }),
                errors = sonarr.errors + radarr.errors,
            )
        }

    private suspend fun enrichPosters(entries: List<dev.pschmitt.jellyfin.models.CalendarEntry>) =
        coroutineScope {
            entries
                .map { entry ->
                    async {
                        if (entry.images?.primary != null || entry.tmdbId == null) entry
                        else {
                            val type =
                                if (entry.source == PvrSource.RADARR) SeerrMediaType.MOVIE
                                else SeerrMediaType.TV
                            val seerrPoster =
                                seerrRepository
                                    .getDetails(entry.tmdbId, type)
                                    .getOrNull()
                                    ?.posterUrl
                            entry.copy(posterUrl = seerrPoster ?: entry.posterUrl)
                        }
                    }
                }
                .awaitAll()
        }

    private suspend fun fetchSonarrCalendar(start: LocalDate, end: LocalDate): CalendarResult {
        val config = resolveSonarrConfig() ?: return CalendarResult()
        if (!config.enabled) return CalendarResult()
        val baseUrl = config.baseUrl
        val apiKey = config.apiKey
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return CalendarResult()

        return try {
            val api = SonarrApi(baseUrl, apiKey)
            val entries = api.getCalendar(start, end)
            val shows = loadJellyfinShows()
            CalendarResult(entries = matchSonarrCalendar(entries, attachSeasons(shows, entries)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Sonarr calendar")
            CalendarResult(errors = listOf(fetchError(PvrSource.SONARR, "Sonarr", e)))
        }
    }

    private suspend fun fetchRadarrCalendar(start: LocalDate, end: LocalDate): CalendarResult {
        val config = resolveRadarrConfig() ?: return CalendarResult()
        if (!config.enabled) return CalendarResult()
        val baseUrl = config.baseUrl
        val apiKey = config.apiKey
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return CalendarResult()

        return try {
            val api = RadarrApi(baseUrl, apiKey)
            val entries = api.getCalendar(start, end)
            val movies = loadJellyfinMovies()
            CalendarResult(entries = matchRadarrCalendar(entries, movies, start, end))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Radarr calendar")
            CalendarResult(errors = listOf(fetchError(PvrSource.RADARR, "Radarr", e)))
        }
    }

    private fun fetchError(source: PvrSource, serviceName: String, e: Exception): PvrFetchError =
        PvrFetchError(
            source = source,
            message = mapPvrSearchError(serviceName, e).message ?: "$serviceName request failed",
        )

    /**
     * [jellyfinRepository.getItems] never populates [JollyfinShow.seasons] (it always defaults to
     * empty - true of every mapping path, not just this one), so [matchSonarrCalendar]'s season
     * lookup would silently never resolve, leaving every entry's `itemId` null and the calendar row
     * permanently unclickable. Fetches seasons only for shows the calendar actually references
     * (matched by tvdbId), not the whole library, since a full-library season fetch would be one
     * request per show.
     */
    private suspend fun attachSeasons(
        shows: List<JollyfinShow>,
        entries: List<SonarrCalendarEntry>,
    ): List<JollyfinShow> = coroutineScope {
        val referencedTvdbIds =
            entries
                .mapNotNull { it.series?.tvdbId?.takeIf { id -> id != 0 } }
                .map { it.toString() }
                .toSet()
        val (referenced, rest) = shows.partition { it.tvdbId in referencedTvdbIds }
        val withSeasons =
            referenced
                .map { show ->
                    async {
                        val seasons = jellyfinRepository.getSeasons(show.id)
                        show.copy(
                            seasons =
                                seasons.map { season ->
                                    season.copy(
                                        episodes =
                                            jellyfinRepository.getEpisodes(show.id, season.id)
                                    )
                                }
                        )
                    }
                }
                .awaitAll()
        withSeasons + rest
    }

    private suspend fun loadJellyfinShows(): List<JollyfinShow> =
        jellyfinRepository
            .getItems(includeTypes = listOf(BaseItemKind.SERIES), recursive = true)
            .filterIsInstance<JollyfinShow>()

    private suspend fun loadJellyfinMovies(): List<JollyfinMovie> =
        jellyfinRepository
            .getItems(includeTypes = listOf(BaseItemKind.MOVIE), recursive = true)
            .filterIsInstance<JollyfinMovie>()
}
