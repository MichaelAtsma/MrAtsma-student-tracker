package com.example.mratsmastudenttracker.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.mratsmastudenttracker.data.Label
import com.example.mratsmastudenttracker.data.Student
import com.example.mratsmastudenttracker.data.StudentWithLabels
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementScreen(
    viewModel: StudentViewModel,
    labelId: Long? = null,
    categoryId: Long? = null
) {
    val filteredStudentsWithLabels by viewModel.filteredStudentsWithLabels.collectAsState()
    val allCategoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()

    val title = remember(labelId, categoryId, allLabels, allCategoriesWithLabels) {
        when {
            labelId != null && labelId != -1L -> {
                val label = allLabels.find { it.id == labelId }
                if (label != null) "Label: ${label.name}" else "Students"
            }
            categoryId != null && categoryId != -1L -> {
                val category = allCategoriesWithLabels.find { it.category.id == categoryId }
                if (category != null) "Category: ${category.category.name}" else "Students"
            }
            labelId == -1L || categoryId == -1L -> "Students without Labels"
            else -> "All Students"
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedStudentForEdit by remember { mutableStateOf<StudentWithLabels?>(null) }
    var selectedStudentForQr by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }
    var editing by remember { mutableStateOf(false) }

    var importPreview by remember { mutableStateOf<FilePreview?>(null) }
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val preview = viewModel.previewImportFile(uri)
                if (preview != null) {
                    importPreview = preview
                } else {
                    snackbarHostState.showSnackbar("Failed to parse file for import.")
                }
            }
        }
    }

    LaunchedEffect(labelId, categoryId) {
        if (labelId != null) {
            viewModel.selectLabel(labelId)
        } else if (categoryId != null) {
            viewModel.selectCategory(categoryId)
        } else {
            viewModel.selectLabel(null)
        }
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
                title = { Text(title) },
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch("*/*")
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Database (CSV/Excel)")
                    }
                    IconButton(onClick = { editing = !editing }) {
                        Icon(
                            imageVector = if (!editing) Icons.Default.Edit else Icons.Default.EditOff,
                            contentDescription = if (!editing) "Edit Students" else "Stop Editing"
                        )
                    }
                    IconButton(onClick = { viewModel.exportQrCodesForLabel(labelId) }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Export QR Codes")
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
            items(filteredStudentsWithLabels, key = { it.student.id }) { studentWithLabels ->
                StudentItem(
                    studentWithLabels = studentWithLabels,
                    editing = editing,
                    onShowQr = { selectedStudentForQr = studentWithLabels.student },
                    onDelete = { studentToDelete = studentWithLabels.student },
                    onEdit = {
                        selectedStudentForEdit = studentWithLabels
                        showEditDialog = true
                    }
                )
            }
        }

        if (showAddDialog) {
            AddStudentDialog(
                defaultLabelId = if (labelId != null && labelId != -1L) labelId else null,
                viewModel = viewModel,
                onDismiss = { showAddDialog = false },
                onConfirm = { first, last, pic, labelIds ->
                    viewModel.addStudent(first, last, labelIds, pic)
                    showAddDialog = false
                }
            )
        }

        if (showEditDialog && selectedStudentForEdit != null) {
            EditStudentDialog(
                studentWithLabels = selectedStudentForEdit!!,
                viewModel = viewModel,
                onDismiss = { showEditDialog = false },
                onDelete = {
                    showEditDialog = false
                    studentToDelete = selectedStudentForEdit!!.student
                },
                onConfirm = { first, last, pic, newLabelIds ->
                    viewModel.updateStudent(
                        student = selectedStudentForEdit!!.student.copy(
                            firstName = first,
                            lastName = last,
                            picturePath = pic
                        ),
                        labelIds = newLabelIds
                    )
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

        if (importPreview != null) {
            ImportDialog(
                filePreview = importPreview!!,
                onDismiss = { importPreview = null },
                onConfirm = { hasHeaderRow, mappings ->
                    val preview = importPreview!!
                    importPreview = null
                    viewModel.importWithMapping(preview, hasHeaderRow, mappings)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentItem(
    studentWithLabels: StudentWithLabels,
    editing: Boolean = false,
    onShowQr: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit = {}
) {
    val student = studentWithLabels.student
    val labels = studentWithLabels.labels

    Card(
        modifier = Modifier
            .fillMaxWidth()
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

                    if (labels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            labels.take(3).forEach { label ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(label.name, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            if (labels.size > 3) {
                                Text(
                                    "+${labels.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                }
            }
            Row {
                if (editing) {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Student")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Student")
                    }
                } else {
                    IconButton(onClick = onShowQr) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = "Show QR")
                    }
                }
            }
        }
    }
}

@Composable
fun AddStudentDialog(
    defaultLabelId: Long?,
    viewModel: StudentViewModel,
    onDismiss: () -> Unit,
    onConfirm: (firstName: String, lastName: String, picturePath: String?, labelIds: List<Long>) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var picturePath by remember { mutableStateOf<String?>(null) }
    val selectedLabelIds = remember { mutableStateListOf<Long>().apply { if (defaultLabelId != null) add(defaultLabelId) } }

    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Last Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
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
                }
                if (picturePath != null) {
                    item {
                        AsyncImage(
                            model = picturePath,
                            contentDescription = "Selected Picture",
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }

                item {
                    Text("Select Labels:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                items(categoriesWithLabels, key = { it.category.id }) { catWithLabels ->
                    Column {
                        Text(catWithLabels.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        FlowRowLayout(horizontalGap = 4.dp, verticalGap = 4.dp) {
                            catWithLabels.labels.forEach { label ->
                                val isSelected = selectedLabelIds.contains(label.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedLabelIds.remove(label.id) else selectedLabelIds.add(label.id)
                                    },
                                    label = { Text(label.name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (firstName.isNotBlank() && lastName.isNotBlank()) {
                        onConfirm(firstName, lastName, picturePath, selectedLabelIds.toList())
                    }
                },
                enabled = firstName.isNotBlank() && lastName.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditStudentDialog(
    studentWithLabels: StudentWithLabels,
    viewModel: StudentViewModel,
    onDismiss: () -> Unit,
    onConfirm: (firstName: String, lastName: String, picturePath: String?, labelIds: List<Long>) -> Unit,
    onDelete: () -> Unit
) {
    val student = studentWithLabels.student
    var firstName by remember { mutableStateOf(student.firstName) }
    var lastName by remember { mutableStateOf(student.lastName) }
    var picturePath by remember { mutableStateOf(student.picturePath) }
    val selectedLabelIds = remember { mutableStateListOf<Long>().apply { addAll(studentWithLabels.labels.map { it.id }) } }

    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Click to copy full ID: ${student.id}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable(onClick = { clipboardManager.setText(AnnotatedString(student.id)) }),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Last Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
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
                }
                if (picturePath != null) {
                    item {
                        AsyncImage(
                            model = picturePath,
                            contentDescription = "Selected Picture",
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }

                item {
                    Text("Select Labels:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                items(categoriesWithLabels, key = { it.category.id }) { catWithLabels ->
                    Column {
                        Text(catWithLabels.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        FlowRowLayout(horizontalGap = 4.dp, verticalGap = 4.dp) {
                            catWithLabels.labels.forEach { label ->
                                val isSelected = selectedLabelIds.contains(label.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedLabelIds.remove(label.id) else selectedLabelIds.add(label.id)
                                    },
                                    label = { Text(label.name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (firstName.isNotBlank() && lastName.isNotBlank()) {
                        onConfirm(firstName, lastName, picturePath, selectedLabelIds.toList())
                    }
                },
                enabled = firstName.isNotBlank() && lastName.isNotBlank()
            ) {
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
                    Button(onClick = { viewModel.exportQrCodesForStudents(listOf(student)) }) {
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
