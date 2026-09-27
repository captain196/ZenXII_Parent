package com.schoolsync.parent.fees

import com.schoolsync.parent.ui.fees.CheckoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  P14/P15 — THE APP DISPATCHES ON THE SERVER'S CHECKOUT ENGINE
 * ══════════════════════════════════════════════════════════════════════════
 *
 * The product rule: the SERVER decides which payment provider a school uses.
 * The app is a checkout CLIENT — it launches the engine the server names, or
 * says plainly that this build does not have it.
 *
 * What the app must never contain:
 *
 *     if (school == A) razorpay
 *     if (school == B) cashfree
 *     if (provider == "razorpay") ...
 *
 * `CheckoutEngine` is what makes that avoidable: providers are matched to
 * ENGINES, several providers can share one engine, and the app only ever
 * asks "do I have this engine?".
 *
 * ── WHY REFUSING MATTERS MORE THAN LAUNCHING ─────────────────────────────
 * Attempting the bundled Razorpay SDK for a hosted-redirect provider would
 * fail deep inside the SDK, mid-payment. To a parent that is indistinguishable
 * from a declined card — the exact confusion that cost a real debugging cycle
 * when a mock-minted order was handed to the Razorpay checkout. An explicit
 * "your app needs updating" is worth far more than an optimistic attempt.
 */
class CheckoutEngineDispatchTest {

    // ══ WHAT THIS BUILD ACTUALLY HAS ══════════════════════════════════════

    @Test
    fun `this build supports the native sdk engine`() {
        assertTrue(
            "the bundled Razorpay Standard Checkout engine is no longer advertised as " +
                "supported, which would break every currently-working school",
            CheckoutEngine.isSupported(CheckoutEngine.NATIVE_SDK)
        )
    }

    /**
     * These engines are NAMED (so the server and app share one vocabulary)
     * but NOT implemented in this build. Claiming them would be the same
     * false promise as an adapter claiming refunds it cannot perform.
     */
    @Test
    fun `engines this build does not implement are refused`() {
        assertFalse(
            "webview checkout is claimed as supported but this build has no webview " +
                "checkout engine - a provider needing it would fail mid-payment",
            CheckoutEngine.isSupported(CheckoutEngine.WEBVIEW)
        )
        assertFalse(
            CheckoutEngine.isSupported(CheckoutEngine.HOSTED_REDIRECT)
        )
        assertFalse(
            CheckoutEngine.isSupported(CheckoutEngine.UPI_INTENT)
        )
    }

    // ══ UNKNOWN AND MISSING VALUES FAIL CLOSED ════════════════════════════

    @Test
    fun `an unknown engine name is refused rather than guessed`() {
        assertFalse(
            "an unrecognised engine was treated as supported. A future server may name " +
                "an engine this build has never heard of; guessing would launch the wrong " +
                "checkout for a real order",
            CheckoutEngine.isSupported("some_future_engine")
        )
    }

    @Test
    fun `null and blank engines are refused`() {
        assertFalse(CheckoutEngine.isSupported(null))
        assertFalse(CheckoutEngine.isSupported(""))
        assertFalse(CheckoutEngine.isSupported("   "))
    }

    /**
     * Engine names come from the server verbatim and are matched exactly.
     * Silently normalising case would mean accepting a value the server did
     * not actually send, which is a different (and unverified) contract.
     */
    @Test
    fun `engine matching is exact, not case-insensitive`() {
        assertFalse(CheckoutEngine.isSupported("NATIVE_SDK"))
        assertFalse(CheckoutEngine.isSupported("Native_Sdk"))
    }

    // ══ THE ENGINE VOCABULARY IS SHARED WITH THE SERVER ═══════════════════

    /**
     * These string literals are a CONTRACT with the server's
     * `capabilities()['checkout_type']`. Renaming one on either side silently
     * makes every school using that engine unpayable, so the values are
     * pinned here rather than left to a rename-refactor.
     */
    @Test
    fun `engine identifiers match the server contract exactly`() {
        assertEquals("native_sdk", CheckoutEngine.NATIVE_SDK)
        assertEquals("webview", CheckoutEngine.WEBVIEW)
        assertEquals("hosted_redirect", CheckoutEngine.HOSTED_REDIRECT)
        assertEquals("upi_intent", CheckoutEngine.UPI_INTENT)
    }

    // ══ CASE A vs CASE B, EXPRESSED AS A TEST ═════════════════════════════

    /**
     * The distinction the whole architecture turns on:
     *
     *   CASE A — a NEW provider that uses an engine this build already has.
     *            Server-side change only. No app release.
     *   CASE B — a new provider needing an engine this build lacks.
     *            An app release is genuinely required.
     *
     * Razorpay and any future native_sdk provider are CASE A. Cashfree
     * (webview) is CASE B for this build, which is why the server does not
     * yet offer it.
     */
    @Test
    fun `a different provider sharing the native sdk engine needs no app change`() {
        // The app never sees a provider name here — only the engine. That is
        // exactly why a second native_sdk provider is a server-only change.
        val engineForSomeOtherNativeProvider = CheckoutEngine.NATIVE_SDK

        assertTrue(
            "a provider reusing an already-supported engine was refused, which would " +
                "force an unnecessary app release and defeat CASE A entirely",
            CheckoutEngine.isSupported(engineForSomeOtherNativeProvider)
        )
    }

    @Test
    fun `a provider needing an unimplemented engine is correctly identified as needing a release`() {
        // Cashfree's declared checkout_type.
        assertFalse(
            "a webview-based provider was reported as supported. This build has no " +
                "webview engine, so this must read as APP RELEASE REQUIRED, not as a " +
                "green light to attempt the Razorpay SDK",
            CheckoutEngine.isSupported(CheckoutEngine.WEBVIEW)
        )
    }

    // ══ THE SUPPORTED SET IS A WHITELIST ══════════════════════════════════

    @Test
    fun `supported set contains only engines that are actually implemented`() {
        assertEquals(
            "the supported-engine whitelist changed. Adding an entry here without " +
                "implementing the engine makes the app attempt a checkout it cannot render.",
            setOf(CheckoutEngine.NATIVE_SDK),
            CheckoutEngine.SUPPORTED
        )
    }
}
