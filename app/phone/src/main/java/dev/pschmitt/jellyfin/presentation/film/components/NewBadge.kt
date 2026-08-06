package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    BaseBadge(modifier = modifier, containerColor = MaterialTheme.colorScheme.tertiary) {
        Text(
            text = stringResource(CoreR.string.new_badge),
            color = MaterialTheme.colorScheme.onTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.align(Alignment.Center)
                    .padding(horizontal = MaterialTheme.spacings.extraSmall),
        )
    }
}

@Composable
@Preview
private fun NewBadgePreview() {
    JollyfinTheme { NewBadge() }
}
