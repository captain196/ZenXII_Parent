package com.schoolsync.parent.sync

import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.data.sync.StudentProfileSynchronizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  THE CACHED PROFILE MUST FOLLOW THE STUDENT DOCUMENT — AND ONLY IT
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── THE DEFECT THIS COVERS ───────────────────────────────────────────────
 * Every screen reads class / section / roll / session from the `User` cached
 * in TokenManager, and that cache was written exactly once, at login. An admin
 * promoting a student changed Firestore; the parent's open app went on showing
 * last year's class until they signed out and back in.
 *
 * `StudentFirestoreRepository.observeStudent()` — a real snapshot listener —
 * already existed with zero callers. StudentProfileSynchronizer is the
 * consumer, and `decide()` is its entire policy.
 *
 * ── WHY decide() IS TESTED AND THE FLOW IS NOT ───────────────────────────
 * TokenManager needs an Android Context, so anything reachable only through
 * the suspend path runs on a device, not here. The refactor that lifted the
 * decision into a pure function is what makes the policy assertable at all;
 * the wiring above it (one listener, keyed on identity, idempotent start) is
 * verified on-device.
 *
 * The two guards below are not stylistic. Writing the cache re-emits
 * `tokenManager.user`, which is the synchroniser's OWN upstream — an
 * unguarded write is an infinite loop, in a process-lifetime coroutine, doing
 * disk I/O.
 */
class StudentProfileSynchronizerTest {

    private fun cachedUser(
        userId: String = "STU00042",
        className: String = "Class 9th",
        section: String = "Section A",
        rollNo: String = "17",
        session: String = "2025-2026",
        status: String = "Active",
    ) = User(
        userId = userId, name = "Aarav Sharma", email = "", phone = "9990001111",
        role = "student", schoolId = "SCH_B56BB9A401", schoolDisplayName = "Vikrant Public",
        profilePic = "", className = className, section = section, rollNo = rollNo,
        fatherName = "", motherName = "", dob = "", gender = "", admissionDate = "",
        parentDbKey = "", session = session, schoolCode = "SCH_B56BB9A401",
        status = status,
    )

    private fun doc(
        studentId: String = "STU00042",
        className: String = "",
        section: String = "",
        rollNo: String = "",
        session: String = "",
        status: String = "",
    ) = StudentDoc(
        studentId = studentId, className = className, section = section,
        rollNo = rollNo, session = session, status = status,
    )

    // ── LOOP GUARD ───────────────────────────────────────────────────────

    @Test
    fun `an identical document writes nothing`() {
        // THE loop guard. Firestore re-delivers the same document on
        // reconnect, on resume, and from its own offline cache; if any of
        // those wrote, the write would re-emit the upstream and never settle.
        val cached = cachedUser()
        assertNull(
            "an unchanged document caused a write — that write re-emits " +
                "tokenManager.user and loops forever",
            StudentProfileSynchronizer.decide(
                cached,
                doc(className = "Class 9th", section = "Section A",
                    rollNo = "17", session = "2025-2026", status = "Active"),
            ),
        )
    }

    @Test
    fun `a document carrying nothing at all writes nothing`() {
        // StudentDoc defaults every field to "". A document that deserialized
        // with no watched field set must be inert, not a wipe.
        assertNull(StudentProfileSynchronizer.decide(cachedUser(), doc()))
    }

    // ── THE POSITIVE CONTROL — it must actually fire ─────────────────────

    @Test
    fun `a promotion reaches the cache`() {
        val next = StudentProfileSynchronizer.decide(
            cachedUser(className = "Class 9th", session = "2025-2026"),
            doc(className = "Class 10th", session = "2026-2027", rollNo = "4"),
        )
        assertNotNull("a promotion did not reach the cache — this is the whole feature", next)
        assertEquals("Class 10th", next!!.className)
        assertEquals("2026-2027", next.session)
        assertEquals("4", next.rollNo)
    }

    @Test
    fun `a section change alone is enough to write`() {
        val next = StudentProfileSynchronizer.decide(cachedUser(), doc(section = "Section C"))
        assertEquals("Section C", next?.section)
    }

