package dev.pschmitt.jellyfin.film.presentation.library

import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.pschmitt.jellyfin.film.presentation.di.ApplicationScope
import dev.pschmitt.jellyfin.models.FindroidItem
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Per-library tabs recreate [LibraryViewModel] (and its paging flow) on every switch, since
 * Navigation-Compose's popUpTo/restoreState can't tell two different libraries apart - they all
 * share the single LibraryRoute destination. Without this, every tab switch re-hit the network
 * even for a library already browsed this session. Caching the paging flow here, scoped to an
 * application-lifetime CoroutineScope rather than the short-lived ViewModel one, makes revisiting
 * an already-loaded library instant.
 */
@Singleton
class LibraryItemsCache @Inject constructor(@ApplicationScope private val scope: CoroutineScope) {
    private val cache = ConcurrentHashMap<String, Flow<PagingData<FindroidItem>>>()

    suspend fun get(
        key: String,
        factory: suspend () -> Flow<PagingData<FindroidItem>>,
    ): Flow<PagingData<FindroidItem>> {
        return cache[key] ?: factory().cachedIn(scope).also { cache[key] = it }
    }

    fun invalidate(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
    }
}
