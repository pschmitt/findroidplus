package dev.pschmitt.jellyfin.presentation.film.components

import android.app.DownloadManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloaderState
import dev.pschmitt.jellyfin.models.QueueItemStatus
import dev.pschmitt.jellyfin.models.QueueStatus
import dev.pschmitt.jellyfin.models.UiText
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.DownloadProgress
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize
import dev.pschmitt.jellyfin.utils.formatDownloadSpeed
import dev.pschmitt.jellyfin.utils.formatEta
import kotlin.math.roundToInt

@Composable
fun DownloaderCard(
    state: DownloaderState,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    onForceClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    statusTextOverride: String? = null,
    showControls: Boolean = true,
    // Set when the current item has a PVR import warning/failure to resolve - makes the whole
    // card tappable to open the manage-import sheet, instead of only the per-status icon buttons.
    onCardClick: (() -> Unit)? = null,
) {
    val animatedProgress by
        animateFloatAsState(
            targetValue = state.progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        )

    val textColor =
        when (state.status) {
            DownloadManager.STATUS_PAUSED,
            DownloadProgress.STATUS_AWAITING_FOREGROUND -> Color.Yellow
            DownloadManager.STATUS_FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }

    val statusText =
        statusTextOverride
            ?: when (state.status) {
                DownloadManager.STATUS_PENDING -> stringResource(CoreR.string.download_queued)
                DownloadManager.STATUS_PAUSED -> stringResource(CoreR.string.download_paused)
                DownloadProgress.STATUS_AWAITING_FOREGROUND ->
                    stringResource(CoreR.string.download_awaiting_foreground)
                DownloadManager.STATUS_FAILED -> stringResource(CoreR.string.download_failed)
                DownloadProgress.STATUS_VERIFYING -> stringResource(CoreR.string.download_verifying)
                else -> stringResource(CoreR.string.download_downloading)
            }

    val progressIndicatorColor =
        when (state.status) {
            DownloadManager.STATUS_PAUSED,
            DownloadProgress.STATUS_AWAITING_FOREGROUND -> Color.Yellow
            DownloadManager.STATUS_SUCCESSFUL -> Color.Green
            DownloadManager.STATUS_FAILED -> MaterialTheme.colorScheme.error
            else -> ProgressIndicatorDefaults.linearColor
        }

    val progressTrackColor =
        when (state.status) {
            DownloadManager.STATUS_FAILED -> MaterialTheme.colorScheme.errorContainer
            else -> ProgressIndicatorDefaults.linearTrackColor
        }

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                title?.let {
                    Text(text = it, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = animatedProgress.times(100).roundToInt().toString() + "%",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                when (state.status) {
                    DownloadManager.STATUS_PENDING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressIndicatorColor,
                            trackColor = progressTrackColor,
                        )
                    }
                }
                if (
                    state.status == DownloadManager.STATUS_RUNNING && state.speedBytesPerSecond > 0
                ) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    val speedText = formatDownloadSpeed(state.speedBytesPerSecond)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text =
                                if (state.etaSeconds >= 0) {
                                    stringResource(
                                        CoreR.string.download_speed_eta,
                                        speedText,
                                        formatEta(state.etaSeconds),
                                    )
                                } else {
                                    speedText
                                },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.totalBytes > 0) {
                            Text(
                                text = formatBinaryFileSize(state.totalBytes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                if (state.errorText != null) {
                    Text(
                        text = state.errorText!!.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (showControls) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)
                    ) {
                        when (state.status) {
                            DownloadManager.STATUS_PENDING -> {
                                FilledTonalIconButton(onClick = onForceClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_fast_forward),
                                        contentDescription =
                                            stringResource(CoreR.string.download_action_force),
                                    )
                                }
                                FilledTonalIconButton(onClick = onCancelClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_x),
                                        contentDescription = null,
                                    )
                                }
                            }
                            DownloadManager.STATUS_RUNNING -> {
                                FilledTonalIconButton(onClick = onPauseClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_pause),
                                        contentDescription =
                                            stringResource(CoreR.string.download_action_pause),
                                    )
                                }
                                FilledTonalIconButton(onClick = onCancelClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_x),
                                        contentDescription = null,
                                    )
                                }
                            }
                            DownloadManager.STATUS_PAUSED,
                            DownloadProgress.STATUS_AWAITING_FOREGROUND -> {
                                FilledTonalIconButton(onClick = onResumeClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_play),
                                        contentDescription =
                                            stringResource(CoreR.string.download_action_resume),
                                    )
                                }
                                FilledTonalIconButton(onClick = onCancelClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_x),
                                        contentDescription = null,
                                    )
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                FilledTonalIconButton(onClick = onRetryClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                        contentDescription = null,
                                    )
                                }
                            }
                            DownloadProgress.STATUS_VERIFYING -> {
                                FilledTonalIconButton(onClick = onCancelClick) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_x),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (onCardClick != null) {
        OutlinedCard(onClick = onCardClick, modifier = modifier) { cardContent() }
    } else {
        OutlinedCard(modifier = modifier) { cardContent() }
    }
}

