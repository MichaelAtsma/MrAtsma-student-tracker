package com.example.mratsmastudenttracker.ui.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("SpellCheckingInspection")
class SearchTest {
    @Test
    fun matchesSearchQuery_incompleteWord_isCorrect() {
        val target = "Mike John Smith"
        assertTrue(target.matchesSearchQuery("john mi"))
        assertTrue(target.matchesSearchQuery("mi john"))
        assertTrue(target.matchesSearchQuery("smith m"))
        assertFalse(target.matchesSearchQuery("john alex"))
    }

    @Test
    fun matchesSearchQuery_repeatedWords_isCorrect() {
        val singleMike = "Mike John Smith"
        val tripleMike = "Mike John Mike Smith Mike"

        assertFalse(singleMike.matchesSearchQuery("mike mike mike"))
        assertFalse(singleMike.matchesSearchQuery("mike mike"))
        assertTrue(singleMike.matchesSearchQuery("mike john"))
        assertTrue(tripleMike.matchesSearchQuery("mike mike mike"))
        assertTrue(tripleMike.matchesSearchQuery("mike john mike"))
    }

    @Test
    fun matchesSearchQuery_backtrackingOverlap_isCorrect() {
        val target = "Mike Ike Tike Smith"
        assertTrue(target.matchesSearchQuery("ik ik mike"))
        assertFalse("Mike John Smith".matchesSearchQuery("ik ik mike"))

        assertTrue("abcde bcdefg".matchesSearchQuery("bcde abc")) // Searchign for the longest query word first should not always work
    }
}
