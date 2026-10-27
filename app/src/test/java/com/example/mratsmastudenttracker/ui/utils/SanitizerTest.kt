package com.example.mratsmastudenttracker.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SanitizerTest {
    @Test
    fun unaccent_isCorrect() {
        assertEquals("aeiou", "áéíóů".unaccent())
    }

    @Test
    fun unspaced_isCorrect() {
        assertEquals("abc", "a b c".unspaced())
    }

    @Test
    fun toBasicLower123abc_isCorrect() {
        assertEquals("aeioubcd3", "áéíóů b-c@D3".toBasicLower123abc())
    }
}
