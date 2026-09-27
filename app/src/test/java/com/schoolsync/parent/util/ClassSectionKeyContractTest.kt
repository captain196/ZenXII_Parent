package com.schoolsync.parent.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * THE KEY THIS APP BUILDS MUST BE THE KEY THE SCHOOL WROTE.
 *
 * Every timetable read here narrows on `sectionKey`, built locally from the
 * student's class and section. The admin panel writes that field through one
 * normaliser — Entity_firestore_sync::normalizeClassSection — and the timetable
 * projection deliberately REBUILDS it from the canonical parts so a malformed
 * stored key cannot reach the collection this app queries.
 *
 * So the contract is exact: for the same input, this app must produce the same
 * string that normaliser produces. If it does not, the query matches nothing
 * and the parent is shown an empty week for a class that has a full timetable —
 * which is why the repository already carries a diagnostic for "0 sectionKey
 * matches", and why a numeric class label is the case that breaks it.
 *
 * The expectations below are not invented: they are the output of running the
 * PHP normaliser on each input.
 */
class ClassSectionKeyContractTest {

    /** input → what Entity_firestore_sync::normalizeClassSection returns. */
    private val canonicalClass = listOf(
        "8" to "Class 8th",
        "Class 8" to "Class 8th",
        "Class 8th" to "Class 8th",
        "1" to "Class 1st",
        "2" to "Class 2nd",
        "3" to "Class 3rd",
        "11" to "Class 11th",
        "12" to "Class 12th",
        "13" to "Class 13th",
        "Nursery" to "Class Nursery",
        "LKG" to "Class LKG",
    )

    private val canonicalSection = listOf(
        "A" to "Section A",
        "Section A" to "Section A",
        "b" to "Section b",
    )

    @Test
    fun classKeyMatchesTheCanonicalNormaliser() {
        for ((input, expected) in canonicalClass) {
            assertEquals(
                "classKey(\"$input\") must equal what the admin panel stored",
                expected,
                Constants.Firebase.classKey(input)
            )
        }
    }

    @Test
    fun sectionKeyMatchesTheCanonicalNormaliser() {
        for ((input, expected) in canonicalSection) {
            assertEquals(expected, Constants.Firebase.sectionKey(input))
        }
    }

    /** The composite the timetable query actually filters on. */
    @Test
    fun theSectionKeyAQueryUsesIsTheOneTheProjectionWrites() {
        val built = Constants.Firebase.classKey("8") + "/" + Constants.Firebase.sectionKey("A")
        assertEquals("Class 8th/Section A", built)
    }

    /** Blank stays blank — a class-wide row must not become "Class ". */
    @Test
    fun blanksStayBlank() {
        assertEquals("", Constants.Firebase.classKey(""))
        assertEquals("", Constants.Firebase.sectionKey(""))
    }

    /**
     * The contract is "match the normaliser", not "be tidy". Fed a label that
     * is only the word Class, the admin's normaliser produces "Class Class",
     * and a school whose class is named that has documents carrying it. An app
     * that cleaned the string up would query for something nobody stored.
     */
    @Test
    fun evenTheOddCasesMatchTheNormaliser() {
        assertEquals("Class Class", Constants.Firebase.classKey("Class "))
        assertEquals("Class Class", Constants.Firebase.classKey("Class"))
        assertEquals("Class Classical Studies", Constants.Firebase.classKey("Classical Studies"))
        assertEquals("Class 8th", Constants.Firebase.classKey("class 8"))
        assertEquals("Class 8th", Constants.Firebase.classKey(" 8 "))
    }
}
