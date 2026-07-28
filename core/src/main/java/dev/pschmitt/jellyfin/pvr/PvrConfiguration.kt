package dev.pschmitt.jellyfin.pvr

import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is Sonarr/Radarr actually usable right now" (enabled + base URL +
 * API key present), so UI surfaces can hide PVR actions instead of showing buttons that can only
 * fail with a toast. Lives in `core` because the API key sits in [SecureCredentialStore], which
 * the `data`-layer repositories can't reach (they get it as an injected lambda instead).
 */
@Singleton
class PvrConfiguration
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val secureCredentialStore: SecureCredentialStore,
) {
    fun isSonarrConfigured(): Boolean =
        appPreferences.getValue(appPreferences.sonarrEnabled) &&
            !appPreferences.getValue(appPreferences.sonarrBaseUrl).isNullOrBlank() &&
            !secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY).isNullOrBlank()

    fun isRadarrConfigured(): Boolean =
        appPreferences.getValue(appPreferences.radarrEnabled) &&
            !appPreferences.getValue(appPreferences.radarrBaseUrl).isNullOrBlank() &&
            !secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY).isNullOrBlank()

    fun isSeerrConfigured(): Boolean =
        appPreferences.getValue(appPreferences.seerrEnabled) &&
            !appPreferences.getValue(appPreferences.seerrBaseUrl).isNullOrBlank() &&
            !secureCredentialStore.getString(PvrCredentialKeys.SEERR_API_KEY).isNullOrBlank()

    fun isAnyConfigured(): Boolean = isSonarrConfigured() || isRadarrConfigured()
}
