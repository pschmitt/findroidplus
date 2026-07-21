package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.SeerrApi
import dev.jdtech.jellyfin.api.pvr.SeerrMediaInfo
import dev.jdtech.jellyfin.api.pvr.SeerrRelatedVideo
import dev.jdtech.jellyfin.api.pvr.SeerrSearchResult
import dev.jdtech.jellyfin.models.SeerrEpisodeDetail
import dev.jdtech.jellyfin.models.SeerrMediaDetail
import dev.jdtech.jellyfin.models.SeerrMediaStatus
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.models.SeerrRequestItem
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.models.SeerrSeasonDetail
import dev.jdtech.jellyfin.models.SeerrSeasonInfo
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * Same lambda-injection pattern as the other PVR repositories ([seerrApiKeyProvider]
 * resolves the secret from `SecureCredentialStore` in `core`). Constructed via
 * `dev.jdtech.jellyfin.di.SeerrModule` (a Hilt `@Provides`) rather than an `@Inject`
 * constructor, since `data` has no Hilt plugin.
 */
class SeerrRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val seerrApiKeyProvider: () -> String?,
) : SeerrRepository {

    override suspend fun search(query: String): Result<List<SeerrSearchItem>> = runAction { api ->
        api.search(query)
            .results
            .mapNotNull { it.toSearchItem() }
    }

    override suspend fun getTrending(): Result<List<SeerrSearchItem>> = runAction { api ->
        api.discover(SeerrApi.DISCOVER_TRENDING).results.mapNotNull { it.toSearchItem() }
    }

    override suspend fun getPopularMovies(): Result<List<SeerrSearchItem>> = runAction { api ->
        api.discover(SeerrApi.DISCOVER_MOVIES).results.mapNotNull {
            it.toSearchItem(fallbackMediaType = SeerrApi.MEDIA_TYPE_MOVIE)
        }
    }

    override suspend fun getPopularShows(): Result<List<SeerrSearchItem>> = runAction { api ->
        api.discover(SeerrApi.DISCOVER_TV).results.mapNotNull {
            it.toSearchItem(fallbackMediaType = SeerrApi.MEDIA_TYPE_TV)
        }
    }

    override suspend fun getDetails(
        tmdbId: Int,
        mediaType: SeerrMediaType,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): Result<SeerrMediaDetail> = runAction { api ->
        when (mediaType) {
            SeerrMediaType.MOVIE -> {
                val details = api.getMovieDetails(tmdbId)
                SeerrMediaDetail(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    title = details.title.ifBlank { "TMDB $tmdbId" },
                    year = details.releaseDate?.take(4)?.toIntOrNull(),
                    overview = details.overview?.takeIf { it.isNotBlank() },
                    posterUrl = details.posterPath?.toPosterUrl(),
                    backdropUrl = details.backdropPath?.toBackdropUrl(),
                    trailerUrl = details.relatedVideos.firstTrailerUrl(seasonNumber = null),
                    genres = details.genres.map { it.name }.filter { it.isNotBlank() },
                    runtimeMinutes = details.runtime?.takeIf { it > 0 },
                    numberOfSeasons = null,
                    status = SeerrMediaStatus.fromCode(details.mediaInfo?.status),
                    cancellableRequestIds = details.mediaInfo.cancellableRequestIds(),
                )
            }
            SeerrMediaType.TV -> {
                val details = api.getTvDetails(tmdbId)
                val season =
                    seasonNumber?.let { requestedSeasonNumber ->
                        val seasonDetails = api.getTvSeason(tmdbId, requestedSeasonNumber)
                        SeerrSeasonDetail(
                            title = seasonDetails.name.ifBlank { "Season $requestedSeasonNumber" },
                            seasonNumber = seasonDetails.seasonNumber,
                            overview = seasonDetails.overview?.takeIf(String::isNotBlank),
                            posterUrl = seasonDetails.posterPath?.toPosterUrl(),
                        ) to seasonDetails
                    }
                val episode =
                    if (season != null && episodeNumber != null) {
                        season.second
                            .episodes
                            .firstOrNull { it.episodeNumber == episodeNumber }
                            ?.let {
                                SeerrEpisodeDetail(
                                    title = it.name.ifBlank { "Episode $episodeNumber" },
                                    seasonNumber = it.seasonNumber,
                                    episodeNumber = it.episodeNumber,
                                    airDate = it.airDate,
                                    overview = it.overview?.takeIf(String::isNotBlank),
                                    stillUrl = it.stillPath?.toBackdropUrl(),
                                )
                            }
                            ?: throw IllegalArgumentException(
                                "Could not find season $seasonNumber episode $episodeNumber in Seerr"
                            )
                    } else {
                        null
                    }
                SeerrMediaDetail(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    title = details.name.ifBlank { "TMDB $tmdbId" },
                    year = details.firstAirDate?.take(4)?.toIntOrNull(),
                    overview = details.overview?.takeIf { it.isNotBlank() },
                    posterUrl = details.posterPath?.toPosterUrl(),
                    backdropUrl = details.backdropPath?.toBackdropUrl(),
                    trailerUrl = details.relatedVideos.firstTrailerUrl(seasonNumber = seasonNumber),
                    genres = details.genres.map { it.name }.filter { it.isNotBlank() },
                    runtimeMinutes = null,
                    numberOfSeasons = details.numberOfSeasons?.takeIf { it > 0 },
                    status = SeerrMediaStatus.fromCode(details.mediaInfo?.status),
                    cancellableRequestIds = details.mediaInfo.cancellableRequestIds(),
                    season = season?.first,
                    episode = episode,
                    seasons =
                        details.mediaInfo?.seasons.orEmpty().map {
                            SeerrSeasonInfo(
                                seasonNumber = it.seasonNumber,
                                status = SeerrMediaStatus.fromCode(it.status),
                            )
                        },
                )
            }
        }
    }

    override suspend fun request(
        tmdbId: Int,
        mediaType: SeerrMediaType,
        seasonNumber: Int?,
    ): Result<Unit> =
        runAction { api ->
            api.createRequest(
                mediaType =
                    when (mediaType) {
                        SeerrMediaType.MOVIE -> SeerrApi.MEDIA_TYPE_MOVIE
                        SeerrMediaType.TV -> SeerrApi.MEDIA_TYPE_TV
                    },
                tmdbId = tmdbId,
                seasonNumber = seasonNumber,
            )
        }

    override suspend fun getRecentRequests(limit: Int): Result<List<SeerrRequestItem>> =
        runAction { api ->
            coroutineScope {
                // The request resource only carries provider ids - titles/posters need one detail
                // lookup each. The list is small (a page of recent requests), so the parallel
                // fan-out stays cheap.
                api.getRequests(take = limit)
                    .results
                    .map { request ->
                        async {
                            try {
                                when (request.media.mediaType) {
                                    SeerrApi.MEDIA_TYPE_MOVIE -> {
                                        val details = api.getMovieDetails(request.media.tmdbId)
                                        SeerrRequestItem(
                                            id = request.id,
                                            tmdbId = request.media.tmdbId,
                                            mediaType = SeerrMediaType.MOVIE,
                                            title = details.title.ifBlank { "TMDB ${request.media.tmdbId}" },
                                            posterUrl = details.posterPath?.toPosterUrl(),
                                            mediaStatus = SeerrMediaStatus.fromCode(request.media.status),
                                        )
                                    }
                                    SeerrApi.MEDIA_TYPE_TV -> {
                                        val details = api.getTvDetails(request.media.tmdbId)
                                        SeerrRequestItem(
                                            id = request.id,
                                            tmdbId = request.media.tmdbId,
                                            mediaType = SeerrMediaType.TV,
                                            title = details.name.ifBlank { "TMDB ${request.media.tmdbId}" },
                                            posterUrl = details.posterPath?.toPosterUrl(),
                                            mediaStatus = SeerrMediaStatus.fromCode(request.media.status),
                                        )
                                    }
                                    else -> null
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // A single failed detail lookup shouldn't take down the whole list.
                                Timber.w(e, "Failed to resolve request ${request.id} details")
                                null
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
            }
        }

    override suspend fun cancelRequest(requestId: Int): Result<Unit> = runAction { api ->
        api.deleteRequest(requestId)
    }

    override suspend fun getSeasonPosterUrls(
        tmdbId: Int,
        seasonNumbers: List<Int>,
    ): Result<Map<Int, String?>> = runAction { api ->
        coroutineScope {
            seasonNumbers
                .map { seasonNumber ->
                    async {
                        seasonNumber to
                            try {
                                api.getTvSeason(tmdbId, seasonNumber).posterPath?.toPosterUrl()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w(
                                    e,
                                    "Failed to fetch poster for tmdbId=$tmdbId season=$seasonNumber",
                                )
                                null
                            }
                    }
                }
                .awaitAll()
                .toMap()
        }
    }

    /**
     * [fallbackMediaType] covers the single-type discover endpoints, whose results are all of a
     * known type even if a payload omits `mediaType`.
     */
    private fun SeerrSearchResult.toSearchItem(
        fallbackMediaType: String? = null
    ): SeerrSearchItem? {
        val type =
            when (mediaType.ifBlank { fallbackMediaType }) {
                SeerrApi.MEDIA_TYPE_MOVIE -> SeerrMediaType.MOVIE
                SeerrApi.MEDIA_TYPE_TV -> SeerrMediaType.TV
                // Person results (actors/directors) aren't requestable - drop them.
                else -> return null
            }
        return SeerrSearchItem(
            tmdbId = id,
            mediaType = type,
            title = (title ?: name).orEmpty().ifBlank { "TMDB $id" },
            year = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull(),
            overview = overview?.takeIf { it.isNotBlank() },
            posterUrl = posterPath?.toPosterUrl(),
            status = SeerrMediaStatus.fromCode(mediaInfo?.status),
        )
    }

    private fun String.toPosterUrl(): String = "$TMDB_IMAGE_BASE$this"

    private fun String.toBackdropUrl(): String = "$TMDB_BACKDROP_BASE$this"

    /** Declined requests can't be cancelled - there is nothing left to undo. */
    private fun SeerrMediaInfo?.cancellableRequestIds(): List<Int> =
        this?.requests?.filter { it.status != REQUEST_STATUS_DECLINED }?.map { it.id }.orEmpty()

    /**
     * TMDB has no structured per-season video scoping - `relatedVideos` is a flat bag of
     * trailers/teasers/clips for the whole series, confirmed live against Jellyseerr's season and
     * episode detail endpoints, which carry no video data at all. When [seasonNumber] is given
     * (viewing a season or an episode in it), the only available signal that a video belongs to
     * that season is its free-text `name` (e.g. "Season 3 Official Trailer") - [seasonNameMatch]
     * is a best-effort match on that, tried before falling back to the show-wide pick. TMDB also
     * lists every kind of video (teasers, clips, featurettes, ...) with no reliable ordering, so
     * within each candidate pool an explicit "Trailer" is preferred, falling back to the first
     * YouTube video at all so there's still something to play if none is tagged "Trailer".
     */
    private fun List<SeerrRelatedVideo>.firstTrailerUrl(seasonNumber: Int?): String? {
        val youtubeVideos = filter { it.site.equals("YouTube", ignoreCase = true) && it.url != null }
        if (seasonNumber != null) {
            val seasonNameMatch = Regex("""(?i)\bseason\s*0*$seasonNumber\b|\bs0*$seasonNumber\b""")
            val seasonScoped = youtubeVideos.filter { it.name?.let(seasonNameMatch::containsMatchIn) == true }
            seasonScoped.bestMatch()?.let { return it }
        }
        return youtubeVideos.bestMatch()
    }

    private fun List<SeerrRelatedVideo>.bestMatch(): String? =
        firstOrNull { it.type.equals("Trailer", ignoreCase = true) }?.url ?: firstOrNull()?.url

    private suspend fun <T> runAction(block: suspend (SeerrApi) -> T): Result<T> {
        val api =
            api() ?: return Result.failure(IllegalStateException("Seerr is not configured"))
        return try {
            Result.success(block(api))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Seerr action failed")
            Result.failure(mapPvrSearchError("Seerr", e))
        }
    }

    private fun api(): SeerrApi? {
        if (!appPreferences.getValue(appPreferences.seerrEnabled)) return null
        val baseUrl = appPreferences.getValue(appPreferences.seerrBaseUrl)
        val apiKey = seerrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return null
        return SeerrApi(baseUrl, apiKey)
    }

    private companion object {
        // Seerr's own web UI loads posters straight from TMDB; w342 is plenty for list rows.
        const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"
        const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
        const val REQUEST_STATUS_DECLINED = 3
    }
}
