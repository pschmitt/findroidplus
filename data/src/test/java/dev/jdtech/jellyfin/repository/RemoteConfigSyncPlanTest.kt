package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.RemoteActiveRuleSummary
import dev.jdtech.jellyfin.models.RemoteConfigCommand
import dev.jdtech.jellyfin.models.RemoteDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigSyncPlanTest {

    private fun command(
        id: String = "cmd",
        targetDeviceId: String = "target",
        createdAt: Long = 0L,
        serverId: String = "server",
    ): RemoteConfigCommand =
        RemoteConfigCommand.ReconcileRules(
            id = id,
            targetDeviceId = targetDeviceId,
            originDeviceId = "origin",
            createdAt = createdAt,
            serverId = serverId,
            displayName = "Some Show",
            userId = "00000000-0000-0000-0000-000000000001",
            seriesId = "00000000-0000-0000-0000-000000000002",
            seasonIds = emptyList(),
            alsoFutureSeasons = true,
            onlyNewEpisodes = false,
            onlyUnwatched = false,
        )

    private fun downloadItemCommand(
        id: String = "dl",
        targetDeviceId: String = "target",
        createdAt: Long = 0L,
        serverId: String = "server",
    ): RemoteConfigCommand =
        RemoteConfigCommand.DownloadItem(
            id = id,
            targetDeviceId = targetDeviceId,
            originDeviceId = "origin",
            createdAt = createdAt,
            serverId = serverId,
            displayName = "Some Episode",
            itemId = "00000000-0000-0000-0000-000000000003",
            sourceId = "source-1",
        )

    @Test
    fun `applies only commands addressed to this device`() {
        val now = 1_000_000L
        val forThisDevice = command(id = "a", targetDeviceId = "this")
        val forOtherDevice = command(id = "b", targetDeviceId = "other")

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                allCommands = listOf(forThisDevice, forOtherDevice),
                devices = emptyList(),
                hasServer = { true },
            )

        assertEquals(listOf(forThisDevice), plan.commandsToApply)
        // The other device's command survives untouched in the remaining queue.
        assertTrue(plan.remainingCommands.contains(forOtherDevice))
        assertTrue(plan.remainingCommands.none { it.id == "a" })
    }

    @Test
    fun `drops a command older than the max age as dead-letter`() {
        val now = REMOTE_CONFIG_MAX_COMMAND_AGE_MILLIS + 1_000L
        val expired = command(id = "expired", targetDeviceId = "this", createdAt = 0L)

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                allCommands = listOf(expired),
                devices = emptyList(),
                hasServer = { true },
            )

        assertTrue(plan.commandsToApply.isEmpty())
        assertTrue(plan.remainingCommands.isEmpty())
    }

    @Test
    fun `leaves a command queued when the target hasn't added its server yet`() {
        val now = 1_000L
        val cmd = command(id = "a", targetDeviceId = "this", serverId = "unknown-server")

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                allCommands = listOf(cmd),
                devices = emptyList(),
                hasServer = { false },
            )

        assertTrue(plan.commandsToApply.isEmpty())
        assertEquals(listOf(cmd), plan.remainingCommands)
    }

    @Test
    fun `applies same-series commands oldest first so the latest push wins`() {
        val now = 1_000L
        val older = command(id = "older", targetDeviceId = "this", createdAt = 100L)
        val newer = command(id = "newer", targetDeviceId = "this", createdAt = 200L)

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                // Deliberately passed newest-first to prove the plan re-sorts by createdAt.
                allCommands = listOf(newer, older),
                devices = emptyList(),
                hasServer = { true },
            )

        assertEquals(listOf(older, newer), plan.commandsToApply)
    }

    @Test
    fun `prunes a device whose heartbeat is past the TTL, and its queued commands with it`() {
        val now = REMOTE_CONFIG_DEVICE_TTL_MILLIS + 1_000L
        val staleDevice = RemoteDeviceInfo(id = "stale", name = "Old Tablet", lastSeenMillis = 0L)
        val freshDevice = RemoteDeviceInfo(id = "fresh", name = "Tablet", lastSeenMillis = now)
        val commandForStaleDevice = command(id = "a", targetDeviceId = "stale")

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                allCommands = listOf(commandForStaleDevice),
                devices = listOf(staleDevice, freshDevice),
                hasServer = { true },
            )

        assertTrue(plan.newDevices.none { it.id == "stale" })
        assertTrue(plan.newDevices.any { it.id == "fresh" })
        assertTrue(plan.remainingCommands.isEmpty())
    }

    @Test
    fun `refreshes this device's own heartbeat every sync`() {
        val now = 12_345L

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                allCommands = emptyList(),
                devices = emptyList(),
                hasServer = { true },
            )

        val self = plan.newDevices.find { it.id == "this" }
        assertEquals("Pixel", self?.name)
        assertEquals(now, self?.lastSeenMillis)
    }

    @Test
    fun `queueing, expiry, and pruning are agnostic to which command subtype is queued`() {
        val now = 1_000L
        val reconcile = command(id = "reconcile", targetDeviceId = "this")
        val download = downloadItemCommand(id = "download", targetDeviceId = "this")

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                allCommands = listOf(reconcile, download),
                devices = emptyList(),
                hasServer = { true },
            )

        assertEquals(setOf(reconcile, download), plan.commandsToApply.toSet())
    }

    @Test
    fun `publishes this device's active rule summary on its own heartbeat entry`() {
        val now = 500L
        val summary =
            listOf(
                RemoteActiveRuleSummary(
                    serverId = "server",
                    seriesId = "00000000-0000-0000-0000-000000000002",
                    showName = "Some Show",
                    seasonCount = 2,
                    alsoFutureSeasons = true,
                )
            )

        val plan =
            planRemoteConfigSync(
                thisDeviceId = "this",
                thisDeviceName = "Pixel",
                now = now,
                allCommands = emptyList(),
                devices = emptyList(),
                hasServer = { true },
                thisDeviceActiveRules = summary,
            )

        val self = plan.newDevices.find { it.id == "this" }
        assertEquals(summary, self?.activeRules)
    }
}
