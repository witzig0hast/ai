package de.hastnetwork.jarvis.data.remote

import de.hastnetwork.jarvis.data.local.AppSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retrofit needs a valid `baseUrl` at construction time, but our real base
 * URL is only known once Settings has been read (and can change at runtime
 * if the user edits it there). To avoid rebuilding Retrofit on every change,
 * Retrofit is built against a harmless placeholder host and this
 * interceptor rewrites every outgoing request's scheme/host/port/path
 * prefix to the currently configured backend base URL before it hits the
 * network.
 */
class DynamicBaseUrlInterceptor(
    private val settingsProvider: () -> AppSettings,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configuredBaseUrl = settingsProvider().normalizedBaseUrl
        if (configuredBaseUrl.isEmpty()) {
            // Nothing configured yet - let the request fail naturally against
            // the placeholder host rather than crash the interceptor chain.
            return chain.proceed(original)
        }
        val configuredHttpUrl = configuredBaseUrl.toHttpUrlOrNull() ?: return chain.proceed(original)

        val originalUrl = original.url
        val rewrittenUrl = originalUrl.newBuilder()
            .scheme(configuredHttpUrl.scheme)
            .host(configuredHttpUrl.host)
            .port(configuredHttpUrl.port)
            .build()

        val newRequest = original.newBuilder().url(rewrittenUrl).build()
        return chain.proceed(newRequest)
    }
}

/** Attaches `Authorization: Bearer <token>` to every REST call, per architecture.md §3. */
class AuthInterceptor(
    private val settingsProvider: () -> AppSettings,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = settingsProvider().token
        val original = chain.request()
        if (token.isBlank()) return chain.proceed(original)
        val authenticated = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authenticated)
    }
}
