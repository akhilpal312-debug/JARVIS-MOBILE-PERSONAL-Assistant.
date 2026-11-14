package com.jarvis.assistant.ai

import com.jarvis.assistant.data.Priority
import com.jarvis.assistant.data.TaskItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * On-device command understanding for Hindi, English and Hinglish.
 *
 * This is what makes task capture work with the network off: a deterministic
 * parser that recognises the natural command families the app advertises
 * (create, complete, remove, reschedule, list, remember) plus time expressions
 * in both languages. Anything it does not recognise is sent to the backend.
 */
object LocalIntents {

    sealed interface Intent {
        data class Create(val title: String, val at: Long?, val priority: Priority) : Intent
        data class Complete(val reference: String) : Intent
        data class Remove(val reference: String) : Intent
        data class Reschedule(val reference: String?, val at: Long) : Intent
        data object ListPending : Intent
        data class Remember(val content: String, val kind: String) : Intent
        data class OpenApp(val query: String) : Intent
        data object Unknown : Intent
    }

    private val LIST_WORDS = listOf(
        "pending", "what's left", "whats left", "what is left", "list tasks", "task list", "my tasks",
        "baaki", "baki", "बाकी", "kya karna hai", "kya pending", "बताओ क्या",
    )
    private val COMPLETE_WORDS = listOf(
        "complete", "completed", "mark done", "mark as done", "finished", "kar diya", "kar liya",
        "ho gaya", "pura ho", "पूरा", "पूरा हो", "khatam", "done kar", "task done",
    )
    private val REMOVE_WORDS = listOf(
        "remove task", "delete task", "remove", "delete", "hata do", "hatao", "हटाओ", "हटा दो", "cancel task",
    )
    private val RESCHEDULE_WORDS = listOf(
        "reschedule", "postpone", "snooze", "shift", "move to", "aage badha", "आगे बढ़ा", "kal kar do",
    )
    private val REMEMBER_WORDS = listOf(
        "remember", "yaad rakho", "याद रखो", "note that", "keep in mind", "note kar lo",
    )
    private val CREATE_WORDS = listOf(
        "remind me", "remind", "yaad dila", "याद दिला", "add task", "add to my task", "add a task",
        "task add", "new task", "todo", "to-do", "task banao", "टास्क", "add ", "schedule",
    )
    private val OPEN_WORDS = listOf("open ", "kholo", "खोलो", "launch ")

    fun parse(input: String, tasks: List<TaskItem>, now: Long = System.currentTimeMillis()): Intent {
        val text = input.trim()
        if (text.isBlank()) return Intent.Unknown
        val lower = text.lowercase()

        if (LIST_WORDS.any { lower.contains(it) }) return Intent.ListPending

        if (REMEMBER_WORDS.any { lower.contains(it) }) {
            val content = stripPrefixes(text, REMEMBER_WORDS).trim()
            if (content.isNotEmpty()) {
                val kind = if (content.lowercase().contains("prefer") || content.lowercase().contains("pasand")) {
                    "preference"
                } else {
                    "fact"
                }
                return Intent.Remember(content, kind)
            }
        }

        if (REMOVE_WORDS.any { lower.contains(it) }) {
            val reference = stripPrefixes(text, REMOVE_WORDS)
                .replace(Regex("(?i)\\b(task|this|that|ko|yeh|ye)\\b"), " ")
                .trim()
            return Intent.Remove(reference.ifBlank { tasks.firstOrNull()?.title.orEmpty() })
        }

        if (COMPLETE_WORDS.any { lower.contains(it) }) {
            val reference = stripPrefixes(text, COMPLETE_WORDS)
                .replace(Regex("(?i)\\b(task|this|that|ko|yeh|ye|kar|diya|mark|as|done|ho|gaya|pura)\\b"), " ")
                .trim()
            return Intent.Complete(reference.ifBlank { tasks.firstOrNull()?.title.orEmpty() })
        }

        if (RESCHEDULE_WORDS.any { lower.contains(it) }) {
            val (at, remaining) = TimeParser.extract(text, now)
            val reference = remaining
                .replace(Regex("(?i)\\b(reschedule|postpone|snooze|shift|move|to|kar|do|task|ko|aage|badha)\\b"), " ")
                .trim()
            if (at != null) return Intent.Reschedule(reference.ifBlank { null }, at)
        }

        if (CREATE_WORDS.any { lower.contains(it) }) {
            val (at, remaining) = TimeParser.extract(text, now)
            val title = stripPrefixes(remaining, CREATE_WORDS)
                .replace(Regex("(?i)^(to|that|ki|ke liye|for)\\s+"), "")
                .replace(Regex("(?i)\\b(please|plz|jarvis)\\b"), " ")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '.', ',', '!', '?', '।')
            if (title.isNotEmpty()) {
                val priority = when {
                    lower.contains("urgent") || lower.contains("jaldi") || lower.contains("important") -> Priority.HIGH
                    lower.contains("low priority") || lower.contains("baad me") -> Priority.LOW
                    else -> Priority.MEDIUM
                }
                // A reminder without an explicit time defaults to one hour from now.
                val scheduled = at ?: (now + 60 * 60 * 1000)
                return Intent.Create(title, scheduled, priority)
            }
        }

