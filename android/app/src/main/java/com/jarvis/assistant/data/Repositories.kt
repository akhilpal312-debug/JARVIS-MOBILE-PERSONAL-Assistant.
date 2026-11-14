package com.jarvis.assistant.data

import android.content.ContentValues
import android.database.Cursor
import com.jarvis.assistant.core.Prefs
import com.jarvis.assistant.core.SecureStore
import java.util.UUID
import kotlin.math.abs

private fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
private fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

private fun Cursor.toTask(): TaskItem = TaskItem(
    id = text("id"),
    title = text("title"),
    notes = text("notes"),
    status = TaskStatus.from(text("status")),
    priority = Priority.from(text("priority")),
    dueAt = longOrNull("due_at"),
    reminderAt = longOrNull("reminder_at"),
    completedAt = longOrNull("completed_at"),
    evidence = text("evidence"),
    createdAt = getLong(getColumnIndexOrThrow("created_at")),
    updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
)

class TaskRepository(private val db: JarvisDb) {

    fun active(): List<TaskItem> {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE status != 'completed' ORDER BY (due_at IS NULL), due_at ASC, updated_at DESC",
            null,
        )
        return cursor.use { listOfTasks(it) }
    }

    fun completed(limit: Int = 200): List<TaskItem> {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE status = 'completed' ORDER BY completed_at DESC LIMIT $limit",
            null,
        )
        return cursor.use { listOfTasks(it) }
    }

    private fun listOfTasks(cursor: Cursor): List<TaskItem> {
        val items = mutableListOf<TaskItem>()
        while (cursor.moveToNext()) items.add(cursor.toTask())
        return items
    }

    fun byId(id: String): TaskItem? {
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM tasks WHERE id = ? LIMIT 1", arrayOf(id))
        return cursor.use { if (it.moveToNext()) it.toTask() else null }
    }

    fun withReminders(): List<TaskItem> =
        active().filter { it.reminderAt != null || it.dueAt != null }

    fun upsert(task: TaskItem): TaskItem {
        val now = System.currentTimeMillis()
        val record = task.copy(updatedAt = now)
        db.writableDatabase.insertWithOnConflict("tasks", null, record.toValues(), 5)
        return record
    }

    fun add(title: String, notes: String = "", dueAt: Long?, reminderAt: Long?, priority: Priority): TaskItem {
        val now = System.currentTimeMillis()
        val task = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            notes = notes.trim(),
            status = if (reminderAt != null || dueAt != null) TaskStatus.SCHEDULED else TaskStatus.PENDING,
            priority = priority,
            dueAt = dueAt,
            reminderAt = reminderAt ?: dueAt,
            createdAt = now,
            updatedAt = now,
        )
        return upsert(task)
    }

    /**
     * Marks a task complete. A completion always needs evidence: an explicit user
     * confirmation, a voice confirmation, or a verified device action result.
     * The row stays in the database as history and disappears from the active list.
     */
    fun complete(id: String, evidence: String): TaskItem? {
        val existing = byId(id) ?: return null
        if (evidence.isBlank()) return null
        val done = existing.copy(
            status = TaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            evidence = evidence,
            updatedAt = System.currentTimeMillis(),
        )
        upsert(done)
        recordCompletion(done)
        return done
    }

    fun updateStatus(id: String, status: TaskStatus): TaskItem? {
        val existing = byId(id) ?: return null
        return if (status == TaskStatus.COMPLETED) {
            complete(id, Evidence.USER_CONFIRMED)
        } else {
            upsert(existing.copy(status = status, completedAt = null, updatedAt = System.currentTimeMillis()))
        }
    }

    fun reschedule(id: String, reminderAt: Long): TaskItem? {
        val existing = byId(id) ?: return null
        return upsert(
            existing.copy(
                reminderAt = reminderAt,
                dueAt = reminderAt,
                status = if (existing.status.isActive) TaskStatus.SCHEDULED else existing.status,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun remove(id: String): Boolean = db.writableDatabase.delete("tasks", "id = ?", arrayOf(id)) > 0

    fun findBestMatch(reference: String): TaskItem? {
        val needle = reference.trim().lowercase()
        if (needle.isBlank()) return null
        val active = active()
        active.firstOrNull { it.title.lowercase() == needle }?.let { return it }
        active.firstOrNull { it.title.lowercase().contains(needle) }?.let { return it }
        active.firstOrNull { needle.contains(it.title.lowercase()) }?.let { return it }
        // Fall back to a simple token overlap score.
        return active
            .map { task -> task to similarity(task.title.lowercase(), needle) }
            .filter { it.second >= 0.5 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun similarity(a: String, b: String): Double {
        val first = a.split(' ').filter { it.length > 2 }.toSet()
        val second = b.split(' ').filter { it.length > 2 }.toSet()
        if (first.isEmpty() || second.isEmpty()) return 0.0
        val shared = first.intersect(second).size
        return shared.toDouble() / maxOf(first.size, second.size)
    }

    private fun recordCompletion(task: TaskItem) {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = task.completedAt ?: System.currentTimeMillis()
        }
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val weekday = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        db.writableDatabase.execSQL(
            """
            INSERT INTO completion_stats (hour, weekday, count, last_title, last_at)
            VALUES (?, ?, 1, ?, ?)
            ON CONFLICT(hour, weekday) DO UPDATE SET
                count = count + 1,
                last_title = excluded.last_title,
                last_at = excluded.last_at
            """.trimIndent(),
        arrayOf(hour, weekday, task.title, System.currentTimeMillis()),
        )
    }

    /** Habits the user has repeated at least three times; offered, never applied silently. */
    fun routineSuggestions(routines: List<RoutineItem>): List<RoutineSuggestion> {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT hour, weekday, count, last_title FROM completion_stats WHERE count >= 3 ORDER BY count DESC LIMIT 6",
            null,
        )
        val grouped = mutableMapOf<Int, RoutineSuggestion>()
        cursor.use {
            while (it.moveToNext()) {
                val hour = it.getInt(0)
                val weekday = it.getInt(1)
                val count = it.getInt(2)
                val title = it.getString(3) ?: ""
                if (routines.any { routine -> routine.hour == hour }) continue
                val existing = grouped[hour]
                grouped[hour] = if (existing == null) {
                    RoutineSuggestion(hour, setOf(weekday), count, title)
                } else {
                    existing.copy(weekdays = existing.weekdays + weekday, sampleCount = abs(existing.sampleCount + count))
                }
            }
        }
        return grouped.values.toList()
    }

    fun clearAll() {
        db.writableDatabase.delete("tasks", null, null)
        db.writableDatabase.delete("completion_stats", null, null)
    }

    private fun TaskItem.toValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("notes", notes)
        put("status", status.wire)
        put("priority", priority.wire)
        dueAt?.let { put("due_at", it) } ?: putNull("due_at")
        reminderAt?.let { put("reminder_at", it) } ?: putNull("reminder_at")
        completedAt?.let { put("completed_at", it) } ?: putNull("completed_at")
        put("evidence", evidence)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }
}

class MemoryRepository(private val db: JarvisDb, private val prefs: Prefs) {

    fun all(): List<MemoryItem> {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM memories ORDER BY updated_at DESC LIMIT 300",
            null,
        )
        val items = mutableListOf<MemoryItem>()
        cursor.use {
            while (it.moveToNext()) {
                val stored = it.getString(it.getColumnIndexOrThrow("content")) ?: ""
                items.add(
                    MemoryItem(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        kind = it.getString(it.getColumnIndexOrThrow("kind")) ?: "fact",
                        content = SecureStore.decrypt(stored),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                        updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at")),
                    ),
                )
            }
        }
        return items
    }

    fun save(id: String?, kind: String, content: String): MemoryItem? {
        if (!prefs.memoryLearning) return null
        val text = content.trim()
        if (text.isEmpty()) return null
        val now = System.currentTimeMillis()
        val memoryId = id ?: UUID.randomUUID().toString()
        val stored = if (prefs.encryptMemories) SecureStore.encrypt(text) else "plain:$text"
        val values = ContentValues().apply {
            put("id", memoryId)
            put("kind", kind)
            put("content", stored)
            put("created_at", now)
            put("updated_at", now)
        }
        db.writableDatabase.insertWithOnConflict("memories", null, values, 5)
        return MemoryItem(memoryId, kind, text, now, now)
    }

    fun update(id: String, content: String, kind: String? = null) {
        val stored = if (prefs.encryptMemories) SecureStore.encrypt(content) else "plain:$content"
        val values = ContentValues().apply {
            put("content", stored)
            put("updated_at", System.currentTimeMillis())
            kind?.let { put("kind", it) }
        }
        db.writableDatabase.update("memories", values, "id = ?", arrayOf(id))
    }

    fun delete(id: String) {
        db.writableDatabase.delete("memories", "id = ?", arrayOf(id))
    }

    fun clear() {
        db.writableDatabase.delete("memories", null, null)
    }
}

