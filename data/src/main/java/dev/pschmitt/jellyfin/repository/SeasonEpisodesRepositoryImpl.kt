package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.api.pvr.SonarrApi
import dev.pschmitt.jellyfin.api.pvr.SonarrEpisodeDto
import dev.pschmitt.jellyfin.models.UpcomingEpisode
import dev.pschmitt.jellyfin.models.UpcomingSeason
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * [sonarrApiKeyProvider] resolves the current secret from `SecureCredentialStore` - passed in as a
 * plain lambda (rather than depending on `SecureCredentialStore` directly) because that type lives
 * in `core`, which depends on `data`, not the other way around. Same pattern as
 * [CalendarRepositoryImpl]/`QueueStatusRepositoryImpl`.
 *
 * Constructed via `dev.pschmitt.jellyfin.di.SeasonEpisodesModule` (a Hilt `@Provides`) rather than
 * an `@Inject` constructor, since `data` has no Hilt plugin.
 */
class SeasonEpisodesRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val sonarrApiKeyProvider: () -> String?,
) : SeasonEpisodesRepository {
    override suspend fun getUpcomingEpisodes(
        seriesTvdbId: String,
        seasonNumber: Int,
        knownEpisodeNumbers: Set<Int>,
    ): List<UpcomingEpisode> =
        matchUpcomingEpisodes(fetchSeriesEpisodes(seriesTvdbId), seasonNumber, knownEpisodeNumbers)

    override suspend fun getMissingSeasons(
        seriesTvdbId: String,
        knownSeasonNumbers: Set<Int>,
    ): List<UpcomingSeason> =
        matchMissingSeasons(fetchSeriesEpisodes(seriesTvdbId), knownSeasonNumbers)

    /**
     * Every Sonarr-known episode of the series matching [seriesTvdbId], regardless of season -
     * shared by both [getUpcomingEpisodes] (which filters to one season) and [getMissingSeasons]
     * (which groups by season). Empty (not an error) when Sonarr isn't configured, the show isn't
     * tracked by Sonarr, or the request fails.
     */
    private suspend fun fetchSeriesEpisodes(seriesTvdbId: String): List<SonarrEpisodeDto> {
        if (!appPreferences.getValue(appPreferences.sonarrEnabled)) return emptyList()
        val baseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl)
        val apiKey = sonarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return emptyList()

        return try {
            val api = SonarrApi(baseUrl, apiKey)
            val seriesId =
                api.getSeries().firstOrNull { it.tvdbId.toString() == seriesTvdbId }?.id
                    ?: return emptyList()
            api.getEpisodes(seriesId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Sonarr episodes for series tvdbId=$seriesTvdbId")
            emptyList()
        }
    }
}
