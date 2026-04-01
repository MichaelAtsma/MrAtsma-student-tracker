package com.example.mratsmastudenttracker.ui

import android.R.attr.onClick
import android.R.attr.text
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.mratsmastudenttracker.data.Student
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementScreen(
    viewModel: StudentViewModel,
    classId: Long?
) {
    val students by viewModel.filteredStudents.collectAsState()
    val classes by viewModel.allClasses.collectAsState()
    val className = if (classId == -1L || classId == null) "Students without Class" else classes.find { it.id == classId }?.name ?: "Students"

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedStudentForEdit by remember { mutableStateOf<Student?>(null) }
    var selectedStudentForQr by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(classId) {
        viewModel.selectClass(if (classId == -1L) null else classId)
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is StudentEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(className) },
                actions = {
                    IconButton(
                        onClick = { editing = !editing },
                    ) {
                        Icon(
                            imageVector = if (!editing) {Icons.Default.Edit} else {Icons.Default.EditOff},
                            contentDescription = if (!editing) {"Edit Students"} else {"Stop editing Students"}
                        )
                    }
                    IconButton(onClick = { viewModel.exportQrCodesForClass(classId) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCode, contentDescription = "Export All QRs")
                            Icon(Icons.Default.Download, contentDescription = "Export All QRs")
                        }
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Student")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(students) { student ->
                StudentItem(
                    student = student,
                    editing = editing,
                    onShowQr = { selectedStudentForQr = student },
                    onDelete = { studentToDelete = student },
                    onEdit = {
                        selectedStudentForEdit = student
                        showEditDialog = true
                    }
                )
            }
        }

        if (showAddDialog) {
            AddStudentDialog(
                classId = if (classId == -1L) null else classId,
                onDismiss = { showAddDialog = false },
                onConfirm = { first, last, pic ->
                    viewModel.addStudent(first, last, if (classId == -1L) null else classId, pic)
                    showAddDialog = false
                }
            )
        }

        if (showEditDialog) {
            EditStudentDialog(
                student = selectedStudentForEdit!!,
                onDismiss = { showEditDialog = false },
                onDelete = {
                    showEditDialog = false
                    studentToDelete = selectedStudentForEdit!!
                },
                onConfirm = { first, last, pic, newClassId ->
                    viewModel.updateStudent(selectedStudentForEdit!!.copy(
                        firstName = first, 
                        lastName = last, 
                        picturePath = pic,
                        classId = newClassId
                    ))
                    showEditDialog = false
                }
            )
        }

        if (selectedStudentForQr != null) {
            QrCodeDialog(
                student = selectedStudentForQr!!,
                viewModel = viewModel,
                onDismiss = { selectedStudentForQr = null }
            )
        }

        if (studentToDelete != null) {
            DeleteStudentDialog(
                student = studentToDelete!!,
                onDismiss = { studentToDelete = null },
                onDelete = {
                    viewModel.deleteStudent(studentToDelete!!)
                    studentToDelete = null
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentItem(
    student: Student,
    editing: Boolean = false,
    onShowQr: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onEdit
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = student.picturePath,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "${student.firstName} ${student.lastName}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${student.id}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row {
                if (editing) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Student"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Student"
                        )
                    }
                } else {
                    IconButton(onClick = onShowQr) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Show QR"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddStudentDialog(
    classId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var picturePath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = File(context.filesDir, "student_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            picturePath = file.absolutePath
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Student") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") })
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text(if (picturePath == null) "Select Picture" else "Picture Selected")
                    }
                    if (picturePath != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { picturePath = null }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Picture")
                        }
                    }
                }
                if (picturePath != null) {
                    AsyncImage(
                        model = picturePath,
                        contentDescription = "Selected picture for new student $firstName $lastName",
                        modifier = Modifier.size(100.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (firstName.isNotBlank() and lastName.isNotBlank()) onConfirm(firstName, lastName, picturePath) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStudentDialog(
    student: Student,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, Long?) -> Unit,
    onDelete: () -> Unit,
    viewModel: StudentViewModel = viewModel()
) {
    var selectedClassId by remember { mutableStateOf(student.classId) }
    var showClassDropDown by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf(student.firstName) }
    var lastName by remember { mutableStateOf(student.lastName) }
    var picturePath by remember { mutableStateOf(student.picturePath) }
    val classes by viewModel.allClasses.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = File(context.filesDir, "student_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            picturePath = file.absolutePath
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Edit Student")
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Student")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Click to copy full ID: ${student.id}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(onClick = { clipboardManager.setText(AnnotatedString(student.id)) }),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                    )
                ExposedDropdownMenuBox(
                    expanded = showClassDropDown,
                    onExpandedChange = { showClassDropDown = it }
                ) {
                    OutlinedTextField(
                        value = classes.find { it.id == selectedClassId }?.name ?: "No Class",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Class") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showClassDropDown) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showClassDropDown,
                        onDismissRequest = { showClassDropDown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Class") },
                            onClick = {
                                selectedClassId = null
                                showClassDropDown = false
                            }
                        )
                        classes.forEach { classRoom ->
                            DropdownMenuItem(
                                text = { Text(classRoom.name) },
                                onClick = {
                                    selectedClassId = classRoom.id
                                    showClassDropDown = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") })
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text(if (picturePath == null) "Select Picture" else "Picture Selected")
                    }
                    if (picturePath != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { picturePath = null }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Picture")
                        }
                    }
                }
                if (picturePath != null) {
                    AsyncImage(
                        model = picturePath,
                        contentDescription = "Selected picture for new student $firstName $lastName",
                        modifier = Modifier.size(100.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                if (firstName.isNotBlank() && lastName.isNotBlank()) {
                    onConfirm(firstName, lastName, picturePath, selectedClassId)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteStudentDialog(
    student: Student,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Student") },
        text = { Text("Are you sure you want to delete ${student.firstName} ${student.lastName}?") },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep")
            }
        }
    )
}

@Composable
fun QrCodeDialog(
    student: Student,
    viewModel: StudentViewModel,
    onDismiss: () -> Unit
) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(student) {
        viewModel.generateQrBitmap(student) { qrBitmap = it }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "${student.firstName} ${student.lastName}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(256.dp)
                    )
                } ?: CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { viewModel.exportQrCodesForStudents(listOf(student)) } ) {
                        Icon(Icons.Default.Download, contentDescription = "Export QR Code")
                    }
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
