package com.schoolsync.parent.data.remote

import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Resolved map configuration from the CodeIgniter admin panel.
 *
 * Authentication follows the same pattern as [FeesApi] and [PtmApi]: the
 * parent's Firebase ID token travels in the Authorization header, `Api_auth`
 * verifies it server-side, and the school is derived from the token claims.
 *
 * THERE IS NO schoolId PARAMETER, deliberately. A parameter is something a
 * caller can change, and cross-tenant access would then be one edited request
 * away. The server reads the school from the verified token and nowhere else.
 *
 * The response is the result of `getClientConfig('parent')` — the identical
 * call the admin panel makes. The app performs no resolution of its own: it
 * does not know what `capability_source`, `platformConfig`, `global_provider`
 * or an override is, and must not learn.
 */
interface MapsApi {

    @GET("index.php/maps_api/client_config")
    suspend fun clientConfig(
        @Header("Authorization") bearer: String
    ): MapConfigResponse
}

data class MapConfigResponse(
    val status: String = "",
    val config: ResolvedMapConfig? = null,
)

/**
 * What a renderer needs, and nothing else.
 *
 * Only PUBLIC credentials appear here — the server keeps every secret in
 * `mapsSecrets` / `platformSecrets`, which no client may read.
 */
data class ResolvedMapConfig(
    /** Provider id, e.g. "maptiler". Blank when nothing resolved. */
    val provider: String = "",
    /** Public credentials for THAT provider only, e.g. style_url / api_key. */
    val credentials: Map<String, String> = emptyMap(),
    /**
     * False when no provider resolved, or when the resolved provider has no
     * Android renderer. The UI must say so rather than draw an empty grey box.
     */
    val available: Boolean = false,
)
