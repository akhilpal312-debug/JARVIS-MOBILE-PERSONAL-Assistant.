package com.jarvis.assistant.core

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * User settings and device identity. The only secret stored here is the
 * deployment token for the user's own backend; LLM provider keys never leave
 * the server, so the app has nothing else to protect.
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            val generated = "dev_" + UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trim().trimEnd('/')).apply()

    var backendToken: String
        get() = prefs.getString(KEY_BACKEND_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BACKEND_TOKEN, value.trim()).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD, value).apply()

    var speakReplies: Boolean
        get() = prefs.getBoolean(KEY_SPEAK, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAK, value).apply()

    /** Suppress speech while music/video is playing and respect audio focus. */
    var quietDuringMedia: Boolean
        get() = prefs.getBoolean(KEY_QUIET_MEDIA, true)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_MEDIA, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Memory learning is on by default but can be paused at any time. */
    var memoryLearning: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_LEARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_LEARNING, value).apply()

    var encryptMemories: Boolean
        get() = prefs.getBoolean(KEY_ENCRYPT_MEMORY, true)
        set(value) = prefs.edit().putBoolean(KEY_ENCRYPT_MEMORY, value).apply()

    /** Follow-up reminder minutes, 0 disables follow-ups. */
    var followUpMinutes: Int
        get() = prefs.getInt(KEY_FOLLOW_UP, 15)
        set(value) = prefs.edit().putInt(KEY_FOLLOW_UP, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var modelsPrepared: Boolean
        get() = prefs.getBoolean(KEY_MODELS_READY, false)
        set(value) = prefs.edit().putBoolean(KEY_MODELS_READY, value).apply()

    var modelsPreparedAt: Long
        get() = prefs.getLong(KEY_MODELS_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_MODELS_AT, value).apply()

    fun backendConfigured(): Boolean = backendUrl.isNotBlank() && backendToken.isNotBlank()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_BACKEND_TOKEN = "backend_token"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_SPEAK = "speak_replies"
        const val KEY_QUIET_MEDIA = "quiet_media"
        const val KEY_LANGUAGE = "language"
        const val KEY_MEMORY_LEARNING = "memory_learning"
        const val KEY_ENCRYPT_MEMORY = "encrypt_memory"
        const val KEY_FOLLOW_UP = "follow_up_minutes"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_MODELS_READY = "models_ready"
        const val KEY_MODELS_AT = "models_prepared_at"
    }
}
