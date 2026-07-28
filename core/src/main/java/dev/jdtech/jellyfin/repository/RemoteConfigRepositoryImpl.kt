package dev.jdtech.jellyfin.repository

import android.os.Build
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.RemoteActiveRuleSummary
import dev.jdtech.jellyfin.models.RemoteConfigCommand
import dev.jdtech.jellyfin.models.RemoteDeviceInfo
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.AutoDownloadRuleEvaluator
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import timber.log.Timber

/**
 * Constructed via [dev.jdtech.jellyfin.di.RemoteConfigModule] (a Hilt `@Provides`, mirroring
 * `AutoDownloadRuleModule`) rather than an `@Inject` constructor, since `data` has no Hilt plugin.
 * Lives in `core` rather than alongside every other `*RepositoryImpl` in `data` because applying
 * an [RemoteConfigCommand.EvaluateNow]/[RemoteConfigCommand.DownloadItem] command needs
 * [AutoDownloadRuleEvaluator]/[Downloader], both `core`-only - `data` has no dependency on `core`
 * (it's the other way around), so this class can't live there. The pure queue/expiry/pruning
 * logic it delegates to ([planRemoteConfigSync]) has no such dependency and stays in `data`,
 * unit-tested there without needing to fake any of this class's dependencies.
 *
 * [writeMutex] only serializes this process's own read-modify-writes; two different devices can
 * still race at the HTTP level (no ETag/CAS against the server). That's an accepted tradeoff for
 * this low-frequency, manual, personal-use feature - the pending queue (keyed by command id) and
 * the device registry (keyed by device id) are idempotent to replay, so a lost race just means a
 * change is picked up again on the next periodic sync rather than silently dropped.
 */
