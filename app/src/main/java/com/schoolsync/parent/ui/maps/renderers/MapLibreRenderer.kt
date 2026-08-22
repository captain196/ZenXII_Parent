package com.schoolsync.parent.ui.maps.renderers

import android.content.Context
import android.view.View
import com.schoolsync.parent.ui.maps.MapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * MapLibreRenderer — provider #1.
 *
 * This is the F13.1 M4 map implementation MOVED behind [MapRenderer], not
 * rewritten. The behaviour that mattered is carried over unchanged:
 *
 *  • Camera and marker glide over ~1 s, because positions arrive at most every
 *    15 s and snapping between two points that far apart reads as the bus
 *    teleporting.
 *  • Linear interpolation on lat/lng — over a 15-second hop the error against a
 *    great-circle path is well under a pixel at street zoom.
 *  • NO dead-reckoning. Extrapolating along a heading would draw the bus
 *    somewhere it has not been reported to be; on a map a parent uses to time
 *    walking a child to the stop, that is a safety claim the data cannot
 *    support. Motion only ever runs between two real, reported fixes.
 *  • Host lifecycle is forwarded — MapView owns a GL surface and a tile
 *    fetcher, and skipping onStop/onDestroy leaks both.
 *
 * Attribution stays enabled: it is a licence term of every OSM tile provider,
 * not a decoration.
 */
class MapLibreRenderer : MapRenderer {

    override val providerId: String = "maplibre"

    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var styleUrl: String = ""
    private var initialised = false

    private val markers = mutableMapOf<String, Marker>()
    private val polylines = mutableMapOf<String, Polyline>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Needs a style document; a key alone renders nothing. */
    override fun isAvailable(): Boolean = styleUrl.isNotBlank()

    override fun initialize(context: Context, credentials: Map<String, String>) {
        // The style URL may arrive whole, or as a MapTiler key the admin entered
        // separately — MapTiler is registered as its own provider but renders
        // through MapLibre, so both shapes land here.
        styleUrl = credentials["style_url"].orEmpty().ifBlank {
            credentials["api_key"]?.takeIf { it.isNotBlank() }
                ?.let { "https://api.maptiler.com/maps/streets-v2/style.json?key=$it" }
                .orEmpty()
        }
        if (!initialised) {
            MapLibre.getInstance(context)
            initialised = true
        }
    }

    override fun createView(context: Context): View {
        val v = MapView(context)
        v.onCreate(null)
        mapView = v
        v.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isAttributionEnabled = true   // licence term of OSM tile providers
                isLogoEnabled = true
            }
            if (styleUrl.isNotBlank()) m.setStyle(styleUrl)
        }
        return v
    }

    override fun moveCamera(lat: Double, lng: Double, zoom: Double) {
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lng)).zoom(zoom).build()
    }

    override fun animateCamera(lat: Double, lng: Double, durationMs: Long) {
        map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lng)), durationMs.toInt())
    }

    override fun addMarker(id: String, lat: Double, lng: Double, rotationDeg: Double?) {
        val m = map ?: return
        markers[id]?.let { m.removeMarker(it) }
        markers[id] = m.addMarker(MarkerOptions().position(LatLng(lat, lng)))
    }

    override fun updateMarker(id: String, lat: Double, lng: Double, rotationDeg: Double?, durationMs: Long) {
        val marker = markers[id] ?: run { addMarker(id, lat, lng, rotationDeg); return }
        val target = LatLng(lat, lng)
        if (durationMs <= 0L) { marker.position = target; return }
        scope.launch { glide(marker, target, durationMs) }
    }

    /** Glide a marker to [target]. Cosmetic only — the value is always the last real fix. */
    private suspend fun glide(marker: Marker, target: LatLng, durationMs: Long) {
        val start = marker.position
        val dLat = target.latitude - start.latitude
        val dLng = target.longitude - start.longitude
        if (dLat == 0.0 && dLng == 0.0) return

        val startedAt = System.currentTimeMillis()
        while (true) {
            val t = ((System.currentTimeMillis() - startedAt).toDouble() / durationMs).coerceIn(0.0, 1.0)
            marker.position = LatLng(start.latitude + dLat * t, start.longitude + dLng * t)
            if (t >= 1.0) break
            awaitFrame()
        }
        marker.position = target
    }

    override fun removeMarker(id: String) {
        markers.remove(id)?.let { map?.removeMarker(it) }
    }

    override fun drawPolyline(id: String, points: List<Pair<Double, Double>>) {
        val m = map ?: return
        clearPolyline(id)
        if (points.size < 2) return
        polylines[id] = m.addPolyline(
            PolylineOptions().addAll(points.map { LatLng(it.first, it.second) }).width(4f)
        )
    }

    override fun clearPolyline(id: String) {
        polylines.remove(id)?.let { map?.removePolyline(it) }
    }

    override fun onStart()  { mapView?.onStart() }
    override fun onResume() { mapView?.onResume() }
    override fun onPause()  { mapView?.onPause() }
    override fun onStop()   { mapView?.onStop() }

    override fun dispose() {
        runCatching { scope.cancel() }
        markers.clear()
        polylines.clear()
        runCatching { mapView?.onDestroy() }
        mapView = null
        map = null
    }
}