class RoutineRepository(private val db: JarvisDb) {

    fun all(): List<RoutineItem> {
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM routines ORDER BY hour, minute", null)
        val items = mutableListOf<RoutineItem>()
        cursor.use {
            while (it.moveToNext()) {
                items.add(
                    RoutineItem(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        hour = it.getInt(it.getColumnIndexOrThrow("hour")),
                        minute = it.getInt(it.getColumnIndexOrThrow("minute")),
                        weekdays = (it.getString(it.getColumnIndexOrThrow("weekdays")) ?: "")
                            .split(',')
                            .mapNotNull { value -> value.toIntOrNull() }
                            .toSet(),
                        enabled = it.getInt(it.getColumnIndexOrThrow("enabled")) == 1,
                        prompt = it.getString(it.getColumnIndexOrThrow("prompt")) ?: "",
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    ),
                )
            }
        }
        return items
    }

    fun save(routine: RoutineItem): RoutineItem {
        val values = ContentValues().apply {
            put("id", routine.id)
            put("name", routine.name)
            put("hour", routine.hour)
            put("minute", routine.minute)
            put("weekdays", routine.weekdays.joinToString(","))
            put("enabled", if (routine.enabled) 1 else 0)
            put("prompt", routine.prompt)
            put("created_at", routine.createdAt)
        }
        db.writableDatabase.insertWithOnConflict("routines", null, values, 5)
        return routine
    }

