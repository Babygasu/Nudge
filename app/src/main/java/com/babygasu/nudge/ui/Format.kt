package com.babygasu.nudge.ui

import com.babygasu.nudge.data.Reminder
import com.babygasu.nudge.data.ReminderStatus
import com.babygasu.nudge.data.effectiveTime
import com.babygasu.nudge.intelligence.DuePrecision
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {
    private val dayFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val timeFormat: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dateTimeFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

    fun due(epoch: Long?, precision: DuePrecision?, now: Long): String? {
        epoch ?: return null
        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(epoch).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = when (dt.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> dayFormat.format(dt)
        }
        val label = when (precision) {
            DuePrecision.APPROXIMATE -> "Around $day"
            else -> "$day, ${timeFormat.format(dt)}"
        }
        return if (epoch < now) "Overdue · $label" else label
    }

    fun reminderDue(r: Reminder, now: Long): String? {
        if (r.status != ReminderStatus.OPEN) return null
        val text = due(r.effectiveTime, if (r.snoozedUntil != null) DuePrecision.EXACT else r.duePrecision, now)
        return if (r.snoozedUntil != null && text != null) "Snoozed until $text" else text
    }

    fun timestamp(epoch: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))

    fun minutes(m: Int): String = if (m % 60 == 0) "${m / 60} h" else "$m min"
}

/** Groups open reminders for the dashboard (and the bubble's count). */
data class HomeSections(
    val overdue: List<Reminder>,
    val today: List<Reminder>,
    val upcoming: List<Reminder>,
    val undated: List<Reminder>,
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty() && undated.isEmpty()
    val attentionCount: Int get() = overdue.size + today.size

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun build(all: List<Reminder>, now: Long): HomeSections {
            val zone = ZoneId.systemDefault()
            val endOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val weekAhead = now + 7 * DAY_MS
            val open = all.filter { it.status == ReminderStatus.OPEN }
            val byPriority = compareByDescending<Reminder> { it.priority }.thenBy { it.effectiveTime ?: Long.MAX_VALUE }
            return HomeSections(
                overdue = open.filter { it.effectiveTime?.let { t -> t < now } == true }.sortedWith(byPriority),
                today = open.filter { it.effectiveTime?.let { t -> t in now until endOfToday } == true }
                    .sortedBy { it.effectiveTime },
                upcoming = open.filter { it.effectiveTime?.let { t -> t in endOfToday..weekAhead } == true }
                    .sortedBy { it.effectiveTime },
                undated = open.filter { it.effectiveTime == null }.sortedWith(byPriority),
            )
        }
    }
}
