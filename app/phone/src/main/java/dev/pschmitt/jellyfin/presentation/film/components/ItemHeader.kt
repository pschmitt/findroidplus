package dev.pschmitt.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.pschmitt.jellyfin.models.FindroidEpisode
import dev.pschmitt.jellyfin.models.FindroidItem
import dev.pschmitt.jellyfin.models.FindroidSeason
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.presentation.utils.parallaxLayoutModifier

@Composable
fun ItemHeader(
    item: FindroidItem,
    scrollState: ScrollState,
    showLogo: Boolean = false,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val context = LocalContext.current
    var backdropUri =
        when (item) {
            is FindroidEpisode -> item.images.primary
            else -> item.images.backdrop
        }

    // Ugly workaround to append the files directory when loading local images
    if (backdropUri?.scheme == null) {
        backdropUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(backdropUri?.path)
                .build()
    }

    ItemHeaderBase(
        item = item,
        showLogo = showLogo,
        backdropImage = {
            AsyncImage(
                model = backdropUri,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize()
                        .parallaxLayoutModifier(scrollState = scrollState, rate = 2),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
            )
        },
        content = content,
    )
}

/**
 * Static (non-scrolling) variant of the same hero banner shape - same 16:9/full-width box as the
 * scrolling overloads above, minus the parallax effect that needs a host scroll position to react
 * to. For hero banners that aren't the top of their own scrollable detail page, e.g. the Home
 * screen's suggestions carousel (HomeCarouselItem) - so the two read as one consistent banner
 * shape instead of a bespoke one-off per screen.
 */
@Composable
fun ItemHeader(
    item: FindroidItem,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val context = LocalContext.current
    var backdropUri =
        when (item) {
            is FindroidEpisode -> item.images.primary
            else -> item.images.backdrop
        }

    // Ugly workaround to append the files directory when loading local images
    if (backdropUri?.scheme == null) {
        backdropUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(backdropUri?.path)
                .build()
    }

    ItemHeaderBase(
        item = item,
        modifier = modifier,
        showLogo = showLogo,
        backdropImage = {
            AsyncImage(
                model = backdropUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
            )
        },
        content = content,
    )
}

@Composable
fun ItemHeader(
    item: FindroidItem,
    lazyListState: LazyListState,
    showLogo: Boolean = false,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val context = LocalContext.current
    var backdropUri =
        when (item) {
            is FindroidEpisode -> item.images.primary
            is FindroidSeason -> item.images.showBackdrop
            else -> item.images.backdrop
        }

    // Ugly workaround to append the files directory when loading local images
    if (backdropUri?.scheme == null) {
        backdropUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(backdropUri?.path)
                .build()
    }

    ItemHeaderBase(
        item = item,
        showLogo = showLogo,
        backdropImage = {
            AsyncImage(
                model = backdropUri,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize()
                        .parallaxLayoutModifier(lazyListState = lazyListState, rate = 2),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
            )
        },
        content = content,
    )
}

@Composable
private fun ItemHeaderBase(
    item: FindroidItem?,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    backdropImage: @Composable (() -> Unit),
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val backgroundColor = MaterialTheme.colorScheme.background

    val logoUri =
        when (item) {
            is FindroidEpisode -> item.images.showLogo
            null -> null
            else -> item.images.logo
        }

    // Same 16:9 ratio, full width, no clip/rounding on any caller - the one hero-banner shape
    // shared by every detail screen and the Home carousel (HomeCarouselItem), rather than each
    // screen defining its own size/shape for what should read as the same component.
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.77f).clipToBounds().then(modifier)) {
        backdropImage()
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.1f))
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, backgroundColor),
                        startY = 0f,
                    )
            )
        }
        content()
        if (showLogo && logoUri != null) {
            AsyncImage(
                model = logoUri,
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(MaterialTheme.spacings.default)
                        .height(100.dp)
                        .fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * Same hero-banner shape as the [FindroidItem] overloads above, for screens whose backdrop isn't
 * a Jellyfin server image at all (e.g. SeerrMediaScreen, which only has a plain TMDB CDN URL, not
 * a [FindroidItem]/[Uri]-based one to resolve). No parallax/logo support - neither has been needed
 * by a non-Jellyfin caller yet.
 */
@Composable
fun ItemHeader(
    backdropUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    ItemHeaderBase(
        item = null,
        modifier = modifier,
        backdropImage = {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
            )
        },
        content = content,
    )
}
