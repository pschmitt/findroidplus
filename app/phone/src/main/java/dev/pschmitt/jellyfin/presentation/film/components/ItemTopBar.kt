package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.window.core.layout.WindowSizeClass
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme

/**
 * Shared top bar for Show/Movie/Season/Episode/Person - these are "fullscreen" detail screens
 * with no [androidx.compose.material3.Scaffold] of their own, so the settings action lives here
 * rather than being duplicated per screen. A real Material3 [TopAppBar] - solid background,
 * standard [IconButton]s, standard height - the same look as the Media/Library screen's own
 * `TopAppBar`, rather than a bespoke transparent/floating bar with its own color and height.
 * On tablets the home button is suppressed even when requested: the nav rail stays visible on
 * detail screens there (see NavigationRoot), so Home is always one tap away already.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemTopBar(
    hasBackButton: Boolean,
    hasHomeButton: Boolean,
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    content: @Composable (RowScope.() -> Unit) = {},
) {
    // Same breakpoint NavigationRoot uses to decide the nav rail is always visible.
    val isTablet =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    TopAppBar(
        title = { Row { content() } },
        navigationIcon = {
            Row {
                if (hasBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                }
                if (hasHomeButton && !isTablet) {
                    IconButton(onClick = onHomeClick) {
                        Icon(painter = painterResource(CoreR.drawable.ic_home), contentDescription = null)
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_settings),
                    contentDescription = stringResource(CoreR.string.title_settings),
                )
            }
        },
        windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    )
}

@Composable
@Preview(showBackground = true)
private fun ItemTopBarPreview() {
    FindroidTheme { ItemTopBar(hasBackButton = true, hasHomeButton = true) }
}
