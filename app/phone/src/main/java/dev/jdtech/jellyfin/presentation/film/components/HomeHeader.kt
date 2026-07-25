package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode

/**
 * Home's own header - server switcher pill + error/retry/search/favorites/settings buttons. Every
 * icon button here is a [HeaderIconButton], the same black-70%-alpha circle [ItemTopBar] uses on
 * every detail screen, so this reads as the same header language rather than a bespoke
 * solid-color one. The server-switcher pill is the one thing with no detail-screen equivalent
 * (variable-width, shows the server name) - kept as its own [Surface] but recolored to match.
 */
@Composable
fun HomeHeader(
    serverName: String,
    isLoading: Boolean,
    isError: Boolean,
    onServerClick: () -> Unit,
    onErrorClick: () -> Unit,
    onRetryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOfflineMode = LocalOfflineMode.current

    // No fixed row height - sizes to its tallest child, same as ItemTopBar's Row, so the two
    // headers are the same height (48dp, the buttons' own natural size) instead of this one
    // being pinned to a taller 56dp.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onServerClick,
            modifier = Modifier.height(48.dp).weight(1f, fill = false).alpha(0.7f),
            shape = CircleShape,
            color = Color.Black,
            contentColor = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacings.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified,
                )
                Text(
                    text = serverName,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.animateContentSize(),
                )
            }
        }

        Spacer(Modifier.width(MaterialTheme.spacings.medium))

        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = isError, enter = fadeIn(), exit = fadeOut()) {
                HeaderIconButton(onClick = onErrorClick) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_alert_circle),
                        contentDescription = null,
                    )
                }
            }

            AnimatedVisibility(visible = isLoading || isError, enter = fadeIn(), exit = fadeOut()) {
                HeaderIconButton(onClick = onRetryClick, enabled = !isLoading) {
                    when {
                        isError -> {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                            )
                        }
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            if (!isOfflineMode) {
                HeaderIconButton(onClick = onSearchClick) {
                    Icon(painter = painterResource(CoreR.drawable.ic_search), contentDescription = null)
                }
            }

            HeaderIconButton(onClick = onFavoritesClick) {
                Icon(painter = painterResource(CoreR.drawable.ic_heart), contentDescription = null)
            }

            HeaderIconButton(onClick = onUserClick) {
                Icon(painter = painterResource(CoreR.drawable.ic_settings), contentDescription = null)
            }
        }
    }
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
            onFavoritesClick = {},
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
            onFavoritesClick = {},
            onUserClick = {},
        )
    }
}
