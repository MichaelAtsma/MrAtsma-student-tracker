package com.example.mratsmastudenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    filePreview: FilePreview,
    onDismiss: () -> Unit,
    onConfirm: (hasHeaderRow: Boolean, mappings: List<ColumnMappingConfig>) -> Unit
) {
    var hasHeaderRow by remember { mutableStateOf(true) }

    // Initialize mapping for each column
    val mappings = remember(filePreview.totalColumns) {
        mutableStateListOf<ColumnMappingConfig>().apply {
            for (c in 0 until filePreview.totalColumns) {
                add(ColumnMappingConfig(columnIndex = c, columnType = ColumnType.IGNORE))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Import Database Mapping",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "File: ${filePreview.fileName} (${filePreview.totalColumns} columns detected)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = hasHeaderRow,
                        onCheckedChange = { hasHeaderRow = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "First row is header",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(mappings) { colIdx, config ->
                        val sampleValues = remember(filePreview, colIdx, hasHeaderRow) {
                            val startRow = if (hasHeaderRow && filePreview.previewRows.size > 1) 1 else 0
                            filePreview.previewRows.drop(startRow).take(3).mapNotNull { row ->
                                if (colIdx < row.size) row[colIdx].ifBlank { null } else null
                            }
                        }

                        val headerName = remember(filePreview, colIdx, hasHeaderRow) {
                            if (hasHeaderRow && filePreview.previewRows.isNotEmpty() && colIdx < filePreview.previewRows[0].size) {
                                filePreview.previewRows[0][colIdx].ifBlank { null }
                            } else null
                        }

                        ColumnColumnMappingCard(
                            colIndex = colIdx,
                            headerName = headerName,
                            sampleValues = sampleValues,
                            config = config,
                            hasHeaderRow = hasHeaderRow,
                            onConfigChange = { newConfig ->
                                mappings[colIdx] = newConfig
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(hasHeaderRow, mappings.toList())
                        }
                    ) {
                        Text("Import")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnColumnMappingCard(
    colIndex: Int,
    headerName: String?,
    sampleValues: List<String>,
    config: ColumnMappingConfig,
    hasHeaderRow: Boolean,
    onConfigChange: (ColumnMappingConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (config.columnType != ColumnType.IGNORE)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Column ${colIndex + 1}" + if (headerName != null) " ($headerName)" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (sampleValues.isNotEmpty()) {
                Text(
                    text = "Samples: " + sampleValues.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Samples: (empty)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var dropdownExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = config.columnType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Field Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        ColumnType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    onConfigChange(config.copy(columnType = type))
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (config.columnType == ColumnType.LABEL_CATEGORY) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.customCategoryName,
                    onValueChange = { onConfigChange(config.copy(customCategoryName = it)) },
                    label = { Text("Category Name") },
                    placeholder = {
                        Text(if (hasHeaderRow && headerName != null) headerName else "e.g. Bus / Gender")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
