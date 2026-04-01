package com.example.mratsmastudenttracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassRoomDao {
    @Query("SELECT * FROM classes ORDER BY name ASC")
    fun getAllClasses(): Flow<List<ClassRoom>>

    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun getClassById(id: Long?): ClassRoom?

    @Query("SELECT * FROM classes WHERE name = :name")
    suspend fun getClassByName(name: String): ClassRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(classRoom: ClassRoom): Long

    @Update
    suspend fun updateClass(classRoom: ClassRoom)

    @Delete
    suspend fun deleteClass(classRoom: ClassRoom)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY LOWER(lastName) ASC, LOWER(firstName) ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY LOWER(lastName) ASC, LOWER(firstName) ASC")
    fun getStudentsByClass(classId: Long): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE classId = NULL ORDER BY LOWER(lastName) ASC, LOWER(firstName) ASC")
    fun getStudentsWithoutClass(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getStudentById(id: String): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student)

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY date DESC, startTime DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance")
    fun getAllAttendance(): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance")
    suspend fun getAllAttendanceSync(): List<Attendance>

    @Query("SELECT * FROM attendance WHERE sessionId = :sessionId")
    fun getAttendanceForSession(sessionId: Long): Flow<List<Attendance>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttendance(attendance: Attendance)

    @Query("DELETE FROM attendance WHERE sessionId = :sessionId AND studentId = :studentId")
    suspend fun deleteAttendance(sessionId: Long, studentId: String)

    @Transaction
    @Query("""
        SELECT s.*, :sessionId as sessionId, c.name as className, 
               (SELECT COUNT(*) FROM attendance a WHERE a.sessionId = :sessionId AND a.studentId = s.id) > 0 as isPresent,
               (SELECT timestamp FROM attendance a WHERE a.sessionId = :sessionId AND a.studentId = s.id LIMIT 1) as timestamp
        FROM students s
        LEFT JOIN classes c ON s.classId = c.id
        WHERE s.classId IN (:classIds)
        ORDER BY LOWER(c.name) ASC, LOWER(s.lastName) ASC, LOWER(s.firstName) ASC
    """)
    fun getStudentAttendanceForSession(sessionId: Long, classIds: List<Long>): Flow<List<StudentAttendanceStatusResult>>
}

data class StudentAttendanceStatusResult(
    @Embedded val student: Student,
    val sessionId: Long,
    val className: String?,
    val isPresent: Boolean,
    val timestamp: Long?
)
