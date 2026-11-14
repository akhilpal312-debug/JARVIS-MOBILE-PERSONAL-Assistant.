package com.jarvis.assistant.voice

import android.content.Context
import com.jarvis.assistant.core.Prefs
import org.json.JSONObject
import java.io.File

/**
 * Prepares the speech models that ship inside the APK.
 *
 * The APK already contains every weight file (Whisper multilingual encoder +
 * decoder, Hindi and English Piper voices, espeak-ng data) under
 * assets/models. sherpa-onnx needs real filesystem paths, so on first launch
 * the models are copied once into private app storage and verified against the
 * size manifest generated at build time. No download, no separate model APK, no
 * user configuration.
 */
class ModelInstaller(private val context: Context, private val prefs: Prefs) {

    data class Paths(
        val asrEncoder: String,
        val asrDecoder: String,
        val asrTokens: String,
        val ttsHindiModel: String,
        val ttsHindiTokens: String,
        val ttsEnglishModel: String,
        val ttsEnglishTokens: String,
        val espeakData: String,
    )

    private val root = File(context.filesDir, "models")

    /** True when every bundled file is present with the expected byte size. */
    fun isReady(): Boolean = runCatching { verify(files()).isEmpty() }.getOrDefault(false)

    fun ensure(onProgress: (Float, String) -> Unit): Result<Paths> {
        return try {
            val expected = manifest()
            var completed = 0
            val total = expected.size
            for ((assetPath, size) in expected) {
                val destination = File(root, assetPath.removePrefix(PREFIX))
                onProgress(completed.toFloat() / total.coerceAtLeast(1), assetPath.substringAfterLast('/'))
                val alreadyThere = destination.exists() && (size <= 0 || destination.length() == size)
                if (!alreadyThere) {
                    if (destination.exists()) destination.delete()
                    destination.parentFile?.mkdirs()
                    context.assets.open(assetPath).use { input ->
                        destination.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                    }
                }
                completed += 1
            }

            val missing = verify(expected.keys.map { it.removePrefix(PREFIX) }.toList())
            if (missing.isNotEmpty()) {
                return Result.failure(IllegalStateException("Model verification failed: ${missing.joinToString()}"))
            }

            prefs.modelsPrepared = true
            prefs.modelsPreparedAt = System.currentTimeMillis()
            onProgress(1f, "ready")
            Result.success(paths())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun paths(): Paths = Paths(
        asrEncoder = File(root, "asr/tiny-encoder.int8.onnx").absolutePath,
        asrDecoder = File(root, "asr/tiny-decoder.int8.onnx").absolutePath,
        asrTokens = File(root, "asr/tiny-tokens.txt").absolutePath,
        ttsHindiModel = File(root, "tts/hi/model.onnx").absolutePath,
        ttsHindiTokens = File(root, "tts/hi/tokens.txt").absolutePath,
        ttsEnglishModel = File(root, "tts/en/model.onnx").absolutePath,
        ttsEnglishTokens = File(root, "tts/en/tokens.txt").absolutePath,
        espeakData = File(root, "tts/espeak-ng-data").absolutePath,
    )

    /** Files that are missing or have a wrong size. */
    fun verify(relativePaths: List<String>): List<String> = relativePaths.filter { relative ->
        val file = File(root, relative)
        if (!file.exists()) return@filter true
        val expected = manifest()[PREFIX + relative] ?: return@filter false
        expected > 0 && file.length() != expected
    }

    fun installedBytes(): Long = files().sumOf { File(root, it).length() }

    fun expectedBytes(): Long = manifest().values.sumOf { it.toLong() }

    fun clear() {
        root.deleteRecursively()
        prefs.modelsPrepared = false
    }

    private fun files(): List<String> {
        val result = mutableListOf<String>()
        collect("models", result)
        return result.map { it.removePrefix(PREFIX) }
    }

    private fun collect(path: String, out: MutableList<String>) {
        val children = context.assets.list(path) ?: return
        if (children.isEmpty()) {
            out.add(path)
            return
        }
        for (child in children) collect("$path/$child", out)
    }

    /** assetPath -> byte size, produced by tools/build_model_manifest.py. */
    private fun manifest(): Map<String, Long> {
        val json = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
        val objectJson = JSONObject(json)
        val map = mutableMapOf<String, Long>()
        val keys = objectJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = objectJson.optLong(key, 0L)
        }
        return map
    }

    private companion object {
        const val PREFIX = "models/"
        const val MANIFEST_ASSET = "models/manifest.json"
    }
}
