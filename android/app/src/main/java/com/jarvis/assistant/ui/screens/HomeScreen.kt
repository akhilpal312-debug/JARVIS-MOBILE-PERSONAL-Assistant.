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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.TimeText
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.InfoCard
import com.jarvis.assistant.ui.JarvisOrb
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.SectionHeader
import com.jarvis.assistant.ui.StatusPill
import com.jarvis.assistant.ui.TaskCard
import com.jarvis.assistant.ui.UiState
import com.jarvis.assistant.ui.VoiceState
import com.jarvis.assistant.data.Priority
import com.jarvis.assistant.data.TaskItem

@Composable
fun HomeScreen(
    state: UiState,
    onMenu: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenReminders: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onTaskToggle: (TaskItem) -> Unit,
    onTaskSnooze: (TaskItem) -> Unit,
    onTaskDelete: (String) -> Unit,
    onTaskTomorrow: (TaskItem) -> Unit,
    onQuickAdd: (String, Long?, Priority) -> Unit,
    onRetryModels: () -> Unit,
    onBackendSetup: () -> Unit,
) {
    ScreenScaffold(title = "Jarvis", subtitle = "Your personal assistant", onMenu = onMenu) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${TimeText.greeting(state.hindi)} · ${TimeText.clockNow(state.hindi)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "How can I help you today?",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            item { VoiceStatusCard(state = state, onOpenVoice = onOpenVoice, onToggleWakeWord = onToggleWakeWord) }

            if (!state.modelReady || state.modelError != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.modelError == null) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    state.modelError ?: state.modelStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (state.modelError == null) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { state.modelProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onRetryModels) { Text("Retry model setup") }
                            }
                        }
                    }
                }
            }

            if (!state.backendConfigured) {
                item {
                    InfoCard(
                        text = "Offline mode: tasks, reminders and voice work now. Connect your Jarvis backend in Settings for cloud reasoning.",
                        icon = Icons.Outlined.CloudOff,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Today's tasks",
                    trailing = if (state.tasks.isEmpty()) null else "${state.tasks.size} pending",
                    onTrailingClick = onOpenTasks,
                )
            }

            if (state.tasks.isEmpty()) {
                item { EmptyHint("No open tasks. Say “Jarvis, remind me to study at 6 pm”.") }
            } else {
                items(minOf(state.tasks.size, 3)) { index ->
                    val task = state.tasks[index]
                    TaskCard(
                        task = task,
                        hindi = state.hindi,
                        onToggle = { onTaskToggle(task) },
                        onSnooze = { onTaskSnooze(task) },
                        onDelete = { onTaskDelete(task.id) },
                        onTomorrow = { onTaskTomorrow(task) },
                    )
                }
                if (state.tasks.size > 3) {
                    item {
                        TextButton(onClick = onOpenTasks) { Text("View all ${state.tasks.size} tasks") }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Upcoming reminders",
                    trailing = if (state.reminders.isEmpty()) null else "View all",
                    onTrailingClick = onOpenReminders,
                )
            }
            if (state.reminders.isEmpty()) {
                item { EmptyHint("No reminders scheduled yet.") }
            } else {
                items(minOf(state.reminders.size, 2)) { index ->
                    val task = state.reminders[index]
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
                            Icon(
                                Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    TimeText.due(task.reminderAt ?: task.dueAt, state.hindi),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(TimeText.relativeFuture(task.reminderAt ?: task.dueAt ?: 0L, state.hindi))
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Chat")
                    }
                    OutlinedButton(onClick = onOpenTasks, modifier = Modifier.weight(1f)) {
                        Text("Add task")
                    }
                }
            }

            item {
                Text(
                    "Jarvis runs its voice engine on this phone. Wake-word audio is never uploaded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VoiceStatusCard(state: UiState, onOpenVoice: () -> Unit, onToggleWakeWord: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JarvisOrb(
                size = 84.dp,
                listening = state.voice == VoiceState.LISTENING,
                level = state.level,
                onClick = onOpenVoice,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        state.voice == VoiceState.LISTENING -> "Listening…"
                        state.voice == VoiceState.THINKING -> "Thinking…"
                        state.voice == VoiceState.SPEAKING -> "Speaking…"
                        state.wakeWordEnabled -> "Wake word active"
                        else -> "Listening is off"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (state.wakeWordEnabled) {
                        "Say “Jarvis” from anywhere. Tap to talk."
                    } else {
                        "Turn on to say “Jarvis” hands-free."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.wakeWordEnabled, onCheckedChange = onToggleWakeWord)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onOpenVoice) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Talk")
                    }
                }
            }
        }
    }
}
