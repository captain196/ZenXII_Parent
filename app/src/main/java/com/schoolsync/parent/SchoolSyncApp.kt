package com.schoolsync.parent

import android.app.Application
import com.schoolsync.parent.service.NotificationChannels
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for SchoolSync Parent.
 * Initializes Hilt dependency injection and enables Firebase offline persistence.
 */
@HiltAndroidApp
class SchoolSyncApp : Application(), ImageLoaderFactory {

    /**
     * App-wide Coil [ImageLoader]. Registers [VideoFrameDecoder] so a video
     * URL fed to AsyncImage (event / gallery media without an explicit
     * thumbnail) renders a decoded poster frame instead of a blank tile.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    override fun onCreate() {
        super.onCreate()

        // Debug-log file (works around OEM Debug-log suppression).
        com.schoolsync.parent.util.initDebugLog(this)

        // Create all notification channels at process start so they exist
        // (and persist through force-stop) before any push arrives — a
        // notification-payload message delivered while the app is killed is
        // shown by the OS against the manifest default channel WITHOUT running
        // the app, so the channel must already exist. Idempotent.
        NotificationChannels.ensureChannels(this)

        // Enable Firebase RTDB disk persistence (legacy notice/calendar paths).
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) { /* already set */ }

        // Enable Firestore persistent disk cache so the Fees tab (and
        // every other Firestore-backed screen) renders last-known data
        // immediately on cold start and survives transient network drops.
        // Without this, snapshot listeners produce nothing while offline
        // and the screen sits on its skeleton state until reconnection.
        // setFirestoreSettings can only be called once before the first
        // Firestore access — wrap in try/catch in case Hilt or another
        // module already touched Firestore.
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
        } catch (_: Exception) { /* already configured */ }
    }
}
