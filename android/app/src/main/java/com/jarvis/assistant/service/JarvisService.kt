package com.jarvis.assistant.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.core.Prefs
import com.jarvis.assistant.voice.ModelInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background assistant.
 *
 * Runs the wake-word loop entirely on the device: VAD-woken microphone capture
 * → local Whisper transcription → local "Jarvis" keyword match → local command
 * handling, with the backend used only when on-device parsing cannot help.
 *
 * Only active after the user turns it on, shows a persistent notification with
 * its microphone state and a stop action, pauses during phone calls, and never
 * uploads background audio. Android requires a foreground service for
 * microphone access outside the UI, which is what this is.
 */
class JarvisService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    @Volatile
    private var listening = false

    private lateinit var graph: com.jarvis.assistant.AppGraph
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        graph = (application as JarvisApp).graph
        prefs = graph.prefs
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.wakeWordEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(Notifications.ASSISTANT_NOTIFICATION_ID, Notifications.assistantNotification(this, false))

        if (!prefs.wakeWordEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            prefs.wakeWordEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (loopJob == null) startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        loopJob = scope.launch {
            val installer = graph.models
            val asr = graph.asr()
            val tts = graph.tts()

            if (!installer.isReady()) {
                val installed = withContext(Dispatchers.IO) { installer.ensure { _, _ -> } }
                if (installed.isFailure) {
                    Log.e(TAG, "models unavailable", installed.exceptionOrNull())
                    stopSelf()
                    return@launch
                }
            }
            asr.load()
            tts.load()

            while (isActive && prefs.wakeWordEnabled) {
                // Never listen or speak while the user is on a call.
                if (graph.audioGuard.inCall()) {
                    updateNotification(false)
                    delay(4000)
                    continue
                }
                updateNotification(true)
                val utterance = withContext(Dispatchers.IO) {
                    graph.voiceCapture.recordOnce(waitForSpeechMs = 15000, silenceMs = 800, maxMs = 8000)
                } ?: continue

                val transcript = withContext(Dispatchers.Default) { asr.transcribe(utterance.samples) }
                if (!WakeWord.contains(transcript)) continue

                updateNotification(true)
                val commandText = WakeWord.strip(transcript)
                val handled = if (commandText.isNotBlank()) {
                    respond(commandText, tts)
                    true
                } else {
                    false
                }
                if (!handled) {
                    // The user only said the wake word: capture the actual command.
                    val commandUtterance = withContext(Dispatchers.IO) {
                        graph.voiceCapture.recordOnce(waitForSpeechMs = 6000, silenceMs = 800, maxMs = 12000)
                    } ?: continue
                    val command = withContext(Dispatchers.Default) { asr.transcribe(commandUtterance.samples) }
                    if (command.isNotBlank()) respond(command, tts)
                }
            }
            listening = false
        }
    }

    private suspend fun respond(text: String, tts: com.jarvis.assistant.voice.PiperTts) {
        val outcome = graph.engine.handle(text, fromVoice = true)
        updateNotification(true)
        if (prefs.speakReplies && prefs.wakeWordEnabled && graph.audioGuard.canSpeak(prefs.quietDuringMedia)) {
            if (graph.audioGuard.requestFocus()) {
                withContext(Dispatchers.Default) { tts.speak(outcome.reply, prefs.speechRate) }
                graph.audioGuard.abandon()
            }
        }
        sendBroadcast(Intent(ACTION_REFRESH).setPackage(packageName))
    }

    private fun updateNotification(isListening: Boolean) {
        if (listening == isListening) return
        listening = isListening
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                NotificationManagerCompat.from(this).notify(
                    Notifications.ASSISTANT_NOTIFICATION_ID,
                    Notifications.assistantNotification(this, isListening),
                )
            }
        }
    }

    override fun onDestroy() {
        graph.voiceCapture.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.jarvis.assistant.action.START_LISTENING"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP_LISTENING"
        const val ACTION_REFRESH = "com.jarvis.assistant.action.REFRESH"
        private const val TAG = "JarvisService"

        fun start(context: Context) {
            val intent = Intent(context, JarvisService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, JarvisService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** Local wake-word matching on the offline transcript. */
object WakeWord {

    private val variants = listOf("jarvis", "जार्विस", "जारविस", "jaarvis", "jervis", "jarvis,")

    fun contains(transcript: String): Boolean {
        val normalized = transcript.lowercase()
        return variants.any { normalized.contains(it) }
    }

    fun strip(transcript: String): String {
        var result = transcript
        for (variant in variants) {
            result = result.replace(variant, " ", ignoreCase = true)
        }
        return result.trim(' ', ',', '.', '!', '?', '।')
    }

    /** Model paths are prepared lazily; exposed for the UI's model status card. */
    fun installer(graph: com.jarvis.assistant.AppGraph): ModelInstaller = graph.models
}
