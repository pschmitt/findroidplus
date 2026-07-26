package dev.jdtech.jellyfin.presentation.components

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
import androidx.compose.ui.unit.dp

/**
 * Top-app-bar title with an optional leading icon identifying the view. The icon is pinned to a
 * standard 24dp regardless of the drawable's own intrinsic size - `ic_logo` (Home's icon) is a
 * 100dp asset meant for a launcher/splash context, so without this it rendered at that full size
 * instead of a normal title icon. [iconTint] defaults to the surrounding text color like any other
 * single-color icon, but Home passes `Color.Unspecified` for `ic_logo` so its own brand gradient
 * isn't flattened to a silhouette - same override [ItemActionButton] already has for exactly this.
 */
@Composable
fun TopBarTitle(
    text: String,
    @DrawableRes iconRes: Int? = null,
    modifier: Modifier = Modifier,
    iconTint: Color = LocalContentColor.current,
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
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
        }
        Text(text = text)
    }
}
