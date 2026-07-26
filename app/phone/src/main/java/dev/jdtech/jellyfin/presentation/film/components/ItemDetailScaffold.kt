package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared frame for Movie/Show/Season/Episode/Person: [ItemTopBar] gets its own row above
 * everything else, rather than floating as a translucent overlay on top of [ItemHeader]'s backdrop
 * image - the two used to share the same 288dp of vertical space, which left barely any room
 * between the top bar and a centered [PlayOverlayButton]. [content] renders below it, filling the
 * rest of the screen (typically a scrollable Column/LazyColumn starting with an [ItemHeader]).
 */
@Composable
fun ItemDetailScaffold(
    hasBackButton: Boolean,
    hasHomeButton: Boolean,
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    topBarContent: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (BoxScope.() -> Unit),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ItemTopBar(
            hasBackButton = hasBackButton,
            hasHomeButton = hasHomeButton,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            onSettingsClick = onSettingsClick,
            content = topBarContent,
        )
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}
