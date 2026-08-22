package com.schoolsync.parent.ui.maps

import android.content.Context
import android.view.View

/**
 * MapRenderer — the seam between ZenXii and any mapping SDK.
 *
 * Every capability the ERP needs from a map, expressed in plain geographic
 * terms. No provider type appears in this file, so callers written against it
 * cannot become provider-specific by accident.
 *
 * Implementations are the ONLY place a map SDK may be imported. Adding a
 * provider means adding one implementation and registering it in
 * [MapRendererFactory] — no change to business logic, repositories,
 * ViewModels, Firestore schemas or GPS code.
 *
 * Lifecycle: [initialize] → [createView] → … → [dispose]. The host composable
 * owns that sequence; a renderer must tolerate [dispose] at any point.
 */
interface MapRenderer {

    /** Stable provider id, matching the admin registry (`maplibre`, `google`, …). */
    val providerId: String

    /**
     * False while the SDK is absent or unconfigured. The host renders an
     * explanatory state instead of a blank grey rectangle — a map that cannot
     * draw should say why.
     */
    fun isAvailable(): Boolean

    /** One-time SDK setup. [credentials] are the provider's PUBLIC values only. */
    fun initialize(context: Context, credentials: Map<String, String>)

    /** Build the map view. Called once per renderer instance. */
    fun createView(context: Context): View

    /** Jump the camera. */
    fun moveCamera(lat: Double, lng: Double, zoom: Double)

    /** Glide the camera — used for the smooth follow between position fixes. */
    fun animateCamera(lat: Double, lng: Double, durationMs: Long)

    /** Add a marker and return an id the caller can address it by. */
    fun addMarker(id: String, lat: Double, lng: Double, rotationDeg: Double?)

    /** Move an existing marker, animating over [durationMs] when > 0. */
    fun updateMarker(id: String, lat: Double, lng: Double, rotationDeg: Double?, durationMs: Long)

    fun removeMarker(id: String)

    /** Draw a named path — route lines, and breadcrumb replay later. */
    fun drawPolyline(id: String, points: List<Pair<Double, Double>>)

    fun clearPolyline(id: String)

    /**
     * GAP-2 (2026-08-16) — provider-neutral map click.
     *
     * The callback receives two plain Doubles. Every provider's own coordinate
     * type is unwrapped INSIDE its renderer and never crosses this interface,
     * so a screen that reacts to a tap cannot acquire an SDK dependency.
     * (Provider type names are deliberately not written here: CH-TRN-MAP-3 and
     * CH-TRN-F13-7 scan this file for SDK identifiers, and a comment naming one
     * is indistinguishable from a real import to a source scanner.)
     *
     * Default no-op: the Parent app is read-only today and taps nothing. It
     * exists so the CONTRACT is identical on both surfaces — the admin panel
     * needs it now for stop placement, and a renderer written later cannot
     * quietly omit it. Pass null to detach.
     */
    fun onMapClick(callback: ((lat: Double, lng: Double) -> Unit)?) {}

    /** Same contract for a dragged marker: two Doubles, no provider type. */
    fun onMarkerDrag(id: String, callback: ((lat: Double, lng: Double) -> Unit)?) {}

    /** Release SDK resources. Must be safe to call more than once. */
    fun dispose()

    /** Forward host lifecycle to SDKs that require it (MapLibre, Google both do). */
    fun onStart() {}
    fun onResume() {}
    fun onPause() {}
    fun onStop() {}
}
