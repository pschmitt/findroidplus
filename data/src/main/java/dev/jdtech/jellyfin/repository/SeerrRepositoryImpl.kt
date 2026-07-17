package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.SeerrApi
import dev.jdtech.jellyfin.api.pvr.SeerrMediaInfo
import dev.jdtech.jellyfin.api.pvr.SeerrSearchResult
import dev.jdtech.jellyfin.models.SeerrMediaDetail
import dev.jdtech.jellyfin.models.SeerrMediaStatus
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.models.SeerrRequestItem
import dev.jdtech.jellyfin.models.SeerrSearchItem
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
                    genres = details.genres.map { it.name }.filter { it.isNotBlank() },
                    runtimeMinutes = details.runtime?.takeIf { it > 0 },
                    numberOfSeasons = null,
                    status = SeerrMediaStatus.fromCode(details.mediaInfo?.status),
                    cancellableRequestIds = details.mediaInfo.cancellableRequestIds(),
                )
            }
            SeerrMediaType.TV -> {
                val details = api.getTvDetails(tmdbId)
                SeerrMediaDetail(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    title = details.name.ifBlank { "TMDB $tmdbId" },
                    year = details.firstAirDate?.take(4)?.toIntOrNull(),
                    overview = details.overview?.takeIf { it.isNotBlank() },
                    posterUrl = details.posterPath?.toPosterUrl(),
                    backdropUrl = details.backdropPath?.toBackdropUrl(),
                    genres = details.genres.map { it.name }.filter { it.isNotBlank() },
                    runtimeMinutes = null,
                    numberOfSeasons = details.numberOfSeasons?.takeIf { it > 0 },
                    status = SeerrMediaStatus.fromCode(details.mediaInfo?.status),
                    cancellableRequestIds = details.mediaInfo.cancellableRequestIds(),
                )
            }
        }
    }

    override suspend fun request(tmdbId: Int, mediaType: SeerrMediaType): Result<Unit> =
        runAction { api ->
            api.createRequest(
                mediaType =
                    when (mediaType) {
                        SeerrMediaType.MOVIE -> SeerrApi.MEDIA_TYPE_MOVIE
                        SeerrMediaType.TV -> SeerrApi.MEDIA_TYPE_TV
                    },
                tmdbId = tmdbId,
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
