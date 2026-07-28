package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

/**
 * One action tile of a detail screen's action row ([ItemButtonsBar]): a filled tonal icon button
 * with a short label underneath, so every action - restart, trailer, download, PVR search - shares
 * the exact same silhouette. Pass [checked] to turn the tile into a toggle (the checked container
 * color marks the "on" state), [contentColor] to tint the icon (e.g. error red for a destructive
 * delete), [iconTint] to override the icon's own tint independently of the button's content color
 * (e.g. `Color.Unspecified` to preserve a full-color brand icon like Sonarr/Radarr's logo, which
 * would otherwise get flattened to a silhouette), and [menu] to anchor an
 * [androidx.compose.material3.DropdownMenu] to the button for actions that open one. The visible
 * label doubles as the accessible name, so the icon itself carries no content description.
 */
@Composable
fun ItemActionButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    contentColor: Color? = null,
    iconTint: Color? = null,
    menu: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.widthIn(min = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (checked != null) {
                FilledTonalIconToggleButton(
                    checked = checked,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = iconTint ?: LocalContentColor.current,
                    )
                }
            } else {
                FilledTonalIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(48.dp),
                    colors =
                        contentColor?.let { color ->
                            IconButtonDefaults.filledTonalIconButtonColors(contentColor = color)
                        } ?: IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = iconTint ?: LocalContentColor.current,
                    )
                }
            }
            menu()
        }
        Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun ItemActionButtonPreview() {
    FindroidTheme {
        ItemActionButton(
            icon = painterResource(CoreR.drawable.ic_download),
            label = "Download",
            onClick = {},
        )
    }
}
