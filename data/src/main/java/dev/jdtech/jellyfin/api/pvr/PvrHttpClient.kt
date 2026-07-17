package dev.jdtech.jellyfin.api.pvr

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Credentials
import timber.log.Timber

/**
 * Shared holder for the [OkHttpClient] used to talk to a Sonarr/Radarr instance. The base client
 * (connection pool, timeouts, dispatcher) is built once and reused via [OkHttpClient.newBuilder]
 * so every [SonarrApi]/[RadarrApi] instance can be constructed cheaply per-call - only a fresh
 * `X-Api-Key` interceptor is attached per instance, since the key can change at runtime if the
 * user reconfigures the server in settings.
 */
internal object PvrHttpClient {
    private const val API_KEY_HEADER = "X-Api-Key"

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(LoggingInterceptor())
            .build()
    }

    fun create(apiKey: String, service: PvrService): OkHttpClient {
        return baseClient.newBuilder().addInterceptor(AuthInterceptor(apiKey, service)).build()
    }

    private class AuthInterceptor(private val apiKey: String, private val service: PvrService) :
        Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val advanced = PvrAdvancedSettings.provider(service)
            val requestBuilder = chain.request().newBuilder().header(API_KEY_HEADER, apiKey)
            if (!advanced.basicAuthUsername.isNullOrBlank() && !advanced.basicAuthPassword.isNullOrBlank()) {
                requestBuilder.header(
                    "Authorization",
                    Credentials.basic(advanced.basicAuthUsername, advanced.basicAuthPassword),
                )
            }
            // Apply custom headers last so an explicit Authorization header wins over Basic auth.
            advanced.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            val request = requestBuilder.build()
            return chain.proceed(request)
        }
    }

    /**
     * Method/URL/status/timing only - never headers or bodies, since the API key travels as a
     * header (see [ApiKeyInterceptor]). The interactive release search in particular can run for
     * a long time waiting on indexers, and otherwise leaves zero trace of having happened at all
     * if it never returns - this is the only way to tell, from logcat, whether a request is still
     * in flight, came back with an unexpected status, or never made it out at all.
     *
     * `BaseApplication` plants a [Timber.DebugTree] regardless of build type specifically so this
     * (and other `Timber` diagnostics) aren't silent on the release builds users actually run.
     */
    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startNs = System.nanoTime()
            Timber.d("PVR --> %s %s", request.method, request.url)
            try {
                val response = chain.proceed(request)
                val tookMs = (System.nanoTime() - startNs) / 1_000_000
                Timber.d("PVR <-- %s %s (%dms)", response.code, request.url, tookMs)
                return response
            } catch (e: IOException) {
                val tookMs = (System.nanoTime() - startNs) / 1_000_000
                Timber.w(e, "PVR <-- FAILED %s %s (%dms)", request.method, request.url, tookMs)
                throw e
            }
        }
    }
}

/** Thrown when a Sonarr/Radarr request completes but with a non-2xx HTTP status. */
class PvrApiException(message: String, val httpCode: Int) : IOException(message)
