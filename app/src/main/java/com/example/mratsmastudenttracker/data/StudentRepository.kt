package com.example.mratsmastudenttracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class StudentRepository(
    private val labelDao: LabelDao,
    private val studentDao: StudentDao,
    private val sessionDao: SessionDao,
    private val attendanceDao: AttendanceDao
) {
    // Categories & Labels
    val allCategoriesWithLabels: Flow<List<LabelCategoryWithLabels>> = labelDao.getAllCategoriesWithLabels()
    val allCategories: Flow<List<LabelCategory>> = labelDao.getAllCategories()
    val allLabels: Flow<List<Label>> = labelDao.getAllLabels()

    suspend fun getCategoryById(id: Long) = labelDao.getCategoryById(id)
    suspend fun getCategoryByName(name: String) = labelDao.getCategoryByName(name)
    suspend fun insertCategory(category: LabelCategory): Long = labelDao.insertCategory(category)
    suspend fun updateCategory(category: LabelCategory) = labelDao.updateCategory(category)
    suspend fun deleteCategory(category: LabelCategory) = labelDao.deleteCategory(category)

    fun getLabelsForCategory(categoryId: Long): Flow<List<Label>> = labelDao.getLabelsForCategory(categoryId)
    suspend fun getLabelById(id: Long) = labelDao.getLabelById(id)
    suspend fun getLabelByNameAndCategory(name: String, categoryId: Long) = labelDao.getLabelByNameAndCategory(name, categoryId)
    suspend fun insertLabel(label: Label): Long = labelDao.insertLabel(label)
    suspend fun updateLabel(label: Label) = labelDao.updateLabel(label)
    suspend fun deleteLabel(label: Label) = labelDao.deleteLabel(label)

    // Student Labels
    suspend fun setStudentLabels(studentId: String, labelIds: List<Long>) {
        labelDao.deleteStudentLabels(studentId)
        labelIds.forEach { labelId ->
            labelDao.insertStudentLabelCrossRef(StudentLabelCrossRef(studentId, labelId))
        }
    }

    fun getLabelsForStudent(studentId: String): Flow<List<Label>> = labelDao.getLabelsForStudent(studentId)
    suspend fun getLabelsForStudentSync(studentId: String): List<Label> = labelDao.getLabelsForStudentSync(studentId)

    // Students
    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()
    val allStudentsWithLabels: Flow<List<StudentWithLabels>> = labelDao.getAllStudentsWithLabels()

    suspend fun getStudentById(id: String) = studentDao.getStudentById(id)
    suspend fun getStudentWithLabelsById(id: String) = labelDao.getStudentWithLabelsById(id)

    suspend fun insertStudent(student: Student, labelIds: List<Long> = emptyList()) {
        studentDao.insertStudent(student)
        setStudentLabels(student.id, labelIds)
    }

    suspend fun updateStudent(student: Student, labelIds: List<Long>? = null) {
        studentDao.updateStudent(student)
        if (labelIds != null) {
            setStudentLabels(student.id, labelIds)
        }
    }

    suspend fun deleteStudent(student: Student) {
        labelDao.deleteStudentLabels(student.id)
        studentDao.deleteStudent(student)
    }

    // Sessions
    val allSessions: Flow<List<Session>> = sessionDao.getAllSessions()

    suspend fun getSessionById(id: Long) = sessionDao.getSessionById(id)
    suspend fun insertSession(session: Session) = sessionDao.insertSession(session)
    suspend fun updateSession(session: Session) = sessionDao.updateSession(session)
    suspend fun deleteSession(session: Session) = sessionDao.deleteSession(session)

    // Attendance
    val allAttendance: Flow<List<Attendance>> = attendanceDao.getAllAttendance()

    suspend fun getAllAttendanceSync(): List<Attendance> = attendanceDao.getAllAttendanceSync()

    fun getAttendanceForSession(sessionId: Long): Flow<List<Attendance>> =
        attendanceDao.getAttendanceForSession(sessionId)

    suspend fun markAttendance(sessionId: Long, studentId: String, timestamp: Long? = null) {
        attendanceDao.insertAttendance(Attendance(sessionId, studentId, timestamp ?: System.currentTimeMillis()))
    }

    suspend fun deleteAttendance(sessionId: Long, studentId: String) {
        attendanceDao.deleteAttendance(sessionId, studentId)
    }

    fun getStudentAttendanceForSession(
        sessionId: Long,
        targetLabelIds: List<Long>,
        labelMatchMode: LabelMatchMode = LabelMatchMode.ALL_CATEGORIES
    ): Flow<List<StudentAttendanceStatusResult>> {
        return combine(
            labelDao.getAllStudentsWithLabels(),
            labelDao.getAllLabels(),
            attendanceDao.getAttendanceForSession(sessionId)
        ) { studentsWithLabels, allLabels, attendances ->
            val attendanceMap = attendances.associateBy { it.studentId }
            val filtered = if (targetLabelIds.isEmpty()) {
                studentsWithLabels
            } else {
                val labelCategoryMap = allLabels
                    .filter { it.id in targetLabelIds }
                    .groupBy { it.categoryId }

                studentsWithLabels.filter { swl ->
                    val studentLabelIds = swl.labels.map { it.id }.toSet()
                    if (labelMatchMode == LabelMatchMode.ALL_CATEGORIES) {
                        labelCategoryMap.values.all { catLabels ->
                            catLabels.any { it.id in studentLabelIds }
                        }
                    } else {
                        labelCategoryMap.values.any { catLabels ->
                            catLabels.any { it.id in studentLabelIds }
                        }
                    }
                }
            }
            filtered.map { swl ->
                val att = attendanceMap[swl.student.id]
                StudentAttendanceStatusResult(
                    student = swl.student,
                    labels = swl.labels,
                    sessionId = sessionId,
                    isPresent = att != null,
                    timestamp = att?.timestamp
                )
            }
        }
    }
}
