package com.babygasu.nudge.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.babygasu.nudge.data.Reminder
import com.babygasu.nudge.data.ReminderStatus
import com.babygasu.nudge.data.effectiveTime

/**
 * Uses AlarmManager so reminders fire even if Nudge is closed or the phone is dozing.
 * Exact alarms are used when the user allows them; otherwise Android may deliver a few minutes late.
 * Alarms don't survive a reboot — [SystemEventReceiver] re-arms them from the database.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(reminder: Reminder, now: Long = System.currentTimeMillis()) {
        cancel(reminder.id)
        if (reminder.status != ReminderStatus.OPEN) return
        val at = reminder.effectiveTime ?: return
        val last = reminder.lastNotifiedAt
        if (last != null && last >= at) return
        val trigger = maxOf(at, now + 2_000)
        val pending = pendingIntent(reminder.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancel(id: Long) {
        pendingIntent(id, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(id: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_REMINDER_ID, id)
        return PendingIntent.getBroadcast(context, id.toInt(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_FIRE = "com.babygasu.nudge.action.FIRE_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
