package com.babygasu.nudge.data

import com.babygasu.nudge.intelligence.Candidate
import com.babygasu.nudge.intelligence.ReminderKind
import com.babygasu.nudge.intelligence.Signal
import com.babygasu.nudge.intelligence.Similarity
import com.babygasu.nudge.reminders.ReminderNotifier
import com.babygasu.nudge.reminders.ReminderScheduler
import com.babygasu.nudge.security.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where a detected reminder came from. Contains no message content. */
data class SourceMeta(
    val origin: Origin,
    val appLabel: String?,
    val packageName: String?,
    val senderDisplay: String?,
    val senderKey: String?,
)

sealed interface IngestOutcome {
    val reminder: Reminder

    data class Created(override val reminder: Reminder) : IngestOutcome
    data class Merged(override val reminder: Reminder, val escalated: Boolean) : IngestOutcome
}

class ReminderRepository(
    private val db: NudgeDatabase,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.reminderDao()
    private val events = db.eventDao()
    /** Serialises writes so duplicate checks can't race each other. */
    private val mutex = Mutex()

    fun observeAll(): Flow<List<Reminder>> = dao.observeAll()
    fun observe(id: Long): Flow<Reminder?> = dao.observe(id)
    suspend fun get(id: Long): Reminder? = dao.get(id)

    /** Create (id == 0) or update a reminder edited by the user. */
    suspend fun save(reminder: Reminder): Long = mutex.withLock {
        val now = clock()
        val tokens = Similarity.tokens(reminder.title, reminder.senderDisplay?.split(' ').orEmpty()).joinToString(" ")
        val id = if (reminder.id == 0L) {
            dao.insert(reminder.copy(createdAt = now, updatedAt = now, matchTokens = tokens)).also { log(it, "created_manual") }
        } else {
            val old = dao.get(reminder.id)
            val timeChanged = old?.effectiveTime != reminder.effectiveTime
            dao.update(
                reminder.copy(
                    updatedAt = now,
                    matchTokens = tokens,
                    lastNotifiedAt = if (timeChanged) null else reminder.lastNotifiedAt,
                ),
            )
            log(reminder.id, "edited")
            reminder.id
        }
        dao.get(id)?.let { scheduler.schedule(it) }
        id
    }

    suspend fun complete(id: Long) = changeStatus(id, ReminderStatus.DONE, "completed")
    suspend fun dismiss(id: Long) = changeStatus(id, ReminderStatus.DISMISSED, "dismissed")

    suspend fun reopen(id: Long) = mutex.withLock {
        val r = dao.get(id) ?: return@withLock
        val updated = r.copy(status = ReminderStatus.OPEN, completedAt = null, updatedAt = clock())
        dao.update(updated)
        log(id, "reopened")
        scheduler.schedule(updated)
    }

    suspend fun snooze(id: Long, until: Long) = mutex.withLock {
        val r = dao.get(id) ?: return@withLock
        val updated = r.copy(status = ReminderStatus.OPEN, snoozedUntil = until, lastNotifiedAt = null, updatedAt = clock())
        dao.update(updated)
        log(id, "snoozed")
        notifier.cancel(id)
        scheduler.schedule(updated)
    }

    suspend fun delete(id: Long) = mutex.withLock {
        scheduler.cancel(id)
        notifier.cancel(id)
        dao.delete(id)
        events.deleteFor(id)
    }

    suspend fun deleteClosed(): Int = mutex.withLock {
        val count = dao.deleteClosed()
        events.deleteOrphans()
        count
    }

    suspend fun deleteAll() = mutex.withLock {
        dao.open().forEach {
            scheduler.cancel(it.id)
            notifier.cancel(it.id)
        }
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }

    suspend fun rescheduleAll() {
        dao.open().forEach { scheduler.schedule(it) }
    }

    /** Called by the alarm. Shows the reminder if it is still due and hasn't been shown for this time. */
    suspend fun fire(id: Long) = mutex.withLock {
        val r = dao.get(id) ?: return@withLock
        if (!r.isOpen) return@withLock
        val at = r.effectiveTime ?: return@withLock
        val now = clock()
        if (at > now + 60_000) {
            scheduler.schedule(r) // early or stale alarm
            return@withLock
        }
        val last = r.lastNotifiedAt
        if (last != null && last >= at) return@withLock
        notifier.showReminder(r, settings.current())
        dao.update(r.copy(lastNotifiedAt = now, snoozedUntil = null))
    }

    /**
     * Saves detected candidates, merging any that describe an obligation Nudge already tracks.
     * Each save is read back to verify it before the caller drops the source text.
     */
    suspend fun ingest(candidates: List<Candidate>, source: SourceMeta): List<IngestOutcome> = mutex.withLock {
        val outcomes = mutableListOf<IngestOutcome>()
        val now = clock()
        for (c in candidates) {
            val open = dao.open()
            val duplicate = findDuplicate(c, source.senderKey, open, now)
            if (duplicate != null) {
                val escalated = Signal.ESCALATION in c.signals || Signal.MISSED_CALL in c.signals
                var priority = maxOf(duplicate.priority, c.priority)
                if (escalated && priority == duplicate.priority) priority = priority.raised()
                if (duplicate.priorityByUser) priority = duplicate.priority
                val candidateDue = c.dueAt?.toInstant()?.toEpochMilli()
                val due = listOfNotNull(duplicate.dueAt, candidateDue).minOrNull()
                val signals = (duplicate.signalSet + c.signals + Signal.MERGED).joinToString(",") { it.name }
                val merged = duplicate.copy(
                    priority = priority,
                    dueAt = due,
                    lastNotifiedAt = if (due != duplicate.dueAt) null else duplicate.lastNotifiedAt,
                    signals = signals,
                    updatedAt = now,
                )
                dao.update(merged)
                log(merged.id, if (escalated) "escalated" else "merged")
                val saved = dao.get(merged.id) ?: continue
                scheduler.schedule(saved)
                outcomes += IngestOutcome.Merged(saved, escalated)
            } else {
                val reminder = Reminder(
                    title = c.title,
                    kind = c.kind,
                    dueAt = c.dueAt?.toInstant()?.toEpochMilli(),
                    duePrecision = c.duePrecision,
                    priority = c.priority,
                    status = ReminderStatus.OPEN,
                    createdAt = now,
                    updatedAt = now,
                    origin = source.origin,
                    sourceApp = source.appLabel,
                    sourcePackage = source.packageName,
                    senderDisplay = source.senderDisplay,
                    senderKey = source.senderKey,
                    signals = c.signals.joinToString(",") { it.name },
                    confidence = c.confidence,
                    matchTokens = c.matchTokens.joinToString(" "),
                )
                val id = dao.insert(reminder)
                val saved = dao.get(id)
                if (saved == null) {
                    SafeLog.error("reminders", "save_not_verified")
                    continue
                }
                log(id, "created_detected")
                scheduler.schedule(saved)
                outcomes += IngestOutcome.Created(saved)
            }
        }
        outcomes
    }

    private fun findDuplicate(c: Candidate, senderKey: String?, open: List<Reminder>, now: Long): Reminder? {
        val recent = open.filter { now - it.createdAt < DUPLICATE_WINDOW_MS }
        return recent.firstOrNull { r ->
            val sameSender = senderKey != null && r.senderKey == senderKey
            val bothFollowUps = (c.kind == ReminderKind.FOLLOW_UP) == (r.kind == ReminderKind.FOLLOW_UP)
            when {
                !bothFollowUps -> false
                c.kind == ReminderKind.CALL_BACK && r.kind == ReminderKind.CALL_BACK -> sameSender
                sameSender -> Similarity.isSameTask(c.matchTokens, r.matchTokenSet)
                senderKey == null && r.senderKey == null ->
                    c.kind == r.kind && Similarity.isSameTask(c.matchTokens, r.matchTokenSet, threshold = 0.8)
                else -> false
            }
        }
    }

    private suspend fun changeStatus(id: Long, status: ReminderStatus, event: String) = mutex.withLock {
        val r = dao.get(id) ?: return@withLock
        val now = clock()
        dao.update(r.copy(status = status, completedAt = now, updatedAt = now))
        log(id, if (status == ReminderStatus.DISMISSED && r.origin != Origin.MANUAL) "dismissed_detected" else event)
        scheduler.cancel(id)
        notifier.cancel(id)
    }

    private suspend fun log(id: Long, type: String) {
        events.insert(ReminderEvent(reminderId = id, type = type, at = clock()))
    }

    private companion object {
        const val DUPLICATE_WINDOW_MS = 21L * 24 * 60 * 60 * 1000
    }
}
