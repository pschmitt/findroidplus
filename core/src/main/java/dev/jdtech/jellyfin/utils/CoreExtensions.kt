package dev.jdtech.jellyfin.utils

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.util.Base64
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.View
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto

fun BaseItemDto.toView(items: List<FindroidItem>): View {
    return View(
        id = id,
        name = name ?: "",
        items = items,
        type = CollectionType.fromString(collectionType?.serialName),
    )
}

fun Resources.dip(px: Int) = (px * displayMetrics.density).toInt()

fun Activity.restart() {
    val intent = Intent(this, this::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
}

/**
 * Unlike [restart], this kills the whole process rather than just recreating the Activity - only
 * an Activity restart isn't enough after a backup restore, since @Singleton-scoped Hilt
 * dependencies like JellyfinApi are constructed once from the current server/user at process
 * startup and never rebuilt for the lifetime of the process.
 */
fun Activity.restartProcess() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    Runtime.getRuntime().exit(0)
}

fun String.base64ToByteArray(): ByteArray {
    return Base64.decode(toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
}

fun ByteArray.toBase64Str(): String {
    return Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP)
}

// For episodes/seasons, `.name` alone ("Pilot", "Season 1") is ambiguous out of context - this
// adds the show (and, for episodes, season/episode number) so delete confirmations read
// unambiguously, e.g. "Breaking Bad • S1E1 • Pilot".
fun FindroidItem.displayNameWithContext(): String =
    when (this) {
        is FindroidEpisode -> "$seriesName • S${parentIndexNumber}E$indexNumber • $name"
        is FindroidSeason -> "$seriesName • $name"
        else -> name
    }

// [pattern] is the raw "pref_date_format" preference value ("system"/"iso"/"dmy"/"mdy") - see
// AppPreferences.dateFormat. Falls back to the locale-based system short date format for
// "system" and for any unrecognized value, so an old/blank preference never breaks formatting.
//
// `DateTime` (a `java.time.LocalDateTime` typealias) only ever carries a calendar date here -
// Jellyfin's metadata providers supply premiere dates with no real time-of-day, and the server
// always emits that as midnight UTC. This used to convert that midnight-UTC value to an
// `Instant`/`Date` and re-render it with the device's default (local) time zone, which rolls the
// *displayed* date back a full day for any zone behind UTC - e.g. a premiere date the Calendar
// screen correctly shows as "Jul 25" (derived from Sonarr's real `airDateUtc` instant, properly
// zone-converted) would show as "Jul 24" here. Formatting the date components directly, with no
// zone conversion at all, keeps the calendar date exactly as Jellyfin sent it.
fun DateTime.format(pattern: String = "system"): String {
    val date = this.toLocalDate()
    return when (pattern) {
        "iso" -> date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
        "dmy" -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))
        "mdy" -> date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    }
}
