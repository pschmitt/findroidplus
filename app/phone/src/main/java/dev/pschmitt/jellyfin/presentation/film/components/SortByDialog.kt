package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.film.presentation.library.MediaFilter
import dev.pschmitt.jellyfin.models.SortBy
import dev.pschmitt.jellyfin.models.SortOrder
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

@Composable
fun SortByDialog(
    currentSortBy: SortBy,
    currentSortOrder: SortOrder,
    onUpdate: (sortBy: SortBy, sortOrder: SortOrder) -> Unit,
    onDismissRequest: () -> Unit,
    // Non-null only for the merged Media view, which is the only place a library also
    // needs a movies/shows/requested split - a single library already knows its own type.
    filter: MediaFilter? = null,
    seerrConfigured: Boolean = false,
    onFilterChange: (MediaFilter) -> Unit = {},
) {
    val optionValues = SortBy.entries
    val optionNames = stringArrayResource(CoreR.array.sort_by_options)
    val options = optionValues.zip(optionNames)

    val orderValues = SortOrder.entries
    val orderNames = stringArrayResource(CoreR.array.sort_order_options)
    val orderOptions = orderValues.zip(orderNames)

    var selectedOption by remember { mutableStateOf(currentSortBy) }
    var selectedOrder by remember { mutableStateOf(currentSortOrder) }

    val lazyListState = rememberLazyListState()

    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
        ) {
            Column {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                Text(
                    text =
                        stringResource(
                            if (filter != null) CoreR.string.sort_and_filter
                            else CoreR.string.sort_by
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                if (filter != null) {
                    FilterChipRow(
                        selected = filter,
                        seerrConfigured = seerrConfigured,
                        onSelect = onFilterChange,
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MaterialTheme.spacings.default),
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                }
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier.padding(horizontal = MaterialTheme.spacings.default).fillMaxWidth()
                ) {
                    orderOptions.forEachIndexed { index, order ->
                        SegmentedButton(
                            selected = order.first == selectedOrder,
                            onClick = {
                                selectedOrder = order.first
                                onUpdate(selectedOption, selectedOrder)
                            },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = orderOptions.size,
                                ),
                            colors =
                                SegmentedButtonDefaults.colors(
                                    inactiveContainerColor = Color.Transparent
                                ),
                            icon = {
                                AnimatedVisibility(
                                    visible = order.first == selectedOrder,
                                    enter = fadeIn(),
                                    exit = ExitTransition.None,
                                ) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_check),
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = { Text(order.second) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                if (!isAtTop) {
                    HorizontalDivider()
                }
                LazyColumn(modifier = Modifier.fillMaxWidth(), state = lazyListState) {
                    items(options) { option ->
                        SortByDialogItem(
                            option = option,
                            isSelected = option.first == selectedOption,
                            onSelect = {
                                selectedOption = option.first
                                onUpdate(selectedOption, selectedOrder)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    selected: MediaFilter,
    seerrConfigured: Boolean,
    onSelect: (MediaFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = buildList {
        add(MediaFilter.ALL)
        add(MediaFilter.MOVIES)
        add(MediaFilter.SHOWS)
        if (seerrConfigured) add(MediaFilter.REQUESTED)
    }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text =
                            stringResource(
                                when (filter) {
                                    MediaFilter.ALL -> CoreR.string.discover_filter_all
                                    MediaFilter.MOVIES -> CoreR.string.discover_filter_movies
                                    MediaFilter.SHOWS -> CoreR.string.discover_filter_shows
                                    MediaFilter.REQUESTED -> CoreR.string.discover_filter_requested
                                }
                            )
                    )
                },
                leadingIcon = {
                    // The Seerr mark is a brand-colored asset, so it's drawn as-is rather
                    // than tinted like the monochrome icons.
                    if (filter == MediaFilter.REQUESTED) {
                        Image(
                            painter = painterResource(CoreR.drawable.ic_seerr),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            painter =
                                painterResource(
                                    when (filter) {
                                        MediaFilter.MOVIES -> CoreR.drawable.ic_film
                                        MediaFilter.SHOWS -> CoreR.drawable.ic_tv
                                        else -> CoreR.drawable.ic_layout_grid
                                    }
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SortByDialogItem(
    option: Pair<SortBy, String>,
    isSelected: Boolean,
    onSelect: (SortBy) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onSelect(option.first) }
                .padding(horizontal = MaterialTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = { onSelect(option.first) })
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
        Text(text = option.second)
    }
}

@Preview
@Composable
private fun SortByDialogPreview() {
    JollyfinTheme {
        SortByDialog(
            currentSortBy = SortBy.NAME,
            currentSortOrder = SortOrder.ASCENDING,
            onUpdate = { _, _ -> },
            onDismissRequest = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SortByDialogItemPreview() {
    JollyfinTheme {
        SortByDialogItem(option = Pair(SortBy.NAME, "Title"), isSelected = true, onSelect = {})
    }
}

@Preview
@Composable
private fun SortByDialogWithFilterPreview() {
    JollyfinTheme {
        SortByDialog(
            currentSortBy = SortBy.NAME,
            currentSortOrder = SortOrder.ASCENDING,
            onUpdate = { _, _ -> },
            onDismissRequest = {},
            filter = MediaFilter.ALL,
            seerrConfigured = true,
            onFilterChange = {},
        )
    }
}