        if (OPEN_WORDS.any { lower.startsWith(it) || lower.contains(" kholo") || lower.contains("खोलो") }) {
            val query = stripPrefixes(text, OPEN_WORDS).trim()
            if (query.isNotEmpty()) return Intent.OpenApp(query)
        }

        return Intent.Unknown
    }

    private fun stripPrefixes(text: String, words: List<String>): String {
        var result = text
        for (word in words.sortedByDescending { it.length }) {
            result = result.replace(word, " ", ignoreCase = true)
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    /** Bilingual time-expression parser: "6 pm", "shaam 6 baje", "kal 18:30", "tomorrow at 9". */
    object TimeParser {

        private val clock = Regex("(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        private val meridiem = Regex("(\\d{1,2})\\s*(am|pm)", RegexOption.IGNORE_CASE)
        private val baje = Regex("(\\d{1,2})\\s*(baje|bje|बजे)", RegexOption.IGNORE_CASE)

        fun extract(text: String, now: Long): Pair<Long?, String> {
            val zone = ZoneId.systemDefault()
            var remaining = text
            var hour: Int? = null
            var minute = 0

            clock.find(text)?.let { match ->
                hour = match.groupValues[1].toIntOrNull()
                minute = match.groupValues[2].toIntOrNull() ?: 0
                remaining = remaining.replace(match.value, " ")
            } ?: meridiem.find(text)?.let { match ->
                hour = match.groupValues[1].toIntOrNull()
                val pm = match.groupValues[2].lowercase() == "pm"
                if (hour != null && pm && hour!! < 12) hour = hour!! + 12
                remaining = remaining.replace(match.value, " ")
            } ?: baje.find(text)?.let { match ->
                hour = match.groupValues[1].toIntOrNull()
                remaining = remaining.replace(match.value, " ")
            }

            val lower = text.lowercase()
            val period = when {
                listOf("subah", "morning", "सुबह").any { lower.contains(it) } -> "morning"
                listOf("dopahar", "afternoon", "दोपहर").any { lower.contains(it) } -> "afternoon"
                listOf("shaam", "evening", "शाम").any { lower.contains(it) } -> "evening"
                listOf("raat", "night", "रात").any { lower.contains(it) } -> "night"
                else -> null
            }
            if (hour != null && hour!! in 1..11) {
                hour = when (period) {
                    "morning" -> hour!!
                    "afternoon" -> hour!! + 12
                    "evening" -> if (hour!! <= 6) hour!! + 12 else hour!!
                    "night" -> hour!! + 12
                    else -> hour!!
                }
            }
            if (hour == null && period != null) {
                hour = when (period) {
                    "morning" -> 8
                    "afternoon" -> 14
                    "evening" -> 18
                    else -> 21
                }
            }

            remaining = remaining
                .replace(Regex("(?i)\\b(subah|dopahar|shaam|raat|morning|afternoon|evening|night|बजे|baje|bje)\\b"), " ")
                .replace(Regex("(?i)\\b(at|par|pe|को)\\b"), " ")

            val date = resolveDate(lower, now, zone)
            if (hour == null && date == null) return null to text

            val targetDate = date ?: LocalDate.now(zone)
            val time = LocalTime.of((hour ?: LocalDateTime.now(zone).hour).coerceIn(0, 23), minute.coerceIn(0, 59))
            var dateTime = LocalDateTime.of(targetDate, time).atZone(zone).toInstant().toEpochMilli()

            // "6 baje" said after 6 pm means tomorrow.
            if (date == null && hour != null && dateTime <= now) {
                dateTime += 24 * 60 * 60 * 1000
            }
            return dateTime to remaining
        }

        private fun resolveDate(lower: String, now: Long, zone: ZoneId): LocalDate? {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            if (listOf("parso", "day after", "परसों").any { lower.contains(it) }) return today.plusDays(2)
            if (listOf("tomorrow", "kal", "कल").any { lower.contains(it) }) return today.plusDays(1)
            if (listOf("today", "aaj", "आज", "tonight").any { lower.contains(it) }) return today
            val weekdays = mapOf(
                "monday" to DayOfWeek.MONDAY, "somvar" to DayOfWeek.MONDAY, "सोमवार" to DayOfWeek.MONDAY,
                "tuesday" to DayOfWeek.TUESDAY, "mangalvar" to DayOfWeek.TUESDAY, "मंगलवार" to DayOfWeek.TUESDAY,
                "wednesday" to DayOfWeek.WEDNESDAY, "budhvar" to DayOfWeek.WEDNESDAY, "बुधवार" to DayOfWeek.WEDNESDAY,
                "thursday" to DayOfWeek.THURSDAY, "guruvar" to DayOfWeek.THURSDAY, "गुरुवार" to DayOfWeek.THURSDAY,
                "friday" to DayOfWeek.FRIDAY, "shukravar" to DayOfWeek.FRIDAY, "शुक्रवार" to DayOfWeek.FRIDAY,
                "saturday" to DayOfWeek.SATURDAY, "shanivar" to DayOfWeek.SATURDAY, "शनिवार" to DayOfWeek.SATURDAY,
                "sunday" to DayOfWeek.SUNDAY, "ravivar" to DayOfWeek.SUNDAY, "रविवार" to DayOfWeek.SUNDAY,
            )
            for ((word, day) in weekdays) {
                if (lower.contains(word)) return today.with(TemporalAdjusters.nextOrSame(day))
            }
            return null
        }
    }
}
