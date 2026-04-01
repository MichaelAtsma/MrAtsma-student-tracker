package com.example.mratsmastudenttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassRoom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = ClassRoom::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("classId")]
)
data class Student(
    @PrimaryKey val id: String, // Unique QR code ID
    val firstName: String,
    val lastName: String,
    val classId: Long?,
    val picturePath: String? = null
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String?,
    val date: Long,
    val startTime: Long,
    val endTime: Long,
    val targetClassIds: List<Long>
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

data class StudentAttendanceStatus(
    val student: Student,
    val classRoom: ClassRoom?,
    val isPresent: Boolean,
    val timestamp: Long?
)