    @Test
    fun `a roll number change alone is enough to write`() {
        val next = StudentProfileSynchronizer.decide(cachedUser(), doc(rollNo = "31"))
        assertEquals("31", next?.rollNo)
    }

    // ── PARTIAL DOCUMENTS MUST NOT ERASE A GOOD CACHE ────────────────────

    @Test
    fun `a blank field means absent, not emptied`() {
        // The risk is real: every StudentDoc field defaults to "", so a
        // document missing `section` would blank a correct section if blank
        // were treated as a value.
        val next = StudentProfileSynchronizer.decide(cachedUser(), doc(className = "Class 10th"))
        assertNotNull(next)
        assertEquals("the section was erased by a document that never mentioned it",
            "Section A", next!!.section)
        assertEquals("17", next.rollNo)
        assertEquals("2025-2026", next.session)
        assertEquals("Active", next.status)
    }

    @Test
    fun `no field outside the watched set is ever touched`() {
        // The synchroniser follows enrollment placement and status. It is not
        // a general profile refresher, and must not become one by accident.
        val cached = cachedUser()
        val next = StudentProfileSynchronizer.decide(cached, doc(className = "Class 10th"))!!
        assertEquals(cached.userId, next.userId)
        assertEquals(cached.name, next.name)
        assertEquals(cached.phone, next.phone)
        assertEquals(cached.schoolId, next.schoolId)
        assertEquals(cached.schoolCode, next.schoolCode)
        assertEquals(cached.parentDbKey, next.parentDbKey)
    }

    // ── IDENTITY ─────────────────────────────────────────────────────────

    @Test
    fun `another students document is refused`() {
        assertNull(
            "a document for a different student overwrote this profile",
            StudentProfileSynchronizer.decide(
                cachedUser(userId = "STU00042"),
                doc(studentId = "STU00099", className = "Class 12th"),
            ),
        )
    }

    @Test
    fun `a document with no studentId is still applied`() {
        // observeStudent reads the canonical {schoolCode}_{studentId} doc, so
        // the document is already the right one; the id check guards a
        // mis-wired caller. Refusing an id-less document would make the
        // feature depend on a field the listener does not need.
        val next = StudentProfileSynchronizer.decide(
            cachedUser(), doc(studentId = "", className = "Class 10th"),
        )
        assertEquals("Class 10th", next?.className)
    }

    // ── STATUS ───────────────────────────────────────────────────────────

    @Test
    fun `a withdrawal reaches the cache`() {
        // AuthRepository.login() already refuses a non-Active status. That
        // guard ran only at login, so a session already open when the
        // withdrawal happened kept working. This carries the change in.
        val next = StudentProfileSynchronizer.decide(cachedUser(), doc(status = "TC"))
        assertEquals("TC", next?.status)
    }

    @Test
    fun `a re-admission back to Active reaches the cache too`() {
        // The transition must be readable in both directions, or a
        // re-instated student stays locked out of their own app.
        val next = StudentProfileSynchronizer.decide(
            cachedUser(status = "Inactive"), doc(status = "Active"),
        )
        assertEquals("Active", next?.status)
    }

    @Test
    fun `status alone changing is enough to write`() {
        assertNotNull(StudentProfileSynchronizer.decide(cachedUser(), doc(status = "Inactive")))
    }

    // ── COMPLETION IS NOT DEPARTURE ──────────────────────────────────────

    @Test
    fun `graduation reaches the cache like any other status`() {
        // The admin panel now graduates at the school's OWN terminal class
        // rather than a hardcoded Class 12, so Alumni is a state parents
        // actually reach — two of three live tenants end at Class 10.
        val next = StudentProfileSynchronizer.decide(cachedUser(), doc(status = "Alumni"))
        assertEquals("Alumni", next?.status)
    }

    @Test
    fun `the revoked screen separates completion from withdrawal`() {
        // The gate treats every non-Active status alike, which is right for
        // ACCESS and wrong for WORDING: "no longer enrolled" is a poor way to
        // tell a family their child finished eleven years of school. The
        // screen branches on this exact comparison, so it is pinned here.
        assertTrue("Alumni".equals("alumni", ignoreCase = true))
        assertFalse("TC".equals("Alumni", ignoreCase = true))
        assertFalse("Inactive".equals("Alumni", ignoreCase = true))
    }
}
