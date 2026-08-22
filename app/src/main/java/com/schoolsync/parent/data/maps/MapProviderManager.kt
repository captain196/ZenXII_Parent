package com.schoolsync.parent.data.maps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.schoolsync.parent.BuildConfig
import com.schoolsync.parent.data.remote.MapsApi
import com.schoolsync.parent.data.remote.ResolvedMapConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mapsDataStore: DataStore<Preferences> by preferencesDataStore(name = "maps_config_prefs")

/**
 * MapProviderManager — THE only place that decides which map renderer runs.
 *
 * Nothing else in the app may resolve a provider. Business logic, repositories
 * and ViewModels stay entirely provider-agnostic; they never learn whether the
 * pixels came from MapLibre, Google or HERE.
 *
 * ─── WHY THIS NO LONGER READS `mapsConfig` FROM FIRESTORE ────────────────
 * It used to observe the tenant's `mapsConfig` document and resolve the
 * provider here, using a Kotlin copy of the panel's `mode` /
 * `global_provider` / `platform_provider` rules.
 *
 * That copy drifted. The server-side algorithm has since grown a platform
 * default layer, a capability layer, surface-awareness and eight canonical
 * states, and this copy knew none of it — it had never heard of
 * `capability_source`. A school inheriting the ZenX platform provider
 * therefore resolved to nothing here, and the bus map stayed blank while the
 * admin panel showed a working configuration.
 *
 * The apps also cannot fix this by reading more: `platformConfig` is deny-all
 * to every client, correctly, so the platform default is only resolvable
 * server-side.
 *
 * A second resolver cannot be kept correct — that is the lesson, not the bug.
 * There is now exactly ONE:
 *
 *     resolveCapability() → getClientConfig('parent') → Maps_api → here
 *
 * This class no longer knows what inheritance, an override or a platform
 * default is. It asks the server what to draw with, and caches the answer.
 *
 * ─── CACHING / OFFLINE (unchanged in shape) ──────────────────────────────
 * The DataStore last-known-good cache works exactly as it did: emit the cached
 * configuration first so a cold start renders without waiting on the network,
 * then refresh from the server and write the result back. If the network is
 * unavailable the cached configuration keeps the map alive, and
 * `distinctUntilChanged` still means a live renderer is torn down only when the
 * provider genuinely changes.
 *
 * One behaviour worth stating plainly: a device that has NEVER reached the
 * server and holds no cache resolves to nothing, and the UI renders its honest
 * "no provider configured" state. No provider is invented for it.
 */
