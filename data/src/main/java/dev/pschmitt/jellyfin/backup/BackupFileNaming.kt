package dev.pschmitt.jellyfin.backup

import android.os.Build
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Backup filename shared by [AutoBackupWorker][dev.pschmitt.jellyfin.work.AutoBackupWorker]'s
 * scheduled exports and the manual "Back up now" flow, so the two can't drift apart - e.g.
 * "findroidplus-backup-Pixel-8-2026-07-17T08:58:03+02:00.frb". SAF/Drive accept ':' in display
 * names, so the UTC offset can stay in its readable form.
 *
 * Includes the device model so backups from multiple devices sharing one destination folder
 * don't overwrite each other's files.
 */
object BackupFileNaming {
    fun fileName(): String {
        val timestamp =
            ZonedDateTime.now()
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        // SAF display names tolerate most characters, but device model strings (e.g. "Pixel 8
        // Pro") often contain spaces - keep the result predictable across vendors.
        val deviceName = Build.MODEL.replace(Regex("[^A-Za-z0-9._-]+"), "-")
        return "findroidplus-backup-$deviceName-$timestamp.frb"
    }
}
