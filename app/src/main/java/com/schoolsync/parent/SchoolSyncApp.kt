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
     * Keeps the cached student profile following its Firestore document while
     * the app is open. Without it, class / section / roll / session showed the
     * value captured at login until the parent signed out and back in — which
     * is what made a promotion invisible to an already-open app.
     *
     * Application-scoped deliberately: every screen reads the profile from
     * TokenManager, so refreshing that one cache fixes all of them. Attaching
     * this to a single ViewModel would leave the app stale whenever that
     * screen was not the one in front of the parent.
     */
    @javax.inject.Inject
    lateinit var studentProfileSynchronizer:
        com.schoolsync.parent.data.sync.StudentProfileSynchronizer

    /** Lives as long as the process — one listener, not per-screen. */
    private val applicationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

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

        // ── FIREBASE EMULATOR (debug-only, opt-in) ───────────────────────
        // MUST run before anything touches Firestore or Auth — both SDKs
        // reject useEmulator() once the instance has been used, and the
        // persistence settings below are the app's first Firestore call.
        //
        // Two independent conditions, either of which alone keeps this off:
        //   • BuildConfig.DEBUG — a release build is never in debug;
        //   • FIREBASE_EMULATOR_HOST — hard-wired "" in the release block
        //     and not readable from any Gradle property there.
        //
        // So a shipped app cannot reach an emulator even if this code is
        // present, and a debug build only does so when explicitly asked.
        val emuHost = BuildConfig.FIREBASE_EMULATOR_HOST
        if (BuildConfig.DEBUG && emuHost.isNotBlank()) {
            try {
                FirebaseFirestore.getInstance().useEmulator(emuHost, 8092)
                com.google.firebase.auth.FirebaseAuth.getInstance().useEmulator(emuHost, 9099)
                android.util.Log.w("SchoolSyncApp",
                    "FIREBASE EMULATOR MODE — Firestore $emuHost:8092, Auth $emuHost:9099. " +
                        "No production data is reachable in this build.")
            } catch (e: Exception) {
                android.util.Log.e("SchoolSyncApp", "emulator wiring failed", e)
            }
        }

        // Start following the student document. Idempotent, and a no-op until
        // someone is signed in — it keys off the cached userId, so a cold
        // start with no session simply attaches nothing.
        studentProfileSynchronizer.start(applicationScope)

        // Create all notification channels at process start so they exist
        // (and persist through force-stop) before any push arrives — a
        // notification-payload message delivered while the app is killed is
        // shown by the OS against the manifest default channel WITHOUT running
        // the app, so the channel must already exist. Idempotent.
        NotificationChannels.ensureChannels(this)

        // Enable Firebase RTDB disk persistence. Phase 3 RTDB removal
        // (2026-08-13, PARENT_APP_FIRESTORE_ONLY_HARDENING_REPORT.md) removed
        // every RTDB call in the app except AuthRepository::lookupSchoolCode()
        // (FirebaseService::readString) — see that class's docblock for why
        // it's deliberately retained. This init call stays for that one path;
        // it is no longer about "notice/calendar paths," which were already
        // dead before this pass.
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
