package com.jarvis.assistant.ui

import com.jarvis.assistant.data.ChatMessage
import com.jarvis.assistant.data.MemoryItem
import com.jarvis.assistant.data.ProviderStatus
import com.jarvis.assistant.data.RoutineItem
import com.jarvis.assistant.data.RoutineSuggestion
import com.jarvis.assistant.data.TaskItem

enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING, UNAVAILABLE }

data class UiState(
    val loaded: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val completed: List<TaskItem> = emptyList(),
    val reminders: List<TaskItem> = emptyList(),
    val memories: List<MemoryItem> = emptyList(),
    val routines: List<RoutineItem> = emptyList(),
    val suggestions: List<RoutineSuggestion> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val voice: VoiceState = VoiceState.IDLE,
    val level: Float = 0f,
    val micLabel: String = "",
    val modelStatus: String = "Preparing offline voice models…",
    val modelProgress: Float = 0f,
    val modelReady: Boolean = false,
    val asrReady: Boolean = false,
    val ttsReady: Boolean = false,
    val modelError: String? = null,
    val wakeWordEnabled: Boolean = false,
    val backendConfigured: Boolean = false,
    val providerStatus: ProviderStatus? = null,
    val providerError: String? = null,
    val providerLoading: Boolean = false,
    val lastReply: String = "",
    val lastProvider: String = "",
    val toast: String? = null,
    val language: String = "auto",
    val memoryLearning: Boolean = true,
    val speakReplies: Boolean = true,
    val quietDuringMedia: Boolean = true,
    val encryptMemories: Boolean = true,
    val followUpMinutes: Int = 15,
    val speechRate: Float = 1.0f,
    val backendUrl: String = "",
    val backendToken: String = "",
    val micPermission: Boolean = false,
    val notificationPermission: Boolean = false,
    val exactAlarms: Boolean = false,
    val calendarPermission: Boolean = false,
    val listeningConfigured: Boolean = false,
) {
    val hindi: Boolean get() = language == "hi"
    val activeCount: Int get() = tasks.size
}
