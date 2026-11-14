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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.ui.InfoCard
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.SettingLinkRow
import com.jarvis.assistant.ui.SettingSwitchRow
import com.jarvis.assistant.ui.StatusPill
import com.jarvis.assistant.ui.UiState

@Composable
fun VoiceSettingsScreen(
    state: UiState,
    onMenu: () -> Unit,
    onLanguage: (String) -> Unit,
    onSpeakReplies: (Boolean) -> Unit,
    onQuietDuringMedia: (Boolean) -> Unit,
    onSpeechRate: (Float) -> Unit,
    onRetryModels: () -> Unit,
    onTestVoice: () -> Unit,
) {
    ScreenScaffold(title = "Voice & language", subtitle = "Hindi · English · Hinglish", onMenu = onMenu) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Reply language", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto" to "Auto", "hi" to "हिन्दी", "en" to "English").forEach { (value, label) ->
                                AssistChip(
                                    onClick = { onLanguage(value) },
                                    label = { Text(label) },
                                    leadingIcon = if (state.language == value) {
                                        { androidx.compose.material3.Icon(Icons.Outlined.Language, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Auto lets the assistant mirror whatever you speak or type, including Hinglish.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        SettingSwitchRow(
                            icon = Icons.Outlined.VolumeUp,
                            title = "Speak replies",
                            description = "Use the bundled Hindi / English neural voices",
                            checked = state.speakReplies,
                            onCheckedChange = onSpeakReplies,
                        )
                        HorizontalDivider()
                        SettingSwitchRow(
                            icon = Icons.Outlined.Schedule,
                            title = "Stay quiet during music & calls",
                            description = "Never interrupt a call, and skip speech while media plays",
                            checked = state.quietDuringMedia,
                            onCheckedChange = onQuietDuringMedia,
                        )
                        HorizontalDivider()
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Text("Speaking speed", style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value = state.speechRate,
                                onValueChange = onSpeechRate,
                                valueRange = 0.6f..1.6f,
                                steps = 4,
                            )
                            Text(
                                String.format("%.1f×", state.speechRate),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Offline models", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusPill(if (state.asrReady && state.ttsReady) "Ready" else "Preparing")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(state.modelStatus, style = MaterialTheme.typography.bodyMedium)
                        if (state.modelError != null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onRetryModels) { Text("Repair models") }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onTestVoice, enabled = state.ttsReady) { Text("Test voice") }
                            OutlinedButton(onClick = onRetryModels) { Text("Verify files") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Whisper tiny (multilingual, int8) for recognition; Piper VITS voices for Hindi and English. " +
                                "All weights ship inside the APK — nothing to download.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(
    state: UiState,
    onMenu: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onToggleLearning: (Boolean) -> Unit,
    onToggleEncrypt: (Boolean) -> Unit,
    onClearMemories: () -> Unit,
    onClearAll: () -> Unit,
) {
    ScreenScaffold(title = "Privacy & security", subtitle = "You are in control", onMenu = onMenu) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        SettingLinkRow(
                            icon = Icons.Outlined.Mic,
                            title = "Microphone",
                            value = if (state.micPermission) "Granted" else "Denied",
                        ) { if (!state.micPermission) onRequestMic() }
                        SettingLinkRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notifications",
                            value = if (state.notificationPermission) "Granted" else "Off",
                        ) { if (!state.notificationPermission) onRequestNotifications() }
                        SettingLinkRow(
                            icon = Icons.Outlined.Alarm,
                            title = "Exact reminders",
                            value = if (state.exactAlarms) "Allowed" else "Inexact",
                        ) { onRequestExactAlarms() }
                        SettingLinkRow(
                            icon = Icons.Outlined.Schedule,
                            title = "Calendar",
                            value = if (state.calendarPermission) "Granted" else "Not granted",
                        ) { if (!state.calendarPermission) onRequestCalendar() }
                    }
                }
            }

            item {
                InfoCard(
                    text = "Wake-word detection and speech recognition run on this device. Background audio is never uploaded, " +
                        "calls are never recorded, and Jarvis does not read your messages or screen.",
                    icon = Icons.Outlined.Security,
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        SettingSwitchRow(
                            icon = Icons.Outlined.Psychology,
                            title = "Memory learning",
                            description = "Save approved preferences and suggest routines",
                            checked = state.memoryLearning,
                            onCheckedChange = onToggleLearning,
                        )
                        HorizontalDivider()
                        SettingSwitchRow(
                            icon = Icons.Outlined.Lock,
                            title = "Encrypt saved memories",
                            description = "AES-256-GCM via the Android Keystore",
                            checked = state.encryptMemories,
                            onCheckedChange = onToggleEncrypt,
                        )
                        HorizontalDivider()
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Text("Data", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${state.memories.size} memories · ${state.tasks.size} open tasks · ${state.messages.size} messages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onClearMemories) { Text("Delete memories") }
                                OutlinedButton(onClick = onClearAll) {
                                    androidx.compose.material3.Icon(
                                        Icons.Outlined.DeleteForever,
                                        contentDescription = null,
                                        modifier = Modifier.width(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete all data")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: UiState,
    onMenu: () -> Unit,
    onSaveBackend: (String, String) -> Unit,
    onSyncTasks: () -> Unit,
    onFollowUpMinutes: (Int) -> Unit,
    onOpenProviders: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var url by remember(state.backendUrl) { mutableStateOf(state.backendUrl) }
    var token by remember(state.backendToken) { mutableStateOf(state.backendToken) }

    ScreenScaffold(title = "Settings", subtitle = "Backend, reminders and permissions", onMenu = onMenu) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Jarvis backend", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusPill(if (state.backendConfigured) "Connected" else "Not set")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Backend URL") },
                            placeholder = { Text("https://your-jarvis.vercel.app") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Deployment token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This is the token you set as JARVIS_API_TOKEN on Vercel. LLM provider keys stay on the server.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSaveBackend(url, token) }) { Text("Save") }
                            OutlinedButton(onClick = onSyncTasks, enabled = state.backendConfigured) {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.width(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sync tasks")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        SettingLinkRow(Icons.Outlined.Language, "Voice & language") { onOpenVoice() }
                        HorizontalDivider()
                        SettingLinkRow(Icons.Outlined.Cloud, "AI model providers") { onOpenProviders() }
                        HorizontalDivider()
                        SettingLinkRow(Icons.Outlined.Security, "Privacy & security") { onOpenPrivacy() }
                        HorizontalDivider()
                        SettingLinkRow(Icons.Outlined.Info, "About Jarvis") { onOpenAbout() }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Follow-up reminders", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "If a reminder is ignored, Jarvis can follow up once — never repeatedly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Off", 10 to "10 min", 15 to "15 min", 30 to "30 min").forEach { (value, label) ->
                                AssistChip(
                                    onClick = { onFollowUpMinutes(value) },
                                    label = { Text(label) },
                                    leadingIcon = if (state.followUpMinutes == value) {
                                        { androidx.compose.material3.Icon(Icons.Outlined.Refresh, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun AboutScreen(state: UiState, onMenu: () -> Unit) {
    ScreenScaffold(title = "About Jarvis", subtitle = "Version ${BuildConfig.VERSION_NAME}", onMenu = onMenu) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("JARVIS", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Your personal AI assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        AboutRow("Voice engine", "Sherpa-ONNX · Whisper tiny int8 · Piper VITS hi/en")
                        HorizontalDivider()
                        AboutRow("Speech recognition", "On-device, multilingual (hi / en / hinglish)")
                        HorizontalDivider()
                        AboutRow("Reasoning", if (state.backendConfigured) "Your Vercel backend" else "On-device commands only")
                        HorizontalDivider()
                        AboutRow("LLM providers", "Free-tier providers, server-side keys only")
                        HorizontalDivider()
                        AboutRow("Licenses", "Apache-2.0 (sherpa-onnx, Whisper) · MIT (Piper voices) · CC-BY (espeak-ng data)")
                    }
                }
            }
            item {
                InfoCard(
                    text = "Model licences and attribution are bundled in assets/models/licenses. " +
                        "Jarvis never claims an action succeeded without a real result.",
                    icon = Icons.Outlined.Info,
                )
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
