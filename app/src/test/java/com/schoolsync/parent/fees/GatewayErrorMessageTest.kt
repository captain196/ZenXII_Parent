package com.schoolsync.parent.fees

import com.schoolsync.parent.util.friendlyGatewayError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PAY-MSG-1 — THE PARENT WAS SHOWN RAW GATEWAY JSON.
 *
 * `Checkout.onPaymentError(code, description, …)` returns the gateway's raw
 * payload in `description`, and the failure dialog rendered it verbatim. A real
 * parent, on a real payment against production, saw:
 *
 *   {"error":{"code":"BAD_REQUEST_ERROR","description":"Payment Failed",
 *    "source":"customer","step":"payment_authentication",
 *    "reason":"payment_error","metadata":{}}}
 *
 * Stage B1 already fixed this class of leak for transport errors —
 * "Failed to verify: retrofit2.HttpException HTTP 502" — and stated the rule:
 * never leak raw text into the parent-visible surface. The gateway path simply
 * never went through the mapper.
 *
 * These drive the REAL mapper against the REAL payload that was observed.
 */
class GatewayErrorMessageTest {

    /** The exact string the parent was shown. */
    private val observed =
        """{"error":{"code":"BAD_REQUEST_ERROR","description":"Payment Failed","source":"customer","step":"payment_authentication","reason":"payment_error","metadata":{}}}"""

    @Test
    fun `the observed payload never reaches the parent verbatim`() {
        val msg = friendlyGatewayError(observed)

        for (leak in listOf("BAD_REQUEST_ERROR", "metadata", "{", "}", "\"step\"", "source")) {
            assertFalse(
                "the gateway payload fragment '$leak' is still shown to the parent",
                msg.contains(leak)
            )
        }
    }

    @Test
    fun `an authentication failure explains what to do about it`() {
        val msg = friendlyGatewayError(observed)

        assertTrue(
            "the message does not tell the parent their bank declined it, so they have no " +
                "idea whether to retry, call the school, or call the bank",
            msg.contains("bank", ignoreCase = true)
        )
        assertTrue(
            "the message does not say the fees are still due — a parent may assume the " +
                "charge cleared and stop",
            msg.contains("still due", ignoreCase = true)
        )
    }

    /**
     * Deliberately NOT claiming "no money was deducted". A payment failing at
     * authentication was never captured, but banks do place and release holds,
     * and a parent who sees a pending debit after being told otherwise will not
     * trust the next thing the app says.
     */
    @Test
    fun `it does not make an absolute claim about deduction`() {
        val msg = friendlyGatewayError(observed)

        assertFalse(
            "the copy asserts no money was deducted. That cannot be guaranteed from the " +
                "gateway payload, and a visible bank hold would make the app look wrong.",
            msg.contains("no money was deducted", ignoreCase = true)
        )
    }

    @Test
    fun `a plain sentence from the sdk is passed through`() {
        assertEquals("Network error", friendlyGatewayError("Network error"))
    }

    @Test
    fun `a blank or null description still yields a usable sentence`() {
        for (raw in listOf(null, "", "   ")) {
            val msg = friendlyGatewayError(raw)
            assertTrue("a blank payload produced an empty dialog body", msg.length > 20)
            assertTrue(msg.contains("still due", ignoreCase = true))
        }
    }

    /** The useless catch-all must not win over our own wording. */
    @Test
    fun `the gateway generic description is not preferred`() {
        val generic =
            """{"error":{"code":"GATEWAY_ERROR","description":"Payment Failed","step":"","reason":""}}"""

        assertFalse(
            "the gateway's catch-all 'Payment Failed' was shown as the explanation, which " +
                "tells the parent nothing they did not already see in the title",
            friendlyGatewayError(generic).trim() == "Payment Failed"
        )
    }

    /** A genuinely specific gateway sentence IS better than our generic one. */
    @Test
    fun `a specific gateway description is preferred over the generic fallback`() {
        val specific =
            """{"error":{"code":"BAD_REQUEST_ERROR","description":"Your card has expired","step":"x","reason":"y"}}"""

        assertEquals("Your card has expired", friendlyGatewayError(specific))
    }

    @Test
    fun `malformed json does not crash and still says something useful`() {
        val msg = friendlyGatewayError("""{"error":{"code":""")
        assertTrue(msg.contains("still due", ignoreCase = true))
    }
}
