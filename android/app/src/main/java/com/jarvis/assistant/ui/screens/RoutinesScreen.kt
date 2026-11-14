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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.jarvis.assistant.data.RoutineItem
import com.jarvis.assistant.data.RoutineSuggestion
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.InfoCard
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.UiState
import java.util.Calendar

@Composable
fun RoutinesScreen(
    state: UiState,
    onMenu: () -> Unit,
    onAdd: (String, Int, Int, Set<Int>, String) -> Unit,
    onToggle: (RoutineItem, Boolean) -> Unit,
    onDelete: (RoutineItem) -> Unit,
    onAcceptSuggestion: (RoutineSuggestion) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Routines", subtitle = "Learned over time, always with your consent", onMenu = onMenu) { padding ->
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add routine") },
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
                if (!state.memoryLearning) {
                    item {
                        InfoCard(
                            text = "Routine learning is paused in Memory settings. Turn it back on to see suggestions.",
                            icon = Icons.Outlined.Lightbulb,
                        )
                    }
                }

                if (state.suggestions.isNotEmpty()) {
                    item { Text("Smart suggestions", style = MaterialTheme.typography.titleMedium) }
                    items(state.suggestions.size) { index ->
                        val suggestion = state.suggestions[index]
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "You often finish “${suggestion.sampleTitle}” around ${suggestion.hour}:00.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Set this as a recurring routine? Nothing is scheduled until you confirm.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { onAcceptSuggestion(suggestion) }) { Text("Yes, set it") }
                            }
                        }
                    }
                }

                item { Text("Your routines", style = MaterialTheme.typography.titleMedium) }

                if (state.routines.isEmpty()) {
                    item { EmptyHint("No routines yet. Add one, or complete a task a few times and Jarvis will suggest one.") }
                }

                items(state.routines.size) { index ->
                    val routine = state.routines[index]
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
                            Icon(Icons.Outlined.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${timeLabel(routine.hour, routine.minute)} · ${daysLabel(routine.weekdays)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (routine.prompt.isNotBlank()) {
                                    Text(
                                        routine.prompt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(routine) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete routine")
                            }
                            Switch(
                                checked = routine.enabled,
                                onCheckedChange = { onToggle(routine, it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRoutineDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, hour, weekdays, prompt ->
                onAdd(name, hour, 0, weekdays, prompt)
                showAdd = false
            },
        )
    }
}

private fun timeLabel(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return String.format("%d:%02d", hour, minute).let {
        val isPm = calendar.get(Calendar.AM_PM) == Calendar.PM
        val twelve = calendar.get(Calendar.HOUR).let { h -> if (h == 0) 12 else h }
        "$twelve:${String.format("%02d", minute)} ${if (isPm) "PM" else "AM"}"
    }
}

private fun daysLabel(days: Set<Int>): String {
    if (days.isEmpty()) return "Every day"
    if (days.size == 7) return "Every day"
    val names = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
    return days.sorted().mapNotNull { names[it] }.joinToString(", ")
}

@Composable
private fun AddRoutineDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Set<Int>, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(18) }
    var days by remember { mutableStateOf(setOf<Int>()) }
    val hours = listOf(6, 7, 8, 9, 12, 14, 16, 18, 19, 20, 21, 22)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New routine") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Reminder text (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Text("Time", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(hours.size) { index ->
                        val value = hours[index]
                        FilterChip(
                            selected = hour == value,
                            onClick = { hour = value },
                            label = { Text(timeLabel(value, 0)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Days (empty = every day)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val labels = listOf(2 to "Mo", 3 to "Tu", 4 to "We", 5 to "Th", 6 to "Fr", 7 to "Sa", 1 to "Su")
                    labels.forEach { (day, label) ->
                        AssistChip(
                            onClick = {
                                days = if (days.contains(day)) days - day else days + day
                            },
                            label = { Text(label) },
                            enabled = true,
                            leadingIcon = if (days.contains(day)) {
                                { Icon(Icons.Outlined.Add, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), hour, days, prompt.trim()) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
