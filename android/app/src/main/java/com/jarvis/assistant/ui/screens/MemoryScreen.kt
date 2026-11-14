package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.data.MemoryItem
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.SettingSwitchRow
import com.jarvis.assistant.ui.StatusPill
import com.jarvis.assistant.ui.UiState

@Composable
fun MemoryScreen(
    state: UiState,
    onMenu: () -> Unit,
    onAdd: (String, String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onToggleLearning: (Boolean) -> Unit,
    onToggleEncrypt: (Boolean) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Memory", subtitle = "Only what you approve", onMenu = onMenu) { padding ->
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add memory") },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            SettingSwitchRow(
                                icon = Icons.Outlined.Psychology,
                                title = "Memory learning",
                                description = "Remember approved preferences and spot routines",
                                checked = state.memoryLearning,
                                onCheckedChange = onToggleLearning,
                            )
                            SettingSwitchRow(
                                icon = Icons.Outlined.Lock,
                                title = "Encrypt memories",
                                description = "AES-256 with a key held by the Android Keystore",
                                checked = state.encryptMemories,
                                onCheckedChange = onToggleEncrypt,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { confirmClear = true }, enabled = state.memories.isNotEmpty()) {
                                    Text("Clear all memories")
                                }
                            }
                        }
                    }
                }

                if (state.memories.isEmpty()) {
                    item { EmptyHint("Nothing saved yet. Say “Jarvis, remember that I study in the evening”.") }
                }

                items(state.memories.size) { index ->
                    val memory = state.memories[index]
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                StatusPill(memory.kind.replaceFirstChar { it.uppercase() })
                                Spacer(Modifier.height(6.dp))
                                Text(memory.content, style = MaterialTheme.typography.bodyLarge)
                            }
                            IconButton(onClick = { editing = memory }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit memory")
                            }
                            IconButton(onClick = { onDelete(memory.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete memory")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        MemoryDialog(
            title = "Add memory",
            initial = "",
            onDismiss = { showAdd = false },
            onConfirm = { text ->
                onAdd(text, "fact")
                showAdd = false
            },
        )
    }

    editing?.let { memory ->
        MemoryDialog(
            title = "Edit memory",
            initial = memory.content,
            onDismiss = { editing = null },
            onConfirm = { text ->
                onUpdate(memory.id, text)
                editing = null
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all memories?") },
            text = { Text("This removes every saved memory from this device. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    confirmClear = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun MemoryDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("What should Jarvis remember?") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
