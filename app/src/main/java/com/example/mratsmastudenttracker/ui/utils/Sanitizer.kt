package com.example.mratsmastudenttracker.ui.utils

import java.text.Normalizer

private val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun CharSequence.unaccent(): String {
    // Source - https://stackoverflow.com/a/51734018
    // Posted by Eugen Pechanec
    // Retrieved 2026-10-26, License - CC BY-SA 4.0
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return REGEX_UNACCENT.replace(temp, "")
}

fun CharSequence.unspaced(): String {
    return this.filterNot { it == ' ' }.toString()
}

fun CharSequence.toBasicLower123abc(): String {
    return this
        .unspaced()
        .unaccent()
        .lowercase()
        .filter { it.isLetterOrDigit() }
}

fun CharSequence.toNormalizedWords(): List<String> {
    return this
        .unaccent()
        .lowercase()
        .split(Regex("\\s+"))
        .map { word -> word.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
}

fun CharSequence.toSetOfSeparatedLower123abcWords(): Set<String> {
    return this.toNormalizedWords().toSet()
}
