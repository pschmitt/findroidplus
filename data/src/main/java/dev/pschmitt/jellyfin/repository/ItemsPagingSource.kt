package dev.pschmitt.jellyfin.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.pschmitt.jellyfin.models.JollyfinItem
import dev.pschmitt.jellyfin.models.SortBy
import dev.pschmitt.jellyfin.models.SortOrder
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

class ItemsPagingSource(
    private val jellyfinRepository: JellyfinRepository,
    private val parentId: UUID?,
    private val includeTypes: List<BaseItemKind>?,
    private val recursive: Boolean,
    private val sortBy: SortBy,
    private val sortOrder: SortOrder,
    private val searchTerm: String? = null,
) : PagingSource<Int, JollyfinItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, JollyfinItem> {
        val position = params.key ?: 0

        Timber.d("Retrieving position: $position")

        return try {
            val items =
                jellyfinRepository.getItems(
                    parentId = parentId,
                    includeTypes = includeTypes,
                    recursive = recursive,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    startIndex = position,
                    limit = params.loadSize,
                    searchTerm = searchTerm,
                )
            LoadResult.Page(
                data = items,
                prevKey = if (position == 0) null else position - params.loadSize,
                nextKey = if (items.isEmpty()) null else position + params.loadSize,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, JollyfinItem>): Int {
        return 0
    }
}
