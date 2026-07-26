package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * A "more options" (⋮) icon pinned to the right of [ItemButtonsBar]'s action row on Movie/Show/
 * Episode/Season, hosting actions that are real but rare enough not to deserve their own tile
 * (favorite, manual PVR search, deleting the item from Jellyfin). [menuContent] receives a
 * `closeMenu` callback so an item can dismiss the menu itself before doing anything that opens a
 * further dialog.
 *
 * The [Box] wrapper matters: Material3's [DropdownMenu] anchors itself to its nearest positioned
 * parent, not specifically to whatever composable precedes it - without a [Box] tying the icon and
 * the menu together, the menu anchors to this composable's *caller's* layout (the whole
 * [ItemButtonsBar] row) instead of the icon itself, landing in a visibly wrong spot.
 */
@Composable
fun ItemOverflowMenu(menuContent: @Composable ColumnScope.(closeMenu: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_more_vertical),
                contentDescription = stringResource(CoreR.string.more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}
