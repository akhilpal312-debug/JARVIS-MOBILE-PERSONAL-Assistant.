package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.UiState
import com.jarvis.assistant.ui.VoiceState

@Composable
fun ChatScreen(
    state: UiState,
    onMenu: () -> Unit,
    onSend: (String) -> Unit,
    onOpenVoice: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.voice) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
        }
    }

    ScreenScaffold(
        title = "Jarvis",
        subtitle = if (state.backendConfigured) {
            "Online · ${state.lastProvider.ifBlank { "ready" }}"
        } else {
            "On-device · offline capable"
        },
        onMenu = onMenu,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Namaste 🙏", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Main Jarvis hoon. Aap Hindi, English ya Hinglish mein baat kar sakte hain — " +
                            "tasks, reminders, routines aur sawaal, sab kuch.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    val samples = listOf(
                        "Aaj mera kya plan hai?",
                        "Remind me to study at 6 pm",
                        "What's pending?",
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(samples.size) { index ->
                            AssistChip(onClick = { onSend(samples[index]) }, label = { Text(samples[index]) })
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages.size) { index ->
                        val message = state.messages[index]
                        val fromUser = message.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
                        ) {
                            Surface(
                                color = if (fromUser) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                contentColor = if (fromUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (fromUser) 18.dp else 4.dp,
                                    bottomEnd = if (fromUser) 4.dp else 18.dp,
                                ),
                                modifier = Modifier.fillMaxWidth(0.88f),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                                    if (!fromUser && message.provider.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            message.provider,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.voice == VoiceState.THINKING) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Jarvis is thinking…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message…") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                    )
                    IconButton(onClick = onOpenVoice) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Voice input")
                    }
                    Box {
                        IconButton(
                            onClick = {
                                if (draft.isNotBlank()) {
                                    onSend(draft)
                                    draft = ""
                                }
                            },
                            enabled = draft.isNotBlank(),
                        ) {
                            Icon(Icons.Outlined.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}
