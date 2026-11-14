package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.DeviceActions
import com.jarvis.assistant.core.Prefs
import com.jarvis.assistant.core.TimeText
import com.jarvis.assistant.data.ConversationRepository
import com.jarvis.assistant.data.Evidence
import com.jarvis.assistant.data.MemoryRepository
import com.jarvis.assistant.data.TaskItem
import com.jarvis.assistant.data.TaskRepository
import com.jarvis.assistant.data.TaskStatus
import com.jarvis.assistant.reminders.ReminderScheduler

/**
 * The assistant's decision loop.
 *
 * Order of handling:
 *  1. Explicit commands understood on-device are applied immediately — this is
 *     what keeps task capture, completion and reminders working offline.
 *  2. Otherwise the backend is asked for a structured command (which can break a
 *     complex request into several tasks), and the reply is applied locally.
 *  3. If that is unavailable, the backend is asked for a normal chat reply.
 *  4. If the network or every provider fails, the user gets an honest offline
 *     answer instead of fabricated intelligence.
 *
 * The phone's database is authoritative: a task is only completed when there is
 * real evidence (user tap, voice confirmation, or a verified action result).
 */
class AssistantEngine(
    private val tasks: TaskRepository,
    private val memories: MemoryRepository,
    private val conversations: ConversationRepository,
    private val backend: BackendClient,
    private val actions: DeviceActions,
    private val reminders: ReminderScheduler,
    private val prefs: Prefs,
) {

    data class Outcome(
        val reply: String,
        val provider: String = "local",
        val applied: List<String> = emptyList(),
        val needsConfirmation: Boolean = false,
        val offline: Boolean = false,
    )

    private var pendingAction: RemoteAction? = null

    suspend fun handle(input: String, fromVoice: Boolean): Outcome {
        val text = input.trim()
        if (text.isEmpty()) return Outcome("I didn't catch that.")

        conversations.add("user", text)

        pendingAction?.let { action ->
            if (isAffirmation(text)) {
                pendingAction = null
                val result = actions.execute(action)
                return finish(Outcome(result.message, applied = if (result.ok) listOf(action.type) else emptyList()))
            }
            if (isRejection(text)) {
                pendingAction = null
                return finish(Outcome("Theek hai, maine woh action cancel kar diya."))
            }
        }

        return when (val intent = LocalIntents.parse(text, tasks.active())) {
            is LocalIntents.Intent.ListPending -> finish(Outcome(pendingSummary()))
            is LocalIntents.Intent.Create -> finish(createTask(intent))
            is LocalIntents.Intent.Complete -> finish(completeTask(intent.reference, fromVoice))
            is LocalIntents.Intent.Remove -> finish(removeTask(intent.reference))
            is LocalIntents.Intent.Reschedule -> finish(reschedule(intent))
            is LocalIntents.Intent.Remember -> finish(remember(intent))
            is LocalIntents.Intent.OpenApp -> {
                val result = actions.openApp(intent.query)
                finish(Outcome(result.message, applied = if (result.ok) listOf("open_app") else emptyList()))
            }
            LocalIntents.Intent.Unknown -> handleWithBackend(text, fromVoice)
        }
    }

    private suspend fun handleWithBackend(text: String, fromVoice: Boolean): Outcome {
        if (!backend.isConfigured()) return finish(offlineOutcome())

        val command = backend.command(text)
        if (command.ok) {
            command.data?.let { parsed ->
                if (parsed.reply.isNotBlank() || parsed.tasks.isNotEmpty() || parsed.taskRefs.isNotEmpty()) {
                    return finish(applyCommand(parsed, fromVoice))
                }
            }
        }

        val history = conversations.recent(12).map { it.role to it.content }
        val chat = backend.chat(history, prefs.language)
        if (chat.ok) {
            val (reply, provider) = chat.data ?: return finish(offlineOutcome())
            return finish(Outcome(reply, provider = provider.ifBlank { "backend" }))
        }

        val reason = command.error ?: chat.error
        return finish(offlineOutcome(reason))
    }

    private fun offlineOutcome(reason: String? = null): Outcome {
        val list = tasks.active()
        val pending = if (list.isEmpty()) {
            "there is nothing pending right now"
        } else {
            "you have ${list.size} pending task${if (list.size == 1) "" else "s"}"
        }
        val detail = if (reason.isNullOrBlank()) "" else " ($reason)"
        return Outcome(
            "I'm working offline right now$detail. On this device I can still take tasks, mark them done and remind you — " +
                "and $pending. Try: “remind me to call mom at 7 pm” or “what's pending?”",
            offline = true,
        )
    }

    private fun createTask(intent: LocalIntents.Intent.Create): Outcome {
        val task = tasks.add(
            title = intent.title,
            reminderAt = intent.at,
            dueAt = intent.at,
            priority = intent.priority,
        )
        reminders.schedule(task)
        val whenText = TimeText.due(task.reminderAt, prefs.language == "hi")
        val reply = if (prefs.language == "hi") {
            "ठीक है — “${task.title}” ${whenText} के लिए सेव कर दिया।"
        } else {
            "Done — I saved “${task.title}” for $whenText."
        }
        return Outcome(reply, applied = listOf(task.id))
    }

    private fun completeTask(reference: String, fromVoice: Boolean): Outcome {
        val match = tasks.findBestMatch(reference)
            ?: return Outcome(
                if (tasks.active().isEmpty()) {
                    "There are no active tasks to complete."
                } else {
                    "Which task should I complete? Pending: ${tasks.active().take(3).joinToString { it.title }}."
                },
            )
        val evidence = if (fromVoice) Evidence.VOICE_CONFIRMED else Evidence.USER_CONFIRMED
        val done = tasks.complete(match.id, evidence)
            ?: return Outcome("I couldn't complete that task.")
        reminders.cancel(done.id)
        return Outcome(
            "Marked “${done.title}” complete and moved it to history. ${tasks.active().size} task(s) still open.",
            applied = listOf(done.id),
        )
    }

    private fun removeTask(reference: String): Outcome {
        val match = tasks.findBestMatch(reference) ?: return Outcome("I couldn't find that task.")
        reminders.cancel(match.id)
        tasks.remove(match.id)
        return Outcome("Removed “${match.title}” from your list.", applied = listOf(match.id))
    }

    private fun reschedule(intent: LocalIntents.Intent.Reschedule): Outcome {
        val match = intent.reference?.let { tasks.findBestMatch(it) } ?: tasks.active().firstOrNull()
            ?: return Outcome("There is no active task to move.")
        val updated = tasks.reschedule(match.id, intent.at) ?: return Outcome("I couldn't move that task.")
        reminders.schedule(updated)
        val whenText = TimeText.due(intent.at, prefs.language == "hi")
        return Outcome("Moved “${match.title}” to $whenText.", applied = listOf(match.id))
    }

    private fun remember(intent: LocalIntents.Intent.Remember): Outcome {
        if (!prefs.memoryLearning) {
            return Outcome("Memory learning is paused, so I didn't save that. You can turn it on in Settings.")
        }
        val saved = memories.save(null, intent.kind, intent.content)
            ?: return Outcome("I couldn't save that.")
        return Outcome("Saved to memory: “${saved.content}”. You can edit or delete it any time.")
    }

    private fun pendingSummary(): String {
        val list = tasks.active()
        if (list.isEmpty()) return "Nothing is pending — your list is clear."
        val top = list.take(5).joinToString("\n") { task ->
            val due = task.reminderAt ?: task.dueAt
            "• ${task.title}" + if (due != null) " — ${TimeText.due(due, false)}" else ""
        }
        return "You have ${list.size} pending task(s):\n$top"
    }

    private fun applyCommand(command: RemoteCommand, fromVoice: Boolean): Outcome {
        val applied = mutableListOf<String>()

        for (remote in command.tasks) {
            val task = tasks.add(
                title = remote.title,
                notes = remote.notes,
                dueAt = remote.dueAt,
                reminderAt = remote.reminderAt ?: remote.dueAt,
                priority = remote.priority,
            )
            reminders.schedule(task)
            applied.add(task.id)
        }

        for (ref in command.taskRefs) {
            val match = tasks.findBestMatch(ref.match) ?: continue
            when (ref.status) {
                "completed" -> {
                    val evidence = if (fromVoice) Evidence.VOICE_CONFIRMED else Evidence.USER_CONFIRMED
                    tasks.complete(match.id, evidence)?.let {
                        reminders.cancel(it.id)
                        applied.add(it.id)
                    }
                }
                "blocked" -> tasks.updateStatus(match.id, TaskStatus.BLOCKED)
                "in_progress" -> tasks.updateStatus(match.id, TaskStatus.IN_PROGRESS)
                "scheduled" -> tasks.updateStatus(match.id, TaskStatus.SCHEDULED)
                "pending" -> tasks.updateStatus(match.id, TaskStatus.PENDING)
            }
        }

        if (prefs.memoryLearning) {
            for (memory in command.memories) {
                memories.save(null, memory.kind, memory.content)
            }
        }

        val action = command.action
        if (action != null) {
            if (command.needsConfirmation) {
                pendingAction = action
                val question = command.reply.ifBlank { "Should I do that?" }
                return Outcome("$question Reply “haan” to confirm.", needsConfirmation = true)
            }
            val result = actions.execute(action)
            applied.add(action.type)
            val reply = listOf(command.reply, result.message).filter { it.isNotBlank() }.joinToString(" ")
            return Outcome(reply, applied = applied)
        }

        val reply = command.reply.ifBlank { "Done." }
        return Outcome(reply, applied = applied)
    }

    private fun finish(outcome: Outcome): Outcome {
        conversations.add("assistant", outcome.reply, outcome.provider)
        return outcome
    }

    fun cancelPendingAction() {
        pendingAction = null
    }

    private fun isAffirmation(text: String): Boolean {
        val value = text.lowercase().trim('.', '!', ' ')
        return listOf("yes", "yeah", "yep", "sure", "ok", "okay", "haan", "han", "ha", "karo", "kar do", "theek hai", "confirm", "हाँ", "हां", "ठीक है").any {
            value == it || value.startsWith("$it ")
        }
    }

    private fun isRejection(text: String): Boolean {
        val value = text.lowercase().trim('.', '!', ' ')
        return listOf("no", "nope", "cancel", "nahi", "nahin", "mat karo", "नहीं").any {
            value == it || value.startsWith("$it ")
        }
    }

    /** Latest open tasks for prompts/context. */
    fun openTasks(): List<TaskItem> = tasks.active()
}
