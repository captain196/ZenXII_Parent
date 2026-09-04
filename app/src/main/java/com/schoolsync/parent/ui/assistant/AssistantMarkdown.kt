package com.schoolsync.parent.ui.assistant

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders the small markdown subset the model actually emits.
 *
 * The model writes `**bold**`, `*italic*` and `* ` / `- ` / `1. ` lists. Those
 * were being drawn with a plain Text(), so students saw literal asterisks on
 * every list and every emphasis — e.g. a timetable arrived as
 * `* **Period 1:** Mathematics`.
 *
 * Deliberately NOT a general markdown parser:
 *  · no links, images or raw HTML. Model output is not fully trusted, and a
 *    tappable link built from generated text is an obvious abuse surface on a
 *    children's screen. Anything unrecognised is shown as plain text.
 *  · no tables or code blocks — the prompt does not ask for them, and silently
 *    mangling something we cannot draw is worse than drawing it verbatim.
 *
 * Output is an AnnotatedString of styled spans, never markup, so there is no
 * injection path: unmatched or malformed syntax degrades to the literal
 * characters the model wrote.
 */
/**
 * Hanging indent for list items: a wrapped line starts under the item's TEXT,
 * not back at the bubble's left edge.
 *
 * Indentation is a PARAGRAPH property, so no amount of prefix characters can do
 * this — padding only affects the line it sits on, which is why the continuation
 * of "12:15 PM - 1:00 PM: Physical Education" used to fall back to the margin.
 * sp and not dp so the indent tracks the system font scale, and a margin rather
 * than characters so it is script-agnostic across the six locales and flips for
 * RTL on its own.
 */
private val BULLET_HANG = ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 14.sp))
private val NUMBER_HANG = ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 20.sp))

internal fun renderAssistantMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val bullet = BULLET.find(lines[i])
        val numbered = if (bullet == null) NUMBERED.find(lines[i]) else null

        if (bullet != null || numbered != null) {
            // ONE paragraph per item: TextIndent is a paragraph property, so two
            // items sharing a paragraph would share a single firstLine treatment.
            //
            // Nothing appends "\n" around this block. A ParagraphStyle boundary is
            // ALREADY a line break, and carrying the break in BOTH a character and
            // a paragraph renders a blank line between every bullet. The break is
            // carried by the paragraph here — that is the whole point of the change.
            withStyle(if (bullet != null) BULLET_HANG else NUMBER_HANG) {
                if (bullet != null) {
                    append("\u2022  ")
                    appendInline(bullet.groupValues[1])
                } else {
                    append("${numbered!!.groupValues[1]}.  ")
                    appendInline(numbered.groupValues[2])
                }
            }
            i++
        } else {
            // A run of prose stays ONE paragraph and keeps its real newlines:
            // there is no indent to isolate, and the characters are what the
            // plain-text contract (and the unit tests) pin.
            val start = i
            while (i < lines.size && !isListLine(lines[i])) i++
            for (n in start until i) {
                if (n > start) append("\n")
                appendInline(lines[n])
            }
        }
    }
}

/**
 * The same content as [renderAssistantMarkdown], as PLAIN TEXT with real newlines.
 *
 * The two cannot be the same string. On screen the break between list items is
 * carried by a ParagraphStyle boundary, because that is what makes a wrapped line
 * hang under the item's text — and a "\n" as well would render a blank line
 * between every bullet. But a paragraph boundary is invisible to everything that
 * consumes the plain string: the clipboard pasted a whole timetable as one
 * run-on line, and TalkBack read six periods with no pause between them.
 *
 * So: paragraphs for layout, characters for text. Use this for copy and for the
 * accessibility label, never for display.
 */
internal fun assistantPlainText(raw: String): String =
    raw.replace("\r\n", "\n").split("\n").joinToString("\n") { line ->
        val bullet = BULLET.find(line)
        val numbered = if (bullet == null) NUMBERED.find(line) else null
        when {
            bullet != null -> "\u2022  " + renderAssistantMarkdown(bullet.groupValues[1]).text
            numbered != null ->
                "${numbered.groupValues[1]}.  " + renderAssistantMarkdown(numbered.groupValues[2]).text
            else -> renderAssistantMarkdown(line).text
        }
    }

private fun isListLine(line: String): Boolean =
    BULLET.containsMatchIn(line) || NUMBERED.containsMatchIn(line)

/**
 * Emphasis within one line: `*italic*`, `**bold**`, `***both***`.
 *
 * A single left-to-right scan. At each `*` it asks one question — does a VALID
 * emphasis run start here? — and if the answer is no it emits the character
 * literally and moves on. The previous version instead searched for the next
 * bold marker and dumped everything before it as plain text, which produced
 * three separate defects, all confirmed by unit test before this rewrite:
 *
 *   "5*4*3*2"                    -> "543*2"   digits FUSED into a new number
 *   "The area is 5 * 3 * 2 cm"   -> "5  3  2" both asterisks DELETED
 *   "*Note* your **fees** are due" -> "*Note*" survived as literal asterisks
 *   "***Fees due***"             -> a stray, styled asterisk at each end
 *
 * The flanking rule is what keeps arithmetic intact. A run only counts as
 * emphasis when it is not welded to a word or a digit on the outside and not
 * padded with a space on the inside — so `5*4*` is multiplication and `*Note*`
 * is emphasis, which is the distinction the old code could not make.
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    var literal = 0
    while (i < text.length) {
        if (text[i] != '*') { i++; continue }

        val run = minOf(markerRun(text, i), 3)
        val close = findClose(text, i + run, run)
        if (close < 0 || !isEmphasis(text, i, run, close)) { i += run; continue }

        if (i > literal) append(text.substring(literal, i))
        // Recurse: the content is strictly shorter, so this terminates, and it
        // is what lets `**bold with *italic* inside**` work at all.
        withStyle(styleFor(run)) { appendInline(text.substring(i + run, close)) }
        i = close + run
        literal = i
    }
    if (literal < text.length) append(text.substring(literal))
}

/** How many `*` in a row start at [i]. */
private fun markerRun(text: String, i: Int): Int {
    var n = 0
    while (i + n < text.length && text[i + n] == '*') n++
    return n
}

/** The next run of at least [run] asterisks at or after [from]; -1 if none. */
private fun findClose(text: String, from: Int, run: Int): Int {
    var j = from
    while (j < text.length) {
        if (text[j] == '*' && markerRun(text, j) >= run) return j
        j++
    }
    return -1
}

/**
 * Is `text[open until close]` a real emphasis run rather than punctuation the
 * model happened to type? Four conditions, all necessary:
 *  - non-empty content, so `**` and `* *` stay literal;
 *  - no space just inside either marker, so `5 * 3 * 2` is arithmetic;
 *  - not welded to an alphanumeric on either outside edge, so `5*4*` is too.
 */
private fun isEmphasis(text: String, open: Int, run: Int, close: Int): Boolean {
    val first = open + run
    if (close <= first) return false
    if (text[first].isWhitespace() || text[close - 1].isWhitespace()) return false
    val before = text.getOrNull(open - 1)
    val after = text.getOrNull(close + run)
    if (before != null && before.isLetterOrDigit()) return false
    if (after != null && after.isLetterOrDigit()) return false
    return true
}

private fun styleFor(run: Int): SpanStyle = when (run) {
    1 -> SpanStyle(fontStyle = FontStyle.Italic)
    2 -> SpanStyle(fontWeight = FontWeight.SemiBold)
    else -> SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
}

private val BULLET = Regex("""^\s*[*\-]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*(\d{1,2})[.)]\s+(.*)$""")
