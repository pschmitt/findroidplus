package dev.pschmitt.jellyfin.models

interface JollyfinSources {
    val sources: List<JollyfinSource>
    val runtimeTicks: Long
    val trickplayInfo: Map<String, JollyfinTrickplayInfo>?
}
