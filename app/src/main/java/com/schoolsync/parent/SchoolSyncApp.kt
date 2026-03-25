package com.schoolsync.parent

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for SchoolSync Parent.
 * Initializes Hilt dependency injection and enables Firebase offline persistence.
 */
@HiltAndroidApp
class SchoolSyncApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable Firebase RTDB disk persistence for offline support.
        // This MUST be called before any other Firebase Database usage.
        // It caches data locally so the app works offline and reduces bandwidth.
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {
            // setPersistenceEnabled can only be called once; ignore if already set
        }
    }
}
