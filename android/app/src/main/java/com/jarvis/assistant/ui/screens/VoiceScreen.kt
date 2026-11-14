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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.JarvisOrb
import com.jarvis.assistant.ui.LevelBars
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.UiState
import com.jarvis.assistant.ui.VoiceState

@Composable
fun VoiceScreen(
    state: UiState,
    onMenu: () -> Unit,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listening = state.voice == VoiceState.LISTENING
    ScreenScaffold(title = "Voice", subtitle = "Offline on this device", onMenu = onMenu) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            JarvisOrb(size = 168.dp, listening = listening, level = state.level, onClick = onListen)
            Spacer(Modifier.height(18.dp))

            Text(
                when (state.voice) {
                    VoiceState.LISTENING -> state.micLabel.ifBlank { "Listening…" }
                    VoiceState.THINKING -> "Thinking…"
                    VoiceState.SPEAKING -> "Speaking…"
                    VoiceState.UNAVAILABLE -> "Voice unavailable"
                    VoiceState.IDLE -> "Tap the orb and speak"
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            LevelBars(level = state.level, active = listening)
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (listening) {
                    Button(onClick = onStop) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onListen, enabled = state.asrReady) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start listening")
                    }
                }
                OutlinedButton(onClick = onOpenSettings) { Text("Voice settings") }
            }

            Spacer(Modifier.height(20.dp))

            when {
                !state.asrReady && state.modelError != null -> EmptyHint(state.modelError ?: state.modelStatus)
                !state.asrReady -> EmptyHint(state.modelStatus)
                !state.micPermission -> EmptyHint("Microphone permission is off. Enable it in Privacy & Security.")
                else -> EmptyHint(
                    "Hindi, English and Hinglish are transcribed by the bundled Whisper model. " +
                        "Speech never leaves the device.",
                )
            }

            Spacer(Modifier.height(14.dp))

            if (state.lastReply.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Jarvis", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(state.lastReply, style = MaterialTheme.typography.bodyLarge)
                        if (state.lastProvider.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                state.lastProvider,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Try saying", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val samples = listOf(
                        "Remind me to study at 6 pm",
                        "Mera homework complete kar do",
                        "What's pending?",
                        "Jarvis, open WhatsApp",
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(samples.size) { index ->
                            AssistChip(onClick = { onSend(samples[index]) }, label = { Text(samples[index]) })
                        }
                    }
                }
            }
        }
    }
}