/** Reuses the local-download progress card for a Sonarr/Radarr queue item. */
@Composable
fun PvrQueueDownloadCard(
    status: QueueStatus,
    title: String? = null,
    modifier: Modifier = Modifier,
    // Set when this queue entry has an import warning/failure to resolve - makes the whole card
    // tappable to open the manage-import sheet.
    onCardClick: (() -> Unit)? = null,
) {
    val statusText =
        when (status.status) {
            QueueItemStatus.QUEUED -> stringResource(CoreR.string.download_queued)
            QueueItemStatus.DOWNLOADING -> stringResource(CoreR.string.download_downloading)
            QueueItemStatus.IMPORTING -> stringResource(CoreR.string.pvr_queue_status_importing)
            QueueItemStatus.WARNING -> stringResource(CoreR.string.pvr_queue_status_warning)
            QueueItemStatus.FAILED -> stringResource(CoreR.string.pvr_queue_status_failed)
        }
    val downloaderStatus =
        when (status.status) {
            QueueItemStatus.QUEUED -> DownloadManager.STATUS_PENDING
            QueueItemStatus.DOWNLOADING -> DownloadManager.STATUS_RUNNING
            QueueItemStatus.IMPORTING -> DownloadProgress.STATUS_VERIFYING
            QueueItemStatus.WARNING,
            QueueItemStatus.FAILED -> DownloadManager.STATUS_FAILED
        }

    DownloaderCard(
        state =
            DownloaderState(
                status = downloaderStatus,
                progress = status.percent.coerceIn(0, 100) / 100f,
                errorText = status.errorMessage?.let(UiText::DynamicString),
                speedBytesPerSecond = status.speedBytesPerSecond,
                etaSeconds = status.etaSeconds,
                downloadedBytes = (status.sizeBytes - status.remainingBytes).coerceAtLeast(0L),
                totalBytes = status.sizeBytes,
            ),
        onCancelClick = {},
        onRetryClick = {},
        title = title,
        statusTextOverride = statusText,
        showControls = false,
        modifier = modifier,
        onCardClick = onCardClick,
    )
}

@Composable
@Preview
private fun DownloaderCardPendingPreview() {
    JollyfinTheme {
        DownloaderCard(
            state = DownloaderState(status = DownloadManager.STATUS_PENDING),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardDownloadingPreview() {
    JollyfinTheme {
        DownloaderCard(
            state = DownloaderState(status = DownloadManager.STATUS_RUNNING, progress = 0.5f),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardFailedPreview() {
    JollyfinTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    status = DownloadManager.STATUS_FAILED,
                    progress = 0.5f,
                    errorText = UiText.DynamicString("Not enough storage space"),
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}
