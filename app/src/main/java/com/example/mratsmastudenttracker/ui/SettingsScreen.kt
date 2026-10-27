package com.example.mratsmastudenttracker.ui

import android.net.Uri
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mratsmastudenttracker.data.LabelMatchMode
import com.example.mratsmastudenttracker.data.StudentWithLabels
import com.example.mratsmastudenttracker.ui.utils.DynamicSortConfig
import com.example.mratsmastudenttracker.ui.utils.SortOption
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StudentViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showQrExportDialog by remember { mutableStateOf(false) }
    var showAttendanceExportDialog by remember { mutableStateOf(false) }
    var showDeleteDatabaseDialog by remember { mutableStateOf(false) }
    var showDatabaseImportConfigDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var currentImportMode by remember { mutableStateOf(ImportMode.MERGE) }
    var currentConflictStrategy by remember { mutableStateOf(CategoryConflictStrategy.OVERWRITE) }
    var importPreview by remember { mutableStateOf<FilePreview?>(null) }

    val context = LocalContext.current
    var showImageImportPickerOptions by remember { mutableStateOf(false) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                viewModel.processImageImport(uri, isZip = true)
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                viewModel.processImageImport(uri, isZip = false)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImportUri = uri
            showDatabaseImportConfigDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudentEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings & Export") },
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Database Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { filePickerLauncher.launch("*/*") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Import Database (JSON, Excel, or CSV)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Import students, pictures, and labels from JSON or with column mapping", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showImageImportPickerOptions = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Import Student Pictures (ZIP / Folder)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Match and import student photos from a ZIP archive or directory", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Image, contentDescription = null)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.exportDatabaseToJson() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export Database (JSON)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Backup all students, pictures, and labels to Downloads", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    onClick = { showDeleteDatabaseDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delete Database", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("Permanently erase all students, sessions, labels, and pictures", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Reporting & Batch Exports", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showQrExportDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Batch QR Code Export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Export QR codes for selected labels with AND/OR matching", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.QrCode, contentDescription = null)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showAttendanceExportDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Advanced Attendance Excel Export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Export session attendance with category columns & multi-level sorting", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.TableChart, contentDescription = null)
                    }
                }
            }
        }

        if (showDatabaseImportConfigDialog && pendingImportUri != null) {
            DatabaseImportConfigDialog(
                onDismiss = {
                    showDatabaseImportConfigDialog = false
                    pendingImportUri = null
                },
                onConfirm = { mode, strategy ->
                    currentImportMode = mode
                    currentConflictStrategy = strategy
                    val uri = pendingImportUri!!
                    showDatabaseImportConfigDialog = false
                    pendingImportUri = null

                    scope.launch {
                        val uriString = uri.toString().lowercase()
                        val isJson = uriString.endsWith(".json") || (context.contentResolver.getType(uri)?.contains("json") == true)
                        if (isJson) {
                            viewModel.importDatabaseFromJson(uri, mode, strategy)
                        } else {
                            val preview = viewModel.previewImportFile(uri)
                            if (preview != null) {
                                importPreview = preview
                            } else {
                                snackbarHostState.showSnackbar("Failed to parse file for import.")
                            }
                        }
                    }
                }
            )
        }

        if (showDeleteDatabaseDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDatabaseDialog = false },
                title = { Text("Delete Database") },
                text = { Text("Are you sure you want to delete all students, sessions, labels, attendance, and pictures? This action CANNOT be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearDatabase()
                            showDeleteDatabaseDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Everything")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDatabaseDialog = false }) {
                        Text("Cancel")
                    }
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
                    viewModel.importWithMapping(preview, hasHeaderRow, mappings, currentImportMode, currentConflictStrategy)
                }
            )
        }

        if (showQrExportDialog) {
            QrExportConfigDialog(
                viewModel = viewModel,
                onDismiss = { showQrExportDialog = false },
                onExport = { targetLabelIds, matchMode ->
                    viewModel.exportQrCodesForLabelsAdvanced(targetLabelIds, matchMode)
                    showQrExportDialog = false
                }
            )
        }

        if (showAttendanceExportDialog) {
            AttendanceExportConfigDialog(
                viewModel = viewModel,
                onDismiss = { showAttendanceExportDialog = false },
                onExport = { selectedSessionIds, sortOptions ->
                    viewModel.exportSessionsToExcelAdvanced(selectedSessionIds, sortOptions)
                    showAttendanceExportDialog = false
                }
            )
        }

        if (showImageImportPickerOptions) {
            AlertDialog(
                onDismissRequest = { showImageImportPickerOptions = false },
                title = { Text("Import Student Pictures") },
                text = { Text("Choose how you would like to import student pictures:") },
                confirmButton = {
                    TextButton(onClick = {
                        showImageImportPickerOptions = false
                        zipPickerLauncher.launch("application/zip")
                    }) { Text("ZIP Archive") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImageImportPickerOptions = false
                        folderPickerLauncher.launch(null)
                    }) { Text("Folder") }
                }
            )
        }

        if (viewModel.pendingImageMatches.isNotEmpty()) {
            ImageImportResolutionDialog(
                pendingMatches = viewModel.pendingImageMatches,
                onConfirm = { pendingMatch, student ->
                    viewModel.assignImageToStudent(pendingMatch, student)
                },
                onSkip = { pendingMatch ->
                    viewModel.skipPendingImage(pendingMatch)
                },
                onSkipAll = {
                    viewModel.skipAllPendingImages()
                }
            )
        }

        if (viewModel.isImportingImages) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            viewModel.imageImportProgressMessage,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Please wait while images are extracted, sanitized, and matched against students.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageImportResolutionDialog(
    pendingMatches: List<PendingImageMatch>,
    onConfirm: (PendingImageMatch, StudentWithLabels) -> Unit,
    onSkip: (PendingImageMatch) -> Unit,
    onSkipAll: () -> Unit
) {
    if (pendingMatches.isEmpty()) return
    val currentMatch = pendingMatches.first()
    var selectedStudent by remember(currentMatch) { mutableStateOf(currentMatch.bestCandidate?.student) }
    var showStudentPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Resolve Image Import (${pendingMatches.size} remaining)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AsyncImage(
                        model = currentMatch.imageFile,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .padding(4.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("File: ${currentMatch.fileName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Match Type: ${currentMatch.matchType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Suggested Match:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedStudent != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${selectedStudent!!.student.firstName} ${selectedStudent!!.student.lastName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("ID: ${selectedStudent!!.student.id}", style = MaterialTheme.typography.bodySmall)
                            if (selectedStudent!!.labels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Labels: ${selectedStudent!!.labels.joinToString(", ") { it.name }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text("No close match found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStudentPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("Pick Different Student")
                    }
                    Button(
                        onClick = {
                            if (selectedStudent != null) {
                                onConfirm(currentMatch, selectedStudent!!)
                            }
                        },
                        enabled = selectedStudent != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirm Match")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onSkip(currentMatch) }) {
                        Text("Skip Image")
                    }
                    TextButton(onClick = onSkipAll, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Skip All Remaining")
                    }
                }
            }
        }
    }

    if (showStudentPicker) {
        StudentPickerDialog(
            candidates = currentMatch.candidates,
            onDismiss = { showStudentPicker = false },
            onSelect = { student ->
                selectedStudent = student
                showStudentPicker = false
            }
        )
    }
}

@Composable
fun StudentPickerDialog(
    candidates: List<ImageMatchCandidate>,
    onDismiss: () -> Unit,
    onSelect: (StudentWithLabels) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Student", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates, key = { it.student.student.id }) { candidate ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(candidate.student) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "${candidate.student.student.firstName} ${candidate.student.student.lastName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("ID: ${candidate.student.student.id} | Similarity: ${(candidate.similarity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                if (candidate.student.labels.isNotEmpty()) {
                                    Text("Labels: ${candidate.student.labels.joinToString(", ") { it.name }}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun DatabaseImportConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (ImportMode, CategoryConflictStrategy) -> Unit
) {
    var importMode by remember { mutableStateOf(ImportMode.MERGE) }
    var conflictStrategy by remember { mutableStateOf(CategoryConflictStrategy.OVERWRITE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Database Import Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Import Mode:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = importMode == ImportMode.MERGE,
                        onClick = { importMode = ImportMode.MERGE }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.clickable { importMode = ImportMode.MERGE }) {
                        Text("Merge", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Combine imported data with existing database", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = importMode == ImportMode.REPLACE,
                        onClick = { importMode = ImportMode.REPLACE }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.clickable { importMode = ImportMode.REPLACE }) {
                        Text("Replace", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Delete entire database before importing", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (importMode == ImportMode.MERGE) {
                    HorizontalDivider()
                    Text("Category Conflict Resolution:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("If an imported category name matches an existing category name:", style = MaterialTheme.typography.bodySmall)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = conflictStrategy == CategoryConflictStrategy.OVERWRITE,
                                onClick = { conflictStrategy = CategoryConflictStrategy.OVERWRITE }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Overwrite existing category label", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { conflictStrategy = CategoryConflictStrategy.OVERWRITE })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = conflictStrategy == CategoryConflictStrategy.KEEP_ORIGINAL,
                                onClick = { conflictStrategy = CategoryConflictStrategy.KEEP_ORIGINAL }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Keep original category label", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { conflictStrategy = CategoryConflictStrategy.KEEP_ORIGINAL })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = conflictStrategy == CategoryConflictStrategy.ADD_DUPLICATE_SAME_CATEGORY,
                                onClick = { conflictStrategy = CategoryConflictStrategy.ADD_DUPLICATE_SAME_CATEGORY }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Keep both in same category", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { conflictStrategy = CategoryConflictStrategy.ADD_DUPLICATE_SAME_CATEGORY })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = conflictStrategy == CategoryConflictStrategy.CREATE_NEW_CATEGORY,
                                onClick = { conflictStrategy = CategoryConflictStrategy.CREATE_NEW_CATEGORY }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create new category (e.g. 'Klas (2)')", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { conflictStrategy = CategoryConflictStrategy.CREATE_NEW_CATEGORY })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(importMode, conflictStrategy) }) {
                Text("Proceed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun QrExportConfigDialog(
    viewModel: StudentViewModel,
    onDismiss: () -> Unit,
    onExport: (List<Long>, LabelMatchMode) -> Unit
) {
    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()
    val selectedLabelIds = remember { mutableStateListOf<Long>() }
    var matchMode by remember { mutableStateOf(LabelMatchMode.ALL_CATEGORIES) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Configure QR Code Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Matching Logic:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = matchMode == LabelMatchMode.ALL_CATEGORIES,
                        onClick = { matchMode = LabelMatchMode.ALL_CATEGORIES },
                        label = { Text("ALL Categories (AND)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = matchMode == LabelMatchMode.ANY_CATEGORY,
                        onClick = { matchMode = LabelMatchMode.ANY_CATEGORY },
                        label = { Text("ANY Category (OR)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select Target Labels (leave empty for all students):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categoriesWithLabels, key = { it.category.id }) { catWithLabels ->
                        Column {
                            Text(catWithLabels.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            FlowRowLayout(horizontalGap = 4.dp, verticalGap = 4.dp) {
                                catWithLabels.labels.forEach { label ->
                                    val isSelected = selectedLabelIds.contains(label.id)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) selectedLabelIds.remove(label.id)
                                            else selectedLabelIds.add(label.id)
                                        },
                                        label = { Text(label.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onExport(selectedLabelIds.toList(), matchMode) }) {
                        Text("Export QR Codes")
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceExportConfigDialog(
    viewModel: StudentViewModel,
    onDismiss: () -> Unit,
    onExport: (List<Long>, List<SortOption>) -> Unit
) {
    val sessions by viewModel.allSessions.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val availableSortOptions = remember(categories) { viewModel.getAvailableSortOptions(categories) }

    val selectedSessionIds = remember { mutableStateListOf<Long>().apply { addAll(sessions.map { it.id }) } }

    val selectedSorts = remember(availableSortOptions) {
        mutableStateListOf<SortOption?>().apply {
            add(availableSortOptions.find { it.key == "last_name" } ?: availableSortOptions.firstOrNull())
            add(availableSortOptions.find { it.key == "first_name" })
            add(null)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Configure Attendance Report Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                // Sessions Selection Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Sessions (${selectedSessionIds.size}/${sessions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        if (selectedSessionIds.size == sessions.size) selectedSessionIds.clear()
                        else {
                            selectedSessionIds.clear()
                            selectedSessionIds.addAll(sessions.map { it.id })
                        }
                    }) {
                        Text(if (selectedSessionIds.size == sessions.size) "Deselect All" else "Select All")
                    }
                }

                LazyColumn(
                    modifier = Modifier.height(160.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val isSelected = selectedSessionIds.contains(session.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedSessionIds.remove(session.id)
                                    else selectedSessionIds.add(session.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedSessionIds.add(session.id)
                                    else selectedSessionIds.remove(session.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = session.title?.ifBlank { null } ?: dateFormatter.format(Date(session.date)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("Multi-Level Sorting Options:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                DynamicSortConfig(
                    availableOptions = availableSortOptions,
                    selectedSorts = selectedSorts
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val options = selectedSorts.filterNotNull()
                            onExport(selectedSessionIds.toList(), options)
                        },
                        enabled = selectedSessionIds.isNotEmpty() && selectedSorts.any { it != null }
                    ) {
                        Text("Export Report")
                    }
                }
            }
        }
    }
}
