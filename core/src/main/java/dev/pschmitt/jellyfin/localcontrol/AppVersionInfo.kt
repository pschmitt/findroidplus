package dev.pschmitt.jellyfin.localcontrol

/**
 * The running app's own build metadata - `versionName`/`versionCode`/`gitRevision`, as reported by
 * `jollyfin-cli version`'s `GET /info` call. [LocalControlRouter] lives in the `core` module, but
 * these values only ever exist as a generated per-app-module `BuildConfig` (`app/phone`'s own,
 * distinct from `app/tv`'s - and `core` cannot reference either module's generated class directly).
 * `app/phone`'s Hilt `AppModule` binds the real implementation from its own `BuildConfig`; `app/tv`
 * doesn't use local control at all, so no binding is needed there.
 */
interface AppVersionInfo {
    val versionName: String
    val versionCode: Int
    val gitRevision: String
}
