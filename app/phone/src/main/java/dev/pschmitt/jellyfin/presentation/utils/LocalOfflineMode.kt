package dev.pschmitt.jellyfin.presentation.utils

import androidx.compose.runtime.compositionLocalOf

val LocalOfflineMode = compositionLocalOf<Boolean> { error("Offline mode not defined") }
