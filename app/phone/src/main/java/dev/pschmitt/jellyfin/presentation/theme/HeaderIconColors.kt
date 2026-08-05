package dev.pschmitt.jellyfin.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed per-screen tints for the Media/Downloads/Calendar header icons (see
 * LibraryScreen/DownloadsScreen/CalendarScreen) - deliberately not derived from the M3 color
 * scheme, whose tonal primary/secondary/tertiary are close cousins of the same seed hue and read as
 * near-identical at a glance, which defeats the point of a per-screen color.
 */
object HeaderIconColors {
    val Media = Color(0xFF4C9AFF)
    val Downloads = Color(0xFF4CAF50)
    val Calendar = Color(0xFFFFA726)
}
