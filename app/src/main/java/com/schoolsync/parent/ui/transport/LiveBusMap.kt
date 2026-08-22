package com.schoolsync.parent.ui.transport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.schoolsync.parent.ui.maps.MapRenderer
import com.schoolsync.parent.ui.maps.MapRendererFactory

/**
 * LiveBusMap — the provider-agnostic map host.
 *
 * Phase B turned this from "the MapLibre screen" into a host that renders
 * whatever [MapRenderer] the centralized configuration selects. It imports no
 * map SDK; the only map type it names is the interface. That is what makes a
 * provider change a configuration change.
 *
 * The caller passes [providerId] — resolved upstream by `MapProviderManager`
 * from Firestore, with a local cache and a BuildConfig fallback — so this
 * composable never decides which provider is active either.
 *
 * ─── RECREATE ONLY WHEN THE PROVIDER ACTUALLY CHANGES ────────────────────
 * The renderer is keyed on [providerId] alone. Position updates flow through
 * `updateMarker`, so a moving bus never rebuilds the map; only an administrator
 * switching providers does. Getting this wrong would tear down and rebuild a GL
 * surface every 15 seconds.
 *
 * F13.1 is untouched: the GPS listener, the hot document and every piece of
 * trip logic are exactly as shipped. Only the pixels changed hands.
 */
private const val MARKER_BUS = "bus"
private const val ANIMATION_MS = 1_000L
private const val DEFAULT_ZOOM = 15.0

@Composable
fun LiveBusMap(
    providerId: String,
    lat: Double,
    lng: Double,
    heading: Double?,
    isStale: Boolean,
    credentials: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keyed on the provider: a new renderer is built if and only if the
    // provider changes. `remember(providerId)` is doing the work the
    // requirement describes.
    val renderer: MapRenderer? = remember(providerId) { MapRendererFactory.create(providerId) }

    if (renderer == null) {
        MapNotice("No map provider is configured for this school yet.", modifier)
        return
    }

    LaunchedEffect(renderer, credentials) { renderer.initialize(context, credentials) }

    if (!renderer.isAvailable()) {
        // A selected-but-unbuilt provider, or one missing its style/key. Say so
        // — a blank grey rectangle is the worst possible failure mode here,
        // because nobody can tell it from a map that simply has not loaded.
        MapNotice(
            "The selected map provider (${renderer.providerId}) isn't available in this app version.",
            modifier
        )
        return
    }

    // The SDK owns a GL surface and a tile fetcher; skipping these leaks both.
    DisposableEffect(renderer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> renderer.onStart()
                Lifecycle.Event.ON_RESUME  -> renderer.onResume()
                Lifecycle.Event.ON_PAUSE   -> renderer.onPause()
                Lifecycle.Event.ON_STOP    -> renderer.onStop()
                Lifecycle.Event.ON_DESTROY -> renderer.dispose()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderer.dispose()
        }
    }

    var placed by remember(renderer) { mutableStateOf(false) }

    AndroidView(modifier = modifier, factory = { renderer.createView(it) })

    LaunchedEffect(renderer, lat, lng) {
        if (!placed) {
            renderer.moveCamera(lat, lng, DEFAULT_ZOOM)
            renderer.addMarker(MARKER_BUS, lat, lng, heading)
            placed = true
        } else {
            // Glide, don't snap: fixes arrive at most every 15 s and a jump
            // between two points that far apart looks like the bus teleporting.
            renderer.updateMarker(MARKER_BUS, lat, lng, heading, ANIMATION_MS)
            renderer.animateCamera(lat, lng, ANIMATION_MS)
        }
    }
}

@Composable
private fun MapNotice(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}
