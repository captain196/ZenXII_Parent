package com.schoolsync.parent.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Generic Firebase Realtime Database helper providing coroutine-based
 * one-time reads and Flow-based real-time listeners.
 */
@Singleton
class FirebaseService @Inject constructor() {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    /** Get a reference to a specific path */
    fun ref(path: String): DatabaseReference = database.getReference(path)

    // ── One-time Reads ───────────────────────────────────────────────────

    /**
     * Read a single value at the given path. Returns null if the path doesn't exist.
     */
    suspend fun readValue(path: String): DataSnapshot? {
        return suspendCancellableCoroutine { cont ->
            val ref = database.getReference(path)
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) {
                        cont.resume(if (snapshot.exists()) snapshot else null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) {
                        cont.resumeWithException(error.toException())
                    }
                }
            })
        }
    }

    /**
     * Read a value forcing a server fetch (bypasses persistence cache).
     */
    suspend fun readValueFresh(path: String): DataSnapshot? {
        return try {
            val snapshot = database.getReference(path).get().await()
            if (snapshot.exists()) snapshot else null
        } catch (e: Exception) {
            // Fallback to cached read
            readValue(path)
        }
    }

    /**
     * Read children forcing a server fetch.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun readChildrenFresh(path: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = readValueFresh(path) ?: return emptyList()
        return snapshot.children.mapNotNull { child ->
            val key = child.key ?: return@mapNotNull null
            val value = child.value
            when (value) {
                is Map<*, *> -> key to (value as Map<String, Any?>)
                else -> key to mapOf("value" to value)
            }
        }
    }

    /**
     * Read a value and convert to a Map. Returns empty map if path doesn't exist.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun readMap(path: String): Map<String, Any?> {
        val snapshot = readValue(path) ?: return emptyMap()
        return (snapshot.value as? Map<String, Any?>) ?: emptyMap()
    }

    /**
     * Read a string value at the given path. Returns null if missing.
     */
    suspend fun readString(path: String): String? {
        val snapshot = readValue(path) ?: return null
        return snapshot.value?.toString()
    }

    /**
     * Read children at a path as a list of (key, Map) pairs.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun readChildren(path: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = readValue(path) ?: return emptyList()
        return snapshot.children.mapNotNull { child ->
            val key = child.key ?: return@mapNotNull null
            val value = child.value
            when (value) {
                is Map<*, *> -> key to (value as Map<String, Any?>)
                else -> key to mapOf("value" to value)
            }
        }
    }

    /**
     * Read children limited to the last N entries (useful for notices, messages).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun readChildrenLimited(path: String, limit: Int): List<Pair<String, Map<String, Any?>>> {
        return suspendCancellableCoroutine { cont ->
            val query = database.getReference(path).limitToLast(limit)
            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) {
                        val result = snapshot.children.mapNotNull { child ->
                            val key = child.key ?: return@mapNotNull null
                            val value = child.value
                            when (value) {
                                is Map<*, *> -> key to (value as Map<String, Any?>)
                                else -> key to mapOf("value" to value)
                            }
                        }
                        cont.resume(result)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) {
                        cont.resumeWithException(error.toException())
                    }
                }
            })
        }
    }

    // ── Real-time Flows ──────────────────────────────────────────────────

    /**
     * Observe a path for real-time changes. Emits a DataSnapshot on each change.
     * The Flow will stay active until the collector is cancelled.
     */
    fun observeValue(path: String): Flow<DataSnapshot?> = callbackFlow {
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(if (snapshot.exists()) snapshot else null)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observe a path and emit as Map on each change.
     */
    @Suppress("UNCHECKED_CAST")
    fun observeMap(path: String): Flow<Map<String, Any?>> = callbackFlow {
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = if (snapshot.exists()) {
                    (snapshot.value as? Map<String, Any?>) ?: emptyMap()
                } else {
                    emptyMap()
                }
                trySend(data)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observe children at a path with real-time updates.
     */
    @Suppress("UNCHECKED_CAST")
    fun observeChildren(path: String): Flow<List<Pair<String, Map<String, Any?>>>> = callbackFlow {
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val value = child.value
                    when (value) {
                        is Map<*, *> -> key to (value as Map<String, Any?>)
                        else -> key to mapOf("value" to value)
                    }
                }
                trySend(result)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observe a query (e.g., limitToLast) with real-time updates.
     */
    @Suppress("UNCHECKED_CAST")
    fun observeQuery(query: Query): Flow<List<Pair<String, Map<String, Any?>>>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val value = child.value
                    when (value) {
                        is Map<*, *> -> key to (value as Map<String, Any?>)
                        else -> key to mapOf("value" to value)
                    }
                }
                trySend(result)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    // ── Write Operations (limited use in parent app) ─────────────────────

    /**
     * Push a new child under the given path and return the generated key.
     * Returns null if the push fails.
     */
    suspend fun pushData(path: String, value: Any?): String? {
        return suspendCancellableCoroutine { cont ->
            val newRef = database.getReference(path).push()
            val key = newRef.key
            newRef.setValue(value)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(key)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    /**
     * Write a value to a path. Used sparingly in parent app
     * (e.g., marking messages as read).
     */
    suspend fun writeValue(path: String, value: Any?): Unit {
        return suspendCancellableCoroutine { cont ->
            database.getReference(path).setValue(value)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    /**
     * Update multiple children at a path.
     */
    suspend fun updateChildren(path: String, updates: Map<String, Any?>): Unit {
        return suspendCancellableCoroutine { cont ->
            database.getReference(path).updateChildren(updates)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }
}
