package dev.pschmitt.jellyfin.pvr

import dev.pschmitt.jellyfin.api.pvr.PvrService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is Sonarr/Radarr actually usable right now" (enabled + base URL + API
 * key present), so UI surfaces can hide PVR actions instead of showing buttons that can only fail
 * with a toast. Delegates to [PvrConfigResolver], which resolves the currently active profile's
 * effective config (its own override, or inherited from the main profile).
 */
@Singleton
class PvrConfiguration @Inject constructor(private val resolver: PvrConfigResolver) {
    fun isSonarrConfigured(): Boolean = resolver.isConfigured(PvrService.SONARR)

    fun isRadarrConfigured(): Boolean = resolver.isConfigured(PvrService.RADARR)

    fun isSeerrConfigured(): Boolean = resolver.isConfigured(PvrService.SEERR)

    fun isAnyConfigured(): Boolean = isSonarrConfigured() || isRadarrConfigured()
}
