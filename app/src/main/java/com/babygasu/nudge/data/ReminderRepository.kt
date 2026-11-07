package com.babygasu.nudge.data

import com.babygasu.nudge.intelligence.Candidate
import com.babygasu.nudge.intelligence.DuePrecision
import com.babygasu.nudge.intelligence.DuplicateMatcher
import com.babygasu.nudge.intelligence.MatchDecision
import com.babygasu.nudge.intelligence.MergePolicy
import com.babygasu.nudge.intelligence.Signal
import com.babygasu.nudge.intelligence.Similarity
import com.babygasu.nudge.intelligence.TaskSnapshot
import com.babygasu.nudge.intelligence.toSnapshot
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

    /** Saved separately because it *might* be the same as [similarTo]; the user is asked. */
    data class NeedsConfirmation(override val reminder: Reminder, val similarTo: Reminder) : IngestOutcome
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
        dao.repointDuplicateLinks(id, null)
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
     * Saves detected candidates. Each one is compared with open reminders (DuplicateMatcher):
     *  - confident match → the existing reminder is updated (MergePolicy), nothing new is created;
     *  - uncertain match → a new reminder is saved but flagged, and the user decides whether to merge;
     *  - no match → a new reminder.
     * Each save is read back to verify it before the caller drops the source text.
     */
    suspend fun ingest(candidates: List<Candidate>, source: SourceMeta): List<IngestOutcome> = mutex.withLock {
        val outcomes = mutableListOf<IngestOutcome>()
        val now = clock()
        for (c in candidates) {
            val open = dao.open()
            val incoming = c.toSnapshot(source.senderKey)
            val decision = DuplicateMatcher.decide(incoming, open.map { it.toSnapshot() }, now)
            if (decision is MatchDecision.Merge) {
                val target = open.first { it.id == decision.targetId }
                val saved = applyMerge(target, incoming, now) ?: continue
                outcomes += IngestOutcome.Merged(saved, Signal.ESCALATION in c.signals || Signal.BLOCKING in c.signals || Signal.MISSED_CALL in c.signals)
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
                    possibleDuplicateOf = (decision as? MatchDecision.Confirm)?.targetId,
                )
                val id = dao.insert(reminder)
                val saved = dao.get(id)
                if (saved == null) {
                    SafeLog.error("reminders", "save_not_verified")
                    continue
                }
                log(id, if (saved.possibleDuplicateOf != null) "created_possible_duplicate" else "created_detected")
                scheduler.schedule(saved)
                val target = saved.possibleDuplicateOf?.let { tid -> open.firstOrNull { it.id == tid } }
                outcomes += if (target != null) IngestOutcome.NeedsConfirmation(saved, target) else IngestOutcome.Created(saved)
            }
        }
        outcomes
    }

    /** User confirmed: fold [sourceId] into [targetId] and remove [sourceId]. */
    suspend fun mergeInto(sourceId: Long, targetId: Long) = mutex.withLock {
        val source = dao.get(sourceId) ?: return@withLock
        val target = dao.get(targetId) ?: return@withLock
        if (target.status != ReminderStatus.OPEN || sourceId == targetId) return@withLock
        applyMerge(target, source.toSnapshot(), clock()) ?: return@withLock
        scheduler.cancel(sourceId)
        notifier.cancel(sourceId)
        dao.delete(sourceId)
        events.deleteFor(sourceId)
        dao.repointDuplicateLinks(sourceId, targetId)
        dao.get(targetId)?.let { if (it.possibleDuplicateOf == targetId) dao.update(it.copy(possibleDuplicateOf = null)) }
        log(targetId, "merged_by_user")
    }

    /** User chose "Keep both": stop suggesting a merge for this reminder. */
    suspend fun keepSeparate(id: Long) = mutex.withLock {
        val r = dao.get(id) ?: return@withLock
        dao.update(r.copy(possibleDuplicateOf = null, duplicateReviewed = true, updatedAt = clock()))
        log(id, "kept_separate")
    }

    /**
     * Looks for likely duplicates among reminders that already exist (e.g. created before this
     * matching existed) and flags them for the user. Never merges existing reminders on its own.
     */
    suspend fun flagPossibleDuplicates() = mutex.withLock {
        val now = clock()
        val open = dao.open().sortedBy { it.createdAt }
        for ((index, r) in open.withIndex()) {
            if (r.duplicateReviewed || r.possibleDuplicateOf != null || r.origin == Origin.MANUAL && r.matchTokens.isBlank()) continue
            val older = open.subList(0, index).map { it.toSnapshot() }
            when (val d = DuplicateMatcher.decide(r.toSnapshot(), older, now)) {
                is MatchDecision.Merge -> dao.update(r.copy(possibleDuplicateOf = d.targetId))
                is MatchDecision.Confirm -> dao.update(r.copy(possibleDuplicateOf = d.targetId))
                MatchDecision.NoMatch -> Unit
            }
        }
    }

    private suspend fun applyMerge(target: Reminder, incoming: TaskSnapshot, now: Long): Reminder? {
        val m = MergePolicy.merge(target.toSnapshot(), incoming)
        val dueChanged = m.dueAt != target.dueAt
        dao.update(
            target.copy(
                title = m.title,
                matchTokens = m.tokens.joinToString(" "),
                signals = m.signals.joinToString(",") { it.name },
                priority = m.priority,
                dueAt = m.dueAt,
                duePrecision = if (dueChanged) DuePrecision.EXACT else target.duePrecision,
                snoozedUntil = if (dueChanged) null else target.snoozedUntil,
                lastNotifiedAt = if (dueChanged) null else target.lastNotifiedAt,
                updatedAt = now,
            ),
        )
        log(target.id, if (m.escalated) "escalated" else "merged")
        val saved = dao.get(target.id) ?: return null
        scheduler.schedule(saved)
        return saved
    }

    private suspend fun changeStatus(id: Long, status: ReminderStatus, event: String) = mutex.withLock {
        val r = dao.get(id) ?: return@withLock
        val now = clock()
        dao.update(r.copy(status = status, completedAt = now, updatedAt = now, possibleDuplicateOf = null))
        dao.repointDuplicateLinks(id, null)
        log(id, if (status == ReminderStatus.DISMISSED && r.origin != Origin.MANUAL) "dismissed_detected" else event)
        scheduler.cancel(id)
        notifier.cancel(id)
    }

    private suspend fun log(id: Long, type: String) {
        events.insert(ReminderEvent(reminderId = id, type = type, at = clock()))
    }

}
