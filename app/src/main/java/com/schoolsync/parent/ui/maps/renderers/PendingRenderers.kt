package com.schoolsync.parent.ui.maps.renderers

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.schoolsync.parent.ui.maps.MapRenderer

/**
 * Renderer placeholders for providers whose SDK is not yet a dependency.
 *
 * These exist so the ABSTRACTION is complete and provable today: the registry,
 * the factory, the admin page and the verifier gates can all address six
 * providers, and adding a real one later touches only its own class.
 *
 * They compile, they are safe to instantiate, and they are honest. Each reports
 * `isAvailable() = false`, so the host renders an explanatory message rather
 * than a blank rectangle, and every geometry call is a no-op instead of a
 * crash. Selecting one of these in System Settings degrades visibly and
 * recovers the moment the provider is switched back — it cannot take the app
 * down.
 *
 * **Deliberately NOT bundled with their SDKs.** Adding Google Maps, Mapbox,
 * HERE and TomTom to the app today would add tens of megabytes and several
 * third-party trackers for renderers nobody has asked to use yet. Each SDK
 * arrives with the provider that needs it.
 *
 * To implement one: add the SDK to `build.gradle.kts`, fill the methods
 * following [MapLibreRenderer], and delete its entry here. Nothing else
 * changes — not the factory contract, not the ViewModel, not the GPS code.
 */
abstract class PendingRenderer(
    final override val providerId: String,
    private val displayName: String,
) : MapRenderer {

    override fun isAvailable(): Boolean = false

    override fun initialize(context: Context, credentials: Map<String, String>) { /* no SDK yet */ }

    /**
     * A plain explanatory view. The host normally intercepts this via
     * [isAvailable], so this is the belt-and-braces path — it must never be a
     * blank grey box a support ticket has to decode.
     */
    override fun createView(context: Context): View = TextView(context).apply {
        text = "$displayName is selected but its map component is not yet installed in this app version."
        gravity = Gravity.CENTER
        setPadding(32, 32, 32, 32)
    }

    override fun moveCamera(lat: Double, lng: Double, zoom: Double) { /* TODO: provider SDK */ }
    override fun animateCamera(lat: Double, lng: Double, durationMs: Long) { /* TODO */ }
    override fun addMarker(id: String, lat: Double, lng: Double, rotationDeg: Double?) { /* TODO */ }
    override fun updateMarker(id: String, lat: Double, lng: Double, rotationDeg: Double?, durationMs: Long) { /* TODO */ }
    override fun removeMarker(id: String) { /* TODO */ }
    override fun drawPolyline(id: String, points: List<Pair<Double, Double>>) { /* TODO */ }
    override fun clearPolyline(id: String) { /* TODO */ }
    override fun dispose() { /* nothing held */ }
}

class GoogleMapsRenderer : PendingRenderer("google", "Google Maps")
class MapboxRenderer    : PendingRenderer("mapbox", "Mapbox")
class HereRenderer      : PendingRenderer("here", "HERE Maps")
class TomTomRenderer    : PendingRenderer("tomtom", "TomTom")

/**
 * MapTiler is a special case: it is a TILE provider, and MapLibre renders its
 * styles. So it is NOT pending — [MapRendererFactory] maps it onto
 * [MapLibreRenderer], and a school can select MapTiler today and have it work.
 * Kept here only to document why no `MapTilerRenderer` class exists.
 */
