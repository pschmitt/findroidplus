package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.RadarrApi
import dev.jdtech.jellyfin.api.pvr.SonarrApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * [sonarrApiKeyProvider]/[radarrApiKeyProvider] resolve the current secret from
 * `SecureCredentialStore` - passed in as plain lambdas (rather than depending on
 * `SecureCredentialStore` directly) because that type lives in `core`, which depends on `data`,
 * not the other way around. Same pattern as `QueueStatusRepositoryImpl`.
 *
 * Constructed via [dev.jdtech.jellyfin.di.CalendarModule] (a Hilt `@Provides`) rather than an
 * `@Inject` constructor, since `data` has no Hilt plugin.
 *
 * Simpler than `QueueStatusRepositoryImpl`: Sonarr/Radarr's calendar entries already embed the
 * provider id per entry (`includeSeries=true` for Sonarr, full movie object for Radarr), so there's
 * no separate series/movie-list fetch+join needed, and there's no polling loop - see
 * `CalendarRepository`'s doc for why.
 */
class CalendarRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val serverDatabase: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val sonarrApiKeyProvider: () -> String?,
    private val radarrApiKeyProvider: () -> String?,
) : CalendarRepository {

    override suspend fun getUpcoming(daysBack: Int, daysForward: Int): List<CalendarEntry> =
        coroutineScope {
            val today = LocalDate.now()
            val start = today.minusDays(daysBack.toLong())
            val end = today.plusDays(daysForward.toLong())

            // Each service is independently try/caught inside its own fetch function - a failure
            // in one must never blank out or crash the other's contribution to the merged list.
            val sonarrDeferred = async { fetchSonarrCalendar(start, end) }
            val radarrDeferred = async { fetchRadarrCalendar(start, end) }
            (sonarrDeferred.await() + radarrDeferred.await()).sortedBy { it.date }
        }

    private suspend fun fetchSonarrCalendar(start: LocalDate, end: LocalDate): List<CalendarEntry> {
        if (!appPreferences.getValue(appPreferences.sonarrEnabled)) return emptyList()
        val baseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl)
        val apiKey = sonarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return emptyList()

        return try {
            val api = SonarrApi(baseUrl, apiKey)
            val entries = api.getCalendar(start, end)
            val shows = loadJellyfinShows()
            matchSonarrCalendar(entries, shows)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Sonarr calendar")
            emptyList()
        }
    }

    private suspend fun fetchRadarrCalendar(start: LocalDate, end: LocalDate): List<CalendarEntry> {
        if (!appPreferences.getValue(appPreferences.radarrEnabled)) return emptyList()
        val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl)
        val apiKey = radarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return emptyList()

        return try {
            val api = RadarrApi(baseUrl, apiKey)
            val entries = api.getCalendar(start, end)
            val movies = loadJellyfinMovies()
            matchRadarrCalendar(entries, movies, start, end)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Radarr calendar")
            emptyList()
        }
    }

    private suspend fun loadJellyfinShows(): List<FindroidShow> =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer) ?: return@withContext emptyList()
            val userId = jellyfinRepository.getUserId()
            serverDatabase.getShowsByServerId(serverId).map { it.toFindroidShow(serverDatabase, userId) }
        }

    private suspend fun loadJellyfinMovies(): List<FindroidMovie> =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer) ?: return@withContext emptyList()
            val userId = jellyfinRepository.getUserId()
            serverDatabase.getMoviesByServerId(serverId).map { it.toFindroidMovie(serverDatabase, userId) }
        }
}
