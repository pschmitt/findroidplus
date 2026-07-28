package dev.jdtech.jellyfin.localcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the Approve/Deny actions on a pairing-request notification (see [PairingNotifier]).
 * [LocalControlServer] is a Hilt singleton, so this receiver gets the exact same instance that's
 * holding the CLI's connection open and waiting on [LocalControlServer.resolvePairing].
 */
@AndroidEntryPoint
class PairingActionReceiver : BroadcastReceiver() {
    @Inject lateinit var localControlServer: LocalControlServer

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val approved =
            when (intent.action) {
                ACTION_APPROVE -> true
                ACTION_DENY -> false
                else -> return
            }
        localControlServer.resolvePairing(requestId, approved)
        PairingNotifier.cancel(context, requestId)
    }

    companion object {
        const val ACTION_APPROVE = "dev.jdtech.jellyfin.action.PAIRING_APPROVE"
        const val ACTION_DENY = "dev.jdtech.jellyfin.action.PAIRING_DENY"
        const val EXTRA_REQUEST_ID = "EXTRA_REQUEST_ID"
    }
}
