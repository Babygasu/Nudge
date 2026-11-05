package com.babygasu.nudge.intelligence

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Context-based priority. Vocabulary such as "urgent" is only one input: time pressure,
 * people being blocked, repeated chasing and the user's own priorities usually matter more.
 */
object PriorityScorer {

    fun score(kind: ReminderKind, signals: Set<Signal>, dueAt: ZonedDateTime?, now: ZonedDateTime): Priority {
        var s = when (kind) {
            ReminderKind.CALL_BACK -> 3
            ReminderKind.TASK, ReminderKind.COMMITMENT, ReminderKind.MEETING -> 2
            ReminderKind.REPLY -> 1
            ReminderKind.FOLLOW_UP -> 0
        }
        if (dueAt != null && kind != ReminderKind.FOLLOW_UP) {
            val hours = Duration.between(now, dueAt).toHours()
            s += when {
                hours < 24 -> 3 // includes overdue
                hours < 72 -> 2
                hours < 168 -> 1
                else -> 0
            }
        }
        if (Signal.BLOCKING in signals) s += 3
        if (Signal.ESCALATION in signals) s += 2
        if (Signal.URGENT_LANGUAGE in signals) s += 2
        if (Signal.IMPORTANT_CONTACT in signals) s += 2
        if (Signal.PROJECT_MATCH in signals) s += 1
        if (Signal.LOW_PRESSURE in signals) s -= 2

        val priority = when {
            s >= 8 -> Priority.CRITICAL
            s >= 5 -> Priority.HIGH
            s >= 3 -> Priority.MEDIUM
            else -> Priority.LOW
        }
        // Someone else's promise only becomes pressing if they start chasing.
        return if (kind == ReminderKind.FOLLOW_UP && Signal.ESCALATION !in signals && priority > Priority.MEDIUM) {
            Priority.MEDIUM
        } else priority
    }
}
