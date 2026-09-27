package com.schoolsync.parent.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.data.sync.StudentProfileSynchronizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  THE CACHE ROUND-TRIP, ON A REAL ANDROID RUNTIME
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── WHY THIS EXISTS SEPARATELY FROM THE JVM TESTS ────────────────────────
 * `StudentProfileSynchronizerTest` proves the POLICY (`decide()`), which is a
 * pure function. It deliberately stops there, because the thing the policy
 * acts on — `TokenManager` — is backed by Jetpack DataStore, which needs a
 * real Android Context. A JVM test cannot touch it.
 *
 * That left the most load-bearing half of the feature unproven: that writing
 * the decided profile actually PERSISTS and that reading it back actually
 * re-emits. The whole fix is "make the cached profile follow the document";
 * if the cache write does not round-trip on a real device, the policy being
 * correct is irrelevant.
 *
 * ── WHAT THIS DOES NOT DO ────────────────────────────────────────────────
 * It does not touch Firestore, the network, or any live tenant. It exercises
 * the local cache the synchroniser writes to, using the same `saveUserDirect`
 * call the synchroniser makes. Listener behaviour against real Firestore is a
 * separate, explicitly-reported gap.
 */
@RunWith(AndroidJUnit4::class)
class StudentProfileCacheInstrumentedTest {

    private lateinit var tokenManager: TokenManager

    private fun user(
        className: String = "Class 9th",
        section: String = "Section A",
        rollNo: String = "17",
        session: String = "2026-27",
        status: String = "Active",
        srNo: String = "7",
    ) = User(
        userId = "STU_INSTR_1", name = "Instrumented Student", email = "",
        phone = "9000000000", role = "student", schoolId = "SCH_EMUFIXTURE1",
        schoolDisplayName = "Fixture", profilePic = "", className = className,
        section = section, rollNo = rollNo, fatherName = "", motherName = "",
        dob = "", gender = "", admissionDate = "", parentDbKey = "",
        session = session, schoolCode = "SCH_EMUFIXTURE1", status = status,
        srNo = srNo,
    )

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        tokenManager = TokenManager(ctx)
    }

    @Test
    fun a_profile_write_round_trips_through_real_datastore() = runBlocking {
        // The mechanism the entire feature rests on.
        tokenManager.saveUserDirect(user(className = "Class 9th"))
        val read = tokenManager.user.first()
        assertEquals("Class 9th", read.className)
        assertEquals("STU_INSTR_1", read.userId)
        assertEquals("the SRNO did not survive the cache round-trip", "7", read.srNo)
    }

    @Test
    fun a_decided_change_is_persisted_and_read_back() = runBlocking {
        // decide() -> saveUserDirect() -> read: the full local path a
        // promotion takes once the listener has delivered the document.
        tokenManager.saveUserDirect(user(className = "Class 9th", session = "2026-27"))
        val cached = tokenManager.user.first()

        val next = StudentProfileSynchronizer.decide(
            cached,
            StudentDoc(studentId = cached.userId, className = "Class 10th", session = "2027-28"),
        )
        assertNotNull("decide() refused a real promotion", next)
        tokenManager.saveUserDirect(next!!)

        val after = tokenManager.user.first()
        assertEquals("the promotion did not reach the cache", "Class 10th", after.className)
        assertEquals("2027-28", after.session)
        assertEquals("the section was clobbered", "Section A", after.section)
        assertEquals("the SRNO was lost on promotion", "7", after.srNo)
    }

    @Test
    fun an_unchanged_document_writes_nothing_on_a_real_device() = runBlocking {
        // The loop guard, on the real store. If decide() returned a value
        // here, the synchroniser would write, DataStore would re-emit, and
        // the flow would feed itself forever — on device, doing disk I/O.
        tokenManager.saveUserDirect(user(className = "Class 9th"))
        val cached = tokenManager.user.first()

        val again = StudentProfileSynchronizer.decide(
            cached,
            StudentDoc(
                studentId = cached.userId, className = "Class 9th",
                section = "Section A", rollNo = "17",
                session = "2026-27", status = "Active",
            ),
        )
        assertNull("an identical document produced a write — the synchroniser would loop", again)
    }

    @Test
    fun a_withdrawal_reaches_the_cache_and_is_readable_back() = runBlocking {
        tokenManager.saveUserDirect(user(status = "Active"))
        val cached = tokenManager.user.first()

        val next = StudentProfileSynchronizer.decide(
            cached, StudentDoc(studentId = cached.userId, status = "TC"))
        assertNotNull(next)
        tokenManager.saveUserDirect(next!!)

        assertEquals("the withdrawal did not persist", "TC", tokenManager.user.first().status)
    }

    @Test
    fun graduation_reaches_the_cache_and_is_readable_back() = runBlocking {
        // Alumni is reachable on the happy path now that the admin side
        // graduates at the school's own terminal class.
        tokenManager.saveUserDirect(user(status = "Active"))
        val cached = tokenManager.user.first()

        val next = StudentProfileSynchronizer.decide(
            cached, StudentDoc(studentId = cached.userId, status = "Alumni"))
        assertNotNull(next)
        tokenManager.saveUserDirect(next!!)

        assertEquals("Alumni", tokenManager.user.first().status)
    }
}
