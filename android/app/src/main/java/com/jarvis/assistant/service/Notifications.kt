package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.reminders.TaskActionReceiver

/** Notification channels and builders for the assistant and its reminders. */
object Notifications {

    const val CHANNEL_ASSISTANT = "jarvis_assistant"
    const val CHANNEL_REMINDERS = "jarvis_reminders"
    const val ASSISTANT_NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ASSISTANT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ASSISTANT,
                    context.getString(R.string.channel_assistant),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_assistant_desc)
                    setShowBadge(false)
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.channel_reminders),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_reminders_desc)
                    enableVibration(true)
                },
            )
        }
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            42,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Persistent notification required for the microphone foreground service. */
    fun assistantNotification(context: Context, listening: Boolean): Notification {
        ensureChannels(context)
        val stopIntent = Intent(context, JarvisService::class.java).setAction(JarvisService.ACTION_STOP)
        val stopPending = PendingIntent.getService(
            context,
            43,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (listening) {
            "Listening for “Jarvis” · audio stays on this device"
        } else {
            "Running · microphone paused"
        }
        return NotificationCompat.Builder(context, CHANNEL_ASSISTANT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.action_stop_listening), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun reminderNotification(
        context: Context,
        taskId: String,
        title: String,
        detail: String,
        followUp: Boolean,
    ): Notification {
        ensureChannels(context)
        val done = Intent(context, TaskActionReceiver::class.java)
            .setAction(TaskActionReceiver.ACTION_COMPLETE)
            .putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
        val snooze = Intent(context, TaskActionReceiver::class.java)
            .setAction(TaskActionReceiver.ACTION_SNOOZE)
            .putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(TaskActionReceiver.EXTRA_TITLE, title)

        fun pending(intent: Intent, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (followUp) "Still pending · $title" else title)
            .setContentText(detail)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.action_done), pending(done, taskId.hashCode()))
            .addAction(0, context.getString(R.string.action_snooze), pending(snooze, taskId.hashCode() + 1))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
