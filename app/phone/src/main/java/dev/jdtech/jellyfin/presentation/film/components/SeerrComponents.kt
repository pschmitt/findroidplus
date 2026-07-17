package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.SeerrMediaStatus
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.models.SeerrRequestItem
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * A Seerr search result: poster, title/year/overview, and a request button or status chip.
 * [onClick] (when non-null) makes the whole row tappable - opens the media's detail screen.
 */
@Composable
fun SeerrResultRow(
    item: SeerrSearchItem,
    requestedThisSession: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    // No horizontal padding of its own - the hosting list/grid's content padding provides it.
    Row(
        modifier = modifier.fillMaxWidth().seerrRowClickable(onClick),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeerrPoster(posterUrl = item.posterUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    listOfNotNull(item.year?.toString(), seerrMediaTypeLabel(item.mediaType))
                        .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.overview?.let { overview ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            requestedThisSession -> SeerrStatusChip(status = null)
            item.status == SeerrMediaStatus.NOT_REQUESTED ->
                Button(onClick = onRequest) {
                    Text(text = stringResource(CoreR.string.discover_request))
                }
            else -> SeerrStatusChip(status = item.status)
        }
    }
}

/**
 * A recently filed Seerr request: poster, title, media type, and its availability status.
 * [onCancel] (when non-null) renders a trailing cancel button - the caller decides which
 * requests are still worth cancelling (pending/processing).
 */
@Composable
fun SeerrRequestRow(
    request: SeerrRequestItem,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    // No horizontal padding of its own - the hosting list/grid's content padding provides it.
    Row(
        modifier = modifier.fillMaxWidth().seerrRowClickable(onClick),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeerrPoster(posterUrl = request.posterUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = seerrMediaTypeLabel(request.mediaType),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SeerrStatusChip(status = request.mediaStatus)
        if (onCancel != null) {
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_x),
                    contentDescription = stringResource(CoreR.string.seerr_cancel_request),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SeerrPoster(posterUrl: String?) {
    Box(
        modifier =
            Modifier.width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(CoreR.drawable.ic_film),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Colored pill making the item's lifecycle scannable at a glance. `null` renders the
 * just-requested-in-this-session marker (the search payload's own status is stale then).
 */
@Composable
fun SeerrStatusChip(status: SeerrMediaStatus?) {
    data class ChipStyle(val textRes: Int, val container: Color, val content: Color)

    val style =
        when (status) {
            SeerrMediaStatus.AVAILABLE ->
                ChipStyle(
                    CoreR.string.discover_status_available,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            SeerrMediaStatus.PARTIALLY_AVAILABLE ->
                ChipStyle(
                    CoreR.string.discover_status_partially_available,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            SeerrMediaStatus.PROCESSING ->
                ChipStyle(
                    CoreR.string.discover_status_processing,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            SeerrMediaStatus.PENDING ->
                ChipStyle(
                    CoreR.string.discover_status_pending,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            SeerrMediaStatus.NOT_REQUESTED,
            null ->
                ChipStyle(
                    CoreR.string.discover_status_requested,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
        }
    Box(
        modifier =
            Modifier.clip(CircleShape)
                .background(style.container)
                .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(style.textRes),
            style = MaterialTheme.typography.labelSmall,
            color = style.content,
        )
    }
}

/** Makes a Seerr row tappable (clipped so the ripple gets rounded corners) - no-op when null. */
@Composable
private fun Modifier.seerrRowClickable(onClick: (() -> Unit)?): Modifier =
    if (onClick != null) {
        this.clip(MaterialTheme.shapes.small).clickable(onClick = onClick)
    } else {
        this
    }

@Composable
fun seerrMediaTypeLabel(mediaType: SeerrMediaType): String =
    stringResource(
        when (mediaType) {
            SeerrMediaType.MOVIE -> CoreR.string.discover_type_movie
            SeerrMediaType.TV -> CoreR.string.discover_type_show
        }
    )
