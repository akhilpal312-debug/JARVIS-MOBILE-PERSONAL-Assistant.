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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.TimeText
import com.jarvis.assistant.data.Priority
import com.jarvis.assistant.data.TaskItem
import com.jarvis.assistant.data.TaskStatus
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.TaskCard
import com.jarvis.assistant.ui.UiState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val FILTERS = listOf("Active", "In progress", "Scheduled", "Blocked", "Completed")

@Composable
fun TasksScreen(
    state: UiState,
    onMenu: () -> Unit,
    onAdd: (String, Long?, Priority) -> Unit,
    onToggle: (TaskItem) -> Unit,
    onSnooze: (TaskItem) -> Unit,
    onDelete: (String) -> Unit,
    onTomorrow: (TaskItem) -> Unit,
) {
    var filter by remember { mutableStateOf("Active") }
    var showAdd by remember { mutableStateOf(false) }

    val visible = when (filter) {
        "Completed" -> state.completed
        "In progress" -> state.tasks.filter { it.status == TaskStatus.IN_PROGRESS }
        "Scheduled" -> state.tasks.filter { it.status == TaskStatus.SCHEDULED }
        "Blocked" -> state.tasks.filter { it.status == TaskStatus.BLOCKED }
        else -> state.tasks
    }

    ScreenScaffold(
        title = "Tasks",
        subtitle = "${state.tasks.size} pending · ${state.completed.size} completed",
        onMenu = onMenu,
    ) { padding ->
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add task") },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(innerPadding),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(FILTERS.size) { index ->
                        val label = FILTERS[index]
                        FilterChip(
                            selected = filter == label,
                            onClick = { filter = label },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (visible.isEmpty()) {
                        item {
                            EmptyHint(
                                when (filter) {
                                    "Completed" -> "No completed tasks yet. Finished tasks move here automatically."
                                    else -> "Nothing here. Add a task, or say “Jarvis, remind me to …”."
                                },
                            )
                        }
                    }
                    items(visible.size) { index ->
                        val task = visible[index]
                        TaskCard(
                            task = task,
                            hindi = state.hindi,
                            onToggle = { onToggle(task) },
                            onSnooze = { onSnooze(task) },
                            onDelete = { onDelete(task.id) },
                            onTomorrow = { onTomorrow(task) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(
            hindi = state.hindi,
            onDismiss = { showAdd = false },
            onConfirm = { title, at, priority ->
                onAdd(title, at, priority)
                showAdd = false
            },
        )
    }
}

/** Quick time presets keep capture fast on a phone without a date/time picker detour. */
internal fun timePreset(label: String): Pair<String, Long?> = when (label) {
    "In 1 hour" -> label to (System.currentTimeMillis() + 60 * 60 * 1000)
    "Tonight 6 PM" -> label to todayAt(18, 0)
    "Tomorrow 9 AM" -> label to TimeText.tomorrowAt(9, 0)
    "Tomorrow 6 PM" -> label to TimeText.tomorrowAt(18, 0)
    else -> label to null
}

internal fun todayAt(hour: Int, minute: Int): Long {
    val zone = ZoneId.systemDefault()
    val date = LocalDate.now(zone)
    var value = LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    if (value <= System.currentTimeMillis()) {
        value = LocalDateTime.of(date.plusDays(1), LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()
    }
    return value
}

@Composable
internal fun AddTaskDialog(
    hindi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Priority) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf("No time") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    val presets = listOf("No time", "In 1 hour", "Tonight 6 PM", "Tomorrow 9 AM", "Tomorrow 6 PM")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hindi) "नया कार्य" else "New task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (hindi) "क्या करना है?" else "What needs doing?") },
                    singleLine = false,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("When", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets.size) { index ->
                        val label = presets[index]
                        AssistChip(
                            onClick = { preset = label },
                            label = { Text(label) },
                            leadingIcon = if (preset == label) {
                                { Icon(Icons.Outlined.Add, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.CRITICAL).forEach { value ->
                        AssistChip(
                            onClick = { priority = value },
                            label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim(), timePreset(preset).second, priority) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
