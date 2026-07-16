package dev.jdtech.jellyfin.presentation.film.components

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * A magnifier icon that opens a small menu offering Sonarr/Radarr's two search modes: an
 * automatic search (the service grabs the best release itself) or a manual/interactive one (opens
 * [ReleasePickerSheet] to pick a specific release). Used from the Season screen's episode rows,
 * the Episode/Movie detail screens, and Calendar entries.
 */
@Composable
fun PvrSearchButton(
    onAutomaticSearch: () -> Unit,
    onManualSearch: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(CoreR.string.search_episode),
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { menuExpanded = true }, modifier = modifier) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_search),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(CoreR.string.search_episode_automatic)) },
            onClick = {
                menuExpanded = false
                onAutomaticSearch()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(CoreR.string.search_episode_manual)) },
            onClick = {
                menuExpanded = false
                onManualSearch()
            },
        )
    }
}

@Composable
@Preview
private fun PvrSearchButtonPreview() {
    FindroidTheme { PvrSearchButton(onAutomaticSearch = {}, onManualSearch = {}) }
}
