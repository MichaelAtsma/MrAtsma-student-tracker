package com.example.mratsmastudenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mratsmastudenttracker.data.LabelMatchMode
import com.example.mratsmastudenttracker.data.Student
import com.example.mratsmastudenttracker.ui.utils.matchesSearchQuery
import com.example.mratsmastudenttracker.data.StudentWithLabels
import com.example.mratsmastudenttracker.ui.utils.DynamicSortConfig
import com.example.mratsmastudenttracker.ui.utils.SortOption
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    viewModel: StudentViewModel
) {
    val allStudentsWithLabels by viewModel.allStudentsWithLabels.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val availableSortOptions = remember(categories) { viewModel.getAvailableSortOptions(categories) }

    var searchQuery by remember { mutableStateOf("") }
    val selectedFilterLabelIds = remember { mutableStateListOf<Long>() }
    var filterMatchMode by remember { mutableStateOf(LabelMatchMode.ALL_CATEGORIES) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    val activeSortOptions = remember { mutableStateListOf<SortOption>() }

    LaunchedEffect(availableSortOptions) {
        if (activeSortOptions.isEmpty() && availableSortOptions.isNotEmpty()) {
            activeSortOptions.add(availableSortOptions.find { it.key == "last_name" } ?: availableSortOptions.first())
            val firstNameOpt = availableSortOptions.find { it.key == "first_name" }
            if (firstNameOpt != null && firstNameOpt != activeSortOptions.first()) {
                activeSortOptions.add(firstNameOpt)
            }
        }
    }

    val selectedLabelsList = selectedFilterLabelIds.toList()
    val sortOptionsList = activeSortOptions.toList()

    val filteredStudents = remember(allStudentsWithLabels, searchQuery, selectedLabelsList, filterMatchMode, allLabels, sortOptionsList) {
        val filtered = allStudentsWithLabels.filter { swl ->
            val student = swl.student
            val fullStudentText = "${student.firstName} ${student.lastName} ${student.id}"
            val matchesSearch = searchQuery.isBlank() || fullStudentText.matchesSearchQuery(searchQuery)

            val matchesLabels = if (selectedLabelsList.isEmpty()) {
                true
            } else {
                val studentLabelIds = swl.labels.map { it.id }.toSet()
                val selectedLabelsByCategory = selectedLabelsList
                    .mapNotNull { labelId -> allLabels.find { it.id == labelId } }
                    .groupBy { it.categoryId }

                if (selectedLabelsByCategory.isEmpty()) {
                    true
                } else if (filterMatchMode == LabelMatchMode.ALL_CATEGORIES) {
                    selectedLabelsByCategory.values.all { labelsInCat ->
                        labelsInCat.any { it.id in studentLabelIds }
                    }
                } else {
                    selectedLabelsByCategory.values.any { labelsInCat ->
                        labelsInCat.any { it.id in studentLabelIds }
                    }
                }
            }

            matchesSearch && matchesLabels
        }

        val comparator = viewModel.buildStudentComparator(sortOptionsList)
        filtered.sortedWith(comparator)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedStudentForEdit by remember { mutableStateOf<StudentWithLabels?>(null) }
    var selectedStudentForQr by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is StudentEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Students (${filteredStudents.size})") },
                actions = {
                    IconButton(onClick = { editing = !editing }) {
                        Icon(
                            imageVector = if (!editing) Icons.Default.Edit else Icons.Default.EditOff,
                            contentDescription = if (!editing) "Edit Students" else "Stop Editing"
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search, Filter & Sort Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                FilledTonalButton(onClick = { showSortSheet = true }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                        Text("Sort")
                    }
                }

                BadgedBox(
                    badge = {
                        if (selectedFilterLabelIds.isNotEmpty()) {
                            Badge { Text("${selectedFilterLabelIds.size}") }
                        }
                    }
                ) {
                    FilledTonalButton(onClick = { showFilterSheet = true }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            Text("Filter")
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredStudents, key = { it.student.id }) { studentWithLabels ->
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
        }

        if (showFilterSheet) {
            FilterDialog(
                viewModel = viewModel,
                selectedLabelIds = selectedFilterLabelIds,
                matchMode = filterMatchMode,
                onMatchModeChange = { filterMatchMode = it },
                onClear = { selectedFilterLabelIds.clear() },
                onDismiss = { showFilterSheet = false }
            )
        }

        if (showSortSheet) {
            SortConfigDialog(
                availableSortOptions = availableSortOptions,
                currentSorts = activeSortOptions,
                onSortChange = { newSorts ->
                    activeSortOptions.clear()
                    activeSortOptions.addAll(newSorts)
                },
                onDismiss = { showSortSheet = false }
            )
        }

        if (showAddDialog) {
            AddStudentDialog(
                defaultLabelId = null,
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


    }
}

@Composable
fun SortConfigDialog(
    availableSortOptions: List<SortOption>,
    currentSorts: List<SortOption>,
    onSortChange: (List<SortOption>) -> Unit,
    onDismiss: () -> Unit
) {
    val tempSorts = remember {
        mutableStateListOf<SortOption?>().apply {
            if (currentSorts.isNotEmpty()) {
                addAll(currentSorts)
                if (currentSorts.size < availableSortOptions.size) {
                    add(null)
                }
            } else {
                add(availableSortOptions.find { it.key == "last_name" } ?: availableSortOptions.firstOrNull())
                add(null)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Sort Students", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                DynamicSortConfig(
                    availableOptions = availableSortOptions,
                    selectedSorts = tempSorts
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onSortChange(tempSorts.filterNotNull())
                        onDismiss()
                    }) {
                        Text("Apply Sort")
                    }
                }
            }
        }
    }
}

@Composable
fun FilterDialog(
    viewModel: StudentViewModel,
    selectedLabelIds: MutableList<Long>,
    matchMode: LabelMatchMode,
    onMatchModeChange: (LabelMatchMode) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter Students by Labels", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClear) { Text("Clear All") }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Matching Logic:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = matchMode == LabelMatchMode.ALL_CATEGORIES,
                        onClick = { onMatchModeChange(LabelMatchMode.ALL_CATEGORIES) },
                        label = { Text("ALL Categories (AND)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = matchMode == LabelMatchMode.ANY_CATEGORY,
                        onClick = { onMatchModeChange(LabelMatchMode.ANY_CATEGORY) },
                        label = { Text("ANY Category (OR)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

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
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Apply Sort")
                }
            }
        }
    }
}
