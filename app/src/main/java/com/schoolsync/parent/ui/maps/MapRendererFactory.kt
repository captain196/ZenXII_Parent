package com.schoolsync.parent.ui.maps

import com.schoolsync.parent.data.maps.MapProviderManager
import com.schoolsync.parent.ui.maps.renderers.GoogleMapsRenderer
import com.schoolsync.parent.ui.maps.renderers.HereRenderer
import com.schoolsync.parent.ui.maps.renderers.MapLibreRenderer
import com.schoolsync.parent.ui.maps.renderers.MapboxRenderer
import com.schoolsync.parent.ui.maps.renderers.TomTomRenderer

/**
 * The one place a provider id becomes a renderer.
 *
 * Keeping this mapping in a single function is what lets every verifier gate
 * assert "no renderer is hardcoded anywhere else": a `when` here is fine, the
 * same `when` scattered through the UI is the thing that makes a provider swap
 * a code change.
 */
object MapRendererFactory {

    /**
     * @return a renderer for [providerId], or null when the id is unknown or
     *         blank. Null means "no provider configured" — the host shows that
     *         state rather than silently defaulting to MapLibre, because a
     *         silent default would hide a misconfiguration from the very
     *         administrator who needs to see it.
     */
    fun create(providerId: String): MapRenderer? = when (providerId) {
        // One arm per provider, deliberately NOT sharing a fall-through branch.
        // The mapping is what decides whether a provider can draw on Android,
        // and the admin registry's per-platform availability flags must agree
        // with it (verifier gate CH-TRN-MAP-4 compares the two). A shared arm
        // made that pairing ambiguous to read and to check.
        MapProviderManager.PROVIDER_MAPLIBRE -> MapLibreRenderer()
        // MapTiler serves styles that MapLibre renders — a tile provider, not a
        // separate SDK — so it maps onto the same renderer and works today.
        MapProviderManager.PROVIDER_MAPTILER -> MapLibreRenderer()
        MapProviderManager.PROVIDER_GOOGLE   -> GoogleMapsRenderer()
        MapProviderManager.PROVIDER_MAPBOX   -> MapboxRenderer()
        MapProviderManager.PROVIDER_HERE     -> HereRenderer()
        // Placeholder: Leaflet renders TomTom raster tiles in the admin panel,
        // but there is no Android TomTom SDK here, so the registry marks
        // parent => false and assignment to Parent is refused server-side.
        MapProviderManager.PROVIDER_TOMTOM   -> TomTomRenderer()
        else -> null
    }
}
