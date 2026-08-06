package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.models.PvrSource
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme

/**
 * A button that opens a small menu offering Sonarr/Radarr's two search modes: an automatic search
 * (the service grabs the best release itself) or a manual/interactive one (opens
 * [ReleasePickerSheet] to pick a specific release). Uses [service]'s brand icon (full color, not
 * tinted) rather than a generic magnifier, so it's clear which service a tap will search. Two
 * shapes: the default compact icon-only button (Season screen episode rows, Calendar entries) and,
 * when [label] is set, a labeled [ItemActionButton] tile matching the other actions in a detail
 * screen's [ItemButtonsBar].
 */
@Composable
fun PvrSearchButton(
    service: PvrSource,
    onAutomaticSearch: () -> Unit,
    onManualSearch: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentDescription: String = stringResource(CoreR.string.search_episode),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val icon =
        painterResource(
            if (service == PvrSource.SONARR) CoreR.drawable.ic_sonarr else CoreR.drawable.ic_radarr
        )

    if (label != null) {
        ItemActionButton(
            icon = icon,
            iconTint = Color.Unspecified,
            label = label,
            onClick = { menuExpanded = true },
            modifier = modifier,
            menu = {
                PvrSearchMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onAutomaticSearch = onAutomaticSearch,
                    onManualSearch = onManualSearch,
                )
            },
        )
    } else {
        IconButton(onClick = { menuExpanded = true }, modifier = modifier) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
        }
        PvrSearchMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            onAutomaticSearch = onAutomaticSearch,
            onManualSearch = onManualSearch,
        )
    }
}

@Composable
private fun PvrSearchMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onAutomaticSearch: () -> Unit,
    onManualSearch: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(stringResource(CoreR.string.search_episode_automatic)) },
            onClick = {
                onDismissRequest()
                onAutomaticSearch()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(CoreR.string.search_episode_manual)) },
            onClick = {
                onDismissRequest()
                onManualSearch()
            },
        )
    }
}

@Composable
@Preview
private fun PvrSearchButtonPreview() {
    JollyfinTheme {
        PvrSearchButton(service = PvrSource.SONARR, onAutomaticSearch = {}, onManualSearch = {})
    }
}

@Composable
@Preview
private fun PvrSearchButtonLabeledPreview() {
    JollyfinTheme {
        PvrSearchButton(
            service = PvrSource.RADARR,
            onAutomaticSearch = {},
            onManualSearch = {},
            label = "Search",
        )
    }
}
