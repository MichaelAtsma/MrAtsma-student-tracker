package com.example.mratsmastudenttracker.ui

import android.app.TimePickerDialog
import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mratsmastudenttracker.data.LabelMatchMode
import com.example.mratsmastudenttracker.data.Session
import com.example.mratsmastudenttracker.data.StudentAttendanceStatusResult
import com.example.mratsmastudenttracker.ui.utils.QrCodeGenerator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: StudentViewModel,
    collaborationViewModel: CollaborationViewModel,
    onNavigateToAddSession: () -> Unit,
    onNavigateToEditSession: (Long) -> Unit,
    onNavigateToSessionDetail: (Long) -> Unit,
    onNavigateToAssistantScanner: () -> Unit
) {
    val sessions by viewModel.allSessions.collectAsState()
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var showHostDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudentEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "blink")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.8f at 400
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (collaborationViewModel.isHosting) {
                    FloatingActionButton(onClick = { showHostDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Host Info",
                            modifier = Modifier.graphicsLayer(alpha = alpha)
                        )
                    }
                } else {
                    FloatingActionButton(
                        onClick = onNavigateToAssistantScanner,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Join as Assistant")
                    }
                    FloatingActionButton(onClick = onNavigateToAddSession) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session"
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.Center) {
                Text("No sessions created yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions.sortedByDescending { it.startTime }, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        collaborationViewModel = collaborationViewModel,
                        onClick = { onNavigateToSessionDetail(session.id) },
                        onEdit = { onNavigateToEditSession(session.id) },
                        onDelete = { sessionToDelete = session },
                        onHost = {
                            collaborationViewModel.startHosting(session.id, viewModel)
                            showHostDialog = true
                        },
                        alpha = alpha
                    )
                }
            }
        }

        if (sessionToDelete != null) {
            DeleteSessionDialog(
                session = sessionToDelete!!,
                onDismiss = { sessionToDelete = null },
                onDelete = {
                    viewModel.deleteSession(sessionToDelete!!)
                    sessionToDelete = null
                }
            )
        }

        if (showHostDialog) {
            val hostSessionId = collaborationViewModel.hostSessionId
            if (hostSessionId != null) {
                val session by viewModel.getSessionById(hostSessionId).collectAsState(initial = null)
                session?.let {
                    HostSessionDialog(
                        session = it,
                        collaborationViewModel = collaborationViewModel,
                        studentViewModel = viewModel,
                        onStopHosting = {
                            collaborationViewModel.stopHosting()
                            showHostDialog = false
                        },
                        onHide = { showHostDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
fun HostSessionDialog(
    session: Session,
    collaborationViewModel: CollaborationViewModel,
    studentViewModel: StudentViewModel,
    onStopHosting: () -> Unit,
    onHide: () -> Unit
) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val localIp = collaborationViewModel.localIp
    val publicIp = collaborationViewModel.publicIp

    LaunchedEffect(session) {
        if (collaborationViewModel.hostSessionId != session.id) {
            collaborationViewModel.startHosting(session.id, studentViewModel)
        }
    }

    LaunchedEffect(localIp, publicIp) {
        if (localIp != null) {
            val qrData = JSONObject().apply {
                put("localIp", localIp)
                put("sessionId", session.id)
                if (publicIp != null) {
                    put("publicIp", publicIp)
                }
            }.toString()
            qrBitmap = QrCodeGenerator.generateQrCodeWithLogo(qrData)
        }
    }

    AlertDialog(
        onDismissRequest = onHide,
        title = { Text("Collaboration QR") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "Collaboration QR",
                        modifier = Modifier.size(250.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Local IP: $localIp")
                    if (publicIp != null && publicIp != localIp) {
                        Text("Public IP: $publicIp", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Starting host...")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Ask the assistant to scan this QR to link.", style = MaterialTheme.typography.bodyMedium)
                Text("Ensure both devices are on the same WiFi or connected by HotSpot for best results.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = onStopHosting) { Text("Stop Hosting") }
        },
        dismissButton = {
            TextButton(onClick = onHide) { Text("Hide") }
        }
    )
}

@Composable
fun DeleteSessionDialog(
    session: Session,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val deleteQuestion = if (!session.title.isNullOrEmpty()) {
        "Are you sure you want to delete the session ${session.title}?"
    } else {
        "Are you sure you want to delete the session on ${dateFormatter.format(session.date)} (${timeFormatter.format(session.startTime)} - ${timeFormatter.format(session.endTime)})?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Session") },
        text = { Text(deleteQuestion) },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionItem(
    session: Session,
    collaborationViewModel: CollaborationViewModel,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHost: () -> Unit,
    alpha: Float = 1f
) {
    val dateFormatterLong = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val dateFormatterShort = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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
            Column {
                if (!session.title.isNullOrEmpty()) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${timeFormatter.format(Date(session.startTime))} - ${timeFormatter.format(Date(session.endTime))} (${dateFormatterShort.format(Date(session.date))})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = dateFormatterLong.format(Date(session.date)),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${timeFormatter.format(Date(session.startTime))} - ${timeFormatter.format(Date(session.endTime))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row {
                IconButton(onClick = onHost) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Host Session",
                        modifier = if (collaborationViewModel.hostSessionId == session.id) Modifier.graphicsLayer(alpha = alpha) else Modifier
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Session")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Session")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()
    val focusManager = LocalFocusManager.current

    var title by remember { mutableStateOf<String?>(null) }

    val calendar = remember { Calendar.getInstance() }
    var selectedDate by remember { mutableLongStateOf(calendar.timeInMillis) }
    val formatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

    var startHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    var endHour by remember { mutableIntStateOf((startHour + 1) % 24) }
    var endMinute by remember { mutableIntStateOf(startMinute) }

    val selectedLabelIds = remember { mutableStateListOf<Long>() }
    var labelMatchMode by remember { mutableStateOf(LabelMatchMode.ALL_CATEGORIES) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    var showDatePicker by remember { mutableStateOf(false) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val startTimePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)
    val endTimePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Session") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Cancel") }
                },
                windowInsets = WindowInsets(top = 0.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title ?: "",
                onValueChange = { title = it },
                label = { Text("Title") },
                shape = CardDefaults.outlinedShape,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedCard(
                onClick = {
                    focusManager.clearFocus()
                    showDatePicker = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Date*", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = formatter.format(Date(selectedDate)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(
                    onClick = {
                        focusManager.clearFocus()
                        showStartTimePicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Start Time*", style = MaterialTheme.typography.labelSmall)
                        Text(text = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedCard(
                    onClick = {
                        focusManager.clearFocus()
                        showEndTimePicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("End Time*", style = MaterialTheme.typography.labelSmall)
                        Text(text = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text("Category Matching Logic:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = labelMatchMode == LabelMatchMode.ALL_CATEGORIES,
                    onClick = { labelMatchMode = LabelMatchMode.ALL_CATEGORIES },
                    label = { Text("ALL Categories (AND)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = labelMatchMode == LabelMatchMode.ANY_CATEGORY,
                    onClick = { labelMatchMode = LabelMatchMode.ANY_CATEGORY },
                    label = { Text("ANY Category (OR)") },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (labelMatchMode == LabelMatchMode.ALL_CATEGORIES)
                    "Student must match labels in ALL selected categories (e.g. Boy AND Class 4A)"
                else
                    "Student must match labels in ANY selected category (e.g. Boy OR Class 4C)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Target Labels*:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(categoriesWithLabels, key = { it.category.id }) { catWithLabels ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(catWithLabels.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        catWithLabels.labels.forEach { label ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        if (selectedLabelIds.contains(label.id)) selectedLabelIds.remove(label.id)
                                        else selectedLabelIds.add(label.id)
                                    }
                                    .padding(vertical = 2.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedLabelIds.contains(label.id),
                                    onCheckedChange = { checked ->
                                        focusManager.clearFocus()
                                        if (checked) selectedLabelIds.add(label.id)
                                        else selectedLabelIds.remove(label.id)
                                    }
                                )
                                Text(text = label.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.addSession(
                        title = title,
                        date = selectedDate,
                        startTime = calendar.apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, startMinute)
                        }.timeInMillis,
                        endTime = calendar.apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, endHour)
                            set(Calendar.MINUTE, endMinute)
                        }.timeInMillis,
                        targetLabelIds = selectedLabelIds.toList(),
                        labelMatchMode = labelMatchMode
                    )
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedLabelIds.isNotEmpty()
            ) {
                Text("Create Session")
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        selectedDate = datePickerState.selectedDateMillis ?: selectedDate
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showStartTimePicker) {
            TimePickerDialog(
                onDismissRequest = { showStartTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        startHour = startTimePickerState.hour
                        startMinute = startTimePickerState.minute
                        showStartTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
                }
            ) {
                TimePicker(state = startTimePickerState)
            }
        }

        if (showEndTimePicker) {
            TimePickerDialog(
                onDismissRequest = { showEndTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        endHour = endTimePickerState.hour
                        endMinute = endTimePickerState.minute
                        showEndTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
                }
            ) {
                TimePicker(state = endTimePickerState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    sessionId: Long,
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val session by viewModel.getSessionById(sessionId).collectAsState(initial = null)
    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()
    val focusManager = LocalFocusManager.current

    if (session == null) return

    var title by remember { mutableStateOf(session!!.title) }

    val calendar = remember { Calendar.getInstance() }
    var selectedDate by remember { mutableLongStateOf(session!!.date) }
    val formatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

    val initialStartCalendar = remember(session) { Calendar.getInstance().apply { timeInMillis = session!!.startTime } }
    var startHour by remember(session) { mutableIntStateOf(initialStartCalendar.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember(session) { mutableIntStateOf(initialStartCalendar.get(Calendar.MINUTE)) }

    val initialEndCalendar = remember(session) { Calendar.getInstance().apply { timeInMillis = session!!.endTime } }
    var endHour by remember(session) { mutableIntStateOf(initialEndCalendar.get(Calendar.HOUR_OF_DAY)) }
    var endMinute by remember(session) { mutableIntStateOf(initialEndCalendar.get(Calendar.MINUTE)) }

    val selectedLabelIds = remember { mutableStateListOf<Long>().apply { addAll(session!!.targetLabelIds) } }
    var labelMatchMode by remember(session) { mutableStateOf(session!!.labelMatchMode) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    var showDatePicker by remember { mutableStateOf(false) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val startTimePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)
    val endTimePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Session") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Cancel") }
                },
                windowInsets = WindowInsets(top = 0.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title ?: "",
                onValueChange = { title = it },
                label = { Text("Title") },
                shape = CardDefaults.outlinedShape,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedCard(
                onClick = {
                    focusManager.clearFocus()
                    showDatePicker = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Date*", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = formatter.format(Date(selectedDate)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(
                    onClick = {
                        focusManager.clearFocus()
                        showStartTimePicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Start Time*", style = MaterialTheme.typography.labelSmall)
                        Text(text = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedCard(
                    onClick = {
                        focusManager.clearFocus()
                        showEndTimePicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("End Time*", style = MaterialTheme.typography.labelSmall)
                        Text(text = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text("Category Matching Logic:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = labelMatchMode == LabelMatchMode.ALL_CATEGORIES,
                    onClick = { labelMatchMode = LabelMatchMode.ALL_CATEGORIES },
                    label = { Text("ALL Categories (AND)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = labelMatchMode == LabelMatchMode.ANY_CATEGORY,
                    onClick = { labelMatchMode = LabelMatchMode.ANY_CATEGORY },
                    label = { Text("ANY Category (OR)") },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (labelMatchMode == LabelMatchMode.ALL_CATEGORIES)
                    "Student must match labels in ALL selected categories (e.g. Boy AND Class 4A)"
                else
                    "Student must match labels in ANY selected category (e.g. Boy OR Class 4C)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Target Labels*:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(categoriesWithLabels, key = { it.category.id }) { catWithLabels ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(catWithLabels.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        catWithLabels.labels.forEach { label ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        if (selectedLabelIds.contains(label.id)) selectedLabelIds.remove(label.id)
                                        else selectedLabelIds.add(label.id)
                                    }
                                    .padding(vertical = 2.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedLabelIds.contains(label.id),
                                    onCheckedChange = { checked ->
                                        focusManager.clearFocus()
                                        if (checked) selectedLabelIds.add(label.id)
                                        else selectedLabelIds.remove(label.id)
                                    }
                                )
                                Text(text = label.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.updateSession(session!!.copy(
                        title = title,
                        date = selectedDate,
                        startTime = calendar.apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, startMinute)
                        }.timeInMillis,
                        endTime = calendar.apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, endHour)
                            set(Calendar.MINUTE, endMinute)
                        }.timeInMillis,
                        targetLabelIds = selectedLabelIds.toList(),
                        labelMatchMode = labelMatchMode
                    ))
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedLabelIds.isNotEmpty()
            ) {
                Text("Update Session")
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        selectedDate = datePickerState.selectedDateMillis ?: selectedDate
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showStartTimePicker) {
            TimePickerDialog(
                onDismissRequest = { showStartTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        startHour = startTimePickerState.hour
                        startMinute = startTimePickerState.minute
                        showStartTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
                }
            ) {
                TimePicker(state = startTimePickerState)
            }
        }

        if (showEndTimePicker) {
            TimePickerDialog(
                onDismissRequest = { showEndTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        endHour = endTimePickerState.hour
                        endMinute = endTimePickerState.minute
                        showEndTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
                }
            ) {
                TimePicker(state = endTimePickerState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    viewModel: StudentViewModel,
    collaborationViewModel: CollaborationViewModel,
    onNavigateToScanner: (Long) -> Unit
) {
    val session by viewModel.getSessionById(sessionId).collectAsState(initial = null)
    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()

    if (session == null) return

    val attendanceStatus by viewModel.getStudentAttendanceForSession(
        sessionId = sessionId,
        targetLabelIds = session!!.targetLabelIds,
        labelMatchMode = session!!.labelMatchMode
    ).collectAsState(initial = emptyList())

    val labelCategoryMap = remember(categoriesWithLabels) {
        val map = mutableMapOf<Long, Pair<String, com.example.mratsmastudenttracker.data.Label>>()
        categoriesWithLabels.forEach { catWithLabels ->
            catWithLabels.labels.forEach { label ->
                map[label.id] = catWithLabels.category.name to label
            }
        }
        map
    }

    val targetLabelsByCategory = remember(session, labelCategoryMap) {
        val targetIds = session?.targetLabelIds ?: emptyList()
        if (targetIds.isEmpty()) {
            emptyMap()
        } else {
            targetIds.mapNotNull { labelId -> labelCategoryMap[labelId] }
                .groupBy({ it.first }, { it.second })
        }
    }

    val lateMap = remember(attendanceStatus, session) {
        val endTime = session?.endTime ?: 0L
        attendanceStatus.associate { it.student.id to viewModel.isStudentLate(it, endTime) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "blink")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.8f at 400
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val dateFormatterLong = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val dateFormatterShort = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var showHostDialog by remember { mutableStateOf(false) }
    var showManualMarkPresentDialog by remember { mutableStateOf(false) }
    var showManualMarkAbsentDialog by remember { mutableStateOf(false) }
    var attendanceToMarkManually by remember { mutableStateOf<StudentAttendanceStatusResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Details") },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ){
                FloatingActionButton(onClick = { onNavigateToScanner(sessionId) }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Students")
                }
                FloatingActionButton(
                    onClick = {
                        collaborationViewModel.startHosting(session!!.id, viewModel)
                        showHostDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Host Info",
                        modifier = if (collaborationViewModel.isHosting && collaborationViewModel.hostSessionId == session!!.id) Modifier.graphicsLayer(alpha = alpha) else Modifier
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!session!!.title.isNullOrEmpty()) {
                        Text(
                            text = session!!.title!!,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${timeFormatter.format(Date(session!!.startTime))} - ${timeFormatter.format(Date(session!!.endTime))} (${dateFormatterShort.format(Date(session!!.date))})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Text(
                            text = dateFormatterLong.format(Date(session!!.date)),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${timeFormatter.format(Date(session!!.startTime))} - ${timeFormatter.format(Date(session!!.endTime))}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Match mode: ${if (session!!.labelMatchMode == LabelMatchMode.ALL_CATEGORIES) "ALL selected categories (AND)" else "ANY selected category (OR)"}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Attendance: ${attendanceStatus.count { it.isPresent }}/${attendanceStatus.size}")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }

                if (targetLabelsByCategory.isEmpty()) {
                    val studentsAll = attendanceStatus.sortedWith(
                        compareBy<StudentAttendanceStatusResult> { it.isPresent }
                            .thenByDescending { it.timestamp }
                            .thenBy { it.student.lastName }
                    )
                    item(key = "header-all") {
                        SubgroupAttendanceBar(
                            groupName = "All Targeted Students",
                            attendanceList = studentsAll,
                            sessionEndTime = session!!.endTime,
                            isStudentLate = viewModel::isStudentLate
                        )
                    }
                    items(studentsAll, key = { it.student.id }) { status ->
                        AttendanceItem(
                            status = status,
                            isLate = lateMap[status.student.id] ?: false,
                            onManualMarkPresent = {
                                attendanceToMarkManually = status
                                showManualMarkPresentDialog = true
                            },
                            onManualMarkAbsent = {
                                attendanceToMarkManually = status
                                showManualMarkAbsentDialog = true
                            }
                        )
                    }
                } else {
                    targetLabelsByCategory.forEach { (categoryName, labels) ->
                        item(key = "cat-$categoryName") {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        labels.forEach { label ->
                            val studentsInLabel = attendanceStatus.filter { status -> status.labels.any { it.id == label.id } }
                                .sortedWith(
                                    compareBy<StudentAttendanceStatusResult> { it.isPresent }
                                        .thenByDescending { it.timestamp }
                                        .thenBy { it.student.lastName }
                                )
                            item(key = "label-${label.id}") {
                                SubgroupAttendanceBar(
                                    groupName = label.name,
                                    attendanceList = studentsInLabel,
                                    sessionEndTime = session!!.endTime,
                                    isStudentLate = viewModel::isStudentLate
                                )
                            }
                            if (studentsInLabel.isEmpty()) {
                                item(key = "empty-${label.id}") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            } else {
                                items(
                                    items = studentsInLabel,
                                    key = { "att_${label.id}_${it.student.id}" }
                                ) { status ->
                                    AttendanceItem(
                                        status = status,
                                        isLate = lateMap[status.student.id] ?: false,
                                        onManualMarkPresent = {
                                            attendanceToMarkManually = status
                                            showManualMarkPresentDialog = true
                                        },
                                        onManualMarkAbsent = {
                                            attendanceToMarkManually = status
                                            showManualMarkAbsentDialog = true
                                        }
                                    )
                                }
                            }
                            item(key = "footer-${label.id}") { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (showHostDialog) {
            HostSessionDialog(
                session = session!!,
                collaborationViewModel = collaborationViewModel,
                studentViewModel = viewModel,
                onStopHosting = {
                    collaborationViewModel.stopHosting()
                    showHostDialog = false
                },
                onHide = { showHostDialog = false }
            )
        }

        if (showManualMarkPresentDialog && attendanceToMarkManually != null) {
            ManualMarkPresentDialog(
                status = attendanceToMarkManually!!,
                onDismiss = {
                    showManualMarkPresentDialog = false
                    attendanceToMarkManually = null
                },
                onConfirm = {
                    viewModel.markAttendance(sessionId = sessionId, studentId = attendanceToMarkManually!!.student.id)
                    showManualMarkPresentDialog = false
                    attendanceToMarkManually = null
                }
            )
        }

        if (showManualMarkAbsentDialog && attendanceToMarkManually != null) {
            ManualMarkAbsentDialog(
                status = attendanceToMarkManually!!,
                onDismiss = {
                    showManualMarkPresentDialog = false
                    attendanceToMarkManually = null
                },
                onConfirm = {
                    viewModel.deleteAttendance(sessionId = sessionId, studentId = attendanceToMarkManually!!.student.id)
                    showManualMarkAbsentDialog = false
                    attendanceToMarkManually = null
                }
            )
        }
    }
}

@Composable
fun SubgroupAttendanceBar(
    groupName: String,
    attendanceList: List<StudentAttendanceStatusResult>,
    sessionEndTime: Long,
    isStudentLate: (StudentAttendanceStatusResult, Long) -> Boolean
) {
    val counts = remember(attendanceList, sessionEndTime) {
        val onTime = attendanceList.count { it.isPresent && !isStudentLate(it, sessionEndTime) }
        val late = attendanceList.count { it.isPresent } - onTime
        val missing = attendanceList.size - onTime - late
        Triple(onTime, late, missing)
    }
    val (onTimeCount, lateCount, missingCount) = counts

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = groupName, style = MaterialTheme.typography.titleLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = "On Time", tint = MaterialTheme.colorScheme.primary)
            Text("$onTimeCount")

            Icon(Icons.Default.AlarmOff, contentDescription = "Late", tint = MaterialTheme.colorScheme.primary)
            Text("$lateCount")

            Icon(Icons.Default.NoAccounts, contentDescription = "Missing", tint = MaterialTheme.colorScheme.error)
            Text("$missingCount")
        }
    }
}

@Composable
fun AttendanceItem(
    status: StudentAttendanceStatusResult,
    isLate: Boolean,
    onManualMarkPresent: () -> Unit = {},
    onManualMarkAbsent: () -> Unit = {}
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = status.student.picturePath,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )

            Text(
                text = "${status.student.firstName} ${status.student.lastName}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            if (!status.isPresent) {
                IconButton(onClick = onManualMarkPresent) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Manually mark student present now")
                }
            }

            if (isLate) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AlarmOff,
                        contentDescription = "Late",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = timeFormatter.format(Date(status.timestamp!!)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (status.isPresent) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "On time",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onManualMarkAbsent() }
                )
            } else {
                Icon(imageVector = Icons.Default.NoAccounts, contentDescription = "Missing", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ManualMarkPresentDialog(
    status: StudentAttendanceStatusResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as present") },
        text = {
            Text("Are you sure you want to mark ${status.student.firstName} ${status.student.lastName} as present?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
        }
    )
}

@Composable
fun ManualMarkAbsentDialog(
    status: StudentAttendanceStatusResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as absent") },
        text = {
            Text("Are you sure you want to mark ${status.student.firstName} ${status.student.lastName} as absent?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Mark absent", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep present") }
        }
    )
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                content()
            }
        }
    )
}
