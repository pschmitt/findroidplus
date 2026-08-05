package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.components.TopBarTitle
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.utils.LocalOfflineMode

/**
 * Home's own header - server switcher title + error/retry/search/settings actions. A real Material3
 * [TopAppBar] - solid background, standard [IconButton]s, standard height - same look as
 * [ItemTopBar] (Movie/Show/Season/Episode/Person) and the Media/Library screen's own `TopAppBar`,
 * rather than a bespoke transparent bar with its own color/height. The server name reuses
 * [TopBarTitle] (the same title composable Library uses), made clickable to open the server-switch
 * sheet. (Favorites used to live here as an icon button - it's now its own Home section instead,
 * next to Continue Watching/Next Up, so it can actually be browsed rather than being a single
 * tap-through.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeader(
    serverName: String,
    isLoading: Boolean,
    isError: Boolean,
    onServerClick: () -> Unit,
    onErrorClick: () -> Unit,
    onRetryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOfflineMode = LocalOfflineMode.current

    TopAppBar(
        modifier = modifier,
        title = {
            TopBarTitle(
                text = serverName,
                iconRes = CoreR.drawable.ic_logo,
                modifier = Modifier.clickable(onClick = onServerClick),
                iconTint = Color.Unspecified,
            )
        },
        actions = {
            AnimatedVisibility(visible = isError, enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onErrorClick) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_alert_circle),
                        contentDescription = null,
                    )
                }
            }

            // Loading feedback lives entirely in the pull-to-refresh gesture now (see
            // HomeScreen's PullToRefreshBox) - same as Downloads/Library, instead of a second,
            // separate spinner living here too. This button is just the error-state retry action.
            AnimatedVisibility(visible = isError, enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onRetryClick, enabled = !isLoading) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                        contentDescription = null,
                    )
                }
            }

            if (!isOfflineMode) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_search),
                        contentDescription = null,
                    )
                }
            }

            IconButton(onClick = onUserClick, modifier = Modifier.testTag("e2e-settings-button")) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_settings),
                    contentDescription = null,
                )
            }
        },
        windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    )
}

@Composable
@Preview(showBackground = true)
private fun HomeHeaderLoadingPreview() {
    FindroidTheme {
        HomeHeader(
            serverName = "Jellyfin",
            isLoading = true,
            isError = false,
            onServerClick = {},
            onErrorClick = {},
            onRetryClick = {},
            onSearchClick = {},
            onUserClick = {},
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun HomeHeaderErrorPreview() {
    FindroidTheme {
        HomeHeader(
            serverName = "Jellyfin",
            isLoading = false,
            isError = true,
            onServerClick = {},
            onErrorClick = {},
            onRetryClick = {},
            onSearchClick = {},
            onUserClick = {},
        )
    }
}
