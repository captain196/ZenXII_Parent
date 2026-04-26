package com.schoolsync.parent.data.remote

import com.schoolsync.parent.data.local.DevPrefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites the scheme + host + port of every outgoing request to match
 * the user's currently-configured BASE_URL override (set via the Dev
 * Settings dialog). Keeps the original path + query untouched.
 *
 * This lets developers point the app at any LAN IP / tunnel URL
 * without rebuilding when their PC's IP changes mid-test session.
 *
 * Behaviour:
 *  - When the override is empty or matches the compiled-in URL, the
 *    request passes through unchanged.
 *  - On parse failure (user typed garbage), the request also passes
 *    through unchanged so the app at least surfaces the original
 *    network error rather than a confusing rewrite failure.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val devPrefs: DevPrefs
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val overrideUrlString = devPrefs.effectiveBaseUrlBlocking()
        val overrideUrl = overrideUrlString.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val originalUrl = original.url
        // No-op if the request is already pointed at the override host
        if (originalUrl.host == overrideUrl.host &&
            originalUrl.port == overrideUrl.port &&
            originalUrl.scheme == overrideUrl.scheme) {
            return chain.proceed(original)
        }

        val rewritten = originalUrl.newBuilder()
            .scheme(overrideUrl.scheme)
            .host(overrideUrl.host)
            .port(overrideUrl.port)
            .build()
        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
