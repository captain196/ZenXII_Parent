package com.schoolsync.parent.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model emits a small markdown subset. Before this renderer existed a plain
 * Text() drew it literally, so a timetable reached students as
 * `* **Period 1:** Mathematics`. These cases pin the subset AND the malformed
 * input that must degrade to literal text rather than swallow characters.
 */
class AssistantMarkdownTest {

    private fun render(s: String) = renderAssistantMarkdown(s).text

    @Test fun `bold markers are removed and text kept`() {
        assertEquals("Period 1: Maths", render("**Period 1:** Maths").trim())
    }

    @Test fun `bullet becomes a real bullet glyph`() {
        val out = render("* English\n* Maths")
        assertTrue(out.contains("•"))
        assertTrue(out.contains("English") && out.contains("Maths"))
        assertTrue("asterisk must not survive", !out.contains("*"))
    }

    @Test fun `numbered list keeps its number`() {
        val out = render("1. First\n2. Second")
        assertTrue(out.contains("1.") && out.contains("First"))
        assertTrue(out.contains("2.") && out.contains("Second"))
    }

    @Test fun `unclosed bold degrades to literal text, losing nothing`() {
        // A naive parser drops the tail here. The student must still see the words.
        val out = render("your fee is **1200 and unpaid")
        assertTrue(out.contains("1200"))
        assertTrue(out.contains("unpaid"))
    }

    @Test fun `lone asterisk mid sentence is not treated as emphasis`() {
        val out = render("2 * 3 = 6")
        assertTrue(out.contains("2") && out.contains("3") && out.contains("6"))
    }

    @Test fun `plain text is unchanged`() {
        val s = "You were absent 3 days in August."
        assertEquals(s, render(s))
    }

    @Test fun `newlines are preserved between lines`() {
        assertEquals(2, render("one\ntwo").count { it == '\n' } + 1)
    }

    @Test fun `devanagari survives intact`() {
        val out = render("**उपस्थिति:** 86%")
        assertTrue(out.contains("उपस्थिति"))
        assertTrue(!out.contains("*"))
    }

    // ── regressions found by adversarial audit, 2026-09-04 ──────────────────

    @Test
    fun `asterisks used as multiplication are never deleted or fused`() {
        // The renderer's contract is that malformed syntax degrades to the
        // literal characters the model wrote. Deleting them changes an answer's
        // meaning; fusing digits invents a different number entirely.
        val out = renderAssistantMarkdown("5*4*3*2").text
        assertFalse("digits must not be fused into a new number", out.contains("543"))
        assertEquals("5*4*3*2", out)
    }

    @Test
    fun `arithmetic with spaces keeps both asterisks`() {
        assertEquals("The area is 5 * 3 * 2 cm", renderAssistantMarkdown("The area is 5 * 3 * 2 cm").text)
    }

    @Test
    fun `italic before bold on the same line still renders`() {
        // The bold scan dumps everything before the bold marker as plain text
        // without scanning it, so an earlier italic survived as literal asterisks
        // -- the exact defect this renderer exists to remove.
        val out = renderAssistantMarkdown("*Note* your **fees** are due").text
        assertFalse("no literal asterisks may survive: <$out>", out.contains("*"))
        assertEquals("Note your fees are due", out)
    }

    @Test
    fun `triple asterisk emphasis leaves no stray marker`() {
        val out = renderAssistantMarkdown("***Fees due***").text
        assertFalse("no literal asterisks may survive: <$out>", out.contains("*"))
    }


    // ── hanging indent for wrapped list items, 2026-09-04 ───────────────────

    @Test
    fun `no paragraph range contains a newline`() {
        // THE trap in this design. A newline AND a paragraph boundary at the same
        // point renders a blank line between every bullet; the break must be
        // carried by one or the other, never both.
        val a = renderAssistantMarkdown("Timetable:\n* 12:15 PM: PE\n* 1:00 PM: Lunch\nThat is all.")
        a.paragraphStyles.forEach {
            assertFalse("paragraph range must not contain a newline",
                a.text.substring(it.start, it.end).contains('\n'))
        }
    }

    @Test
    fun `each list item is its own paragraph with a hanging indent`() {
        val a = renderAssistantMarkdown("Timetable:\n* 12:15 PM: PE\n* 1:00 PM: Lunch")
        assertEquals("one paragraph per item; the prose line takes none", 2, a.paragraphStyles.size)
        a.paragraphStyles.forEach {
            val hang = it.item.textIndent!!
            assertTrue("must hang, not first-line indent", hang.restLine.value > hang.firstLine.value)
            assertTrue("sp so it tracks the font scale", hang.restLine.isSp)
        }
    }

    @Test
    fun `prose keeps its newline characters and takes no indent`() {
        val a = renderAssistantMarkdown("one\ntwo")
        assertEquals("one\ntwo", a.text)
        assertTrue("prose needs no paragraph styling", a.paragraphStyles.isEmpty())
    }


    @Test
    fun `copied text keeps a line break between list items`() {
        // The hanging-indent change moved the line break from a "\n" character
        // into a ParagraphStyle boundary. That is right on screen and WRONG for
        // anything that consumes the plain string: the clipboard and TalkBack.
        val copied = assistantPlainText("Timetable:\n* 8:00 AM: Maths\n* 8:45 AM: English")
        assertTrue("items must not run together: <$copied>", copied.contains("Maths\n"))
        assertEquals("Timetable:\n\u2022  8:00 AM: Maths\n\u2022  8:45 AM: English", copied)
    }

}
