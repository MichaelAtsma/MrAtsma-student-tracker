package com.example.mratsmastudenttracker.ui

import android.R.id.bold
import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mratsmastudenttracker.data.*
import com.example.mratsmastudenttracker.ui.utils.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

sealed class StudentEvent {
    data class ShowSnackbar(val message: String) : StudentEvent()
}

class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = StudentRepository(
        database.classRoomDao(),
        database.studentDao(),
        database.sessionDao(),
        database.attendanceDao()
    )

    val allClasses = repository.allClasses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allStudents = repository.allStudents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allSessions = repository.allSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAttendance = repository.allAttendance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedClassId = MutableStateFlow<Long?>(null)
    val selectedClassId = _selectedClassId.asStateFlow()

    private val _events = MutableSharedFlow<StudentEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val filteredStudents = combine(allStudents, selectedClassId) { students, classId ->
        if (classId == null || classId == -1L) {
            students.filter { it.classId == null || it.classId == -1L }
        } else {
            students.filter { it.classId == classId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectClass(classId: Long?) {
        _selectedClassId.value = classId
    }

    // Classes
    fun addClass(name: String) {
        viewModelScope.launch {
            repository.insertClass(ClassRoom(name = name))
        }
    }

    fun deleteClass(classRoom: ClassRoom) {
        viewModelScope.launch {
            repository.deleteClass(classRoom)
        }
    }

    fun getClassById(classId: Long): Flow<ClassRoom?> = flow {
        emit(repository.getClassById(classId))
    }

    // Students
    fun addStudent(firstName: String, lastName: String, classId: Long?, picturePath: String?) {
        viewModelScope.launch {
            var customId = UUID.randomUUID().toString()
            while (repository.getStudentById(customId) != null) {
                customId = UUID.randomUUID().toString()
            }
            repository.insertStudent(Student(customId, firstName, lastName, classId, picturePath))
        }
    }

    fun getStudentById(id: String): Flow<Student?> = flow {
        emit(repository.getStudentById(id))
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }

    // Sessions
    fun addSession(title: String? = null, date: Long, startTime: Long, endTime: Long, targetClassIds: List<Long>) {
        viewModelScope.launch {
            repository.insertSession(Session(title = title, date = date, startTime = startTime, endTime = endTime, targetClassIds = targetClassIds))
        }
    }

    fun updateSession(session: Session) {
        viewModelScope.launch {
            repository.updateSession(session)
        }
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun getSessionById(id: Long): Flow<Session?> = flow {
        emit(repository.getSessionById(id))
    }

    fun getStudentAttendanceForSession(sessionId: Long, classIds: List<Long>) =
        repository.getStudentAttendanceForSession(sessionId, classIds)

    /**
     * Checks if a student is late. This is a pure function for performance.
     */
    fun isStudentLate(attendanceStatusResult: StudentAttendanceStatusResult, sessionEndTime: Long): Boolean {
        return attendanceStatusResult.isPresent &&
               attendanceStatusResult.timestamp != null &&
               attendanceStatusResult.timestamp > sessionEndTime
    }

    fun markAttendance(sessionId: Long, studentId: String) {
        viewModelScope.launch {
            repository.markAttendance(sessionId, studentId)
        }
    }

    fun deleteAttendance(sessionId: Long, studentId: String) {
        viewModelScope.launch {
            repository.deleteAttendance(sessionId, studentId)
        }
    }

    suspend fun getAllAttendances() : List<Attendance> {
        return repository.getAllAttendanceSync()
    }


    // QR Export
    fun exportQrCodesForClass(classId: Long?) {
        viewModelScope.launch {
            val classObj =
                if (classId == -1L || classId == null) null else repository.getClassById(classId)
            val className = classObj?.name ?: "NoClass"

            val students = allStudents.value.filter {
                if (classId == -1L || classId == null) it.classId == null || it.classId == -1L
                else it.classId == classId
            }

            if (students.isEmpty()) {
                _events.emit(StudentEvent.ShowSnackbar("No students in this class to export."))
                return@launch
            }

            exportQrCodesForStudents(students = students, prefix = className)
        }
    }

    fun exportQrCodesForStudents(
        students: List<Student>,
        prefix: String? = null,
        suffix: String? = null
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                students.forEach { student ->
                    val logo = student.picturePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) BitmapFactory.decodeFile(path) else null
                    }
                    val qrBitmap = QrCodeGenerator.generateQrCodeWithLogo(student.id, logo = logo)
                    val fileName = sanitizeFileName("${if (prefix != null) "${prefix}_" else ""}${student.lastName}_${student.firstName}_${student.id.take(8)}${if (suffix != null) "_${prefix}" else ""}")

                    saveImageToPublicStorage(qrBitmap, "$fileName.png")
                }
            }
            _events.emit(StudentEvent.ShowSnackbar("Exported ${students.size} QR codes to Pictures."))
        }
    }

    private fun sanitizeFileName(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents
            .replace("'", "")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(" ", "-")
    }

    private fun saveImageToPublicStorage(bitmap: Bitmap, fileName: String) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MrAtsmaStudentTracker")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val imageUri: Uri? = resolver.insert(imageCollection, contentValues)
            imageUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateQrBitmap(student: Student, onResult: (Bitmap) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val logo = student.picturePath?.let { path ->
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(path) else null
            }
            val bitmap = QrCodeGenerator.generateQrCodeWithLogo(student.id, logo = logo)
            withContext(Dispatchers.Main) {
                onResult(bitmap)
            }
        }
    }

    // JSON Export/Import
    fun exportDatabaseToJson() {
        viewModelScope.launch {
            val students = allStudents.value
            val classes = allClasses.value
            val jsonArray = JSONArray()

            withContext(Dispatchers.IO) {
                students.forEach { student ->
                    val studentJson = JSONObject()
                    studentJson.put("id", student.id)
                    studentJson.put("firstName", student.firstName)
                    studentJson.put("lastName", student.lastName)

                    val className = classes.find { it.id == student.classId }?.name ?: "No Class"
                    studentJson.put("className", className)

                    val imageBase64 = student.picturePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(path)
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                        } else null
                    }
                    studentJson.put("image", imageBase64 ?: "")

                    jsonArray.put(studentJson)
                }

                saveJsonToPublicStorage(jsonArray.toString(), "MrAtsmaStudents_Export_${System.currentTimeMillis()}.json")
            }

            _events.emit(StudentEvent.ShowSnackbar("Database exported to Downloads."))
        }
    }

    private suspend fun saveJsonToPublicStorage(jsonString: String, fileName: String) {
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/MrAtsmaStudentTracker")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                return@withContext
            }

            try {
                val uri = resolver.insert(collection, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        output.write(jsonString.toByteArray())
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importDatabaseFromJson(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }
                }

                if (jsonString == null) {
                    _events.emit(StudentEvent.ShowSnackbar("Failed to read file."))
                    return@launch
                }

                val jsonArray = JSONArray(jsonString)
                var importedCount = 0

                withContext(Dispatchers.IO) {
                    for (i in 0 until jsonArray.length()) {
                        val studentJson = jsonArray.getJSONObject(i)
                        val id = studentJson.getString("id")
                        val firstName = studentJson.getString("firstName")
                        val lastName = studentJson.getString("lastName")
                        val className = studentJson.getString("className")
                        val imageBase64 = studentJson.optString("image", "")

                        // Check if exists
                        val existingStudent = repository.getStudentById(id)
                        val existingClass = if (className != "No Class") repository.getClassByName(className) else null

                        if (existingStudent != null &&
                            existingStudent.firstName == firstName &&
                            existingStudent.lastName == lastName &&
                            ((existingClass == null && existingStudent.classId == null) ||
                             (existingClass != null && existingStudent.classId == existingClass.id))) {
                            continue // Exact match exists
                        }

                        // Handle Class
                        var targetClassId: Long? = null
                        if (className != "No Class") {
                            val foundClass = repository.getClassByName(className)
                            targetClassId = foundClass?.id ?: repository.insertClass(ClassRoom(name = className))
                        }

                        // Handle Image
                        var picturePath: String? = null
                        if (imageBase64.isNotEmpty()) {
                            val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            val file = File(context.filesDir, "student_${System.currentTimeMillis()}_$i.jpg")
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            picturePath = file.absolutePath
                        }

                        repository.insertStudent(Student(id, firstName, lastName, targetClassId, picturePath))
                        importedCount++
                    }
                }
                _events.emit(StudentEvent.ShowSnackbar("Imported $importedCount students."))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(StudentEvent.ShowSnackbar("Failed to import: ${e.message}"))
            }
        }
    }

    fun exportSessionsToExcel() {
        viewModelScope.launch {
            val students = allStudents.first()
            val classes = allClasses.first()
            val sessions = allSessions.first().sortedBy { it.startTime }
            val attendances = getAllAttendances()
            val columnLengths: MutableList<Int> = MutableList(sessions.size + 3) { 5 }

            if (students.isEmpty() || sessions.isEmpty()) {
                _events.emit(StudentEvent.ShowSnackbar("No data to export."))
                return@launch
            }

            withContext(Dispatchers.IO) {
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Attendance Report")

                // Styles
                val boldFont = workbook.createFont()
                boldFont.bold = true
                val boldStyle = workbook.createCellStyle()
                boldStyle.setFont(boldFont)
                val yellowStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.YELLOW.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }
                val orangeStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.ORANGE.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }
                val greenStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.LIGHT_GREEN.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }
                val redStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.RED.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }

                val dateFormatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

                // 1. Sort students
                val sortedStudents = students.sortedWith(compareBy(
                    { student -> classes.find { it.id == student.classId }?.name?.lowercase() ?: "" },
                    { it.lastName.lowercase() },
                    { it.firstName.lowercase() }
                ))

                // 2. Prepare headers (Sessions in first 4 rows)
                val row0 = sheet.createRow(0)
                val row1 = sheet.createRow(1)
                val row2 = sheet.createRow(2)
                val row3 = sheet.createRow(3)

                // Labels for student info columns in row 3
                row3.createCell(0).setCellValue("Class")
                row3.createCell(1).setCellValue("Last Name")
                row3.createCell(2).setCellValue("First Name")

                sessions.forEachIndexed { sIdx, session ->
                    val col = sIdx + 3
                    row0.createCell(col).setCellValue(session.title ?: "No Title")
                    row1.createCell(col).setCellValue(dateFormatter.format(Date(session.date)))
                    row2.createCell(col).setCellValue(timeFormatter.format(Date(session.startTime)))
                    row3.createCell(col).setCellValue(timeFormatter.format(Date(session.endTime)))
                }

                for (row in 0 until 4) {
                    for (col in 0 until (sessions.size + 3)) {
                        sheet.getRow(row)?.getCell(col)?.cellStyle = boldStyle
                    }
                }

                // 3. Fill Student Data and Attendance
                val attendanceMap = attendances.associateBy { it.sessionId to it.studentId }

                sortedStudents.forEachIndexed { stIdx, student ->
                    val row = sheet.createRow(stIdx + 4)
                    val className = classes.find { it.id == student.classId }?.name ?: "-"
                    row.createCell(0).setCellValue(className)
                    row.createCell(1).setCellValue(student.lastName)
                    row.createCell(2).setCellValue(student.firstName)

                    sessions.forEachIndexed { sIdx, session ->
                        val col = sIdx + 3
                        val cell = row.createCell(col)
                        val att = attendanceMap[session.id to student.id]
//                        val att = attendances.find { it.sessionId == session.id && it.studentId == student.id }

                        if (student.classId != null && session.targetClassIds.contains(student.classId)) {
//                            cell.setCellValue(attendances.size.toString())
                            if (att != null) {
                                if (att.timestamp > session.endTime) {
                                    val x = (att.timestamp - session.endTime) / 60000
                                    cell.setCellValue("LATE ($x mins)")
                                    if (x <= 5) {
                                        cell.setCellStyle(yellowStyle)
                                    } else {
                                        cell.setCellStyle(orangeStyle)
                                    }
                                } else {
                                    cell.setCellValue("ON_TIME")
                                    cell.setCellStyle(greenStyle)
                                }
                            } else {
                                cell.setCellValue("ABSENT")
                                cell.setCellStyle(redStyle)
                            }
                        } else {
                            cell.setCellValue("-")
                        }
                    }
                }

                val formatter = DataFormatter()

                // Set column widths as autoSizeColumn doesn't work because it depends on java.awt.font.FontRenderContext
                for (i in 0 until (sessions.size + 3)) {
                    for (j in 0 until students.size + 4) {
                        val cell = sheet.getRow(j)?.getCell(i)
                        val stringCellValue = formatter.formatCellValue(cell)
                        if (stringCellValue.length > columnLengths[i]) {
                            columnLengths[i] = stringCellValue.length
                        }
                    }
                    sheet.setColumnWidth(i, 256 * columnLengths[i])
                }

                saveExcelToPublicStorage(workbook, "Attendance_Report_${System.currentTimeMillis()}.xlsx")
                workbook.close()
            }
            _events.emit(StudentEvent.ShowSnackbar("Excel report exported to Downloads."))
        }
    }

    private fun saveExcelToPublicStorage(workbook: XSSFWorkbook, fileName: String) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/MrAtsmaStudentTracker")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            return
        }

        try {
            val uri = resolver.insert(collection, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { output ->
                    workbook.write(output)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
