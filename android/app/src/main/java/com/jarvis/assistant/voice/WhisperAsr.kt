package com.jarvis.assistant.voice

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/**
 * Offline multilingual speech recognition (Whisper tiny, int8) through the
 * bundled sherpa-onnx runtime. The model is multilingual, so Hindi, English and
 * naturally mixed Hinglish are transcribed by the same local model.
 *
 * Nothing is uploaded: audio is decoded on the device.
 */
class WhisperAsr(private val paths: ModelInstaller.Paths) {

    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var loaded = false

    fun load(): String? {
        if (loaded) return null
        return try {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = paths.asrEncoder,
                        decoder = paths.asrDecoder,
                        language = "", // empty => auto-detect hi/en between utterances
                        task = "transcribe",
                        tailPaddings = -1,
                    ),
                    tokens = paths.asrTokens,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            )
            recognizer = OfflineRecognizer(config = config)
            loaded = true
            null
        } catch (error: Throwable) {
            Log.e(TAG, "ASR load failed", error)
            recognizer = null
            loaded = false
            "Speech recognition model could not be loaded (${error.message ?: "unknown error"})."
        }
    }

    fun isReady(): Boolean = loaded

    /** Transcribes 16 kHz mono float samples. Returns "" when nothing was heard. */
    fun transcribe(samples: FloatArray): String {
        val engine = recognizer ?: return ""
        if (samples.isEmpty()) return ""
        return try {
            val stream = engine.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                engine.decode(stream)
                engine.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "decode failed", error)
            ""
        }
    }

    fun close() {
        runCatching { recognizer?.release() }
        recognizer = null
        loaded = false
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val TAG = "WhisperAsr"
    }
}
