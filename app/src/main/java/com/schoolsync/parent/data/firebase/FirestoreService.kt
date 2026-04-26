package com.schoolsync.parent.data.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Generic Firestore helper that centralises one-time reads, real-time
 * listeners and write operations behind a clean coroutine / Flow API.
 */
@Singleton
class FirestoreService @Inject constructor(
    @PublishedApi internal val firestore: FirebaseFirestore
) {

    // ── One-time Reads ────────────────────────────────────────────────────

    /**
     * Fetches a single document by [collection] and [docId].
     * Returns `null` when the document does not exist.
     */
    suspend fun getDocument(collection: String, docId: String): DocumentSnapshot? {
        return suspendCancellableCoroutine { cont ->
            firestore.collection(collection).document(docId).get()
                .addOnSuccessListener { snapshot ->
                    cont.resume(if (snapshot.exists()) snapshot else null)
                }
                .addOnFailureListener { exception ->
                    cont.resumeWithException(exception)
                }
        }
    }

    /**
     * Fetches a single document and deserialises it to [T].
     * Returns `null` when the document does not exist or cannot be mapped.
     */
    suspend inline fun <reified T> getDocumentAs(collection: String, docId: String): T? {
        return getDocument(collection, docId)?.toObject(T::class.java)
    }

    /**
     * Convenience wrapper that returns the document data as a plain [Map].
     */
    suspend fun getDocumentMap(collection: String, docId: String): Map<String, Any?>? {
        return getDocument(collection, docId)?.data
    }

    /**
     * Runs a query built via [queryBuilder] and returns the raw [QuerySnapshot].
     */
    suspend fun queryDocuments(
        collection: String,
        queryBuilder: (CollectionReference) -> Query
    ): QuerySnapshot {
        return suspendCancellableCoroutine { cont ->
            val query = queryBuilder(firestore.collection(collection))
            query.get()
                .addOnSuccessListener { snapshot ->
                    cont.resume(snapshot)
                }
                .addOnFailureListener { exception ->
                    cont.resumeWithException(exception)
                }
        }
    }

    /**
     * Runs a query and deserialises every resulting document to [T].
     */
    suspend inline fun <reified T> queryDocumentsAs(
        collection: String,
        noinline queryBuilder: (CollectionReference) -> Query
    ): List<T> {
        return queryDocuments(collection, queryBuilder).toObjects(T::class.java)
    }

    // ── Real-time Listeners ───────────────────────────────────────────────

    /**
     * Emits the latest [DocumentSnapshot] every time the document changes.
     * Emits `null` when the document does not exist.
     */
    fun observeDocument(collection: String, docId: String): Flow<DocumentSnapshot?> =
        callbackFlow {
            val registration = firestore.collection(collection).document(docId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        cancel("Error observing $collection/$docId", error)
                        return@addSnapshotListener
                    }
                    trySend(if (snapshot != null && snapshot.exists()) snapshot else null)
                }
            awaitClose { registration.remove() }
        }

    /**
     * Observes a document and deserialises snapshots to [T].
     */
    inline fun <reified T> observeDocumentAs(
        collection: String,
        docId: String
    ): Flow<T?> = callbackFlow {
        val registration = firestore.collection(collection).document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    cancel("Error observing $collection/$docId", error)
                    return@addSnapshotListener
                }
                val value = if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(T::class.java)
                } else {
                    null
                }
                trySend(value)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Emits the latest [QuerySnapshot] every time the query results change.
     */
    fun observeQuery(
        collection: String,
        queryBuilder: (CollectionReference) -> Query
    ): Flow<QuerySnapshot> = callbackFlow {
        val query = queryBuilder(firestore.collection(collection))
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // close() propagates the error to downstream .catch {}.
                // The previous cancel() raised CancellationException which
                // .catch deliberately doesn't catch, so listener errors
                // (rules rejection, missing index) silently killed the flow.
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot)
            }
        }
        awaitClose { registration.remove() }
    }

    // ── Write Operations ──────────────────────────────────────────────────

    /**
     * Creates or overwrites a document. When [merge] is `true` the write
     * behaves as a merge rather than a full overwrite.
     */
    suspend fun setDocument(
        collection: String,
        docId: String,
        data: Any,
        merge: Boolean = false
    ) {
        suspendCancellableCoroutine { cont ->
            val ref = firestore.collection(collection).document(docId)
            val task = if (merge) ref.set(data, SetOptions.merge()) else ref.set(data)
            task
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /**
     * Applies a partial update to an existing document.
     */
    suspend fun updateDocument(
        collection: String,
        docId: String,
        updates: Map<String, Any?>
    ) {
        suspendCancellableCoroutine { cont ->
            firestore.collection(collection).document(docId)
                .update(updates)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /**
     * Deletes a document.
     */
    suspend fun deleteDocument(collection: String, docId: String) {
        suspendCancellableCoroutine { cont ->
            firestore.collection(collection).document(docId)
                .delete()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    /**
     * Returns a [DocumentReference] for the given path.
     */
    fun docRef(collection: String, docId: String): DocumentReference =
        firestore.collection(collection).document(docId)

    /**
     * Returns a Firestore server-timestamp sentinel value.
     */
    fun serverTimestamp(): FieldValue = FieldValue.serverTimestamp()
}
