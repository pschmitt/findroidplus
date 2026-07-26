package dev.jdtech.jellyfin.presentation.film.components

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
 * A "more options" (⋮) icon in [ItemTopBar]'s actions row - lives next to the Settings gear on
 * Movie/Show/Episode, hosting actions that are real but rare enough not to deserve their own
 * [ItemButtonsBar] tile (a manual PVR search, deleting the item from Jellyfin). [menuContent]
 * receives a `closeMenu` callback so an item can dismiss the menu itself before doing anything
 * that opens a further dialog.
 */
@Composable
fun ItemOverflowMenu(menuContent: @Composable ColumnScope.(closeMenu: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

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
