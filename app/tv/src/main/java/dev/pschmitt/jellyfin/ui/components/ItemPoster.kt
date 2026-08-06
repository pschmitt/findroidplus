package dev.pschmitt.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import dev.pschmitt.jellyfin.models.JollyfinEpisode
import dev.pschmitt.jellyfin.models.JollyfinItem
import dev.pschmitt.jellyfin.models.JollyfinMovie

enum class Direction {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun ItemPoster(item: JollyfinItem, direction: Direction, modifier: Modifier = Modifier) {
    var imageUri = item.images.primary

    when (direction) {
        Direction.HORIZONTAL -> {
            if (item is JollyfinMovie) imageUri = item.images.backdrop
        }
        Direction.VERTICAL -> {
            when (item) {
                is JollyfinEpisode -> imageUri = item.images.showPrimary
            }
        }
    }

    AsyncImage(
        model = imageUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(if (direction == Direction.HORIZONTAL) 1.77f else 0.66f)
                .background(MaterialTheme.colorScheme.surface),
    )
}
