package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.library.LibraryAction
import dev.jdtech.jellyfin.film.presentation.library.LibraryEvent
import dev.jdtech.jellyfin.film.presentation.library.LibraryState
import dev.jdtech.jellyfin.film.presentation.library.LibraryViewModel
import dev.jdtech.jellyfin.film.presentation.library.MediaFilter
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SeerrMediaStatus
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.SeerrRequestRow
import dev.jdtech.jellyfin.presentation.film.components.SeerrResultRow
import dev.jdtech.jellyfin.presentation.film.components.SortByDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.jdtech.jellyfin.presentation.utils.plus
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun LibraryScreen(
    // Null renders the merged "Media" view: all movies and shows across libraries.
    libraryId: UUID?,
    libraryName: String,
    libraryType: CollectionType,
    onItemClick: (item: FindroidItem) -> Unit,
    navigateBack: () -> Unit,
    showBackButton: Boolean = true,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var initialLoad by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(true) {
        viewModel.setup(parentId = libraryId, libraryType = libraryType)
        if (initialLoad) {
            viewModel.loadItems()
            initialLoad = false
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        val message =
            when (event) {
                is LibraryEvent.SeerrRequested ->
                    context.getString(CoreR.string.discover_requested_toast, event.title)
                is LibraryEvent.SeerrRequestFailed ->
                    context.getString(
                        CoreR.string.discover_request_failed_toast,
                        event.message ?: context.getString(CoreR.string.unknown_error),
                    )
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    LibraryScreenLayout(
        libraryName = libraryName,
        state = state,
        isMergedMedia = libraryId == null,
        showBackButton = showBackButton,
        onAction = { action ->
            when (action) {
                is LibraryAction.OnItemClick -> onItemClick(action.item)
                is LibraryAction.OnBackClick -> navigateBack()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenLayout(
    libraryName: String,
    state: LibraryState,
    isMergedMedia: Boolean = false,
    showBackButton: Boolean = true,
    onAction: (LibraryAction) -> Unit,
) {
    val contentPadding = PaddingValues(all = MaterialTheme.spacings.default)

    val items = state.items.collectAsLazyPagingItems()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showSortByDialog by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (searchExpanded) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = {
                                onAction(LibraryAction.OnSearchQueryChange(it))
                            },
                            modifier =
                                Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                            placeholder = { Text(libraryName) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions =
                                KeyboardActions(onSearch = { keyboardController?.hide() }),
                        )
                    } else {
                        Text(libraryName)
                    }
                },
                navigationIcon = {
                    if (searchExpanded || showBackButton) {
                        IconButton(
                            onClick = {
                                if (searchExpanded) {
                                    searchExpanded = false
                                    onAction(LibraryAction.OnSearchQueryChange(""))
                                } else {
                                    onAction(LibraryAction.OnBackClick)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_arrow_left),
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    if (searchExpanded) {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onAction(LibraryAction.OnSearchQueryChange("")) }
                            ) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_x),
                                    contentDescription = null,
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_search),
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = { showSortByDialog = true }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_arrow_down_up),
                                contentDescription = null,
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        // In the merged Media view the filter chips consume the scaffold insets once for the
        // whole column, so the grid must not add them again.
        val listPadding = if (isMergedMedia) contentPadding else contentPadding + innerPadding

        Column(modifier = if (isMergedMedia) Modifier.padding(innerPadding) else Modifier) {
            if (isMergedMedia) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                ) {
                    MediaFilterChip(
                        filter = MediaFilter.ALL,
                        labelRes = CoreR.string.discover_filter_all,
                        state = state,
                        onAction = onAction,
                    )
                    MediaFilterChip(
                        filter = MediaFilter.MOVIES,
                        labelRes = CoreR.string.discover_filter_movies,
                        state = state,
                        onAction = onAction,
                    )
                    MediaFilterChip(
                        filter = MediaFilter.SHOWS,
                        labelRes = CoreR.string.discover_filter_shows,
                        state = state,
                        onAction = onAction,
                    )
                }
            }
            ErrorGroup(
                loadStates = items.loadState,
                onRefresh = { items.refresh() },
                modifier = Modifier.fillMaxWidth().padding(listPadding),
            )

            val seerrActive = isMergedMedia && state.seerrConfigured
            // Opening search with an empty query surfaces the recent Seerr requests - feedback
            // on what's been asked for and how far along it is.
            val showRecentRequests =
                seerrActive &&
                    searchExpanded &&
                    state.searchQuery.isBlank() &&
                    state.recentRequests.isNotEmpty()
            // Fully available results would duplicate the library results right above them.
            val seerrResults =
                if (seerrActive && state.searchQuery.isNotBlank()) {
                    state.seerrResults
                        .filter {
                            when (state.filter) {
                                MediaFilter.ALL -> true
                                MediaFilter.MOVIES -> it.mediaType == SeerrMediaType.MOVIE
                                MediaFilter.SHOWS -> it.mediaType == SeerrMediaType.TV
                            }
                        }
                        .filter { it.status != SeerrMediaStatus.AVAILABLE }
                } else {
                    emptyList()
                }
            val showSeerrSection =
                seerrActive &&
                    state.searchQuery.isNotBlank() &&
                    (seerrResults.isNotEmpty() || state.seerrSearching || state.seerrError != null)

            LazyVerticalGrid(
                columns = GridCellsAdaptiveWithMinColumns(minSize = 160.dp, minColumns = 2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = listPadding,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            ) {
                if (showRecentRequests) {
                    item(key = "seerr-requests-header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(CoreR.string.discover_recent_requests),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(
                        items = state.recentRequests,
                        key = { "seerr-request-${it.id}" },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { request ->
                        SeerrRequestRow(request = request)
                    }
                }
                items(count = items.itemCount, key = items.itemKey { it.id }) {
                    val item = items[it]
                    item?.let { item ->
                        ItemCard(
                            item = item,
                            direction = Direction.VERTICAL,
                            onClick = { onAction(LibraryAction.OnItemClick(item)) },
                            modifier = Modifier.animateItem(),
                            queueStatus = state.queueStatus[item.id],
                        )
                    }
                }
                if (showSeerrSection) {
                    item(key = "seerr-results-header", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(
                                text = stringResource(CoreR.string.media_seerr_section),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.seerrSearching) {
                                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            state.seerrError?.let { error ->
                                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    items(
                        items = seerrResults,
                        key = { "seerr-result-${it.mediaType}-${it.tmdbId}" },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { result ->
                        SeerrResultRow(
                            item = result,
                            requestedThisSession = result.tmdbId in state.requestedTmdbIds,
                            onRequest = { onAction(LibraryAction.OnSeerrRequest(result)) },
                        )
                    }
                }
            }
        }
    }

    if (showSortByDialog) {
        SortByDialog(
            currentSortBy = state.sortBy,
            currentSortOrder = state.sortOrder,
            onUpdate = { sortBy, sortOrder ->
                onAction(LibraryAction.ChangeSorting(sortBy, sortOrder))
            },
            onDismissRequest = { showSortByDialog = false },
        )
    }
}

@Composable
private fun MediaFilterChip(
    filter: MediaFilter,
    labelRes: Int,
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
) {
    FilterChip(
        selected = state.filter == filter,
        onClick = { onAction(LibraryAction.ChangeFilter(filter)) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun ErrorGroup(
    loadStates: CombinedLoadStates,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }

    val loadStateError =
        when {
            loadStates.refresh is LoadState.Error -> {
                loadStates.refresh as LoadState.Error
            }
            loadStates.prepend is LoadState.Error -> {
                loadStates.prepend as LoadState.Error
            }
            loadStates.append is LoadState.Error -> {
                loadStates.append as LoadState.Error
            }
            else -> null
        }

    loadStateError?.let {
        ErrorCard(
            onShowStacktrace = { showErrorDialog = true },
            onRetryClick = onRefresh,
            modifier = modifier,
        )
        if (showErrorDialog) {
            ErrorDialog(exception = it.error, onDismissRequest = { showErrorDialog = false })
        }
    }
}

@PreviewScreenSizes
@Composable
private fun LibraryScreenLayoutPreview() {
    val items: Flow<PagingData<FindroidItem>> = flowOf(PagingData.from(dummyMovies))
    FindroidTheme {
        LibraryScreenLayout(
            libraryName = "Movies",
            state = LibraryState(items = items),
            onAction = {},
        )
    }
}
