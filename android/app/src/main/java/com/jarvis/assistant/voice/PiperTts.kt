package com.jarvis.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/**
 * Offline neural speech output. Two bundled Piper (VITS) voices are shipped:
 * Hindi (hi_IN) and English (en_US). The voice is chosen from the script of the
 * reply, so Hinglish answers are spoken with the Hindi voice, which handles
 * Latin-script Hindi words acceptably while keeping Devanagari natural.
 */
class PiperTts(
    private val paths: ModelInstaller.Paths,
    private val audioManager: AudioManager,
) {

    private var hindi: OfflineTts? = null
    private var english: OfflineTts? = null
    private var track: AudioTrack? = null

    @Volatile
    private var stopped = false

    fun load(): String? {
        return try {
            if (hindi == null) {
                hindi = OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(
                                model = paths.ttsHindiModel,
                                tokens = paths.ttsHindiTokens,
                                dataDir = paths.espeakData,
                                lengthScale = 1.0f,
                            ),
                            numThreads = 2,
                            provider = "cpu",
                        ),
                        maxNumSentences = 1,
                    ),
                )
            }
            if (english == null) {
                english = OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(
                                model = paths.ttsEnglishModel,
                                tokens = paths.ttsEnglishTokens,
                                dataDir = paths.espeakData,
                                lengthScale = 1.0f,
                            ),
                            numThreads = 2,
                            provider = "cpu",
                        ),
                        maxNumSentences = 1,
                    ),
                )
            }
            null
        } catch (error: Throwable) {
            Log.e(TAG, "TTS load failed", error)
            "Voice output model could not be loaded (${error.message ?: "unknown error"})."
        }
    }

    fun isReady(): Boolean = hindi != null && english != null

    /** Synthesizes and plays [text] on the current thread. Returns false if it could not speak. */
    fun speak(text: String, speed: Float = 1.0f): Boolean {
        val clean = sanitize(text)
        if (clean.isBlank()) return false
        val engine = if (isDevanagari(clean)) hindi else english
        val chosen = engine ?: hindi ?: english ?: return false
        stopped = false
        return try {
            val audio = chosen.generate(clean, sid = 0, speed = speed.coerceIn(0.6f, 1.6f))
            if (audio.samples.isEmpty()) return false
            play(audio.samples, audio.sampleRate)
            true
        } catch (error: Throwable) {
            Log.e(TAG, "speak failed", error)
            false
        }
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(samples.size * 4)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        try {
            audioTrack.play()
            var offset = 0
            val chunk = 2048
            while (offset < samples.size && !stopped) {
                val end = minOf(offset + chunk, samples.size)
                val written = audioTrack.write(samples, offset, end - offset, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
        } finally {
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            track = null
        }
    }

    fun stop() {
        stopped = true
        runCatching { track?.pause() }
        runCatching { track?.flush() }
    }

    fun close() {
        stop()
        runCatching { hindi?.release() }
        runCatching { english?.release() }
        hindi = null
        english = null
    }

    private fun sanitize(text: String): String {
        // Strip markdown, emoji-free technical noise and URLs so speech stays natural.
        return text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[*_#`]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(600)
    }

    private fun isDevanagari(text: String): Boolean = text.any { it.code in 0x0900..0x097F }

    companion object {
        private const val TAG = "PiperTts"
    }
}
