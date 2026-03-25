package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.GalleryAlbumDoc
import com.schoolsync.parent.data.model.firestore.GalleryMediaDoc
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading gallery data from Firestore.
 * Collections: `galleryAlbums`, `galleryMedia`
 */
@Singleton
class GalleryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    suspend fun getAlbums(): Result<List<GalleryAlbumDoc>> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val albums = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "active")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(albums)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlbumMedia(albumId: String): Result<List<GalleryMediaDoc>> {
        return try {
            val media = firestoreService.queryDocumentsAs<GalleryMediaDoc>(
                "galleryMedia"
            ) { ref ->
                ref.whereEqualTo("albumId", albumId)
                    .orderBy("uploadedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(media)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
