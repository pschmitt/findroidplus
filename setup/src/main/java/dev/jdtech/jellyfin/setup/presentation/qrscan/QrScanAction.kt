package dev.jdtech.jellyfin.setup.presentation.qrscan

sealed interface QrScanAction {
    data class OnCodeScanned(val raw: String) : QrScanAction

    data class OnPasswordSubmit(val password: String) : QrScanAction

    data object OnBackClick : QrScanAction
}
