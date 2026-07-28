package dev.jdtech.jellyfin.localcontrol

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The local control API's transport - a `ContentProvider` instead of a raw local socket. A prior
 * attempt used `android.net.LocalServerSocket` (a Linux abstract-namespace unix socket); on a real
 * device that's blocked outright by SELinux's default domain separation between arbitrary
 * `untrusted_app` processes (confirmed: connecting fails with `EACCES` under enforcing, works
 * under permissive) - not something any app-level fix can work around. A `ContentProvider`'s
 * `call()` is Binder-backed, which is the IPC mechanism Android's own policy is written to allow
 * between apps, and it's reachable from a plain shell via the `content call` command-line tool -
 * no compiled helper needed on the calling side. Auth is a single bearer token
 * ([LocalControlAuth]), not caller-identity-based, since a `call()` from a shell command doesn't
 * carry meaningful "this is Termux" identity the way a real app-to-app call would.
 *
 * Only `call()` is implemented - this isn't a real content resource, so query/insert/update/
 * delete/getType are all no-ops.
 */
class LocalControlProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocalControlProviderEntryPoint {
        fun appPreferences(): AppPreferences

        fun localControlAuth(): LocalControlAuth

        fun localControlRouter(): LocalControlRouter
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val entryPoint =
            EntryPointAccessors.fromApplication(
                context ?: return errorBundle(LocalControlStatus.INTERNAL_ERROR, "No context"),
                LocalControlProviderEntryPoint::class.java,
            )

        val appPreferences = entryPoint.appPreferences()
        if (!appPreferences.getValue(appPreferences.localControlEnabled)) {
            return errorBundle(LocalControlStatus.UNAUTHORIZED, "Local control is disabled")
        }

        val token = extras?.getString(EXTRA_TOKEN)
        if (!entryPoint.localControlAuth().verifyToken(token)) {
            return errorBundle(LocalControlStatus.UNAUTHORIZED, "Invalid or missing token")
        }

        val requestMethod = extras?.getString(EXTRA_METHOD) ?: "GET"
        val path = extras?.getString(EXTRA_PATH) ?: ""
        val bodyJson =
            extras?.getString(EXTRA_BODY)?.let {
                runCatching { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }.getOrNull()
            }
        val bodyElement = bodyJson?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

        val response =
            try {
                runBlocking { entryPoint.localControlRouter().handle(requestMethod, path, bodyElement) }
            } catch (e: Exception) {
                Timber.e(e, "LocalControlProvider.call failed")
                return errorBundle(LocalControlStatus.INTERNAL_ERROR, e.message ?: "Internal error")
            }

        return Bundle().apply {
            putInt(EXTRA_STATUS, response.status)
            response.body?.let {
                putString(EXTRA_BODY, Base64.encodeToString(it.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            }
        }
    }

    private fun errorBundle(status: Int, message: String): Bundle =
        Bundle().apply {
            putInt(EXTRA_STATUS, status)
            putString(
                EXTRA_BODY,
                Base64.encodeToString("{\"error\":\"${message.replace("\"", "'")}\"}".toByteArray(), Base64.NO_WRAP),
            )
        }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val CALL_METHOD = "rpc"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_METHOD = "method"
        const val EXTRA_PATH = "path"
        const val EXTRA_BODY = "body"
        const val EXTRA_STATUS = "status"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
