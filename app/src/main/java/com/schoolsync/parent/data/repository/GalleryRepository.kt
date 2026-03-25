package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.GalleryAlbum
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-based gallery repository (legacy fallback).
 * Primary gallery data now lives in Firestore via [GalleryFirestoreRepository].
 * This stub returns empty data so the build passes while RTDB paths are phased out.
 */
@Singleton
class GalleryRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all gallery albums.
     */
    suspend fun getAlbums(): List<GalleryAlbum> {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
        return emptyList()
    }

    /**
     * Fetch a single album with its media items populated.
     */
    suspend fun getAlbumWithMedia(albumId: String): GalleryAlbum {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
        return GalleryAlbum(albumId = albumId)
    }
}
