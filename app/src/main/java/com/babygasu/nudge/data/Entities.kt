package com.babygasu.nudge.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babygasu.nudge.intelligence.DuePrecision
import com.babygasu.nudge.intelligence.Priority
import com.babygasu.nudge.intelligence.ReminderKind
import com.babygasu.nudge.intelligence.Signal

enum class ReminderStatus { OPEN, DONE, DISMISSED }

enum class Origin { MANUAL, NOTIFICATION, SHARED }

/**
 * "Reminder memory". Note what is NOT here: there is no column for message bodies, email text
 * or conversation history. Only the structured obligation is kept.
 */
@Entity(tableName = "reminders", indices = [Index("status"), Index("dueAt")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Notes the user typed themselves. Never filled from captured content. */
    val notes: String? = null,
    val kind: ReminderKind = ReminderKind.TASK,
    val dueAt: Long? = null,
    val duePrecision: DuePrecision? = null,
    val priority: Priority = Priority.MEDIUM,
    val priorityByUser: Boolean = false,
    val status: ReminderStatus = ReminderStatus.OPEN,
    val snoozedUntil: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val origin: Origin = Origin.MANUAL,
    /** App name, e.g. "WhatsApp". */
    val sourceApp: String? = null,
    val sourcePackage: String? = null,
    /** Sender's display name — only stored when the user turns on "Show who reminders are from". */
    val senderDisplay: String? = null,
    /** Keyed hash of the sender, for duplicate detection without storing identity. */
    val senderKey: String? = null,
    /** Comma-separated [Signal] codes explaining why Nudge created this. */
    val signals: String = "",
    val confidence: Double = 1.0,
    /** Space-separated tokens derived from the title, used to avoid duplicates. */
    val matchTokens: String = "",
    val lastNotifiedAt: Long? = null,
)

val Reminder.effectiveTime: Long? get() = snoozedUntil ?: dueAt
val Reminder.isOpen: Boolean get() = status == ReminderStatus.OPEN
val Reminder.signalSet: Set<Signal>
    get() = signals.split(',').mapNotNull { code -> Signal.entries.firstOrNull { it.name == code } }.toSet()
val Reminder.matchTokenSet: Set<String>
    get() = matchTokens.split(' ').filter { it.isNotBlank() }.toSet()

/** Content-free history of what happened to a reminder (created, merged, snoozed, done…). */
@Entity(tableName = "reminder_events", indices = [Index("reminderId")])
data class ReminderEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val type: String,
    val at: Long,
)

/** Which apps the user allows Nudge to read notifications from. Package names only. */
@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean,
    val firstSeenAt: Long,
)
