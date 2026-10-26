@file:Suppress("GrazieInspection")

package com.example.mratsmastudenttracker.ui.utils


/**
 * Checks whether this target text matches all tokenized words in [query].
 *
 * Each query word must match a DISTINCT target word in the target text (e.g. as a prefix or substring).
 * Employs a maximum bipartite matching (DFS with backtracking) to resolve complex overlapping query terms.
 * E.g.:
 * - "john mi" matches "Mike John Smith" ("john" -> "john", "mi" -> "mike").
 * - "ik ik mike" matches "Mike Ike Tike Smith" ("ik" -> "ike", "ik" -> "tike", "mike" -> "mike").
 * - "mike mike mike" does NOT match "Mike John Smith" (only 1 "mike" in target).
 */
@Suppress("SpellCheckingInspection")
fun CharSequence.matchesSearchQuery(query: String): Boolean {
    val queryWords = query.toNormalizedWords()
    if (queryWords.isEmpty()) return true
    val targetWords = this.toNormalizedWords()
    if (targetWords.isEmpty()) return false
    if (queryWords.size > targetWords.size) return false

    val usedTargetIndices = BooleanArray(targetWords.size)

    fun matchDfs(qIdx: Int): Boolean {
        if (qIdx == queryWords.size) return true
        val q = queryWords[qIdx]

        for (idx in targetWords.indices) {
            if (!usedTargetIndices[idx]) {
                val t = targetWords[idx]
                if (t.startsWith(q) || t.contains(q)) {
                    usedTargetIndices[idx] = true
                    if (matchDfs(qIdx + 1)) return true
                    usedTargetIndices[idx] = false // Backtrack
                }
            }
        }
        return false
    }

    return matchDfs(0)
}
