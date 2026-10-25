package com.example.mratsmastudenttracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    // Categories
    @Query("SELECT * FROM label_categories ORDER BY LOWER(name) ASC")
    fun getAllCategories(): Flow<List<LabelCategory>>

    @Transaction
    @Query("SELECT * FROM label_categories ORDER BY LOWER(name) ASC")
    fun getAllCategoriesWithLabels(): Flow<List<LabelCategoryWithLabels>>

    @Query("SELECT * FROM label_categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): LabelCategory?

    @Query("SELECT * FROM label_categories WHERE LOWER(name) = LOWER(:name)")
    suspend fun getCategoryByName(name: String): LabelCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: LabelCategory): Long

    @Update
    suspend fun updateCategory(category: LabelCategory)

    @Delete
    suspend fun deleteCategory(category: LabelCategory)

    // Labels
    @Query("SELECT * FROM labels ORDER BY LOWER(name) ASC")
    fun getAllLabels(): Flow<List<Label>>

    @Query("SELECT * FROM labels WHERE categoryId = :categoryId ORDER BY LOWER(name) ASC")
    fun getLabelsForCategory(categoryId: Long): Flow<List<Label>>

    @Query("SELECT * FROM labels WHERE id = :id")
    suspend fun getLabelById(id: Long): Label?

    @Query("SELECT * FROM labels WHERE categoryId = :categoryId AND LOWER(name) = LOWER(:name)")
    suspend fun getLabelByNameAndCategory(name: String, categoryId: Long): Label?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: Label): Long

    @Update
    suspend fun updateLabel(label: Label)

    @Delete
    suspend fun deleteLabel(label: Label)

    // Student-Label Junction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentLabelCrossRef(crossRef: StudentLabelCrossRef)

    @Query("DELETE FROM student_label_cross_ref WHERE studentId = :studentId AND labelId = :labelId")
    suspend fun deleteStudentLabelCrossRef(studentId: String, labelId: Long)

    @Query("DELETE FROM student_label_cross_ref WHERE studentId = :studentId")
    suspend fun deleteStudentLabels(studentId: String)

    @Query("SELECT l.* FROM labels l INNER JOIN student_label_cross_ref ref ON l.id = ref.labelId WHERE ref.studentId = :studentId")
    fun getLabelsForStudent(studentId: String): Flow<List<Label>>

    @Query("SELECT l.* FROM labels l INNER JOIN student_label_cross_ref ref ON l.id = ref.labelId WHERE ref.studentId = :studentId")
    suspend fun getLabelsForStudentSync(studentId: String): List<Label>

    @Transaction
    @Query("SELECT * FROM students ORDER BY LOWER(lastName) ASC, LOWER(firstName) ASC")
    fun getAllStudentsWithLabels(): Flow<List<StudentWithLabels>>

    @Transaction
    @Query("SELECT * FROM students WHERE id = :studentId")
    suspend fun getStudentWithLabelsById(studentId: String): StudentWithLabels?
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY LOWER(lastName) ASC, LOWER(firstName) ASC")
    fun getAllStudents(): Flow<List<Student>>

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
}

data class StudentAttendanceStatusResult(
    val student: Student,
    val labels: List<Label>,
    val sessionId: Long,
    val isPresent: Boolean,
    val timestamp: Long?
)
