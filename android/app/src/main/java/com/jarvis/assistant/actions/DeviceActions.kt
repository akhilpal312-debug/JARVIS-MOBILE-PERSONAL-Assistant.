package com.jarvis.assistant.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.jarvis.assistant.ai.RemoteAction

/**
 * Android actions the assistant is allowed to perform.
 *
 * Everything here uses public, documented APIs and intents that keep the user in
 * control: the dialer and calendar insert screen are opened for confirmation,
 * media is toggled through the public media-key API, and only launcher
 * activities of installed apps can be started (declared via <queries>).
 * No accessibility service, hidden API or privileged permission is used.
 */
class DeviceActions(private val context: Context) {

    data class Result(val ok: Boolean, val message: String)

    fun execute(action: RemoteAction): Result = when (action.type) {
        "open_app" -> openApp(action.target)
        "open_settings" -> openSettings()
        "calendar_event" -> addCalendarEvent(
            action.title ?: action.target.ifBlank { "Jarvis event" },
            action.startAt ?: (System.currentTimeMillis() + 60 * 60 * 1000),
        )
        "dial" -> dial(action.target)
        "media" -> mediaToggle()
        else -> Result(false, "I can't perform that action on this device.")
    }

    fun openApp(query: String): Result {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return Result(false, "Which app should I open?")
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = packageManager.queryIntentActivities(intent, 0)
        val exact = matches.firstOrNull { it.loadLabel(packageManager).toString().lowercase() == needle }
        val partial = exact ?: matches.firstOrNull {
            it.loadLabel(packageManager).toString().lowercase().contains(needle)
        }
        val target = partial ?: return Result(false, "I couldn't find an app called “$query”.")
        return try {
            val launch = packageManager.getLaunchIntentForPackage(target.activityInfo.packageName)
                ?: return Result(false, "${target.loadLabel(packageManager)} has no launchable screen.")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            Result(true, "Opened ${target.loadLabel(packageManager)}.")
        } catch (error: Exception) {
            Log.w(TAG, "openApp failed", error)
            Result(false, "Android blocked opening that app.")
        }
    }

    fun openSettings(): Result = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Result(true, "Opened Jarvis settings.")
    } catch (error: Exception) {
        Result(false, "Settings could not be opened.")
    }

    fun addCalendarEvent(title: String, startAt: Long): Result {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return Result(false, "Calendar permission isn't granted, so I can't add events yet.")
        }
        return try {
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startAt)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startAt + 60 * 60 * 1000)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result(true, "I opened the calendar with “$title”. Confirm there to save it.")
        } catch (error: Exception) {
            Result(false, "No calendar app is available to handle that.")
        }
    }

    fun dial(number: String): Result {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        if (cleaned.isEmpty()) return Result(false, "I need a number to dial.")
        return try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            Result(true, "Opened the dialer with $cleaned. Press call to continue.")
        } catch (error: Exception) {
            Result(false, "The dialer isn't available on this device.")
        }
    }

    fun mediaToggle(): Result = try {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
        Result(true, "Toggled media playback.")
    } catch (error: Exception) {
        Result(false, "This device doesn't let apps control media playback.")
    }

    private companion object {
        const val TAG = "DeviceActions"
    }
}
