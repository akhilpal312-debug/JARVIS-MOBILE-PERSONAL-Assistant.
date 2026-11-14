package com.jarvis.assistant.data

/** Status of a task in the user's task board. */
enum class TaskStatus(val wire: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    SCHEDULED("scheduled"),
    BLOCKED("blocked"),
    COMPLETED("completed");

    val isActive: Boolean get() = this != COMPLETED

    companion object {
        fun from(value: String?): TaskStatus =
            entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

enum class Priority(val wire: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    companion object {
        fun from(value: String?): Priority = entries.firstOrNull { it.wire == value } ?: MEDIUM
    }
}

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: Priority = Priority.MEDIUM,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val completedAt: Long? = null,
    val evidence: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class MemoryItem(
    val id: String,
    val kind: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RoutineItem(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val weekdays: Set<Int>,
    val enabled: Boolean,
    val prompt: String,
    val createdAt: Long,
)

/** An inferred habit that is not a routine until the user confirms it. */
data class RoutineSuggestion(
    val hour: Int,
    val weekdays: Set<Int>,
    val sampleCount: Int,
    val sampleTitle: String,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val provider: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class ProviderInfo(
    val slug: String,
    val name: String,
    val configured: Boolean,
    val freeTier: String,
    val rateLimit: String,
    val models: List<String>,
)

data class ProviderStatus(
    val reachable: Boolean,
    val storage: String,
    val providers: List<ProviderInfo>,
    val message: String,
)

/** Evidence for why a task was marked complete. Never set without a real signal. */
object Evidence {
    const val USER_CONFIRMED = "user_confirmed"
    const val VOICE_CONFIRMED = "voice_confirmed"
}
