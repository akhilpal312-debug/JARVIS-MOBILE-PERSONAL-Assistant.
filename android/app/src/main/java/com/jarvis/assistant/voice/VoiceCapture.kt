package com.jarvis.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Microphone capture with local voice-activity detection.
 *
 * VAD is an adaptive energy gate: the first ~300 ms calibrate a noise floor, then
 * speech is detected above it. Recording stops after a silence window, so the
 * recogniser only ever runs on real utterances, which keeps CPU (and battery)
 * use low. Audio is held in memory only for as long as it takes to transcribe.
 */
class VoiceCapture(private val context: Context) {

    data class Utterance(val samples: FloatArray, val sampleRate: Int, val durationMs: Int)

    @Volatile
    private var cancelled = false
    private var recorder: AudioRecord? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Records one utterance.
     *
     * @param waitForSpeechMs how long to wait for speech to begin before giving up
     * @param silenceMs trailing silence that ends the utterance
     * @param maxMs hard cap on utterance length
     */
    fun recordOnce(
        waitForSpeechMs: Int = 6000,
        silenceMs: Int = 900,
        maxMs: Int = 12000,
        onLevel: (Float) -> Unit = {},
    ): Utterance? {
        if (!hasPermission()) return null
        cancelled = false

        val sampleRate = WhisperAsr.SAMPLE_RATE
        val frameSamples = sampleRate / 10 // 100 ms frames
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(frameSamples * 4)

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
        } catch (error: Exception) {
            Log.e(TAG, "AudioRecord unavailable", error)
            return null
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return null
        }

        recorder = audioRecord
        val collected = ArrayList<Float>(sampleRate * 4)
        val frame = ShortArray(frameSamples)
        var noiseFloor = 0.01f
        var calibrationFrames = 3
        var voicedFrames = 0
        var trailingSilenceMs = 0
        var elapsedMs = 0
        var started = false

        try {
            audioRecord.startRecording()
            while (!cancelled) {
                val read = audioRecord.read(frame, 0, frame.size)
                if (read <= 0) break
                elapsedMs += 100

                var sum = 0.0
                val floats = FloatArray(read)
                for (i in 0 until read) {
                    val value = frame[i] / 32768f
                    floats[i] = value
                    sum += (value * value).toDouble()
                }
                val rms = sqrt(sum / read).toFloat()
                onLevel(rms)

                if (calibrationFrames > 0) {
                    noiseFloor = maxOf(0.006f, (noiseFloor * 2f + rms) / 3f)
                    calibrationFrames -= 1
                }

                val threshold = maxOf(0.010f, noiseFloor * 2.2f)
                val voiced = rms > threshold

                if (!started) {
                    if (voiced) {
                        voicedFrames += 1
                        if (voicedFrames >= 2) {
                            started = true
                            // Keep a short pre-roll so the first phoneme isn't clipped.
                            collected.addAll(List(frameSamples * 2) { 0f })
                            collected.addAll(floats.toList())
                        }
                    } else {
                        voicedFrames = 0
                    }
                    if (elapsedMs >= waitForSpeechMs) return null
                } else {
                    collected.addAll(floats.toList())
                    trailingSilenceMs = if (voiced) 0 else trailingSilenceMs + 100
                    if (trailingSilenceMs >= silenceMs) break
                    if (elapsedMs >= maxMs) break
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "capture failed", error)
            return null
        } finally {
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            recorder = null
        }

        if (collected.size < sampleRate / 4) return null // shorter than 250 ms
        val samples = collected.toFloatArray()
        return Utterance(samples, sampleRate, samples.size * 1000 / sampleRate)
    }

    fun cancel() {
        cancelled = true
        runCatching { recorder?.stop() }
    }

    private companion object {
        const val TAG = "VoiceCapture"
    }
}
