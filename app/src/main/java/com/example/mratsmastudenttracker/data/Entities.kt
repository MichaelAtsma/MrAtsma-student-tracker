package com.example.mratsmastudenttracker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class LabelMatchMode(val displayName: String, val description: String) {
    ALL_CATEGORIES("Match ALL selected categories", "Student must match at least one label in ALL selected categories (e.g. Boy AND 4A)"),
    ANY_CATEGORY("Match ANY selected category", "Student must match at least one label in ANY selected category (e.g. Boy OR 4C)")
}

@Entity(tableName = "label_categories")
data class LabelCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "labels",
    foreignKeys = [
        ForeignKey(
            entity = LabelCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String
)

@Entity(
    tableName = "student_label_cross_ref",
    primaryKeys = ["studentId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Label::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId"), Index("labelId")]
)
data class StudentLabelCrossRef(
    val studentId: String,
    val labelId: Long
)

@Entity(tableName = "students")
data class Student(
    @PrimaryKey val id: String, // Unique QR code ID
    val firstName: String,
    val lastName: String,
    val picturePath: String? = null
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String?,
    val date: Long,
    val startTime: Long,
    val endTime: Long,
    val targetLabelIds: List<Long>,
    val labelMatchMode: LabelMatchMode = LabelMatchMode.ALL_CATEGORIES
)

@Entity(
    tableName = "attendance",
    primaryKeys = ["sessionId", "studentId"],
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("studentId")]
)
data class Attendance(
    val sessionId: Long,
    val studentId: String,
    val timestamp: Long
)

data class LabelCategoryWithLabels(
    @Embedded val category: LabelCategory,
    @Relation(
        parentColumn = "id",
        entityColumn = "categoryId"
    )
    val labels: List<Label>
)

data class StudentWithLabels(
    @Embedded val student: Student,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = StudentLabelCrossRef::class,
            parentColumn = "studentId",
            entityColumn = "labelId"
        )
    )
    val labels: List<Label>
)

data class StudentAttendanceStatus(
    val student: Student,
    val labels: List<Label>,
    val isPresent: Boolean,
    val timestamp: Long?
)
