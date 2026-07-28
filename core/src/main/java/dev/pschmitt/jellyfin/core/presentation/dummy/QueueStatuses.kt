package dev.pschmitt.jellyfin.core.presentation.dummy

import dev.pschmitt.jellyfin.models.PvrSource
import dev.pschmitt.jellyfin.models.QueueItemStatus
import dev.pschmitt.jellyfin.models.QueueStatus

val dummyQueueStatus =
    QueueStatus(
        source = PvrSource.SONARR,
        status = QueueItemStatus.DOWNLOADING,
        percent = 42,
        sizeBytes = 4_000_000_000L,
        remainingBytes = 2_320_000_000L,
        speedBytesPerSecond = 5_000_000L,
        etaSeconds = 464L,
    )