    fun create(name: String, hour: Int, minute: Int, weekdays: Set<Int>, prompt: String): RoutineItem =
        save(
            RoutineItem(
                id = UUID.randomUUID().toString(),
                name = name,
                hour = hour,
                minute = minute,
                weekdays = weekdays,
                enabled = true,
                prompt = prompt,
                createdAt = System.currentTimeMillis(),
            ),
        )

    fun setEnabled(id: String, enabled: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE routines SET enabled = ? WHERE id = ?",
            arrayOf(if (enabled) 1 else 0, id),
        )
    }

    fun delete(id: String) {
        db.writableDatabase.delete("routines", "id = ?", arrayOf(id))
    }
}

class ConversationRepository(private val db: JarvisDb) {

    fun recent(limit: Int = 100): List<ChatMessage> {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM messages ORDER BY created_at DESC LIMIT $limit",
            null,
        )
        val items = mutableListOf<ChatMessage>()
        cursor.use {
            while (it.moveToNext()) {
                items.add(
                    ChatMessage(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        role = it.getString(it.getColumnIndexOrThrow("role")) ?: "user",
                        content = it.getString(it.getColumnIndexOrThrow("content")) ?: "",
                        provider = it.getString(it.getColumnIndexOrThrow("provider")) ?: "",
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    ),
                )
            }
        }
        return items.reversed()
    }

    fun add(role: String, content: String, provider: String = ""): ChatMessage {
        val message = ChatMessage(UUID.randomUUID().toString(), role, content, provider)
        val values = ContentValues().apply {
            put("id", message.id)
            put("role", message.role)
            put("content", message.content)
            put("provider", message.provider)
            put("created_at", message.createdAt)
        }
        db.writableDatabase.insert("messages", null, values)
        return message
    }

    fun clear() {
        db.writableDatabase.delete("messages", null, null)
    }
}
