package com.jarvis.assistant.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.core.Prefs
import com.jarvis.assistant.core.TimeText
import com.jarvis.assistant.data.Evidence
import com.jarvis.assistant.data.TaskItem
import com.jarvis.assistant.data.TaskRepository
import com.jarvis.assistant.data.TaskStatus
import com.jarvis.assistant.service.Notifications
import java.util.Calendar

/**
 * Schedules task reminders with AlarmManager.
 *
 * Exact alarms are used when the user has granted the exact-alarm permission;
 * otherwise Android's inexact-but-doze-safe alarm is used and the UI says so.
 * Every reminder includes Done / Snooze / Open actions, so nothing is forced on
 * the user and no app is opened without a tap.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(task: TaskItem) {
        val at = task.reminderAt ?: task.dueAt ?: return
        if (!task.status.isActive) {
            cancel(task.id)
            return
        }
        // Ignore stale times (for example a reminder missed while the phone was off).
        if (at <= System.currentTimeMillis()) return
        setAlarm(task.id, task.title, at, task.id.hashCode(), followUp = false)
    }

    fun scheduleFollowUp(taskId: String, title: String, minutes: Int) {
        if (minutes <= 0) return
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(taskId, title, at, taskId.hashCode() + 7, followUp = true)
    }

    fun snooze(taskId: String, title: String, minutes: Int = 10) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(taskId, title, at, taskId.hashCode() + 3, followUp = false)
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(pendingIntent(taskId, "", 0, taskId.hashCode(), followUp = false))
        alarmManager.cancel(pendingIntent(taskId, "", 0, taskId.hashCode() + 7, followUp = true))
        Notifications.cancel(context, taskId.hashCode())
    }

    fun rescheduleAll(tasks: List<TaskItem>) {
        for (task in tasks) schedule(task)
    }

    private fun setAlarm(taskId: String, title: String, at: Long, requestCode: Int, followUp: Boolean) {
        val intent = pendingIntent(taskId, title, at, requestCode, followUp)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "exact alarm refused, falling back", error)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun pendingIntent(
        taskId: String,
        title: String,
        at: Long,
        requestCode: Int,
        followUp: Boolean,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_INTERVAL_MS, at)
            putExtra(ReminderReceiver.EXTRA_FOLLOW_UP, followUp)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "ReminderScheduler"
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val followUp = intent.getBooleanExtra(EXTRA_FOLLOW_UP, false)
        val app = context.applicationContext as? JarvisApp ?: return
        val repo: TaskRepository = app.graph.tasks

        val task = repo.byId(taskId)
        // A finished or deleted task must never produce a reminder.
        if (task == null || !task.status.isActive) {
            Notifications.cancel(context, taskId.hashCode())
            return
        }

        val prefs = app.graph.prefs
        val hindi = prefs.language == "hi"
        val detail = TimeText.due(task.reminderAt ?: task.dueAt, hindi)
        Notifications.notify(
            context,
            taskId.hashCode(),
            Notifications.reminderNotification(context, task.id, task.title, detail, followUp),
        )

        if (!followUp && prefs.followUpMinutes > 0) {
            app.graph.reminders.scheduleFollowUp(task.id, task.title, prefs.followUpMinutes)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.jarvis.assistant.action.REMIND"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val EXTRA_FOLLOW_UP = "follow_up"
    }
}

/** Handles the notification's Done / Snooze actions from anywhere. */
class TaskActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val app = context.applicationContext as? JarvisApp ?: return
        val repo = app.graph.tasks
        val title = intent.getStringExtra(EXTRA_TITLE) ?: repo.byId(taskId)?.title ?: "Task"

        when (intent.action) {
            ACTION_COMPLETE -> {
                // Completion is recorded only because the user tapped Done.
                repo.complete(taskId, Evidence.USER_CONFIRMED)
                app.graph.reminders.cancel(taskId)
                Notifications.cancel(context, taskId.hashCode())
            }
            ACTION_SNOOZE -> {
                Notifications.cancel(context, taskId.hashCode())
                app.graph.reminders.snooze(taskId, title)
            }
        }
        context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_COMPLETE = "com.jarvis.assistant.action.COMPLETE_TASK"
        const val ACTION_SNOOZE = "com.jarvis.assistant.action.SNOOZE_TASK"
        const val ACTION_REFRESH = "com.jarvis.assistant.action.REFRESH"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
    }
}

/** Restores reminders after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? JarvisApp ?: return
        app.graph.reminders.rescheduleAll(app.graph.tasks.active())
    }
}

/** Convenience for routines: the next occurrence of a weekday/time pair. */
internal fun nextOccurrence(hour: Int, minute: Int, weekdays: Set<Int>): Long {
    val calendar = Calendar.getInstance()
    if (weekdays.isEmpty()) {
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }
    repeat(8) {
        if (weekdays.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
            val candidate = calendar.clone() as Calendar
            candidate.set(Calendar.HOUR_OF_DAY, hour)
            candidate.set(Calendar.MINUTE, minute)
            candidate.set(Calendar.SECOND, 0)
            if (candidate.timeInMillis > System.currentTimeMillis()) return candidate.timeInMillis
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return System.currentTimeMillis() + 24 * 60 * 60 * 1000
}

/** Marks a task as in progress when the user accepts a routine prompt. */
internal fun startTask(repo: TaskRepository, taskId: String) {
    repo.updateStatus(taskId, TaskStatus.IN_PROGRESS)
}
