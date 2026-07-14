package dev.jdtech.jellyfin.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Resolves `jellyfin://tv-shows/<show name>[/<season name or number>[/<episode name or
 * number>]]` deep links to the item they refer to, using fuzzy name matching (exact ->
 * starts-with -> contains -> first search result) since callers can't reasonably supply exact
 * IDs.
 */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _target = MutableStateFlow<FindroidItem?>(null)
    val target = _target.asStateFlow()

    fun resolve(uri: Uri) {
        viewModelScope.launch {
            try {
                _target.value = resolveUri(uri)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve deep link $uri")
            }
        }
    }

    fun consumeTarget() {
        _target.value = null
    }

    private suspend fun resolveUri(uri: Uri): FindroidItem? {
        if (uri.scheme != "jellyfin") return null

        val segments = uri.pathSegments.map { decode(it) }
        return when (uri.host) {
            "tv-shows" -> resolveShowPath(segments)
            else -> null
        }
    }

    private suspend fun resolveShowPath(segments: List<String>): FindroidItem? {
        val showQuery = segments.getOrNull(0) ?: return null
        val show =
            findBestMatch(
                repository.getSearchItems(showQuery).filterIsInstance<FindroidShow>(),
                showQuery,
            ) { it.name } ?: return null

        val seasonQuery = segments.getOrNull(1) ?: return show
        val seasons = repository.getSeasons(show.id)
        val season = findBestMatchByIndexOrName(seasons, seasonQuery, { it.indexNumber }, { it.name }) ?: return show

        val episodeQuery = segments.getOrNull(2) ?: return season
        val episodes = repository.getEpisodes(show.id, season.id)
        return findBestMatchByIndexOrName(episodes, episodeQuery, { it.indexNumber }, { it.name }) ?: season
    }

    private fun <T> findBestMatchByIndexOrName(
        candidates: List<T>,
        query: String,
        index: (T) -> Int,
        name: (T) -> String,
    ): T? {
        Regex("""\d+""").find(query)?.value?.toIntOrNull()?.let { number ->
            candidates.firstOrNull { index(it) == number }?.let {
                return it
            }
        }
        return findBestMatch(candidates, query, name)
    }

    private fun <T> findBestMatch(candidates: List<T>, query: String, name: (T) -> String): T? {
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { name(it).equals(query, ignoreCase = true) }
            ?: candidates.firstOrNull { name(it).startsWith(query, ignoreCase = true) }
            ?: candidates.firstOrNull { name(it).contains(query, ignoreCase = true) }
            ?: candidates.first()
    }

    private fun decode(segment: String): String {
        return try {
            URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            segment
        }
    }
}
