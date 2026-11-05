package com.babygasu.nudge.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.babygasu.nudge.MainActivity
import com.babygasu.nudge.R
import com.babygasu.nudge.bubble.BubbleService
import com.babygasu.nudge.data.IngestOutcome
import com.babygasu.nudge.data.NudgeSettings
import com.babygasu.nudge.data.Reminder
import com.babygasu.nudge.data.signalSet
import com.babygasu.nudge.intelligence.Priority
import com.babygasu.nudge.reminders.ReminderScheduler.Companion.EXTRA_REMINDER_ID
import com.babygasu.nudge.ui.Format

object Channels {
    const val CRITICAL = "critical"
    const val REMINDERS = "reminders"
    const val QUIET = "quiet"
    const val DETECTED = "detected"
    const val BUBBLE = "bubble"
    const val TEST = "test"

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CRITICAL, "Critical reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders Nudge thinks you can't afford to miss"
            },
            NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders when they're due"
            },
            NotificationChannel(QUIET, "Quiet reminders", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Lower-priority reminders, delivered without sound"
            },
            NotificationChannel(DETECTED, "New items noticed", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tells you when Nudge creates a reminder from a message"
            },
            NotificationChannel(BUBBLE, "Floating bubble", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Required by Android while the floating bubble is shown"
            },
            NotificationChannel(TEST, "Test messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Sample messages you send yourself to test detection"
            },
        ).forEach(nm::createNotificationChannel)
    }
}

class ReminderNotifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun canPost(): Boolean =
        nm.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    @SuppressLint("MissingPermission")
    fun showReminder(r: Reminder, settings: NudgeSettings) {
        if (!canPost()) return
        val channel = when {
            r.priority < settings.interruptFrom -> Channels.QUIET
            r.priority == Priority.CRITICAL -> Channels.CRITICAL
            else -> Channels.REMINDERS
        }
        val why = r.signalSet.firstOrNull()?.description
        val line = listOfNotNull("${r.priority.label} priority", r.sourceApp, r.senderDisplay).joinToString(" · ")
        val body = listOfNotNull(line, why).joinToString("\n")
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setContentTitle(r.title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (r.priority >= Priority.HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent(r.id))
            .setAutoCancel(true)
            .addAction(0, "Done", actionIntent(NotificationActionReceiver.ACTION_DONE, r.id))
            .addAction(0, "Snooze ${Format.minutes(settings.snoozeMinutes)}", actionIntent(NotificationActionReceiver.ACTION_SNOOZE, r.id))
            .build()
        nm.notify(TAG_REMINDER, r.id.toInt(), notification)
    }

    /** A quiet heads-up that Nudge created (or escalated) a reminder, with a one-tap correction. */
    @SuppressLint("MissingPermission")
    fun showDetected(outcome: IngestOutcome) {
        if (!canPost()) return
        val r = outcome.reminder
        val heading = when (outcome) {
            is IngestOutcome.Created -> "Nudge noticed something"
            is IngestOutcome.Merged -> if (outcome.escalated) "Still waiting on you" else return
        }
        val due = Format.due(r.dueAt, r.duePrecision, System.currentTimeMillis())
        val line = listOfNotNull(r.priority.label, due, r.sourceApp).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, Channels.DETECTED)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setContentTitle("$heading: ${r.title}")
            .setContentText(line)
            .setContentIntent(openIntent(r.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Not a reminder", actionIntent(NotificationActionReceiver.ACTION_DISMISS, r.id))
            .build()
        nm.notify(TAG_DETECTED, r.id.toInt(), notification)
    }

    fun cancel(id: Long) {
        nm.cancel(TAG_REMINDER, id.toInt())
        nm.cancel(TAG_DETECTED, id.toInt())
    }

    fun bubbleServiceNotification(): Notification {
        val hide = PendingIntent.getService(
            context, 0,
            Intent(context, BubbleService::class.java).setAction(BubbleService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, Channels.BUBBLE)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setContentTitle("Nudge bubble is on")
            .setContentText("Tap the bubble to see what needs attention")
            .setOngoing(true)
            .setContentIntent(openIntent(null))
            .addAction(0, "Hide bubble", hide)
            .build()
    }

    fun openIntent(id: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (id != null) intent.putExtra(MainActivity.EXTRA_REMINDER_ID, id)
        return PendingIntent.getActivity(
            context, id?.toInt() ?: -1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_REMINDER_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val TAG_REMINDER = "reminder"
        const val TAG_DETECTED = "detected"
    }
}
