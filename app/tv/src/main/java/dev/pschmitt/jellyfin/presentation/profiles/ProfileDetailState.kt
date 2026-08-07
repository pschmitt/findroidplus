package dev.pschmitt.jellyfin.presentation.profiles

/**
 * TV mirror of app/phone's
 * `dev.pschmitt.jellyfin.presentation.settings.profiles.ProfileDetailState`
 * - app/tv has no module dependency on app/phone, so the state shape is duplicated here rather than
 *   imported. Keep this in sync with the phone version if its shape changes.
 */

/** One Sonarr/Radarr/Seerr card's worth of state - either an inherited preview or an override. */
data class PvrSectionState(
    val inheriting: Boolean = true,
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val httpHeaders: String = "",
    val basicAuthUsername: String = "",
    val basicAuthPassword: String = "",
    val testState: PvrTestState = PvrTestState.Idle,
)

data class ProfileDetailState(
    val loading: Boolean = true,
    val name: String = "",
    val isMain: Boolean = false,
    val sonarr: PvrSectionState = PvrSectionState(),
    val radarr: PvrSectionState = PvrSectionState(),
    val seerr: PvrSectionState = PvrSectionState(),
    // Set once the profile has been deleted - the screen observes this to navigate back.
    val deleted: Boolean = false,
)
