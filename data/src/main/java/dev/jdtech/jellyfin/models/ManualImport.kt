package dev.jdtech.jellyfin.models

/**
 * One file Sonarr/Radarr found in a download it couldn't fully auto-import (e.g. a season-pack
 * release where the service can't map every file, or a rejected quality/language), with the
 * service's own guessed mapping - see
 * [dev.jdtech.jellyfin.repository.QueueStatusRepository.getManualImportCandidates].
 *
 * [canImport] is false when the service couldn't map the file to anything at all (Sonarr: no
 * episode guess) - importing it needs a manual episode assignment, which isn't supported yet (see
 * TODO.md); such files are shown for visibility but can't be selected.
 */
data class ManualImportCandidate(
    val id: Int,
    val name: String,
    val sizeBytes: Long,
    val qualityName: String?,
    val episodeLabel: String?,
    val canImport: Boolean,
    val rejections: List<String>,
)
