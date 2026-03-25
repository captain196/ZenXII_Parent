package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.StudentFlag
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for student red flags / alerts (read-only for parent).
 * Path: StudentFlags/{schoolCode}/{studentId}/{flagId}
 */
@Singleton
class RedFlagRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all active flags for the current student.
     */
    suspend fun getActiveFlags(): List<StudentFlag> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) return emptyList()

        val path = Constants.Firebase.studentFlagsPath(
            schoolCode = user.schoolCode,
            studentId = user.userId
        )

        return try {
            val children = firebaseService.readChildren(path)
            children.map { (flagId, data) ->
                StudentFlag.fromMap(flagId, data)
            }
                .filter { it.status == "active" }
                .sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Observe all flags in real-time.
     */
    fun observeFlags(): Flow<List<StudentFlag>> {
        return tokenManager.user.map { user ->
            if (!user.isLoggedIn || user.schoolCode.isBlank()) {
                return@map emptyList()
            }
            val path = Constants.Firebase.studentFlagsPath(
                schoolCode = user.schoolCode,
                studentId = user.userId
            )
            try {
                val children = firebaseService.readChildren(path)
                children.map { (flagId, data) ->
                    StudentFlag.fromMap(flagId, data)
                }.sortedByDescending { it.timestamp }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Get count of active flags.
     */
    suspend fun getActiveFlagCount(): Int {
        return getActiveFlags().size
    }
}
