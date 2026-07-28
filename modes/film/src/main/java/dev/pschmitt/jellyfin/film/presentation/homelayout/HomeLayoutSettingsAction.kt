package dev.pschmitt.jellyfin.film.presentation.homelayout

sealed interface HomeLayoutSettingsAction {
    data class OnMoveUp(val index: Int) : HomeLayoutSettingsAction

    data class OnMoveDown(val index: Int) : HomeLayoutSettingsAction

    data class OnHide(val key: String) : HomeLayoutSettingsAction

    data class OnRestore(val key: String) : HomeLayoutSettingsAction

    data object OnResetLayout : HomeLayoutSettingsAction
}
