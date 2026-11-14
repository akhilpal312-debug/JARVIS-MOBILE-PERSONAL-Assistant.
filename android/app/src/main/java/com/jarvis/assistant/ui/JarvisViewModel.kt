package com.jarvis.assistant.ui

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.AppGraph
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.Evidence
import com.jarvis.assistant.data.Priority
import com.jarvis.assistant.data.RoutineItem
import com.jarvis.assistant.data.RoutineSuggestion
import com.jarvis.assistant.data.TaskItem
import com.jarvis.assistant.service.JarvisService
import com.jarvis.assistant.voice.WhisperAsr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val graph: AppGraph = (application as JarvisApp).graph
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        loadSettingsIntoState()
        prepareModels()
    }

    private fun loadSettingsIntoState() {
        val prefs = graph.prefs
        _state.update {
            it.copy(
                language = prefs.language,
                memoryLearning = prefs.memoryLearning,
                speakReplies = prefs.speakReplies,
                quietDuringMedia = prefs.quietDuringMedia,
                encryptMemories = prefs.encryptMemories,
                followUpMinutes = prefs.followUpMinutes,
                speechRate = prefs.speechRate,
                backendUrl = prefs.backendUrl,
                backendToken = prefs.backendToken,
                backendConfigured = prefs.backendConfigured(),
                wakeWordEnabled = prefs.wakeWordEnabled,
                listeningConfigured = prefs.wakeWordEnabled,
            )
        }
        refreshPermissions()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        fun granted(permission: String) = ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        _state.update {
            it.copy(
                micPermission = granted(Manifest.permission.RECORD_AUDIO),
                notificationPermission = granted(Manifest.permission.POST_NOTIFICATIONS),
                calendarPermission = granted(Manifest.permission.WRITE_CALENDAR),
                exactAlarms = exact,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val active = graph.tasks.active()
                Snapshot(
                    tasks = active,
                    completed = graph.tasks.completed(),
                    reminders = active.filter { it.reminderAt != null || it.dueAt != null }
                        .sortedBy { it.reminderAt ?: it.dueAt },
                    memories = graph.memories.all(),
                    routines = graph.routines.all(),
                    messages = graph.conversations.recent(),
                )
            }
            val suggestions = withContext(Dispatchers.IO) {
                graph.tasks.routineSuggestions(snapshot.routines)
            }
            _state.update {
                it.copy(
                    loaded = true,
                    tasks = snapshot.tasks,
                    completed = snapshot.completed,
                    reminders = snapshot.reminders,
                    memories = snapshot.memories,
                    routines = snapshot.routines,
                    suggestions = suggestions,
                    messages = snapshot.messages,
                )
            }
        }
    }

    private data class Snapshot(
        val tasks: List<TaskItem>,
        val completed: List<TaskItem>,
        val reminders: List<TaskItem>,
        val memories: List<com.jarvis.assistant.data.MemoryItem>,
        val routines: List<RoutineItem>,
        val messages: List<com.jarvis.assistant.data.ChatMessage>,
    )

    // ----- voice models -----------------------------------------------------

    private fun prepareModels() {
        viewModelScope.launch {
            val installer = graph.models
            if (!installer.isReady()) {
                _state.update { it.copy(modelStatus = "Preparing bundled voice models…", modelProgress = 0f) }
                val result = withContext(Dispatchers.IO) {
                    installer.ensure { progress, name ->
                        _state.update {
                            it.copy(
                                modelProgress = progress,
                                modelStatus = if (progress >= 1f) "Voice models ready" else "Preparing $name",
                            )
                        }
                    }
                }
                if (result.isFailure) {
                    _state.update {
                        it.copy(
                            modelError = result.exceptionOrNull()?.message ?: "Voice models could not be prepared",
                            modelStatus = "Voice models unavailable",
                            voice = VoiceState.UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            }

            _state.update { it.copy(modelReady = true, modelProgress = 1f) }
            val asr = graph.asr()
            val asrError = withContext(Dispatchers.Default) { asr.load() }
            val tts = graph.tts()
            val ttsError = withContext(Dispatchers.Default) { tts.load() }
            val error = asrError ?: ttsError
            _state.update {
                it.copy(
                    asrReady = asr.isReady(),
                    ttsReady = tts.isReady(),
                    modelStatus = error ?: "Offline voice ready · Hindi, English, Hinglish",
                    modelError = error,
                    voice = if (error == null) VoiceState.IDLE else VoiceState.UNAVAILABLE,
                )
            }
        }
    }

    fun retryModels() {
        graph.models.clear()
        _state.update { it.copy(modelError = null, modelStatus = "Preparing bundled voice models…", modelProgress = 0f) }
        prepareModels()
    }

    // ----- assistant --------------------------------------------------------

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(voice = VoiceState.THINKING, micLabel = "Thinking…") }
            val outcome = withContext(Dispatchers.IO) { graph.engine.handle(trimmed, fromVoice = false) }
            _state.update {
                it.copy(
                    lastReply = outcome.reply,
                    lastProvider = outcome.provider,
                    voice = VoiceState.IDLE,
                    micLabel = "",
                )
            }
            refresh()
            speak(outcome.reply)
        }
    }

    fun speak(text: String) {
        if (!_state.value.speakReplies) return
        if (!graph.audioGuard.canSpeak(_state.value.quietDuringMedia)) return
        viewModelScope.launch {
            val tts = graph.tts()
            if (!tts.isReady()) return@launch
            if (!graph.audioGuard.requestFocus()) return@launch
            _state.update { it.copy(voice = VoiceState.SPEAKING) }
            withContext(Dispatchers.Default) { tts.speak(text, _state.value.speechRate) }
            graph.audioGuard.abandon()
            _state.update { it.copy(voice = VoiceState.IDLE) }
        }
    }

    fun stopSpeaking() {
        graph.tts().stop()
        _state.update { it.copy(voice = VoiceState.IDLE) }
    }

    fun startListening() {
        if (_state.value.voice == VoiceState.LISTENING) {
            stopListening()
            return
        }
        viewModelScope.launch {
            if (!graph.voiceCapture.hasPermission()) {
                _state.update { it.copy(toast = "Microphone permission is needed for voice input.") }
                return@launch
            }
            val asr = graph.asr()
            if (!asr.isReady()) {
                _state.update { it.copy(toast = _state.value.modelError ?: "Voice model is still preparing.") }
                return@launch
            }
            _state.update { it.copy(voice = VoiceState.LISTENING, micLabel = "Listening…", level = 0f) }
            val utterance = withContext(Dispatchers.IO) {
                graph.voiceCapture.recordOnce(
                    waitForSpeechMs = 8000,
                    silenceMs = 900,
                    maxMs = 15000,
                    onLevel = { level -> _state.update { it.copy(level = level) } },
                )
            }
            if (utterance == null) {
                _state.update { it.copy(voice = VoiceState.IDLE, micLabel = "", level = 0f) }
                return@launch
            }
            _state.update { it.copy(voice = VoiceState.THINKING, micLabel = "Transcribing…") }
            val text = withContext(Dispatchers.Default) { asr.transcribe(utterance.samples) }
            if (text.isBlank()) {
                _state.update {
                    it.copy(voice = VoiceState.IDLE, micLabel = "", toast = "I didn't catch that — try again.")
                }
                return@launch
            }
            _state.update { it.copy(micLabel = "", voice = VoiceState.THINKING) }
            val outcome = withContext(Dispatchers.IO) { graph.engine.handle(text, fromVoice = true) }
            _state.update {
                it.copy(lastReply = outcome.reply, lastProvider = outcome.provider, voice = VoiceState.IDLE)
            }
            refresh()
            speak(outcome.reply)
        }
    }

    fun stopListening() {
        graph.voiceCapture.cancel()
        _state.update { it.copy(voice = VoiceState.IDLE, micLabel = "", level = 0f) }
    }

    // ----- tasks ------------------------------------------------------------

    fun addTask(title: String, dueAt: Long?, priority: Priority) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = withContext(Dispatchers.IO) {
                graph.tasks.add(title, reminderAt = dueAt, dueAt = dueAt, priority = priority)
            }
            graph.reminders.schedule(task)
            refresh()
        }
    }

    fun completeTask(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.tasks.complete(id, Evidence.USER_CONFIRMED) }
            graph.reminders.cancel(id)
            refresh()
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.tasks.remove(id) }
            graph.reminders.cancel(id)
            refresh()
        }
    }

    fun snoozeTask(task: TaskItem) {
        graph.reminders.snooze(task.id, task.title)
        _state.update { it.copy(toast = "Snoozed “${task.title}” for 10 minutes.") }
    }

    fun moveTaskToTomorrow(task: TaskItem) {
        viewModelScope.launch {
            val at = com.jarvis.assistant.core.TimeText.tomorrowAt(
                hour = 9,
                minute = 0,
            )
            withContext(Dispatchers.IO) { graph.tasks.reschedule(task.id, at) }
            graph.reminders.schedule((graph.tasks.byId(task.id) ?: task))
            refresh()
        }
    }

    // ----- memory -----------------------------------------------------------

    fun saveMemory(content: String, kind: String = "fact") {
        if (content.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.memories.save(null, kind, content) }
            refresh()
        }
    }

    fun updateMemory(id: String, content: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.memories.update(id, content) }
            refresh()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.memories.delete(id) }
            refresh()
        }
    }

    fun clearMemories() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.memories.clear() }
            refresh()
            _state.update { it.copy(toast = "All saved memories deleted.") }
        }
    }

    fun setMemoryLearning(enabled: Boolean) {
        graph.prefs.memoryLearning = enabled
        _state.update { it.copy(memoryLearning = enabled) }
    }

    // ----- routines ---------------------------------------------------------

    fun addRoutine(name: String, hour: Int, minute: Int, weekdays: Set<Int>, prompt: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.routines.create(name, hour, minute, weekdays, prompt) }
            refresh()
        }
    }

    fun toggleRoutine(routine: RoutineItem, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.routines.setEnabled(routine.id, enabled) }
            refresh()
        }
    }

    fun deleteRoutine(routine: RoutineItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.routines.delete(routine.id) }
            refresh()
        }
    }

    fun acceptSuggestion(suggestion: RoutineSuggestion) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graph.routines.create(
                    name = "Daily focus",
                    hour = suggestion.hour,
                    minute = 0,
                    weekdays = suggestion.weekdays,
                    prompt = suggestion.sampleTitle,
                )
                graph.tasks.add(
                    title = suggestion.sampleTitle,
                    reminderAt = com.jarvis.assistant.reminders.nextOccurrence(
                        suggestion.hour,
                        0,
                        suggestion.weekdays,
                    ),
                    dueAt = null,
                    priority = Priority.MEDIUM,
                )
            }
            refresh()
            _state.update { it.copy(toast = "Routine created — you can edit it any time.") }
        }
    }

    // ----- settings ---------------------------------------------------------

    fun setLanguage(language: String) {
        graph.prefs.language = language
        _state.update { it.copy(language = language) }
    }

    fun setSpeakReplies(enabled: Boolean) {
        graph.prefs.speakReplies = enabled
        _state.update { it.copy(speakReplies = enabled) }
        if (!enabled) graph.tts().stop()
    }

    fun setQuietDuringMedia(enabled: Boolean) {
        graph.prefs.quietDuringMedia = enabled
        _state.update { it.copy(quietDuringMedia = enabled) }
    }

    fun setEncryptMemories(enabled: Boolean) {
        graph.prefs.encryptMemories = enabled
        _state.update { it.copy(encryptMemories = enabled) }
    }

    fun setFollowUpMinutes(minutes: Int) {
        graph.prefs.followUpMinutes = minutes
        _state.update { it.copy(followUpMinutes = minutes) }
    }

    fun setSpeechRate(rate: Float) {
        graph.prefs.speechRate = rate
        _state.update { it.copy(speechRate = rate) }
    }

    fun saveBackend(url: String, token: String) {
        graph.prefs.backendUrl = url
        graph.prefs.backendToken = token
        _state.update { it.copy(backendUrl = url, backendToken = token, backendConfigured = graph.prefs.backendConfigured()) }
        _state.update { it.copy(toast = if (graph.prefs.backendConfigured()) "Backend saved." else "Backend URL and token are needed.") }
    }

    fun setWakeWord(enabled: Boolean) {
        graph.prefs.wakeWordEnabled = enabled
        _state.update { it.copy(wakeWordEnabled = enabled, listeningConfigured = enabled) }
        val context = getApplication<Application>()
        if (enabled) {
            JarvisService.start(context)
            _state.update { it.copy(toast = "Wake-word listening on. A notification keeps the microphone state visible.") }
        } else {
            JarvisService.stop(context)
            _state.update { it.copy(toast = "Wake-word listening off.") }
        }
    }

    fun refreshProviders() {
        if (!graph.backend.isConfigured()) {
            _state.update { it.copy(providerError = "Add your backend URL and token in Settings first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(providerLoading = true, providerError = null) }
            val result = withContext(Dispatchers.IO) { graph.backend.status() }
            _state.update {
                it.copy(
                    providerLoading = false,
                    providerStatus = result.data,
                    providerError = result.error,
                )
            }
        }
    }

    fun pushTasksToCloud() {
        if (!graph.backend.isConfigured()) {
            _state.update { it.copy(toast = "Add your backend URL and token first.") }
            return
        }
        viewModelScope.launch {
            val pending = _state.value.tasks
            var pushed = 0
            withContext(Dispatchers.IO) {
                for (task in pending) {
                    if (graph.backend.pushTask(task).ok) pushed += 1
                }
            }
            _state.update { it.copy(toast = "Synced $pushed task(s) to the backend.") }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.conversations.clear() }
            refresh()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graph.tasks.clearAll()
                graph.memories.clear()
                graph.conversations.clear()
                graph.routines.all().forEach { graph.routines.delete(it.id) }
            }
            refresh()
            _state.update { it.copy(toast = "All local Jarvis data deleted.") }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    override fun onCleared() {
        graph.voiceCapture.cancel()
        super.onCleared()
    }

    companion object {
        const val SAMPLE_ASR = WhisperAsr.SAMPLE_RATE
    }
}