class RemoteConfigRepositoryImpl(
    private val jellyfinRepository: JellyfinRepository,
    private val ruleRepository: AutoDownloadRuleRepository,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
) : RemoteConfigRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val writeMutex = Mutex()

    override suspend fun listOtherDevices(): List<RemoteDeviceInfo> =
        withContext(Dispatchers.IO) {
            val thisId = appPreferences.getOrCreateThisDeviceId()
            decodeDevices(fetchBucket().customPrefs[KEY_DEVICES]).filter { it.id != thisId }
        }

    override suspend fun pushRuleUpdate(
        targetDeviceId: String,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonIds: Set<UUID>,
        alsoFutureSeasons: Boolean,
        onlyNewEpisodes: Boolean,
        onlyUnwatched: Boolean,
    ) {
        val showName = resolveShowName(seriesId)
        val originId = appPreferences.getOrCreateThisDeviceId()
        enqueue(
            RemoteConfigCommand.ReconcileRules(
                id = UUID.randomUUID().toString(),
                targetDeviceId = targetDeviceId,
                originDeviceId = originId,
                createdAt = System.currentTimeMillis(),
                serverId = serverId,
                displayName = showName,
                userId = userId.toString(),
                seriesId = seriesId.toString(),
                seasonIds = seasonIds.map { it.toString() },
                alsoFutureSeasons = alsoFutureSeasons,
                onlyNewEpisodes = onlyNewEpisodes,
                onlyUnwatched = onlyUnwatched,
            )
        )
    }

    override suspend fun pushRemoveRule(
        targetDeviceId: String,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
    ) {
        pushRuleUpdate(
            targetDeviceId = targetDeviceId,
            serverId = serverId,
            userId = userId,
            seriesId = seriesId,
            seasonIds = emptySet(),
            alsoFutureSeasons = false,
            onlyNewEpisodes = false,
            onlyUnwatched = false,
        )
    }

    override suspend fun pushDownloadWithScope(
        targetDeviceId: String,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonIds: Set<UUID>,
        alsoFollowNew: Boolean,
        alsoFutureSeasons: Boolean,
        onlyUnwatched: Boolean,
    ) {
        // Mirrors ShowViewModel/SeasonViewModel/EpisodeViewModel's local downloadWithScope: the
        // immediate "evaluate what matches right now" part and the persistent-rule part are
        // independent of each other, not alternatives - see those ViewModels' own kdoc.
        val showName = resolveShowName(seriesId)
        val originId = appPreferences.getOrCreateThisDeviceId()
        val now = System.currentTimeMillis()
        if (seasonIds.isNotEmpty()) {
            enqueue(
                RemoteConfigCommand.EvaluateNow(
                    id = UUID.randomUUID().toString(),
                    targetDeviceId = targetDeviceId,
                    originDeviceId = originId,
                    createdAt = now,
                    serverId = serverId,
                    displayName = showName,
                    userId = userId.toString(),
                    seriesId = seriesId.toString(),
                    seasonIds = seasonIds.map { it.toString() },
                    onlyUnwatched = onlyUnwatched,
                )
            )
        }
        if (alsoFollowNew || alsoFutureSeasons) {
            enqueue(
                RemoteConfigCommand.ReconcileRules(
                    id = UUID.randomUUID().toString(),
                    targetDeviceId = targetDeviceId,
                    originDeviceId = originId,
                    createdAt = now,
                    serverId = serverId,
                    displayName = showName,
                    userId = userId.toString(),
                    seriesId = seriesId.toString(),
                    seasonIds = if (alsoFollowNew) seasonIds.map { it.toString() } else emptyList(),
                    alsoFutureSeasons = alsoFutureSeasons,
                    onlyNewEpisodes = false,
                    onlyUnwatched = onlyUnwatched,
                )
            )
        }
    }

    override suspend fun pushItemDownload(
        targetDeviceId: String,
        serverId: String,
        itemId: UUID,
        sourceId: String,
    ) {
        val itemName =
            try {
                jellyfinRepository.getItem(itemId)?.name ?: itemId.toString()
            } catch (e: Exception) {
                Timber.w(e, "Failed to resolve item name for remote download push")
                itemId.toString()
            }
        enqueue(
            RemoteConfigCommand.DownloadItem(
                id = UUID.randomUUID().toString(),
                targetDeviceId = targetDeviceId,
                originDeviceId = appPreferences.getOrCreateThisDeviceId(),
                createdAt = System.currentTimeMillis(),
                serverId = serverId,
                displayName = itemName,
                itemId = itemId.toString(),
                sourceId = sourceId,
            )
        )
    }

    override suspend fun listPendingCommandsFromThisDevice(): List<RemoteConfigCommand> =
        withContext(Dispatchers.IO) {
            val thisId = appPreferences.getOrCreateThisDeviceId()
            decodeCommands(fetchBucket().customPrefs[KEY_PENDING]).filter {
                it.originDeviceId == thisId
            }
        }

    override suspend fun cancelPendingCommand(commandId: String) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val bucket = fetchBucket()
                val pending =
                    decodeCommands(bucket.customPrefs[KEY_PENDING]).filterNot { it.id == commandId }
                writeBucket(bucket, KEY_PENDING to json.encodeToString(pending))
            }
        }
    }

    override fun isRemoteManagementEnabled(): Boolean =
        appPreferences.getValue(appPreferences.remoteManagementEnabled)

    override suspend fun setRemoteManagementEnabled(enabled: Boolean) {
        appPreferences.setValue(appPreferences.remoteManagementEnabled, enabled)
        if (enabled) return
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val thisId = appPreferences.getOrCreateThisDeviceId()
                val bucket = fetchBucket()
                val devices = decodeDevices(bucket.customPrefs[KEY_DEVICES]).filterNot { it.id == thisId }
                val pending =
                    decodeCommands(bucket.customPrefs[KEY_PENDING]).filterNot {
                        it.targetDeviceId == thisId
                    }
                writeBucket(
                    bucket,
                    KEY_DEVICES to json.encodeToString(devices),
                    KEY_PENDING to json.encodeToString(pending),
                )
            }
        }
    }

    private suspend fun resolveShowName(seriesId: UUID): String =
        try {
            jellyfinRepository.getShow(seriesId).name
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve show name for remote push")
            seriesId.toString()
        }

    private suspend fun enqueue(command: RemoteConfigCommand) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                // Re-fetch immediately before merging so this write is based on the freshest
                // state available, rather than whatever was last read by a different call.
                val bucket = fetchBucket()
                val pending = decodeCommands(bucket.customPrefs[KEY_PENDING]) + command
                writeBucket(bucket, KEY_PENDING to json.encodeToString(pending))
            }
        }
    }

    override suspend fun syncNow() {
        if (!isRemoteManagementEnabled()) return
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val thisId = appPreferences.getOrCreateThisDeviceId()
                val now = System.currentTimeMillis()
                val bucket = fetchBucket()
                val allCommands = decodeCommands(bucket.customPrefs[KEY_PENDING])
                val devices = decodeDevices(bucket.customPrefs[KEY_DEVICES])

                val plan =
                    planRemoteConfigSync(
                        thisDeviceId = thisId,
                        thisDeviceName = Build.MODEL,
                        now = now,
                        allCommands = allCommands,
                        devices = devices,
                        hasServer = { serverId -> database.get(serverId) != null },
                        thisDeviceActiveRules = currentActiveRuleSummaries(),
                    )

                plan.commandsToApply.forEach { applyCommand(it) }

                writeBucket(
                    bucket,
                    KEY_PENDING to json.encodeToString(plan.remainingCommands),
                    KEY_DEVICES to json.encodeToString(plan.newDevices),
                )
            }
        }
    }

    /**
     * This device's own currently-active auto-download rules, summarized per show - published on
     * every sync so a "remote devices" management screen elsewhere can see (and remotely clear)
     * what's live here, without needing its own query channel into this device's Room database.
     */
    private suspend fun currentActiveRuleSummaries(): List<RemoteActiveRuleSummary> {
        val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return emptyList()
        val userId =
            try {
                jellyfinRepository.getUserId()
            } catch (e: Exception) {
                return emptyList()
            }
        return ruleRepository.getRules(serverId, userId).groupBy { it.seriesId }.mapNotNull {
            (seriesId, rules) ->
            try {
                RemoteActiveRuleSummary(
                    serverId = serverId,
                    seriesId = seriesId.toString(),
                    showName = jellyfinRepository.getShow(seriesId).name,
                    seasonCount = rules.count { it.seasonId != null && it.enabled },
                    alsoFutureSeasons = rules.any { it.seasonId == null && it.enabled },
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to resolve show name while publishing active-rule summary")
                null
            }
        }
    }

    private suspend fun applyCommand(command: RemoteConfigCommand) {
        try {
            when (command) {
                is RemoteConfigCommand.ReconcileRules -> applyReconcileRules(command)
                is RemoteConfigCommand.EvaluateNow -> applyEvaluateNow(command)
                is RemoteConfigCommand.DownloadItem -> applyDownloadItem(command)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply remote command ${command.id}")
        }
    }

    private suspend fun applyReconcileRules(command: RemoteConfigCommand.ReconcileRules) {
        ruleRepository.reconcileRules(
            serverId = command.serverId,
            userId = UUID.fromString(command.userId),
            seriesId = UUID.fromString(command.seriesId),
            seasonIds = command.seasonIds.map { UUID.fromString(it) }.toSet(),
            alsoFutureSeasons = command.alsoFutureSeasons,
            onlyNewEpisodes = command.onlyNewEpisodes,
            onlyUnwatched = command.onlyUnwatched,
        )
    }

    private suspend fun applyEvaluateNow(command: RemoteConfigCommand.EvaluateNow) {
        val userId = UUID.fromString(command.userId)
        val seriesId = UUID.fromString(command.seriesId)
        val evaluator = AutoDownloadRuleEvaluator()
        for (seasonIdString in command.seasonIds) {
            // Transient (never persisted) - same as ShowViewModel/SeasonViewModel/EpisodeViewModel's
            // local one-time "download what matches now" path, just evaluated here instead.
            val transientRule =
                AutoDownloadRuleDto(
                    serverId = command.serverId,
                    userId = userId,
                    seriesId = seriesId,
                    seasonId = UUID.fromString(seasonIdString),
                    enabled = true,
                    createdAt = command.createdAt,
                    onlyNewEpisodes = false,
                )
            evaluator.evaluate(
                transientRule,
                database,
                jellyfinRepository,
                downloader,
                appPreferences,
                command.onlyUnwatched,
            )
        }
    }

    private suspend fun applyDownloadItem(command: RemoteConfigCommand.DownloadItem) {
        val item = jellyfinRepository.getItem(UUID.fromString(command.itemId)) ?: return
        downloader.downloadItem(item, command.sourceId, downloader.resolvePreferredStorageIndex())
    }

    private suspend fun fetchBucket(): DisplayPreferencesDto =
        jellyfinRepository.getDisplayPreferences(BUCKET_ID, CLIENT)

    private suspend fun writeBucket(bucket: DisplayPreferencesDto, vararg entries: Pair<String, String>) {
        jellyfinRepository.updateDisplayPreferences(
            BUCKET_ID,
            CLIENT,
            bucket.copy(customPrefs = bucket.customPrefs + entries),
        )
    }

    private fun decodeCommands(raw: String?): List<RemoteConfigCommand> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode pending remote commands, dropping")
            emptyList()
        }
    }

    private fun decodeDevices(raw: String?): List<RemoteDeviceInfo> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode remote device registry, dropping")
            emptyList()
        }
    }

    private companion object {
        const val BUCKET_ID = "findroidplus-remoteconfig"
        const val CLIENT = "FindroidPlusRemoteConfig"
        const val KEY_PENDING = "pending"
        const val KEY_DEVICES = "devices"
    }
}
