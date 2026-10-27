package com.example.mratsmastudenttracker.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mratsmastudenttracker.data.*
import com.example.mratsmastudenttracker.ui.utils.QrCodeGenerator
import com.example.mratsmastudenttracker.ui.utils.SortOption
import com.example.mratsmastudenttracker.ui.utils.StudentSortField
import com.example.mratsmastudenttracker.ui.utils.XlsxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.zip.ZipInputStream
import java.text.SimpleDateFormat
import java.util.*

sealed class StudentEvent {
    data class ShowSnackbar(val message: String) : StudentEvent()
}

enum class ColumnType(val displayName: String) {
    IGNORE("Ignore"),
    FIRST_NAME("First Name"),
    LAST_NAME("Last Name"),
    STUDENT_ID("Student ID"),
    IMAGE_BASE64("Image (Base64)"),
    LABEL_CATEGORY("Label / Category")
}

data class FilePreview(
    val uri: Uri,
    val fileName: String,
    val totalColumns: Int,
    val previewRows: List<List<String>>
)

data class ColumnMappingConfig(
    val columnIndex: Int,
    val columnType: ColumnType,
    val customCategoryName: String = ""
)

enum class ImportMode(val displayName: String, val description: String) {
    REPLACE("Replace", "Delete all existing database data before importing"),
    MERGE("Merge", "Combine imported data with existing students and labels")
}

enum class CategoryConflictStrategy(val displayName: String, val description: String) {
    OVERWRITE("Overwrite label", "Replace student's existing label in conflicting category with imported value"),
    KEEP_ORIGINAL("Keep original label", "Keep student's original label in conflicting category"),
    ADD_DUPLICATE_SAME_CATEGORY("Keep both in same category", "Assign imported label alongside existing label under the same category"),
    CREATE_NEW_CATEGORY("Create new category (e.g. 'Category (2)')", "Create a new numbered category for imported labels so old and new categories stay separate")
}

enum class MatchType {
    FULL,
    ONE_CHAR_DIFF,
    HIGH_SIMILARITY,
    MULTIPLE,
    CLOSEST_CONFIRM
}

data class ImageMatchCandidate(
    val student: StudentWithLabels,
    val similarity: Double,
    val distance: Int
)

data class PendingImageMatch(
    val imageFile: File,
    val fileName: String,
    val candidates: List<ImageMatchCandidate>,
    val bestCandidate: ImageMatchCandidate?,
    val matchType: MatchType
)

