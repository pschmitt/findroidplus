package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.SonarrApi
import dev.pschmitt.jellyfin.api.pvr.SonarrEpisodeDto
import dev.pschmitt.jellyfin.models.UpcomingEpisode
import dev.pschmitt.jellyfin.models.UpcomingSeason
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * [resolveConfig] resolves the active profile's effective Sonarr config from `core`'s
 * `PvrConfigResolver` - passed in as a plain lambda (rather than depending on `PvrConfigResolver`
 * directly) because that type lives in `core`, which depends on `data`, not the other way around.
 * Same pattern as [CalendarRepositoryImpl]/`QueueStatusRepositoryImpl`.
 *
 * Constructed via `dev.pschmitt.jellyfin.di.SeasonEpisodesModule` (a Hilt `@Provides`) rather than
 * an `@Inject` constructor, since `data` has no Hilt plugin.
 */
class SeasonEpisodesRepositoryImpl(private val resolveConfig: () -> PvrClientConfig?) :
    SeasonEpisodesRepository {
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
        val config = resolveConfig() ?: return emptyList()
        if (!config.enabled) return emptyList()
        val baseUrl = config.baseUrl
        val apiKey = config.apiKey
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
