package dev.pschmitt.jellyfin.setup.presentation.welcome

sealed interface WelcomeAction {
    data object OnContinueClick : WelcomeAction

    data object OnLearnMoreClick : WelcomeAction

    data object OnRestoreClick : WelcomeAction

    data object OnScanQrClick : WelcomeAction
}