class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = StudentRepository(
        database.labelDao(),
        database.studentDao(),
        database.sessionDao(),
        database.attendanceDao()
    )

    val allCategoriesWithLabels = repository.allCategoriesWithLabels.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allCategories = repository.allCategories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allLabels = repository.allLabels.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allStudents = repository.allStudents.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allStudentsWithLabels = repository.allStudentsWithLabels.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allSessions = repository.allSessions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allAttendance = repository.allAttendance.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _selectedLabelId = MutableStateFlow<Long?>(null)
    val selectedLabelId = _selectedLabelId.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private val _events = MutableSharedFlow<StudentEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val filteredStudentsWithLabels = combine(
        allStudentsWithLabels, selectedLabelId, selectedCategoryId
    ) { students, labelId, categoryId ->
        when {
            labelId != null && labelId != -1L -> {
                students.filter { swl -> swl.labels.any { it.id == labelId } }
            }
            categoryId != null && categoryId != -1L -> {
                students.filter { swl -> swl.labels.any { it.categoryId == categoryId } }
            }
            labelId == -1L || categoryId == -1L -> {
                students.filter { swl -> swl.labels.isEmpty() }
            }
            else -> students
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectLabel(labelId: Long?) {
        _selectedLabelId.value = labelId
        _selectedCategoryId.value = null
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
        _selectedLabelId.value = null
    }

    // Category CRUD
    fun addCategory(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.insertCategory(LabelCategory(name = name.trim()))
            }
        }
    }

    fun updateCategory(category: LabelCategory) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: LabelCategory) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    // Label CRUD
    fun addLabel(categoryId: Long, name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.insertLabel(Label(categoryId = categoryId, name = name.trim()))
            }
        }
    }

    fun updateLabel(label: Label) {
        viewModelScope.launch {
            repository.updateLabel(label)
        }
    }

    fun deleteLabel(label: Label) {
        viewModelScope.launch {
            repository.deleteLabel(label)
        }
    }

    // Student CRUD
    fun addStudent(firstName: String, lastName: String, labelIds: List<Long>, picturePath: String?) {
        viewModelScope.launch {
            var customId = UUID.randomUUID().toString()
            while (repository.getStudentById(customId) != null) {
                customId = UUID.randomUUID().toString()
            }
            val student = Student(
                id = customId,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                picturePath = picturePath
            )
            repository.insertStudent(student, labelIds)
        }
    }

    fun getStudentById(id: String): Flow<Student?> = flow {
        emit(repository.getStudentById(id))
    }

    fun getStudentWithLabelsById(id: String): Flow<StudentWithLabels?> = flow {
        emit(repository.getStudentWithLabelsById(id))
    }

    fun updateStudent(student: Student, labelIds: List<Long>? = null) {
        viewModelScope.launch {
            repository.updateStudent(student, labelIds)
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }

    // Sessions
    fun addSession(
        title: String? = null,
        date: Long,
        startTime: Long,
        endTime: Long,
        targetLabelIds: List<Long>,
        labelMatchMode: LabelMatchMode = LabelMatchMode.ALL_CATEGORIES
    ) {
        viewModelScope.launch {
            repository.insertSession(
                Session(
                    title = title,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    targetLabelIds = targetLabelIds,
                    labelMatchMode = labelMatchMode
                )
            )
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

    fun getStudentAttendanceForSession(
        sessionId: Long,
        targetLabelIds: List<Long>,
        labelMatchMode: LabelMatchMode = LabelMatchMode.ALL_CATEGORIES
    ) = repository.getStudentAttendanceForSession(sessionId, targetLabelIds, labelMatchMode)

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

    suspend fun getAllAttendances(): List<Attendance> {
        return repository.getAllAttendanceSync()
    }

    // QR Export
    fun exportQrCodesForLabel(labelId: Long?) {
        viewModelScope.launch {
            val labelObj = if (labelId == null || labelId == -1L) null else repository.getLabelById(labelId)
            val prefixName = labelObj?.name ?: "All_Students"

            val studentsWithLabels = allStudentsWithLabels.value
            val targetStudents = if (labelId == null || labelId == -1L) {
                studentsWithLabels.map { it.student }
            } else {
                studentsWithLabels.filter { swl -> swl.labels.any { it.id == labelId } }.map { it.student }
            }

            if (targetStudents.isEmpty()) {
                _events.emit(StudentEvent.ShowSnackbar("No students to export."))
                return@launch
            }

            exportQrCodesForStudents(students = targetStudents, prefix = prefixName)
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
                    val fileName = sanitizeFileName(
                        "${if (prefix != null) "${prefix}_" else ""}${student.lastName}_${student.firstName}_${student.id.take(8)}${if (suffix != null) "_${suffix}" else ""}"
                    )

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
            val studentsWithLabels = allStudentsWithLabels.value
            val categories = allCategories.value
            val categoryMap = categories.associateBy { it.id }
            val jsonArray = JSONArray()

            withContext(Dispatchers.IO) {
                studentsWithLabels.forEach { swl ->
                    val student = swl.student
                    val studentJson = JSONObject()
                    studentJson.put("id", student.id)
                    studentJson.put("firstName", student.firstName)
                    studentJson.put("lastName", student.lastName)

                    val labelsArray = JSONArray()
                    swl.labels.forEach { label ->
                        val labelJson = JSONObject()
                        labelJson.put("labelId", label.id)
                        labelJson.put("labelName", label.name)
                        labelJson.put("categoryId", label.categoryId)
                        labelJson.put("categoryName", categoryMap[label.categoryId]?.name ?: "General")
                        labelsArray.put(labelJson)
                    }
                    studentJson.put("labels", labelsArray)

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

    // Sessions Export/Import
    fun exportSessionsToJson(selectedSessionIds: List<Long>) {
        viewModelScope.launch {
            if (selectedSessionIds.isEmpty()) {
                _events.emit(StudentEvent.ShowSnackbar("No sessions selected to export."))
                return@launch
            }

            withContext(Dispatchers.IO) {
                val sessionsToExport = allSessions.value.filter { it.id in selectedSessionIds }
                if (sessionsToExport.isEmpty()) {
                    _events.emit(StudentEvent.ShowSnackbar("No matching sessions found."))
                    return@withContext
                }

                val attendances = getAllAttendances()
                val attendanceMap = attendances.groupBy { it.sessionId }
                val studentMap = allStudents.value.associateBy { it.id }

                val rootObject = JSONObject()
                rootObject.put("type", "MR_ATSMA_SESSIONS_EXPORT")
                rootObject.put("version", 1)
                rootObject.put("exportedAt", System.currentTimeMillis())

                val sessionsJsonArray = JSONArray()

                sessionsToExport.forEach { session ->
                    val sessionObj = JSONObject()
                    sessionObj.put("id", session.id)
                    sessionObj.put("title", session.title ?: "")
                    sessionObj.put("date", session.date)
                    sessionObj.put("startTime", session.startTime)
                    sessionObj.put("endTime", session.endTime)
                    sessionObj.put("labelMatchMode", session.labelMatchMode.name)

                    val targetLabelIdsArray = JSONArray()
                    session.targetLabelIds.forEach { targetLabelIdsArray.put(it) }
                    sessionObj.put("targetLabelIds", targetLabelIdsArray)

                    val attendanceArray = JSONArray()
                    val sessionAttendances = attendanceMap[session.id] ?: emptyList()
                    sessionAttendances.forEach { att ->
                        val attObj = JSONObject()
                        attObj.put("studentId", att.studentId)
                        attObj.put("timestamp", att.timestamp)
                        val student = studentMap[att.studentId]
                        if (student != null) {
                            attObj.put("firstName", student.firstName)
                            attObj.put("lastName", student.lastName)
                        }
                        attendanceArray.put(attObj)
                    }
                    sessionObj.put("attendance", attendanceArray)

                    sessionsJsonArray.put(sessionObj)
                }

                rootObject.put("sessions", sessionsJsonArray)

                saveJsonToPublicStorage(rootObject.toString(), "MrAtsmaSessions_Export_${System.currentTimeMillis()}.json")
                _events.emit(StudentEvent.ShowSnackbar("${sessionsToExport.size} session(s) exported to Downloads."))
            }
        }
    }

    fun importSessionsFromJson(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            var importedSessionCount = 0
            var importedAttendanceCount = 0

            try {
                withContext(Dispatchers.IO) {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: return@withContext

                    val trimmed = jsonString.trim()
                    val jsonArray = if (trimmed.startsWith("{")) {
                        val rootObj = JSONObject(trimmed)
                        rootObj.optJSONArray("sessions") ?: JSONArray()
                    } else if (trimmed.startsWith("[")) {
                        JSONArray(trimmed)
                    } else {
                        JSONArray()
                    }

                    if (jsonArray.length() == 0) {
                        _events.emit(StudentEvent.ShowSnackbar("No sessions found in JSON file."))
                        return@withContext
                    }

                    for (i in 0 until jsonArray.length()) {
                        val sessionObj = jsonArray.getJSONObject(i)
                        val title = sessionObj.optString("title", "").ifBlank { null }
                        val date = sessionObj.optLong("date", System.currentTimeMillis())
                        val startTime = sessionObj.optLong("startTime", System.currentTimeMillis())
                        val endTime = sessionObj.optLong("endTime", System.currentTimeMillis())
                        val matchModeStr = sessionObj.optString("labelMatchMode", LabelMatchMode.ALL_CATEGORIES.name)
                        val labelMatchMode = try {
                            LabelMatchMode.valueOf(matchModeStr)
                        } catch (_: Exception) {
                            LabelMatchMode.ALL_CATEGORIES
                        }

                        val targetLabelIds = mutableListOf<Long>()
                        val targetLabelIdsArray = sessionObj.optJSONArray("targetLabelIds")
                        if (targetLabelIdsArray != null) {
                            for (k in 0 until targetLabelIdsArray.length()) {
                                targetLabelIds.add(targetLabelIdsArray.getLong(k))
                            }
                        }

                        val newSession = Session(
                            id = 0,
                            title = title,
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            targetLabelIds = targetLabelIds,
                            labelMatchMode = labelMatchMode
                        )

                        val newSessionId = repository.insertSession(newSession)
                        importedSessionCount++

                        val attendanceArray = sessionObj.optJSONArray("attendance")
                        if (attendanceArray != null) {
                            for (j in 0 until attendanceArray.length()) {
                                val attObj = attendanceArray.getJSONObject(j)
                                val studentId = attObj.optString("studentId", "").trim()
                                if (studentId.isBlank()) continue

                                val timestamp = attObj.optLong("timestamp", System.currentTimeMillis())
                                val firstName = attObj.optString("firstName", "").trim()
                                val lastName = attObj.optString("lastName", "").trim()

                                val existingStudent = repository.getStudentById(studentId)
                                if (existingStudent == null) {
                                    repository.insertStudent(
                                        Student(
                                            id = studentId,
                                            firstName = firstName.ifBlank { "Student" },
                                            lastName = lastName.ifBlank { studentId }
                                        )
                                    )
                                }

                                repository.markAttendance(newSessionId, studentId, timestamp)
                                importedAttendanceCount++
                            }
                        }
                    }

                    _events.emit(
                        StudentEvent.ShowSnackbar("Successfully imported $importedSessionCount session(s) and $importedAttendanceCount attendance entry(ies).")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(StudentEvent.ShowSnackbar("Failed to import sessions: ${e.localizedMessage}"))
            }
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

    fun clearDatabase() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                val context = getApplication<Application>()
                val files = context.filesDir.listFiles()
                files?.forEach { file ->
                    if (file.name.startsWith("student_")) {
                        file.delete()
                    }
                }
            }
            _events.emit(StudentEvent.ShowSnackbar("Entire database cleared."))
        }
    }

    fun importDatabaseFromJson(
        uri: Uri,
        importMode: ImportMode = ImportMode.MERGE,
        conflictStrategy: CategoryConflictStrategy = CategoryConflictStrategy.OVERWRITE
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            var importedCount = 0

            try {
                withContext(Dispatchers.IO) {
                    if (importMode == ImportMode.REPLACE) {
                        database.clearAllTables()
                        val files = context.filesDir.listFiles()
                        files?.forEach { file ->
                            if (file.name.startsWith("student_")) file.delete()
                        }
                    }

                    val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: return@withContext

                    val jsonArray = JSONArray(jsonString)

                    val existingCategories = repository.allCategories.first()
                    val existingCategoryNames = existingCategories.map { it.name.lowercase() }.toSet()

                    val categoryCache = mutableMapOf<String, LabelCategory>()
                    val labelCache = mutableMapOf<Pair<Long, String>, Label>()

                    existingCategories.forEach { categoryCache[it.name.lowercase()] = it }

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("id", UUID.randomUUID().toString())
                        val firstName = obj.optString("firstName", "").trim()
                        val lastName = obj.optString("lastName", "").trim()
                        val imageBase64 = obj.optString("image", "").trim()

                        if (firstName.isBlank() && lastName.isBlank()) continue

                        val existingStudent = if (importMode == ImportMode.MERGE) {
                            repository.getStudentWithLabelsById(id)
                                ?: repository.allStudentsWithLabels.first().find {
                                    it.student.firstName.equals(firstName, ignoreCase = true) &&
                                            it.student.lastName.equals(lastName, ignoreCase = true)
                                }
                        } else null

                        val studentLabelIds = mutableListOf<Long>()
                        if (existingStudent != null) {
                            studentLabelIds.addAll(existingStudent.labels.map { it.id })
                        }

                        val labelsJson = obj.optJSONArray("labels")
                        if (labelsJson != null) {
                            for (j in 0 until labelsJson.length()) {
                                val labelObj = labelsJson.getJSONObject(j)
                                val labelName = labelObj.optString("labelName", "").trim()
                                val categoryName = labelObj.optString("categoryName", "General").trim()

                                if (labelName.isNotBlank()) {
                                    val catKey = categoryName.lowercase()
                                    val isConflictingCategory = existingCategoryNames.contains(catKey)

                                    val targetCatName = if (isConflictingCategory && conflictStrategy == CategoryConflictStrategy.CREATE_NEW_CATEGORY) {
                                        generateUniqueCategoryName(categoryName, existingCategoryNames, categoryCache.keys)
                                    } else {
                                        categoryName
                                    }
                                    val targetCatKey = targetCatName.lowercase()

                                    val category = categoryCache.getOrPut(targetCatKey) {
                                        val existing = repository.getCategoryByName(targetCatName)
                                        if (existing != null) existing
                                        else {
                                            val catId = repository.insertCategory(LabelCategory(name = targetCatName))
                                            LabelCategory(id = catId, name = targetCatName)
                                        }
                                    }

                                    val labelKey = category.id to labelName.lowercase()
                                    val label = labelCache.getOrPut(labelKey) {
                                        val existing = repository.getLabelByNameAndCategory(labelName, category.id)
                                        if (existing != null) existing
                                        else {
                                            val labelId = repository.insertLabel(Label(categoryId = category.id, name = labelName))
                                            Label(id = labelId, categoryId = category.id, name = labelName)
                                        }
                                    }

                                    if (existingStudent != null && isConflictingCategory && conflictStrategy != CategoryConflictStrategy.CREATE_NEW_CATEGORY) {
                                        val existingLabelsInCat = existingStudent.labels.filter { it.categoryId == category.id }
                                        if (existingLabelsInCat.isNotEmpty()) {
                                            when (conflictStrategy) {
                                                CategoryConflictStrategy.OVERWRITE -> {
                                                    studentLabelIds.removeAll(existingLabelsInCat.map { it.id })
                                                    studentLabelIds.add(label.id)
                                                }
                                                CategoryConflictStrategy.KEEP_ORIGINAL -> {
                                                    // Keep original label
                                                }
                                                CategoryConflictStrategy.ADD_DUPLICATE_SAME_CATEGORY, CategoryConflictStrategy.CREATE_NEW_CATEGORY -> {
                                                    studentLabelIds.add(label.id)
                                                }
                                            }
                                        } else {
                                            studentLabelIds.add(label.id)
                                        }
                                    } else {
                                        studentLabelIds.add(label.id)
                                    }
                                }
                            }
                        }

                        var picturePath: String? = existingStudent?.student?.picturePath
                        if (imageBase64.isNotBlank()) {
                            try {
                                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                if (bitmap != null) {
                                    val file = File(context.filesDir, "student_${System.currentTimeMillis()}_$i.jpg")
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    picturePath = file.absolutePath
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val studentId = existingStudent?.student?.id ?: id
                        val student = Student(
                            id = studentId,
                            firstName = firstName,
                            lastName = lastName,
                            picturePath = picturePath
                        )
                        repository.insertStudent(student, studentLabelIds.distinct())
                        importedCount++
                    }
                }
                _events.emit(StudentEvent.ShowSnackbar("Successfully imported $importedCount students from JSON."))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(StudentEvent.ShowSnackbar("JSON Import failed: ${e.message}"))
            }
        }
    }

    // --- CSV / Excel Interactive Importer ---

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var fileName = ""
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            fileName = cursor.getString(idx) ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (fileName.isBlank()) {
            fileName = uri.lastPathSegment ?: "import_file.xlsx"
        }
        return fileName
    }

    suspend fun previewImportFile(uri: Uri): FilePreview? {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val fileName = getFileNameFromUri(context, uri)
                val isCsv = fileName.lowercase().endsWith(".csv")
                val rows = mutableListOf<List<String>>()

                if (isCsv) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val reader = BufferedReader(InputStreamReader(stream))
                        var count = 0
                        var line = reader.readLine()
                        while (line != null && count < 10) {
                            rows.add(parseCsvRow(line))
                            count++
                            line = reader.readLine()
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        rows.addAll(XlsxParser.parseXlsx(stream, maxRows = 10))
                    }
                }

                if (rows.isEmpty()) return@withContext null

                val totalCols = rows.maxOf { it.size }
                val normalizedRows = rows.map { row ->
                    if (row.size < totalCols) row + List(totalCols - row.size) { "" } else row
                }

                FilePreview(
                    uri = uri,
                    fileName = fileName,
                    totalColumns = totalCols,
                    previewRows = normalizedRows
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(char)
            }
        }
        result.add(sb.toString().trim())
        return result
    }

    fun importWithMapping(
        filePreview: FilePreview,
        hasHeaderRow: Boolean,
        mappings: List<ColumnMappingConfig>,
        importMode: ImportMode = ImportMode.MERGE,
        conflictStrategy: CategoryConflictStrategy = CategoryConflictStrategy.OVERWRITE
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            var importedCount = 0

            try {
                withContext(Dispatchers.IO) {
                    if (importMode == ImportMode.REPLACE) {
                        database.clearAllTables()
                        val files = context.filesDir.listFiles()
                        files?.forEach { file ->
                            if (file.name.startsWith("student_")) file.delete()
                        }
                    }

                    val allRows = mutableListOf<List<String>>()

                    val isCsv = filePreview.fileName.lowercase().endsWith(".csv")
                    if (isCsv) {
                        context.contentResolver.openInputStream(filePreview.uri)?.use { stream ->
                            val reader = BufferedReader(InputStreamReader(stream))
                            var line = reader.readLine()
                            while (line != null) {
                                allRows.add(parseCsvRow(line))
                                line = reader.readLine()
                            }
                        }
                    } else {
                        context.contentResolver.openInputStream(filePreview.uri)?.use { stream ->
                            allRows.addAll(XlsxParser.parseXlsx(stream))
                        }
                    }

                    if (allRows.isEmpty()) return@withContext

                    val startRowIdx = if (hasHeaderRow) 1 else 0
                    val headerRow = if (hasHeaderRow) allRows.firstOrNull() else null

                    val categoryNameMap = mutableMapOf<Int, String>()
                    mappings.filter { it.columnType == ColumnType.LABEL_CATEGORY }.forEach { mapConfig ->
                        val name = when {
                            mapConfig.customCategoryName.isNotBlank() -> mapConfig.customCategoryName.trim()
                            hasHeaderRow && headerRow != null && mapConfig.columnIndex < headerRow.size ->
                                headerRow[mapConfig.columnIndex].ifBlank { "Category ${mapConfig.columnIndex + 1}" }
                            else -> "Category ${mapConfig.columnIndex + 1}"
                        }
                        categoryNameMap[mapConfig.columnIndex] = name
                    }

                    val existingCategories = repository.allCategories.first()
                    val existingCategoryNames = existingCategories.map { it.name.lowercase() }.toSet()

                    val categoryCache = mutableMapOf<String, LabelCategory>()
                    val labelCache = mutableMapOf<Pair<Long, String>, Label>()

                    existingCategories.forEach { categoryCache[it.name.lowercase()] = it }

                    for (r in startRowIdx until allRows.size) {
                        val row = allRows[r]
                        if (row.all { it.isBlank() }) continue

                        var firstName = ""
                        var lastName = ""
                        var customStudentId = ""
                        var imageBase64 = ""
                        val importedCategoryLabels = mutableListOf<Pair<String, String>>()

                        mappings.forEach { mapConfig ->
                            val colIdx = mapConfig.columnIndex
                            val value = if (colIdx < row.size) row[colIdx].trim() else ""

                            when (mapConfig.columnType) {
                                ColumnType.FIRST_NAME -> firstName = value
                                ColumnType.LAST_NAME -> lastName = value
                                ColumnType.STUDENT_ID -> customStudentId = value
                                ColumnType.IMAGE_BASE64 -> imageBase64 = value
                                ColumnType.LABEL_CATEGORY -> {
                                    if (value.isNotBlank()) {
                                        val catName = categoryNameMap[colIdx] ?: "Category ${colIdx + 1}"
                                        importedCategoryLabels.add(catName to value)
                                    }
                                }
                                ColumnType.IGNORE -> {}
                            }
                        }

                        if (firstName.isBlank() && lastName.isBlank()) continue

                        val existingStudent = if (importMode == ImportMode.MERGE) {
                            if (customStudentId.isNotBlank()) repository.getStudentWithLabelsById(customStudentId)
                            else repository.allStudentsWithLabels.first().find {
                                it.student.firstName.equals(firstName, ignoreCase = true) &&
                                        it.student.lastName.equals(lastName, ignoreCase = true)
                            }
                        } else null

                        val studentLabelIds = mutableListOf<Long>()
                        if (existingStudent != null) {
                            studentLabelIds.addAll(existingStudent.labels.map { it.id })
                        }

                        importedCategoryLabels.forEach { (catName, value) ->
                            val catKey = catName.lowercase()
                            val isConflictingCategory = existingCategoryNames.contains(catKey)

                            val targetCatName = if (isConflictingCategory && conflictStrategy == CategoryConflictStrategy.CREATE_NEW_CATEGORY) {
                                generateUniqueCategoryName(catName, existingCategoryNames, categoryCache.keys)
                            } else {
                                catName
                            }
                            val targetCatKey = targetCatName.lowercase()

                            val category = categoryCache.getOrPut(targetCatKey) {
                                val existing = repository.getCategoryByName(targetCatName)
                                if (existing != null) existing
                                else {
                                    val id = repository.insertCategory(LabelCategory(name = targetCatName))
                                    LabelCategory(id = id, name = targetCatName)
                                }
                            }

                            val labelKey = category.id to value.lowercase()
                            val label = labelCache.getOrPut(labelKey) {
                                val existing = repository.getLabelByNameAndCategory(value, category.id)
                                if (existing != null) existing
                                else {
                                    val id = repository.insertLabel(Label(categoryId = category.id, name = value))
                                    Label(id = id, categoryId = category.id, name = value)
                                }
                            }

                            if (existingStudent != null && isConflictingCategory && conflictStrategy != CategoryConflictStrategy.CREATE_NEW_CATEGORY) {
                                val existingLabelsInCat = existingStudent.labels.filter { it.categoryId == category.id }
                                if (existingLabelsInCat.isNotEmpty()) {
                                    when (conflictStrategy) {
                                        CategoryConflictStrategy.OVERWRITE -> {
                                            studentLabelIds.removeAll(existingLabelsInCat.map { it.id })
                                            studentLabelIds.add(label.id)
                                        }
                                        CategoryConflictStrategy.KEEP_ORIGINAL -> {
                                            // Keep original
                                        }
                                        CategoryConflictStrategy.ADD_DUPLICATE_SAME_CATEGORY -> {
                                            studentLabelIds.add(label.id)
                                        }
                                        CategoryConflictStrategy.CREATE_NEW_CATEGORY -> {
                                            studentLabelIds.add(label.id)
                                        }
                                    }
                                } else {
                                    studentLabelIds.add(label.id)
                                }
                            } else {
                                studentLabelIds.add(label.id)
                            }
                        }

                        var picturePath: String? = existingStudent?.student?.picturePath
                        if (imageBase64.isNotBlank()) {
                            try {
                                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                if (bitmap != null) {
                                    val file = File(context.filesDir, "student_${System.currentTimeMillis()}_$r.jpg")
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    picturePath = file.absolutePath
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val studentId = existingStudent?.student?.id ?: customStudentId.ifBlank { UUID.randomUUID().toString() }

                        val student = Student(
                            id = studentId,
                            firstName = firstName,
                            lastName = lastName,
                            picturePath = picturePath
                        )
                        repository.insertStudent(student, studentLabelIds.distinct())
                        importedCount++
                    }
                }
                _events.emit(StudentEvent.ShowSnackbar("Successfully imported $importedCount students."))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(StudentEvent.ShowSnackbar("Import failed: ${e.message}"))
            }
        }
    }

    private fun isStudentTargetedForSession(
        swl: StudentWithLabels,
        session: Session,
        labels: List<Label>,
        attendanceMap: Map<Pair<Long, String>, Attendance>
    ): Boolean {
        if (attendanceMap.containsKey(session.id to swl.student.id)) return true
        if (session.targetLabelIds.isEmpty()) return true

        val labelCategoryMap = labels.filter { it.id in session.targetLabelIds }.groupBy { it.categoryId }
        val studentLabelIds = swl.labels.map { it.id }.toSet()
        return if (session.labelMatchMode == LabelMatchMode.ALL_CATEGORIES) {
            labelCategoryMap.values.all { catLabels -> catLabels.any { it.id in studentLabelIds } }
        } else {
            labelCategoryMap.values.any { catLabels -> catLabels.any { it.id in studentLabelIds } }
        }
    }

    fun getAvailableSortOptions(categories: List<LabelCategory>): List<SortOption> {
        val list = mutableListOf<SortOption>()
        list.add(SortOption("last_name", "Last Name", studentField = StudentSortField.LAST_NAME))
        list.add(SortOption("first_name", "First Name", studentField = StudentSortField.FIRST_NAME))
        categories.forEach { cat ->
            list.add(SortOption("cat_${cat.id}", cat.name, categoryId = cat.id))
        }
        return list
    }

    fun buildStudentComparator(sortOptions: List<SortOption>): Comparator<StudentWithLabels> {
        var comparator: Comparator<StudentWithLabels>? = null
        for (opt in sortOptions) {
            val nextComp = when {
                opt.studentField == StudentSortField.LAST_NAME -> compareBy<StudentWithLabels> { it.student.lastName.lowercase() }
                opt.studentField == StudentSortField.FIRST_NAME -> compareBy<StudentWithLabels> { it.student.firstName.lowercase() }
                opt.categoryId != null -> compareBy<StudentWithLabels> { swl ->
                    swl.labels.find { it.categoryId == opt.categoryId }?.name?.lowercase() ?: ""
                }
                else -> compareBy { it.student.lastName.lowercase() }
            }
            comparator = if (comparator == null) nextComp else comparator!!.then(nextComp)
        }
        return comparator ?: compareBy { it.student.lastName.lowercase() }
    }

    fun exportSessionsToExcelAdvanced(selectedSessionIds: List<Long>, sortOptions: List<SortOption>) {
        viewModelScope.launch {
            val studentsWithLabels = allStudentsWithLabels.first()
            val sessions = allSessions.first().filter { it.id in selectedSessionIds }.sortedBy { it.startTime }
            val attendances = getAllAttendances()
            val labels = allLabels.first()
            val categories = allCategories.first()

            if (studentsWithLabels.isEmpty() || sessions.isEmpty()) {
                _events.emit(StudentEvent.ShowSnackbar("No sessions or students selected to export."))
                return@launch
            }

            withContext(Dispatchers.IO) {
                val attendanceMap = attendances.associateBy { it.sessionId to it.studentId }

                val relevantStudents = studentsWithLabels.filter { swl ->
                    sessions.any { session -> isStudentTargetedForSession(swl, session, labels, attendanceMap) }
                }

                if (relevantStudents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _events.emit(StudentEvent.ShowSnackbar("No students match the selected sessions."))
                    }
                    return@withContext
                }

                val sortedCategoryIds = sortOptions.mapNotNull { it.categoryId }
                val orderedCategories = sortedCategoryIds.mapNotNull { catId -> categories.find { it.id == catId } } +
                        categories.filter { cat -> cat.id !in sortedCategoryIds }

                val colOffset = 2 + orderedCategories.size
                val totalCols = sessions.size + colOffset

                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Attendance Report")

                val boldFont = workbook.createFont().apply { bold = true }
                val boldStyle = workbook.createCellStyle().apply { setFont(boldFont) }
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

                val finalComparator = buildStudentComparator(sortOptions)
                val sortedStudents = relevantStudents.sortedWith(finalComparator)

                val row0 = sheet.createRow(0)
                val row1 = sheet.createRow(1)
                val row2 = sheet.createRow(2)
                val row3 = sheet.createRow(3)

                row3.createCell(0).setCellValue("Last Name")
                row3.createCell(1).setCellValue("First Name")

                orderedCategories.forEachIndexed { cIdx, cat ->
                    row3.createCell(cIdx + 2).setCellValue(cat.name)
                }

                sessions.forEachIndexed { sIdx, session ->
                    val col = sIdx + colOffset
                    row0.createCell(col).setCellValue(session.title ?: "No Title")
                    row1.createCell(col).setCellValue(dateFormatter.format(Date(session.date)))
                    row2.createCell(col).setCellValue(timeFormatter.format(Date(session.startTime)))
                    row3.createCell(col).setCellValue(timeFormatter.format(Date(session.endTime)))
                }

                for (row in 0 until 4) {
                    for (col in 0 until totalCols) {
                        sheet.getRow(row)?.getCell(col)?.cellStyle = boldStyle
                    }
                }

                sortedStudents.forEachIndexed { stIdx, swl ->
                    val row = sheet.createRow(stIdx + 4)
                    row.createCell(0).setCellValue(swl.student.lastName)
                    row.createCell(1).setCellValue(swl.student.firstName)

                    orderedCategories.forEachIndexed { cIdx, cat ->
                        val labelName = swl.labels.find { it.categoryId == cat.id }?.name ?: "-"
                        row.createCell(cIdx + 2).setCellValue(labelName)
                    }

                    sessions.forEachIndexed { sIdx, session ->
                        val col = sIdx + colOffset
                        val cell = row.createCell(col)
                        val att = attendanceMap[session.id to swl.student.id]
                        val isTargeted = isStudentTargetedForSession(swl, session, labels, attendanceMap)

                        if (isTargeted) {
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

                sheet.setAutoFilter(CellRangeAddress(3, sheet.lastRowNum, 0, categories.size + 2 - 1))

                for (i in 0 until totalCols) {
                    var maxLen = 5
                    for (j in 0 until sortedStudents.size + 4) {
                        val cell = sheet.getRow(j)?.getCell(i)
                        val stringCellValue = formatter.formatCellValue(cell)
                        var cellLength = stringCellValue.length
                        if (j == 3 && i < categories.size + 2) {
                            cellLength += 5  // Account for filter buttons
                        }
                        if (cellLength > maxLen) {
                            maxLen = cellLength
                        }
                    }
                    sheet.setColumnWidth(i, 256 * maxLen)
                }

                saveExcelToPublicStorage(workbook, "Advanced_Attendance_Report_${System.currentTimeMillis()}.xlsx")
                workbook.close()
            }
            _events.emit(StudentEvent.ShowSnackbar("Advanced attendance report exported to Downloads."))
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

    fun exportQrCodesForLabelsAdvanced(targetLabelIds: List<Long>, matchMode: LabelMatchMode) {
        viewModelScope.launch {
            val studentsWithLabels = allStudentsWithLabels.value
            val allLabelsList = allLabels.value

            val targetStudents = if (targetLabelIds.isEmpty()) {
                studentsWithLabels.map { it.student }
            } else {
                val labelCategoryMap = allLabelsList.filter { it.id in targetLabelIds }.groupBy { it.categoryId }
                studentsWithLabels.filter { swl ->
                    val studentLabelIds = swl.labels.map { it.id }.toSet()
                    if (matchMode == LabelMatchMode.ALL_CATEGORIES) {
                        labelCategoryMap.values.all { catLabels -> catLabels.any { it.id in studentLabelIds } }
                    } else {
                        labelCategoryMap.values.any { catLabels -> catLabels.any { it.id in studentLabelIds } }
                    }
                }.map { it.student }
            }

            if (targetStudents.isEmpty()) {
                _events.emit(StudentEvent.ShowSnackbar("No students match the selected label criteria."))
                return@launch
            }

            val prefixName = if (targetLabelIds.size == 1) {
                allLabelsList.find { it.id == targetLabelIds.first() }?.name ?: "Filtered"
            } else "Batch_Export"

            exportQrCodesForStudents(students = targetStudents, prefix = prefixName)
        }
    }

    suspend fun getSessionByIdSync(sessionId: Long): Session? {
        return withContext(Dispatchers.IO) {
            repository.getSessionById(sessionId)
        }
    }

    suspend fun isStudentLateForSession(sessionId: Long, scanTimestamp: Long = System.currentTimeMillis()): Boolean {
        return withContext(Dispatchers.IO) {
            val session = repository.getSessionById(sessionId) ?: return@withContext false
            scanTimestamp > session.endTime
        }
    }

    suspend fun isStudentTargetedForSession(sessionId: Long, studentId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val session = repository.getSessionById(sessionId) ?: return@withContext true
            val swl = repository.getStudentWithLabelsById(studentId) ?: return@withContext false
            val labels = allLabels.value
            val attendances = getAllAttendances()
            val attendanceMap = attendances.associateBy { it.sessionId to it.studentId }
            isStudentTargetedForSession(swl, session, labels, attendanceMap)
        }
    }

    var pendingImageMatches by mutableStateOf(emptyList<PendingImageMatch>())
        private set

    var isImportingImages by mutableStateOf(false)
        private set

    var imageImportProgressMessage by mutableStateOf("Processing images...")
        private set

    fun clearPendingImageMatches() {
        pendingImageMatches = emptyList()
    }

    suspend fun processImageImport(uri: Uri, isZip: Boolean) {
        isImportingImages = true
        imageImportProgressMessage = "Reading and extracting files..."

        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val imageFiles = mutableListOf<File>()
            val tempDir = File(context.cacheDir, "imported_images_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                if (isZip) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val name = File(entry.name).name
                                    val lower = name.lowercase()
                                    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                                        val outFile = File(tempDir, name)
                                        FileOutputStream(outFile).use { fos ->
                                            val buffer = ByteArray(4096)
                                            var len: Int
                                            while (zis.read(buffer).let { len = it; it > 0 }) {
                                                fos.write(buffer, 0, len)
                                            }
                                        }
                                        imageFiles.add(outFile)
                                    }
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } else {
                    val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                    if (docFile != null) {
                        processDocumentFileRecursive(context, docFile, tempDir, imageFiles)
                    }
                }

                if (imageFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isImportingImages = false
                        _events.emit(StudentEvent.ShowSnackbar("No image files found in selection."))
                    }
                    return@withContext
                }

                withContext(Dispatchers.Main) {
                    imageImportProgressMessage = "Matching images against students..."
                }

                val students = allStudentsWithLabels.value
                val pending = mutableListOf<PendingImageMatch>()
                var autoAssignedCount = 0

                imageFiles.forEach { file ->
                    val baseName = file.nameWithoutExtension
                    val cleanFile = sanitizeString(baseName)

                    val candidates = students.map { swl ->
                        val sId = sanitizeString(swl.student.id)
                        val sName1 = sanitizeString(swl.student.firstName + swl.student.lastName)
                        val sName2 = sanitizeString(swl.student.lastName + swl.student.firstName)
                        val sFirst = sanitizeString(swl.student.firstName)
                        val sLast = sanitizeString(swl.student.lastName)

                        val dist = minOf(
                            levenshteinDistance(cleanFile, sId),
                            levenshteinDistance(cleanFile, sName1),
                            levenshteinDistance(cleanFile, sName2),
                            levenshteinDistance(cleanFile, sFirst),
                            levenshteinDistance(cleanFile, sLast)
                        )
                        val sim = maxOf(
                            similarityPercentage(cleanFile, sId),
                            similarityPercentage(cleanFile, sName1),
                            similarityPercentage(cleanFile, sName2)
                        )
                        ImageMatchCandidate(swl, sim, dist)
                    }.sortedByDescending { it.similarity }

                    val best = candidates.firstOrNull()
                    val matchType = when {
                        best == null -> MatchType.CLOSEST_CONFIRM
                        best.distance == 0 -> MatchType.FULL
                        best.distance == 1 && candidates.count { it.distance == 1 } == 1 -> MatchType.ONE_CHAR_DIFF
                        best.similarity >= 0.90 && candidates.count { it.similarity >= 0.90 } == 1 -> MatchType.HIGH_SIMILARITY
                        candidates.count { it.similarity >= 0.80 || it.distance <= 3 } > 1 -> MatchType.MULTIPLE
                        else -> MatchType.CLOSEST_CONFIRM
                    }

                    if (matchType == MatchType.FULL || matchType == MatchType.ONE_CHAR_DIFF) {
                        val student = best!!.student.student
                        val savedFile = File(context.filesDir, "student_${System.currentTimeMillis()}_${student.id}.jpg")
                        file.copyTo(savedFile, overwrite = true)
                        repository.insertStudent(student.copy(picturePath = savedFile.absolutePath), best.student.labels.map { it.id })
                        autoAssignedCount++
                    } else {
                        pending.add(PendingImageMatch(file, baseName, candidates, best, matchType))
                    }
                }

                withContext(Dispatchers.Main) {
                    isImportingImages = false
                    pendingImageMatches = pending
                    _events.emit(StudentEvent.ShowSnackbar("Auto-assigned $autoAssignedCount pictures. ${pending.size} require review."))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isImportingImages = false
                    _events.emit(StudentEvent.ShowSnackbar("Error importing images: ${e.message}"))
                }
            }
        }
    }

    private fun processDocumentFileRecursive(context: Context, docFile: androidx.documentfile.provider.DocumentFile, outputDir: File, imageFiles: MutableList<File>) {
        for (file in docFile.listFiles()) {
            if (file.isDirectory) {
                processDocumentFileRecursive(context, file, outputDir, imageFiles)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val lower = name.lowercase()
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                    val outFile = File(outputDir, name)
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    imageFiles.add(outFile)
                }
            }
        }
    }

    fun assignImageToStudent(pendingMatch: PendingImageMatch, student: StudentWithLabels) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val savedFile = File(context.filesDir, "student_${System.currentTimeMillis()}_${student.student.id}.jpg")
            pendingMatch.imageFile.copyTo(savedFile, overwrite = true)
            repository.insertStudent(student.student.copy(picturePath = savedFile.absolutePath), student.labels.map { it.id })
            pendingImageMatches = pendingImageMatches.filter { it != pendingMatch }
            _events.emit(StudentEvent.ShowSnackbar("Assigned picture to ${student.student.firstName} ${student.student.lastName}"))
        }
    }

    fun skipPendingImage(pendingMatch: PendingImageMatch) {
        pendingImageMatches = pendingImageMatches.filter { it != pendingMatch }
    }

    fun skipAllPendingImages() {
        pendingImageMatches = emptyList()
        viewModelScope.launch {
            _events.emit(StudentEvent.ShowSnackbar("Skipped remaining unmatched images."))
        }
    }

    private fun generateUniqueCategoryName(
        baseName: String,
        existingNames: Set<String>,
        cacheNames: Set<String>
    ): String {
        var index = 2
        var candidate = "$baseName ($index)"
        while (existingNames.contains(candidate.lowercase()) || cacheNames.contains(candidate.lowercase())) {
            index++
            candidate = "$baseName ($index)"
        }
        return candidate
    }

    private fun sanitizeString(str: String): String {
        val normalized = Normalizer.normalize(str, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val memo = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) memo[i][0] = i
        for (j in 0..s2.length) memo[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                memo[i][j] = minOf(
                    memo[i - 1][j] + 1,
                    memo[i][j - 1] + 1,
                    memo[i - 1][j - 1] + cost
                )
            }
        }
        return memo[s1.length][s2.length]
    }

    private fun similarityPercentage(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        val maxLen = maxOf(s1.length, s2.length).toDouble()
        if (maxLen == 0.0) return 1.0
        val dist = levenshteinDistance(s1, s2).toDouble()
        return (maxLen - dist) / maxLen
    }
}
