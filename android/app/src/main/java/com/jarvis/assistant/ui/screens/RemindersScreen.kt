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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.jarvis.assistant.core.TimeText
import com.jarvis.assistant.data.Priority
import com.jarvis.assistant.data.TaskItem
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.InfoCard
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.StatusPill
import com.jarvis.assistant.ui.UiState

@Composable
fun RemindersScreen(
    state: UiState,
    onMenu: () -> Unit,
    onAdd: (String, Long?, Priority) -> Unit,
    onToggle: (TaskItem) -> Unit,
    onSnooze: (TaskItem) -> Unit,
    onRequestExactAlarms: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Reminders", subtitle = "Scheduled on this device", onMenu = onMenu) { padding ->
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add reminder") },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!state.exactAlarms) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "Reminders may arrive a few minutes late because exact alarms aren't allowed. " +
                                        "You can grant it in Android settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onRequestExactAlarms) { Text("Allow exact reminders") }
                            }
                        }
                    }
                }

                if (!state.notificationPermission) {
                    item {
                        InfoCard(
                            text = "Notifications are off for Jarvis, so reminders can't be shown. Enable them in Privacy & Security.",
                            icon = Icons.Outlined.NotificationsActive,
                        )
                    }
                }

                item { Text("Upcoming", style = MaterialTheme.typography.titleMedium) }

                if (state.reminders.isEmpty()) {
                    item { EmptyHint("No reminders yet. Try “Jarvis, remind me to drink water at 3 pm”.") }
                }

                items(state.reminders.size) { index ->
                    val task = state.reminders[index]
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        TimeText.due(task.reminderAt ?: task.dueAt, state.hindi),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusPill(
                                    TimeText.relativeFuture(task.reminderAt ?: task.dueAt ?: 0L, state.hindi),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { onSnooze(task) }) { Text("Snooze 10 min") }
                                TextButton(onClick = { onToggle(task) }) { Text("Mark done") }
                            }
                        }
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
