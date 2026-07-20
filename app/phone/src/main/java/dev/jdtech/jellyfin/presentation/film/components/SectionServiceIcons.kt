package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * The Jellyfin/Sonarr/Radarr/Seerr brand logo(s) a Home section's content actually depends on,
 * shown before its title - same idea (and same "Customize home screen" pairing) as the service
 * icons there. No-ops when [serviceIcons] is empty, so callers can pass it unconditionally. No
 * spacing of its own - the caller's Row (icon before text) supplies that via `spacedBy`, so an
 * empty icon list doesn't leave a dangling gap in front of the title.
 */
@Composable
fun SectionServiceIcons(serviceIcons: List<Int>, modifier: Modifier = Modifier) {
    if (serviceIcons.isEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall)) {
        serviceIcons.forEach { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                // Unspecified, not the default tint - these are full-color brand logos, and the
                // default tint would flatten them into a solid silhouette (see the identical
                // PvrQueueRow fix on the Downloads screen).
                tint = Color.Unspecified,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
