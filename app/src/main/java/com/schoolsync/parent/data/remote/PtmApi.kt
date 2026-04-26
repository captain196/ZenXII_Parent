package com.schoolsync.parent.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * PTM RSVP submission API exposed by the CodeIgniter admin panel.
 * Authenticates via Firebase ID token in the Authorization header;
 * the server enforces every booking-shape constraint (slot existence,
 * teacher uniqueness, no overlap, capacity, attended-status preservation)
 * so the client cannot bypass safeguards by hand-crafting payloads.
 *
 * Round 3b: replaces the previous direct-Firestore RSVP write path.
 */
interface PtmApi {

    @POST("index.php/ptm/parent_submit_rsvp/{ptmEventId}")
    suspend fun submitRsvp(
        @Header("Authorization") bearer: String,
        @Path("ptmEventId") ptmEventId: String,
        @Body body: SubmitRsvpRequest
    ): Response<SubmitRsvpResponse>
}

data class SubmitRsvpRequest(
    val studentId: String,
    val bookings: List<BookingPayload>
)

/**
 * What the client proposes for one slot. The server IGNORES `teacherId`,
 * `slotStartTime`, and `slotEndTime` even if sent — those are enriched
 * from the live PTM doc to prevent tampering.
 */
data class BookingPayload(
    val slotIndex: Int,
    val status: String = "confirmed",  // "pending" or "confirmed" only
    val note: String = ""
)

data class SubmitRsvpResponse(
    val status: String = "",            // "success" or "error"
    val code: String? = null,           // rejection code on error
    val message: String? = null,
    val ptmEventId: String? = null,
    val studentId: String? = null,
    val bookingCount: Int = 0,
    val bookings: List<EnrichedBooking> = emptyList(),
    /**
     * Bookings the parent could not change because the teacher had
     * already marked attendance. Surface to the user so they understand
     * why a previously attended/no-show slot is showing through their
     * "new" submission.
     */
    val preservedBookings: List<PreservedBooking> = emptyList()
)

/** Server-enriched booking — what actually got written to Firestore. */
data class EnrichedBooking(
    val slotIndex: Int = -1,
    val teacherId: String = "",
    val teacherName: String = "",
    val slotStartTime: String = "",
    val slotEndTime: String = "",
    val status: String = "",
    val note: String = ""
)

data class PreservedBooking(
    val slotIndex: Int = -1,
    val status: String = "",
    val reason: String = ""
)
