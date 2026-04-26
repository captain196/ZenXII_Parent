package com.schoolsync.parent.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.schoolsync.parent.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.devDataStore by preferencesDataStore(name = "schoolsync_dev_prefs")

/**
 * Developer-only override for the PHP backend BASE_URL.
 *
 * Why this exists: during local testing the dev PC's LAN IP changes
 * every time the user joins a new WiFi network, which would otherwise
 * require updating `build.gradle.kts` and rebuilding the app on every
 * IP change. This wrapper lets the user paste a new URL in-app from
 * the hidden Dev Settings dialog (long-press the app title on the
 * Login screen).
 *
 * The override is read synchronously by `BaseUrlInterceptor` on every
 * outgoing request, so changes take effect immediately without an app
 * restart. Falls back to `BuildConfig.BASE_URL` when no override is set.
 *
 * Stored in a separate DataStore from `TokenManager` so a user "Sign
 * out" doesn't wipe the dev override.
 */
@Singleton
class DevPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.devDataStore

    private object Keys {
        val BASE_URL_OVERRIDE = stringPreferencesKey("base_url_override")
    }

    /** Live flow of the active base URL — override if set, else BuildConfig default. */
    val effectiveBaseUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.BASE_URL_OVERRIDE]?.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL
    }

    /** Synchronous read used by the OkHttp interceptor (off the main thread). */
    fun effectiveBaseUrlBlocking(): String {
        return runBlocking {
            dataStore.data.first()[Keys.BASE_URL_OVERRIDE]?.takeIf { it.isNotBlank() }
                ?: BuildConfig.BASE_URL
        }
    }

    /** Compile-time default for the "Reset" button. */
    fun defaultBaseUrl(): String = BuildConfig.BASE_URL

    suspend fun setOverride(url: String) {
        val cleaned = url.trim().let { if (it.endsWith("/")) it else "$it/" }
        dataStore.edit { it[Keys.BASE_URL_OVERRIDE] = cleaned }
    }

    suspend fun clearOverride() {
        dataStore.edit { it.remove(Keys.BASE_URL_OVERRIDE) }
    }
}
