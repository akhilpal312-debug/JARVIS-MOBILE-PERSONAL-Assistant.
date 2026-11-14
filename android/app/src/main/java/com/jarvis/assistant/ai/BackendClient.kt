package com.jarvis.assistant.ai

import com.jarvis.assistant.core.Prefs
import com.jarvis.assistant.data.Priority
import com.jarvis.assistant.data.ProviderInfo
import com.jarvis.assistant.data.ProviderStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteTask(
    val title: String,
    val notes: String,
    val priority: Priority,
    val dueAt: Long?,
    val reminderAt: Long?,
)

data class RemoteTaskRef(val match: String, val status: String)

data class RemoteMemory(val kind: String, val content: String)

data class RemoteAction(
    val type: String,
    val target: String,
    val title: String?,
    val startAt: Long?,
)

data class RemoteCommand(
    val reply: String,
    val intent: String,
    val language: String,
    val tasks: List<RemoteTask>,
    val taskRefs: List<RemoteTaskRef>,
    val memories: List<RemoteMemory>,
    val action: RemoteAction?,
    val needsConfirmation: Boolean,
)

/** Result of one backend call: either a payload or a human-readable failure. */
data class ApiResult<T>(val data: T?, val error: String? = null) {
    val ok: Boolean get() = data != null && error == null
}

/**
 * HTTPS client for the user's own Jarvis backend.
 *
 * Only the deployment token configured by the developer/owner is stored on the
 * device — never an LLM provider key. If the backend is unreachable the caller
 * falls back to the on-device engine, so the assistant degrades instead of breaking.
 */
class BackendClient(private val prefs: Prefs) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean = prefs.backendConfigured()

    private fun call(path: String, method: String, body: JSONObject? = null): ApiResult<JSONObject> {
        if (!prefs.backendConfigured()) {
            return ApiResult(null, "Backend is not configured yet.")
        }
        val url = prefs.backendUrl.trimEnd('/') + path
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${prefs.backendToken}")
                .header("X-Device-Id", prefs.deviceId)
                .header("Accept", "application/json")
            if (body != null) {
                builder.method(method, body.toString().toRequestBody(jsonMedia))
            } else {
                builder.method(method, null)
            }
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(text).getJSONObject("error").optString("message")
                    }.getOrDefault("HTTP ${response.code}")
                    return ApiResult(null, message)
                }
                if (text.isBlank()) return ApiResult(JSONObject())
                ApiResult(JSONObject(text))
            }
        } catch (error: Exception) {
            ApiResult(null, "Backend unreachable: ${error.message ?: "network error"}")
        }
    }

    fun chat(messages: List<Pair<String, String>>, language: String): ApiResult<Pair<String, String>> {
        val array = JSONArray()
        for ((role, content) in messages) {
            array.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject()
            .put("messages", array)
            .put("language", language)
            .put("stream", false)
        val result = call("/api/assistant/chat", "POST", body)
        if (!result.ok) return ApiResult(null, result.error)
        val payload = result.data ?: return ApiResult(null, "Empty response")
        if (payload.optBoolean("degraded", false)) {
            return ApiResult(null, payload.optString("reason", "No AI provider is available right now."))
        }
        val reply = payload.optString("reply").trim()
        if (reply.isEmpty()) return ApiResult(null, "The backend returned an empty reply.")
        val provider = payload.optString("providerName").ifBlank { payload.optString("provider") }
        return ApiResult(reply to provider)
    }

    fun command(text: String): ApiResult<RemoteCommand> {
        val body = JSONObject().put("text", text)
        val result = call("/api/assistant/command", "POST", body)
        if (!result.ok) return ApiResult(null, result.error)
        val payload = result.data ?: return ApiResult(null, "Empty response")
        if (payload.optBoolean("degraded", false) || !payload.has("command")) {
            return ApiResult(null, payload.optString("reason", "The assistant could not parse that command."))
        }
        return ApiResult(parseCommand(payload.getJSONObject("command")))
    }

    fun status(): ApiResult<ProviderStatus> {
        val result = call("/api/assistant/status", "GET")
        if (!result.ok) return ApiResult(null, result.error)
        val payload = result.data ?: return ApiResult(null, "Empty response")
        val providers = mutableListOf<ProviderInfo>()
        val array = payload.optJSONArray("providers") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val models = mutableListOf<String>()
            item.optJSONArray("models")?.let { list ->
                for (m in 0 until list.length()) models.add(list.optString(m))
            }
            providers.add(
                ProviderInfo(
                    slug = item.optString("slug"),
                    name = item.optString("name"),
                    configured = item.optBoolean("configured"),
                    freeTier = item.optString("freeTier"),
                    rateLimit = item.optString("rateLimit"),
                    models = models,
                ),
            )
        }
        return ApiResult(
            ProviderStatus(
                reachable = true,
                storage = payload.optString("storage", "unknown"),
                providers = providers,
                message = payload.optJSONObject("hints")?.optString("storage").orEmpty(),
            ),
        )
    }

    fun pushTask(task: com.jarvis.assistant.data.TaskItem): ApiResult<Unit> {
        val body = JSONObject()
            .put("id", task.id)
            .put("title", task.title)
            .put("notes", task.notes)
            .put("status", task.status.wire)
            .put("priority", task.priority.wire)
        task.dueAt?.let { body.put("dueAt", it) }
        task.reminderAt?.let { body.put("reminderAt", it) }
        val result = call("/api/assistant/tasks", "POST", body)
        return if (result.ok) ApiResult(Unit) else ApiResult(null, result.error)
    }

    private fun parseCommand(json: JSONObject): RemoteCommand {
        val tasks = mutableListOf<RemoteTask>()
        json.optJSONArray("tasks")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                tasks.add(
                    RemoteTask(
                        title = item.optString("title"),
                        notes = item.optString("notes"),
                        priority = Priority.from(item.optString("priority")),
                        dueAt = if (item.isNull("dueAt")) null else item.optLong("dueAt"),
                        reminderAt = if (item.isNull("reminderAt")) null else item.optLong("reminderAt"),
                    ),
                )
            }
        }
        val refs = mutableListOf<RemoteTaskRef>()
        json.optJSONArray("taskRefs")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                refs.add(RemoteTaskRef(item.optString("match"), item.optString("status", "completed")))
            }
        }
        val memories = mutableListOf<RemoteMemory>()
        json.optJSONArray("memories")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                memories.add(RemoteMemory(item.optString("kind", "fact"), item.optString("content")))
            }
        }
        val action = json.optJSONObject("deviceAction")?.let { item ->
            RemoteAction(
                type = item.optString("type"),
                target = item.optString("target"),
                title = if (item.isNull("title")) null else item.optString("title"),
                startAt = if (item.isNull("startAt")) null else item.optLong("startAt"),
            )
        }
        return RemoteCommand(
            reply = json.optString("reply"),
            intent = json.optString("intent", "chat"),
            language = json.optString("language", "auto"),
            tasks = tasks,
            taskRefs = refs,
            memories = memories,
            action = action,
            needsConfirmation = json.optBoolean("needsConfirmation", false),
        )
    }
}
