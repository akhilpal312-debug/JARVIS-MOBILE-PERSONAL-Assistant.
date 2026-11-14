package com.jarvis.assistant

import android.app.Application
import android.content.Context
import android.media.AudioManager
import com.jarvis.assistant.actions.DeviceActions
import com.jarvis.assistant.ai.AssistantEngine
import com.jarvis.assistant.ai.BackendClient
import com.jarvis.assistant.core.Prefs
import com.jarvis.assistant.data.ConversationRepository
import com.jarvis.assistant.data.JarvisDb
import com.jarvis.assistant.data.MemoryRepository
import com.jarvis.assistant.data.RoutineRepository
import com.jarvis.assistant.data.TaskRepository
import com.jarvis.assistant.reminders.ReminderScheduler
import com.jarvis.assistant.service.Notifications
import com.jarvis.assistant.voice.AudioGuard
import com.jarvis.assistant.voice.ModelInstaller
import com.jarvis.assistant.voice.PiperTts
import com.jarvis.assistant.voice.VoiceCapture
import com.jarvis.assistant.voice.WhisperAsr

/**
 * Manual dependency graph. Deliberately explicit instead of an annotation
 * processor: fewer moving parts, faster cold start, and every dependency is
 * visible in one place.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val prefs = Prefs(appContext)
    val db = JarvisDb(appContext)
    val tasks = TaskRepository(db)
    val memories = MemoryRepository(db, prefs)
    val routines = RoutineRepository(db)
    val conversations = ConversationRepository(db)
    val backend = BackendClient(prefs)
    val actions = DeviceActions(appContext)
    val reminders = ReminderScheduler(appContext)
    val audioGuard = AudioGuard(appContext)
    val models = ModelInstaller(appContext, prefs)
    val voiceCapture = VoiceCapture(appContext)
    val engine = AssistantEngine(tasks, memories, conversations, backend, actions, reminders, prefs)

    @Volatile
    private var asrEngine: WhisperAsr? = null

    @Volatile
    private var ttsEngine: PiperTts? = null

    @Synchronized
    fun asr(): WhisperAsr {
        return asrEngine ?: WhisperAsr(models.paths()).also { asrEngine = it }
    }

    @Synchronized
    fun tts(): PiperTts {
        return ttsEngine ?: PiperTts(
            models.paths(),
            appContext.getSystemService(AudioManager::class.java),
        ).also { ttsEngine = it }
    }

    fun languageHindi(): Boolean = prefs.language == "hi"
}

class JarvisApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.ensureChannels(this)
    }
}
