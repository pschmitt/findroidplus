package dev.jdtech.jellyfin.api.pvr

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

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
            .build()
    }

    fun create(apiKey: String): OkHttpClient {
        return baseClient.newBuilder().addInterceptor(ApiKeyInterceptor(apiKey)).build()
    }

    private class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder().header(API_KEY_HEADER, apiKey).build()
            return chain.proceed(request)
        }
    }
}

/** Thrown when a Sonarr/Radarr request completes but with a non-2xx HTTP status. */
class PvrApiException(message: String) : IOException(message)
