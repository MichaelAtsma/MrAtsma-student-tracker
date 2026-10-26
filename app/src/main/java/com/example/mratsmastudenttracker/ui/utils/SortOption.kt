package com.example.mratsmastudenttracker.ui.utils

data class SortOption(
    val key: String,
    val displayName: String,
    val categoryId: Long? = null,
    val studentField: StudentSortField? = null
)

enum class StudentSortField {
    LAST_NAME, FIRST_NAME
}
