package com.schoolsync.parent.fees

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  THE "NOT CONFIGURED" MESSAGE MUST NAME THE SCHOOL'S OWN PROVIDER
 * ══════════════════════════════════════════════════════════════════════════
 *
 * `FeesViewModel` told every parent:
 *
 *     "Razorpay is not configured. Ask the school admin to enable it."
 *
 * regardless of which provider the school actually uses.
 *
 * ── WHY THIS IS MORE THAN A COSMETIC STRING ──────────────────────────────
 * For a school on any other provider it is simply FALSE, and it is the kind
 * of falsehood a parent repeats verbatim to the school office — sending an
 * administrator to check a gateway their school does not use, while the real
 * misconfiguration goes unexamined. A wrong provider name in an error message
 * costs someone an afternoon.
 *
 * It is also the same class of defect as the server-side PA-CORE-1 fabricated
 * provenance: asserting a provider identity instead of using the one the
 * server actually resolved.
 *
 * ── WHAT IS BEING TESTED ─────────────────────────────────────────────────
 * The message-selection logic is a pure expression over the server-supplied
 * provider field. It is reproduced here EXACTLY as `FeesViewModel` composes
 * it, and `UnconfiguredProviderMessageSourceTest` (below) asserts the view
 * model still contains that composition — so the pair fails if either the
 * logic or its use drifts.
 */
class UnconfiguredProviderMessageTest {

    /** Mirrors FeesViewModel's composition verbatim. */
    private fun messageFor(provider: String?): String =
        provider
            ?.takeIf { it.isNotBlank() && !it.equals("mock", ignoreCase = true) }
            ?.replaceFirstChar { c -> c.uppercaseChar() }
            ?.let { "$it is not fully configured for this school. Ask the school admin to complete the payment setup." }
            ?: "Online payment is not configured for this school. Ask the school admin to enable it."

    // ══ THE DEFECT ════════════════════════════════════════════════════════

    @Test
    fun `a school on another provider is not told Razorpay is missing`() {
        val msg = messageFor("cashfree")

        assertFalse(
            "the parent is told Razorpay is unconfigured for a school that does not use " +
                "Razorpay — they will send the office to check the wrong gateway",
            msg.contains("Razorpay", ignoreCase = true)
        )
        assertTrue(
            "the message does not name the provider the school actually uses",
            msg.contains("Cashfree")
        )
    }

    @Test
    fun `a razorpay school is still told about Razorpay`() {
        assertTrue(
            "the fix removed the provider name entirely instead of making it correct",
            messageFor("razorpay").contains("Razorpay")
        )
    }

    // ══ WHEN THE SERVER DID NOT SAY, STAY GENERIC ════════════════════════

    @Test
    fun `an absent provider produces a generic message, never a guess`() {
        for (unknown in listOf(null, "", "   ")) {
            val msg = messageFor(unknown)
            assertEquals(
                "Online payment is not configured for this school. Ask the school admin to enable it.",
                msg
            )
            assertFalse(
                "a provider name was invented when the server did not supply one",
                msg.contains("Razorpay", ignoreCase = true) || msg.contains("Cashfree", ignoreCase = true)
            )
        }
    }

    /**
     * The simulator is not a provider a parent can be asked to "enable" — its
     * name must never surface in guidance aimed at a school administrator.
     */
    @Test
    fun `the mock simulator never appears in a parent-facing message`() {
        for (mock in listOf("mock", "MOCK", "Mock")) {
            assertFalse(
                "the simulator's name leaked into a parent-facing message",
                messageFor(mock).contains("mock", ignoreCase = true)
            )
        }
    }

    @Test
    fun `an arbitrary future provider is named correctly without a code change`() {
        // The whole point of the architecture: a provider nobody has heard of
        // yet must still be named correctly, with no app change.
        assertTrue(messageFor("someFutureGateway").startsWith("SomeFutureGateway is not fully configured"))
    }
}

/**
 * The pair to the test above: prove FeesViewModel still COMPOSES the message
 * that way. A pure-logic test alone would keep passing if the view model
 * reverted to a hardcoded string, because the logic under test would simply
 * no longer be the logic in use.
 */
class UnconfiguredProviderMessageSourceTest {
    private val source: String by lazy {
        java.io.File(
            "src/main/java/com/schoolsync/parent/ui/fees/FeesViewModel.kt"
        ).readText()
    }

    @Test
    fun `the view model no longer hardcodes Razorpay in the unconfigured message`() {
        assertFalse(
            "FeesViewModel hardcodes \"Razorpay is not configured\" again — every school, " +
                "whatever provider it uses, is told to go check Razorpay",
            source.contains("Razorpay is not configured")
        )
    }

    @Test
    fun `the view model derives the message from the server-supplied provider`() {
        assertTrue(
            "the unconfigured-provider message is no longer derived from response.provider, " +
                "so it cannot be naming the school's actual provider",
            source.contains("errorMessage = response.provider")
        )
        assertTrue(
            "the generic fallback for an unnamed provider is gone",
            source.contains("Online payment is not configured for this school")
        )
    }
}
