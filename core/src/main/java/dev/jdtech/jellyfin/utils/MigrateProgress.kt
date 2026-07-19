package dev.jdtech.jellyfin.utils

/** A snapshot of an in-flight [Downloader.migrateItems] batch, emitted by [Downloader.getMigrateProgressFlow]. */
data class MigrateProgress(val done: Int, val total: Int)
