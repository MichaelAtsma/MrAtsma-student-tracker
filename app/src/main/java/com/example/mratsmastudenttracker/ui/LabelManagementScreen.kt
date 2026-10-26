package com.example.mratsmastudenttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mratsmastudenttracker.data.Label
import com.example.mratsmastudenttracker.data.LabelCategory
import com.example.mratsmastudenttracker.data.LabelCategoryWithLabels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelManagementScreen(
    viewModel: StudentViewModel
) {
    val categoriesWithLabels by viewModel.allCategoriesWithLabels.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<LabelCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<LabelCategory?>(null) }

    var categoryForAddLabel by remember { mutableStateOf<LabelCategory?>(null) }
    var labelToEdit by remember { mutableStateOf<Label?>(null) }
    var labelToDelete by remember { mutableStateOf<Label?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudentEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Label Management") },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCategoryDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Category") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (categoriesWithLabels.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No label categories found. Tap '+ New Category' to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(categoriesWithLabels, key = { it.category.id }) { catWithLabels ->
                    CategoryCard(
                        categoryWithLabels = catWithLabels,
                        onEditCategory = { categoryToEdit = catWithLabels.category },
                        onDeleteCategory = { categoryToDelete = catWithLabels.category },
                        onAddLabel = { categoryForAddLabel = catWithLabels.category },
                        onEditLabel = { label -> labelToEdit = label },
                        onDeleteLabel = { label -> labelToDelete = label }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Dialogs
    if (showAddCategoryDialog) {
        CategoryInputDialog(
            title = "Add Category",
            initialName = "",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name)
                showAddCategoryDialog = false
            }
        )
    }

    if (categoryToEdit != null) {
        CategoryInputDialog(
            title = "Edit Category",
            initialName = categoryToEdit!!.name,
            onDismiss = { categoryToEdit = null },
            onConfirm = { newName ->
                viewModel.updateCategory(categoryToEdit!!.copy(name = newName))
                categoryToEdit = null
            }
        )
    }

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete '${categoryToDelete!!.name}'? All labels in this category will also be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(categoryToDelete!!)
                    categoryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (categoryForAddLabel != null) {
        CategoryInputDialog(
            title = "Add Label to ${categoryForAddLabel!!.name}",
            initialName = "",
            onDismiss = { categoryForAddLabel = null },
            onConfirm = { labelName ->
                viewModel.addLabel(categoryForAddLabel!!.id, labelName)
                categoryForAddLabel = null
            }
        )
    }

    if (labelToEdit != null) {
        CategoryInputDialog(
            title = "Edit Label",
            initialName = labelToEdit!!.name,
            onDismiss = { labelToEdit = null },
            onConfirm = { newName ->
                viewModel.updateLabel(labelToEdit!!.copy(name = newName))
                labelToEdit = null
            }
        )
    }

    if (labelToDelete != null) {
        AlertDialog(
            onDismissRequest = { labelToDelete = null },
            title = { Text("Delete Label") },
            text = { Text("Are you sure you want to delete '${labelToDelete!!.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLabel(labelToDelete!!)
                    labelToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { labelToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CategoryCard(
    categoryWithLabels: LabelCategoryWithLabels,
    onEditCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onAddLabel: () -> Unit,
    onEditLabel: (Label) -> Unit,
    onDeleteLabel: (Label) -> Unit
) {
    val category = categoryWithLabels.category
    val labels = categoryWithLabels.labels
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${labels.size} ${if (labels.size == 1) "label" else "labels"}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditCategory) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Category", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDeleteCategory) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                if (labels.isEmpty()) {
                    Text(
                        "No labels in this category yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRowLayout(
                        horizontalGap = 8.dp,
                        verticalGap = 8.dp
                    ) {
                        labels.forEach { label ->
                            InputChip(
                                selected = false,
                                onClick = { onEditLabel(label) },
                                label = { Text(label.name) },
                                avatar = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { onDeleteLabel(label) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Delete Label",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onAddLabel,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Label")
                }
            }
        }
    }
}

@Composable
fun CategoryInputDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
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
fun FlowRowLayout(
    horizontalGap: androidx.compose.ui.unit.Dp,
    verticalGap: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val hGapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()

        var currentX = 0
        var currentY = 0
        var maxLineHeight = 0

        val placeables = measurables.map { measurable ->
            val placeable = measurable.measure(constraints)
            if (currentX + placeable.width > constraints.maxWidth && currentX > 0) {
                currentX = 0
                currentY += maxLineHeight + vGapPx
                maxLineHeight = 0
            }
            val x = currentX
            val y = currentY
            currentX += placeable.width + hGapPx
            maxLineHeight = maxOf(maxLineHeight, placeable.height)
            Triple(placeable, x, y)
        }

        val totalHeight = currentY + maxLineHeight
        layout(constraints.maxWidth, totalHeight) {
            placeables.forEach { (placeable, x, y) ->
                placeable.placeRelative(x, y)
            }
        }
    }
}
