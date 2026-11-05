package com.babygasu.nudge.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.babygasu.nudge.container
import com.babygasu.nudge.reminders.ReminderScheduler.Companion.EXTRA_REMINDER_ID
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.container.appScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id < 0) return
        runAsync(context) { context.container.reminders.fire(id) }
    }
}

/** "Done", "Snooze" and "Not a reminder" buttons on notifications — work without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id < 0) return
        val c = context.container
        runAsync(context) {
            when (intent.action) {
                ACTION_DONE -> c.reminders.complete(id)
                ACTION_SNOOZE -> {
                    val minutes = c.settings.current().snoozeMinutes
                    c.reminders.snooze(id, System.currentTimeMillis() + minutes * 60_000L)
                }
                ACTION_DISMISS -> c.reminders.dismiss(id)
            }
            c.notifier.cancel(id)
        }
    }

    companion object {
        const val ACTION_DONE = "com.babygasu.nudge.action.DONE"
        const val ACTION_SNOOZE = "com.babygasu.nudge.action.SNOOZE"
        const val ACTION_DISMISS = "com.babygasu.nudge.action.DISMISS"
    }
}

/** Restores alarms after reboot, app update, clock/time-zone change or exact-alarm permission change. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { context.container.reminders.rescheduleAll() }
    }
}
