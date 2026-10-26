package com.example.mratsmastudenttracker.ui.utils

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDropdown(
    label: String,
    selectedOption: SortOption?,
    availableOptions: List<SortOption>,
    onSelect: (SortOption?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedOption?.displayName ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            availableOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DynamicSortConfig(
    availableOptions: List<SortOption>,
    selectedSorts: androidx.compose.runtime.snapshots.SnapshotStateList<SortOption?>
) {
    if (selectedSorts.isEmpty()) {
        selectedSorts.add(null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        selectedSorts.forEachIndexed { index, currentSort ->
            val label = if (index == 0) "Sort by" else "Then by"

            val alreadySelectedKeys = selectedSorts.mapIndexed { idx, s -> if (idx == index) null else s?.key }.filterNotNull()
            val filteredOptions = availableOptions.filter { it.key !in alreadySelectedKeys }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SortDropdown(
                        label = label,
                        selectedOption = currentSort,
                        availableOptions = filteredOptions,
                        onSelect = { newOption ->
                            selectedSorts[index] = newOption
                            if (newOption == null) {
                                while (selectedSorts.size > index + 1) {
                                    selectedSorts.removeAt(selectedSorts.size - 1)
                                }
                            } else {
                                val currentAssignedCount = selectedSorts.count { it != null }
                                if (index == selectedSorts.size - 1 && currentAssignedCount < availableOptions.size) {
                                    selectedSorts.add(null)
                                }
                            }
                        }
                    )
                }

                if (selectedSorts.size > 1 && index > 0) {
                    IconButton(onClick = {
                        selectedSorts.removeAt(index)
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove sort level")
                    }
                }
            }
        }
    }
}
