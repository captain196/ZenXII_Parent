package com.schoolsync.parent.ui.support

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The conduct-route contact sanitisers, tested as attacks rather than as
 * happy-path formatting.
 *
 * These values come from the school document and end up in a `tel:` / `mailto:`
 * intent. Mail clients parse mailto QUERY PARAMETERS out of the address, so an
 * address carrying `?to=` opens a compose window addressed to somebody else as
 * well — silently copying a staff-conduct report, which is the most sensitive
 * message this module handles. A dialler treats `,` and `;` as DTMF separators,
 * so a suffix can change what is actually dialled.
 *
 * Exploiting either needs control of the school doc, so this is hardening rather
 * than an open hole. It is tested because the failure is silent: nothing errors,
 * the parent simply reaches the wrong recipient.
 *
 * The contract under test is REJECT, never repair. Repairing a phone number
 * would dial a different number without telling anyone.
 */
class ConductContactSanitiserTest {

    // ── email ────────────────────────────────────────────────────────────────

    @Test
    fun `plain address is kept`() {
        assertEquals("office@school.edu", ConductContactViewModel.safeEmail("office@school.edu"))
    }

    @Test
    fun `surrounding whitespace is trimmed, not rejected`() {
        assertEquals("office@school.edu", ConductContactViewModel.safeEmail("  office@school.edu \n"))
    }

    @Test
    fun `mailto query injection is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("office@school.edu?to=attacker@evil.com"))
    }

    @Test
    fun `mailto subject and body injection is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("a@b.com?subject=Hi&body=leak"))
    }

    @Test
    fun `comma separated second recipient is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("a@b.com,attacker@evil.com"))
    }

    @Test
    fun `header injection via CRLF is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("a@b.com\r\nBcc: attacker@evil.com"))
    }

    @Test
    fun `embedded space is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("a b@school.edu"))
    }

    @Test
    fun `missing at sign is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("not-an-address"))
    }

    @Test
    fun `missing tld is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("a@localhost"))
    }

    @Test
    fun `blank is rejected`() {
        assertEquals("", ConductContactViewModel.safeEmail("   "))
    }

    // ── phone ────────────────────────────────────────────────────────────────

    @Test
    fun `international number is kept verbatim`() {
        assertEquals("+91876543210", ConductContactViewModel.safePhone("+91876543210"))
    }

    @Test
    fun `spacing brackets and dashes are acceptable`() {
        assertEquals("+91 (876) 543-210", ConductContactViewModel.safePhone("+91 (876) 543-210"))
    }

    @Test
    fun `DTMF comma suffix is REJECTED, never stripped`() {
        // Stripping would dial +9187654321999 — a different number, silently.
        assertEquals("", ConductContactViewModel.safePhone("+9187654321,999"))
    }

    @Test
    fun `semicolon suffix is rejected`() {
        assertEquals("", ConductContactViewModel.safePhone("+9187654321;123"))
    }

    @Test
    fun `letters are rejected`() {
        assertEquals("", ConductContactViewModel.safePhone("+91-CALL-NOW"))
    }

    @Test
    fun `too few digits is rejected`() {
        assertEquals("", ConductContactViewModel.safePhone("12345"))
    }

    @Test
    fun `absurdly long number is rejected`() {
        assertEquals("", ConductContactViewModel.safePhone("1234567890123456789"))
    }

    @Test
    fun `blank is rejected too`() {
        assertEquals("", ConductContactViewModel.safePhone("  "))
    }
}