@Singleton
class MapProviderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapsApi: MapsApi,
) {
    private val gson = Gson()
    private val store = context.mapsDataStore

    /**
     * The most recent configuration this process has seen.
     *
     * Exists so [credentialsFor] cannot disagree with [providerFor]. Without it
     * the two go through separate [config] collections and agreement relies on
     * `writeCache` having completed before `emit` — true today, invisible to a
     * reader, and silently false if the DataStore write fails (it is caught and
     * ignored, because a cache miss must not brick the map). The failure that
     * produces is the nastiest kind: provider set, credentials empty, so
     * MapLibreRenderer reports unavailable and the map is blank while every
     * status says it resolved.
     */
    @Volatile private var latest: ResolvedMapConfig? = null

    /**
     * Live configuration, cache-first.
     *
     * A failed fetch is deliberately NOT propagated: a map that keeps drawing
     * with a slightly stale provider beats a blank screen, and the next
     * successful refresh corrects it.
     */
    fun config(): Flow<ResolvedMapConfig?> = flow {
        val cached = readCache()
        if (cached != null) { latest = cached }
        emit(cached)

        val fresh = fetch()
        if (fresh != null) {
            latest = fresh          // BEFORE emit — see [latest]
            writeCache(fresh)
            emit(fresh)
        }
    }

    /**
     * The active provider id for this surface.
     *
     * There is no `platform` parameter, deliberately. The server pins the
     * surface to `parent` inside `Maps_api::client_config`, because letting a
     * caller choose would allow a parent to request the admin surface, which
     * resolves against different renderer availability. A parameter accepted
     * here and ignored downstream would read as if it worked.
     *
     * `distinctUntilChanged` is what satisfies "recreate the renderer only when
     * the provider actually changes": a refresh returning the same provider
     * passes through without re-emitting, so a live map is never torn down and
     * rebuilt for an unchanged answer.
     */
    fun providerFor(): Flow<String> =
        config().map { activeProvider(it) }.distinctUntilChanged()

    /** One-shot resolution, for callers that cannot collect a flow. */
    suspend fun currentProvider(): String = activeProvider(current())

    /** Public credentials for the active provider. Never contains a secret. */
    suspend fun credentialsFor(provider: String): Map<String, String> {
        val cfg = current() ?: return emptyMap()
        // Only hand back credentials belonging to the provider being asked
        // about, so a provider switch can never pair a new renderer with the
        // previous provider's key.
        return if (cfg.available && cfg.provider == provider) cfg.credentials else emptyMap()
    }

    /**
     * The configuration to answer a one-shot question with.
     *
     * Prefers what this process last resolved, so an accessor called straight
     * after a [config] emission always agrees with it. Falls back to the cache
     * for the first call of a cold process, and never issues its own fetch —
     * one map screen must not cost two round trips.
     */
    private suspend fun current(): ResolvedMapConfig? = latest ?: readCache()

    /**
     * The server has already resolved everything — inheritance, override,
     * activation, surface support. This is a read, not a decision: adding any
     * condition here would recreate the second resolver that caused the
     * original defect.
     */
    private fun activeProvider(cfg: ResolvedMapConfig?): String =
        if (cfg != null && cfg.available && cfg.provider.isNotBlank()) cfg.provider
        else fallbackProvider()

    /**
     * Last resort. `MAP_STYLE_URL` is the M4 build-time property: if a build was
     * given one, MapLibre can still draw before the server is ever reached.
     * It is unset in every current build, so in practice this returns "" and
     * the UI renders "no provider configured" — the honest answer for a device
     * that has never reached ZenX and has no cache.
     */
    private fun fallbackProvider(): String =
        if (BuildConfig.MAP_STYLE_URL.isNotBlank()) PROVIDER_MAPLIBRE else ""

    // ── server ───────────────────────────────────────────────────────────

    /**
     * One authenticated call. The school is derived server-side from the token
     * claims, so there is no school identifier to send and none to forge.
     */
    private suspend fun fetch(): ResolvedMapConfig? = try {
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        if (token.isNullOrBlank()) null
        else mapsApi.clientConfig("Bearer $token").config
    } catch (_: Exception) {
        // Offline, server down, token refresh in flight — all mean "keep using
        // the cache". Failing loudly here would blank a working map.
        null
    }

    // ── cache ────────────────────────────────────────────────────────────

    private suspend fun readCache(): ResolvedMapConfig? = try {
        store.data.firstOrNull()?.get(KEY_CONFIG)
            ?.let { gson.fromJson(it, ResolvedMapConfig::class.java) }
    } catch (_: Exception) {
        // A cache that cannot be parsed (schema drift, partial write) must not
        // brick the map — drop through to the fallback instead.
        null
    }

    private suspend fun writeCache(cfg: ResolvedMapConfig) {
        try {
            store.edit { it[KEY_CONFIG] = gson.toJson(cfg) }
        } catch (_: Exception) {
            // Caching is an optimisation; failing to persist is not fatal.
        }
    }

    companion object {
        const val PROVIDER_MAPLIBRE = "maplibre"
        const val PROVIDER_MAPTILER = "maptiler"
        const val PROVIDER_GOOGLE   = "google"
        const val PROVIDER_MAPBOX   = "mapbox"
        const val PROVIDER_HERE     = "here"
        const val PROVIDER_TOMTOM   = "tomtom"

        private val KEY_CONFIG = stringPreferencesKey("maps_config_json")
    }
}
