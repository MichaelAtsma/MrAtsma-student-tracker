package com.example.mratsmastudenttracker.data

import kotlinx.coroutines.flow.Flow

class StudentRepository(
    private val classRoomDao: ClassRoomDao,
    private val studentDao: StudentDao,
    private val sessionDao: SessionDao,
    private val attendanceDao: AttendanceDao
) {
    // Classes
    val allClasses: Flow<List<ClassRoom>> = classRoomDao.getAllClasses()
    
    suspend fun getClassById(id: Long?) = classRoomDao.getClassById(id)
    suspend fun getClassByName(name: String) = classRoomDao.getClassByName(name)
    suspend fun insertClass(classRoom: ClassRoom) = classRoomDao.insertClass(classRoom)
    suspend fun updateClass(classRoom: ClassRoom) = classRoomDao.updateClass(classRoom)
    suspend fun deleteClass(classRoom: ClassRoom) = classRoomDao.deleteClass(classRoom)

    // Students
    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()
    
    fun getStudentsByClass(classId: Long?): Flow<List<Student>> =
        if (classId != null && classId != -1L) {
            studentDao.getStudentsByClass(classId)
        } else {
            studentDao.getStudentsWithoutClass()
        }

    suspend fun getStudentById(id: String) = studentDao.getStudentById(id)
    suspend fun insertStudent(student: Student) = studentDao.insertStudent(student)
    suspend fun updateStudent(student: Student) = studentDao.updateStudent(student)
    suspend fun deleteStudent(student: Student) = studentDao.deleteStudent(student)

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

    fun getStudentAttendanceForSession(sessionId: Long, classIds: List<Long>): Flow<List<StudentAttendanceStatusResult>> =
        attendanceDao.getStudentAttendanceForSession(sessionId, classIds)
}
