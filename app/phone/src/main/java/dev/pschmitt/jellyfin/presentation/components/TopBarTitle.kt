package dev.pschmitt.jellyfin.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Top-app-bar title with an optional leading icon identifying the view. The icon is pinned to
 * [iconSize] (default 32dp) regardless of the drawable's own intrinsic size -
 * `ic_launcher_foreground` (Home's icon) is a 108dp asset meant for a launcher/splash context, so
 * without this it rendered at that full size instead of a normal title icon. [iconTint] defaults to
 * the surrounding text color like any other single-color icon, but Home passes `Color.Unspecified`
 * for `ic_launcher_foreground` so its own brand gradient isn't flattened to a silhouette - same
 * override [ItemActionButton] already has for exactly this.
 */
@Composable
fun TopBarTitle(
    text: String,
    @DrawableRes iconRes: Int? = null,
    modifier: Modifier = Modifier,
    iconTint: Color = LocalContentColor.current,
    iconSize: Dp = 32.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        iconRes?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
        }
        Text(text = text)
    }
}
