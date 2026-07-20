package dev.jdtech.jellyfin.presentation.film

import android.app.Activity

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.models.PvrQueueEntry
import dev.jdtech.jellyfin.presentation.film.components.FilmSearchBar
import dev.jdtech.jellyfin.presentation.film.components.HomeCarousel
import dev.jdtech.jellyfin.presentation.film.components.HomeDiscoverSection
import dev.jdtech.jellyfin.presentation.film.components.HomeHeader
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.film.components.HomeView
import dev.jdtech.jellyfin.presentation.film.components.PvrQueueDownloadCard
import dev.jdtech.jellyfin.presentation.film.components.SectionServiceIcons
import dev.jdtech.jellyfin.presentation.film.components.ServerSelectionBottomSheet
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.HomeSectionKeys
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HomeScreen(
    onLibraryClick: (library: FindroidCollection) -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageServers: () -> Unit,
    onItemClick: (item: FindroidItem) -> Unit,
    onSeerrItemClick: (item: SeerrSearchItem) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadData() }

    // Picks up a reorder made from the "Customize home screen" settings screen as soon as Home
    // resumes - cheap (no network), unlike loadData() above which only runs once on first entry.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSectionOrder()
        onPauseOrDispose {}
    }

    HomeScreenLayout(
        state = state,
        searchState = searchState,
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> onItemClick(action.item)
                is HomeAction.OnSeerrItemClick -> onSeerrItemClick(action.item)
                is HomeAction.OnLibraryClick -> onLibraryClick(action.library)
                is HomeAction.OnFavoritesClick -> onFavoritesClick()
                is HomeAction.OnSettingsClick -> onSettingsClick()
                is HomeAction.OnManageServers -> onManageServers()
                is HomeAction.OnEnableOfflineMode -> (context as? Activity)?.recreate()
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onSearchAction = { action ->
            when (action) {
                is SearchAction.OnItemClick -> onItemClick(action.item)
                is SearchAction.OnSeerrItemClick -> onSeerrItemClick(action.item)
                else -> Unit
            }
            searchViewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenLayout(
    state: HomeState,
    searchState: SearchState,
    onAction: (HomeAction) -> Unit,
    onSearchAction: (SearchAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val safePadding = rememberSafePadding(handleStartInsets = false)

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingTop = safePadding.top + MaterialTheme.spacings.small
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val itemsPadding = PaddingValues(start = paddingStart, end = paddingEnd)

    val contentPaddingTop = safePadding.top + 88.dp

    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val showServerSelectionSheetState = rememberModalBottomSheetState()
    var showServerSelectionBottomSheet by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = { onAction(HomeAction.OnRetryClick) }) {
            val lazyListState = rememberLazyListState()
            val reorderableState =
                rememberReorderableLazyListState(lazyListState) { from, to ->
                    onAction(HomeAction.OnReorderSections(from.index, to.index))
                }

            LazyColumn(
                modifier = Modifier.fillMaxSize().semantics { traversalIndex = 1f },
                state = lazyListState,
                contentPadding = PaddingValues(top = contentPaddingTop, bottom = paddingBottom),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            ) {
                items(state.sectionOrder, key = { it }) { key ->
                    ReorderableItem(reorderableState, key = key) { isDragging ->
                        // Long-press the section's own title to start dragging it, rather than a
                        // persistent handle shown at all times - Suggestions is itself a swipeable
                        // pager, so wrapping the whole item in a drag-anywhere modifier would fight
                        // that nested gesture. Each section composable applies `titleModifier` to
                        // just its title Text, leaving the rest of its content (posters, the
                        // "view all" arrow, etc.) clickable/scrollable as normal.
                        val titleModifier = Modifier.longPressDraggableHandle()

                        // Subtle "picked up" feedback while dragging - a slight scale/elevation
                        // lift plus a faint tint, so entering reorder mode reads as a distinct
                        // state rather than the section just silently moving on its own.
                        val scale by
                            animateFloatAsState(
                                targetValue = if (isDragging) 1.02f else 1f,
                                label = "sectionDragScale",
                            )
                        val elevation by
                            animateDpAsState(
                                targetValue = if (isDragging) 6.dp else 0.dp,
                                label = "sectionDragElevation",
                            )
                        val tint by
                            animateColorAsState(
                                targetValue =
                                    if (isDragging) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        Color.Transparent
                                    },
                                label = "sectionDragTint",
                            )

                        Box(
                            modifier =
                                Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                                    .shadow(elevation, shape = MaterialTheme.shapes.medium)
                                    .background(tint, shape = MaterialTheme.shapes.medium)
                        ) {
                            when {
                            key == HomeSectionKeys.SUGGESTIONS ->
                                state.suggestionsSection?.let { section ->
                                    HomeCarousel(
                                        items = section.items,
                                        itemsPadding = itemsPadding,
                                        onAction = onAction,
                                        titleModifier = titleModifier,
                                    )
                                }
                            key == HomeSectionKeys.CONTINUE_WATCHING ->
                                state.resumeSection?.let { section ->
                                    HomeSection(
                                        section = section.homeSection,
                                        itemsPadding = itemsPadding,
                                        onAction = onAction,
                                        titleModifier = titleModifier,
                                    )
                                }
                            key == HomeSectionKeys.NEXT_UP ->
                                state.nextUpSection?.let { section ->
                                    HomeSection(
                                        section = section.homeSection,
                                        itemsPadding = itemsPadding,
                                        onAction = onAction,
                                        titleModifier = titleModifier,
                                    )
                                }
                            key == HomeSectionKeys.ACTIVE_DOWNLOADS ->
                                HomeDownloadProgress(
                                    entries = state.activeDownloads,
                                    modifier = Modifier.padding(itemsPadding),
                                    titleModifier = titleModifier,
                                    serviceIcons = state.pvrServiceIcons,
                                )
                            key.startsWith("view:") ->
                                state.views
                                    .firstOrNull { HomeSectionKeys.view(it.view.id) == key }
                                    ?.let { view ->
                                        HomeView(
                                            view = view,
                                            itemsPadding = itemsPadding,
                                            onAction = onAction,
                                            titleModifier = titleModifier,
                                        )
                                    }
                            key.startsWith("discover:") ->
                                state.discoverSections
                                    .firstOrNull { HomeSectionKeys.discover(it.titleRes) == key }
                                    ?.let { section ->
                                        HomeDiscoverSection(
                                            section = section,
                                            itemsPadding = itemsPadding,
                                            onAction = onAction,
                                            titleModifier = titleModifier,
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }

        if (state.error != null && showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text(stringResource(CoreR.string.no_server_connection)) },
                text = { Text(state.error!!.message ?: stringResource(CoreR.string.unknown_error)) },
                confirmButton = {
                    TextButton(onClick = { onAction(HomeAction.OnEnableOfflineMode) }) {
                        Text(stringResource(CoreR.string.enable_offline_mode))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showErrorDialog = false
                            onAction(HomeAction.OnRetryClick)
                        }
                    ) {
                        Text(stringResource(CoreR.string.retry))
                    }
                },
            )
        }
    }

    if (searchExpanded) {
        FilmSearchBar(
            state = searchState,
            expanded = true,
            onExpand = { searchExpanded = it },
            onAction = onSearchAction,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = paddingStart, top = paddingTop, end = paddingEnd),
        )
    } else {
        HomeHeader(
            serverName = state.server?.name ?: "",
            isLoading = state.isLoading,
            isError = state.error != null,
            onServerClick = { showServerSelectionBottomSheet = true },
            onErrorClick = { showErrorDialog = true },
            onRetryClick = { onAction(HomeAction.OnRetryClick) },
            onSearchClick = { searchExpanded = true },
            onFavoritesClick = { onAction(HomeAction.OnFavoritesClick) },
            onUserClick = { onAction(HomeAction.OnSettingsClick) },
            modifier = Modifier.padding(start = paddingStart, top = paddingTop, end = paddingEnd),
        )
    }

    if (showServerSelectionBottomSheet) {
        ServerSelectionBottomSheet(
            currentServerId = state.server?.id ?: "",
            onUpdate = {
                onAction(HomeAction.OnRetryClick)
                scope
                    .launch { showServerSelectionSheetState.hide() }
                    .invokeOnCompletion {
                        if (!showServerSelectionSheetState.isVisible) {
                            showServerSelectionBottomSheet = false
                        }
                    }
            },
            onManage = {
                onAction(HomeAction.OnManageServers)
                scope.launch { showServerSelectionSheetState.hide() }
            },
            onDismissRequest = { showServerSelectionBottomSheet = false },
            sheetState = showServerSelectionSheetState,
        )
    }
}

@Composable
private fun HomeDownloadProgress(
    entries: List<PvrQueueEntry>,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    serviceIcons: List<Int> = emptyList(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionServiceIcons(serviceIcons)
            Text(
                text = stringResource(CoreR.string.pvr_queue_section_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = titleModifier,
            )
        }
        if (entries.isEmpty()) {
            Text(
                text = stringResource(CoreR.string.pvr_queue_section_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.take(2).forEach { entry ->
                PvrQueueDownloadCard(status = entry.status, title = entry.title)
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun HomeScreenLayoutPreview() {
    FindroidTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    server = dummyServer,
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                    error = Exception("Failed to load data"),
                ),
            searchState = SearchState(),
            onAction = {},
            onSearchAction = {},
        )
    }
}
