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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.TimeText
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.UiState

@Composable
fun HistoryScreen(state: UiState, onMenu: () -> Unit, onClear: () -> Unit) {
    ScreenScaffold(
        title = "Conversation history",
        subtitle = "${state.messages.size} messages on this device",
        onMenu = onMenu,
        actions = {
            IconButton(onClick = onClear, enabled = state.messages.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { EmptyHint("No history yet. Your chats and voice commands are stored locally, never in the cloud unless you sync.") }
            }
            items(state.messages.size) { index ->
                val message = state.messages[index]
                val fromUser = message.role == "user"
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (fromUser) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (fromUser) "You" else "Jarvis",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                TimeText.due(message.createdAt, state.hindi),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(message.content, style = MaterialTheme.typography.bodyMedium)
                        if (message.provider.isNotBlank()) {
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
    }
}
