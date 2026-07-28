package dev.pschmitt.jellyfin.settings.domain.models

data class Preference<out T>(val backendName: String, val defaultValue: T)
