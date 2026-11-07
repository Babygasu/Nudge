package com.babygasu.nudge.intelligence

import kotlin.math.abs

/**
 * A reminder (existing or incoming) as seen by duplicate detection. Contains no message text:
 * only title-derived tokens, kind, sender pseudonym, deadline and reasons.
 */
data class TaskSnapshot(
    val id: Long = 0,
    val title: String,
    val kind: ReminderKind,
    val tokens: Set<String>,
    val senderKey: String? = null,
    val dueAt: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val priorityByUser: Boolean = false,
    val signals: Set<Signal> = emptySet(),
    val createdAt: Long = 0,
    /** Title typed by the user: never replaced automatically. */
    val userWritten: Boolean = false,
) {
    /** A deadline that was actually stated, as opposed to "you were due yesterday". */
    val hasExplicitDeadline: Boolean
        get() = dueAt != null && Signal.DEADLINE in signals && Signal.OVERDUE_REFERENCE !in signals
}

fun Candidate.toSnapshot(senderKey: String?): TaskSnapshot = TaskSnapshot(
    title = title,
    kind = kind,
    tokens = matchTokens,
    senderKey = senderKey,
    dueAt = dueAt?.toInstant()?.toEpochMilli(),
    priority = priority,
    signals = signals,
)

sealed interface MatchDecision {
    /** Clearly a new obligation. */
    data object NoMatch : MatchDecision

    /** Confidently the same obligation: update the existing reminder. */
    data class Merge(val targetId: Long, val score: Double) : MatchDecision

    /** Possibly the same: keep both, but ask the user whether to merge. */
    data class Confirm(val targetId: Long, val score: Double) : MatchDecision
}

/**
 * Decides whether an incoming task is the same obligation as an existing reminder.
 *
 * Score = subject × action × sender × deadline, each in 0..1. Any hard conflict
 * (different action family, different known sender, incompatible kinds) scores 0, so
 * "review the proposal" and "submit the proposal" are never merged.
 */
object DuplicateMatcher {
    const val CERTAIN = 0.7
    const val POSSIBLE = 0.4
    /** If the runner-up is this close to the best match, we can't be sure which one it is. */
    const val AMBIGUITY_MARGIN = 0.1
    const val WINDOW_MS = 21L * 24 * 60 * 60 * 1000
    private const val DEADLINE_CONFLICT_MS = 48L * 60 * 60 * 1000

    fun decide(incoming: TaskSnapshot, existing: List<TaskSnapshot>, nowMs: Long): MatchDecision {
        val ranked = existing
            .filter { nowMs - it.createdAt < WINDOW_MS && it.id != incoming.id }
            .map { it to score(incoming, it) }
            .filter { it.second >= POSSIBLE }
            .sortedWith(compareByDescending<Pair<TaskSnapshot, Double>> { it.second }.thenByDescending { it.first.createdAt })
        val best = ranked.firstOrNull() ?: return MatchDecision.NoMatch
        val runnerUp = ranked.getOrNull(1)
        val clearWinner = runnerUp == null || best.second - runnerUp.second >= AMBIGUITY_MARGIN - 1e-9
        return if (best.second >= CERTAIN - 1e-9 && clearWinner) {
            MatchDecision.Merge(best.first.id, best.second)
        } else {
            MatchDecision.Confirm(best.first.id, best.second)
        }
    }

    fun score(a: TaskSnapshot, b: TaskSnapshot): Double {
        if (!kindsCompatible(a.kind, b.kind)) return 0.0
        val sender = when {
            a.senderKey != null && b.senderKey != null -> if (a.senderKey == b.senderKey) 1.0 else return 0.0
            else -> 0.7 // unknown: shared/pasted text or names hidden — never certain on its own
        }
        if (a.kind == ReminderKind.CALL_BACK) {
            // Call-backs have no subject; only the same known person counts.
            return if (a.senderKey != null && a.senderKey == b.senderKey) 1.0 else 0.0
        }
        val action = actionScore(a.tokens, b.tokens) ?: return 0.0
        val subject = subjectScore(a.tokens, b.tokens)
        if (subject == 0.0) return 0.0
        val deadline = if (a.hasExplicitDeadline && b.dueAt != null && abs(a.dueAt!! - b.dueAt) > DEADLINE_CONFLICT_MS) 0.6 else 1.0
        return subject * action * sender * deadline
    }

    private fun kindsCompatible(a: ReminderKind, b: ReminderKind): Boolean {
        fun group(k: ReminderKind) = when (k) {
            ReminderKind.TASK, ReminderKind.COMMITMENT -> 0
            ReminderKind.FOLLOW_UP -> 1
            ReminderKind.MEETING -> 2
            ReminderKind.CALL_BACK -> 3
            ReminderKind.REPLY -> 4
        }
        return group(a) == group(b)
    }

    /** null = conflicting actions (hard no). */
    private fun actionScore(a: Set<String>, b: Set<String>): Double? {
        val va = a.filter { it.startsWith("v:") }.map { it.removePrefix("v:") }.toSet()
        val vb = b.filter { it.startsWith("v:") }.map { it.removePrefix("v:") }.toSet()
        if (va.isEmpty() || vb.isEmpty()) return 0.75 // e.g. "They're still waiting for the report"
        if (va.intersect(vb).isNotEmpty()) return 1.0
        if (va.any { x -> vb.any { y -> Vocabulary.sameActionFamily(x, y) } }) return 0.85
        return null
    }

    private fun subjectScore(a: Set<String>, b: Set<String>): Double {
        val na = a.filterNot { it.startsWith("v:") }.toSet()
        val nb = b.filterNot { it.startsWith("v:") }.toSet()
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        val (small, large) = if (na.size <= nb.size) na to nb else nb to na
        if (large.containsAll(small)) return 0.8 // "the report" vs "the project report"
        return na.intersect(nb).size.toDouble() / na.union(nb).size // "project report" vs "expense report" = 0.33
    }
}

/** Result of folding an incoming task into an existing reminder. */
data class MergeResult(
    val title: String,
    val tokens: Set<String>,
    val signals: Set<Signal>,
    val priority: Priority,
    val dueAt: Long?,
    val escalated: Boolean,
)

object MergePolicy {
    /**
     * - Keeps the existing deadline unless the incoming message states a new one explicitly
     *   ("by Friday"). "I expected it yesterday" is pressure, not a new deadline.
     * - Escalation (chasing, blocking, a repeat missed call) raises priority at least one level,
     *   unless the user set the priority by hand.
     * - A vague detected title ("Ama is waiting for the report") is replaced by a concrete action
     *   title when one arrives. Titles the user wrote are never replaced.
     */
    fun merge(target: TaskSnapshot, incoming: TaskSnapshot): MergeResult {
        val escalated = Signal.ESCALATION in incoming.signals || Signal.BLOCKING in incoming.signals ||
            Signal.MISSED_CALL in incoming.signals
        var priority = maxOf(target.priority, incoming.priority)
        if (escalated && priority == target.priority) priority = priority.raised()
        if (target.priorityByUser) priority = target.priority

        val dueAt = if (incoming.hasExplicitDeadline) incoming.dueAt else target.dueAt

        val targetHasAction = target.tokens.any { it.startsWith("v:") }
        val incomingHasAction = incoming.tokens.any { it.startsWith("v:") }
        val title = if (!target.userWritten && !targetHasAction && incomingHasAction) incoming.title else target.title

        return MergeResult(
            title = title,
            tokens = target.tokens + incoming.tokens,
            signals = target.signals + incoming.signals + Signal.MERGED,
            priority = priority,
            dueAt = dueAt,
            escalated = escalated,
        )
    }
}
