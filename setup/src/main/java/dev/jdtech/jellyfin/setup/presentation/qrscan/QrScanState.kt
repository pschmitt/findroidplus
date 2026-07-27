package dev.jdtech.jellyfin.setup.presentation.qrscan

data class QrScanState(
    val isApplying: Boolean = false,
    val needsPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)
