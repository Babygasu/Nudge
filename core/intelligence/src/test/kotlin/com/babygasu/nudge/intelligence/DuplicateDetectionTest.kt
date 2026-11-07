package com.babygasu.nudge.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Regression tests for duplicate detection (Phase 1 device-testing defect, 2026-11-07).
 * All messages are invented. "Now" is Friday 6 November 2026, 10:00 UTC.
 */
class DuplicateDetectionTest {
    private val now: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2026, 11, 6, 10, 0), ZoneId.of("UTC"))
    private val nowMs = now.toInstant().toEpochMilli()
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private val engine = LocalEngine()

    private fun shared(text: String): Candidate =
        engine.analyze(AnalysisInput(text = text, sourceKind = SourceKind.SHARED_BY_USER, now = now)).single()

    private fun existing(c: Candidate, id: Long, ageMs: Long, senderKey: String? = null) =
        c.toSnapshot(senderKey).copy(id = id, createdAt = nowMs - ageMs)

    private fun task(
        id: Long = 0,
        title: String = "Send the report",
        tokens: Set<String> = setOf("v:send", "report"),
        sender: String? = "k1",
        due: Long? = null,
        signals: Set<Signal> = emptySet(),
        priority: Priority = Priority.MEDIUM,
        kind: ReminderKind = ReminderKind.TASK,
        ageMs: Long = hour,
        byUser: Boolean = false,
        userWritten: Boolean = false,
    ) = TaskSnapshot(id, title, kind, tokens, sender, due, priority, byUser, signals, nowMs - ageMs, userWritten)

    // ---- The reported device scenario ----

    @Test
    fun `reported scenario - follow-up updates the matching reminder and keeps its deadline`() {
        val send = existing(shared("Please send the project report by tomorrow at 10am"), id = 1, ageMs = 2 * day)
        val submitCandidate = shared("Please submit the project report by Monday at 10 AM.")
        assertEquals("Submit the project report", submitCandidate.title)

        // Step 1: "submit" vs an older "send" of the same report, no known sender → ask, don't auto-merge.
        val step1 = DuplicateMatcher.decide(submitCandidate.toSnapshot(null), listOf(send), nowMs)
        assertTrue("expected Confirm, got $step1", step1 is MatchDecision.Confirm)
        assertEquals(1L, (step1 as MatchDecision.Confirm).targetId)

        val submit = existing(submitCandidate, id = 2, ageMs = hour)
        val followUp = shared("I'm still waiting for the project report. We cannot proceed until you submit it.")
        assertEquals("Submit the project report", followUp.title) // "it" resolved to the report
        assertTrue(Signal.ESCALATION in followUp.signals)
        assertTrue(Signal.BLOCKING in followUp.signals)

        // Step 2: the follow-up updates "Submit…" (exact match), not the first reminder in the list.
        val step2 = DuplicateMatcher.decide(followUp.toSnapshot(null), listOf(send, submit), nowMs)
        assertTrue(step2 is MatchDecision.Merge)
        assertEquals(2L, (step2 as MatchDecision.Merge).targetId)

        val merged = MergePolicy.merge(submit, followUp.toSnapshot(null))
        assertEquals(submit.dueAt, merged.dueAt) // original Monday 10:00 kept
        assertTrue(merged.priority > submit.priority) // escalated
        assertTrue(merged.escalated)
        assertEquals("Submit the project report", merged.title)
    }

    // ---- Semantic action matching ----

    @Test
    fun `send and submit from the same person are the same task`() {
        val d = DuplicateMatcher.decide(
            task(tokens = setOf("v:submit", "project", "report")),
            listOf(task(id = 7, tokens = setOf("v:send", "project", "report"))),
            nowMs,
        )
        assertEquals(7L, (d as MatchDecision.Merge).targetId)
    }

    @Test
    fun `different actions on the same subject are never merged`() {
        val d = DuplicateMatcher.decide(
            task(tokens = setOf("v:review", "project", "report")),
            listOf(task(id = 7, tokens = setOf("v:submit", "project", "report"))),
            nowMs,
        )
        assertEquals(MatchDecision.NoMatch, d)
    }

    @Test
    fun `different subjects sharing a word are not merged`() {
        val d = DuplicateMatcher.decide(
            task(tokens = setOf("v:send", "expense", "report")),
            listOf(task(id = 7, tokens = setOf("v:send", "project", "report"))),
            nowMs,
        )
        assertEquals(MatchDecision.NoMatch, d)
    }

    @Test
    fun `same task from different people stays separate`() {
        val d = DuplicateMatcher.decide(task(sender = "k1"), listOf(task(id = 7, sender = "k2")), nowMs)
        assertEquals(MatchDecision.NoMatch, d)
    }

    // ---- Uncertainty → user confirmation ----

    @Test
    fun `conflicting explicit deadlines ask the user`() {
        val d = DuplicateMatcher.decide(
            task(due = nowMs + day, signals = setOf(Signal.DEADLINE)),
            listOf(task(id = 7, due = nowMs + 6 * day)),
            nowMs,
        )
        assertTrue(d is MatchDecision.Confirm)
    }

    @Test
    fun `two equally good matches ask the user instead of guessing`() {
        val d = DuplicateMatcher.decide(task(), listOf(task(id = 1, ageMs = 2 * hour), task(id = 2)), nowMs)
        assertTrue(d is MatchDecision.Confirm)
    }

    @Test
    fun `vague reference to a subset of the subject asks rather than merges`() {
        val d = DuplicateMatcher.decide(
            task(tokens = setOf("report")),
            listOf(task(id = 7, tokens = setOf("v:send", "project", "report"))),
            nowMs,
        )
        assertTrue(d is MatchDecision.Confirm)
    }

    // ---- Deadlines ----

    @Test
    fun `explicit new deadline replaces the old one`() {
        val m = MergePolicy.merge(
            task(due = nowMs + 5 * day),
            task(due = nowMs + day, signals = setOf(Signal.DEADLINE)),
        )
        assertEquals(nowMs + day, m.dueAt)
    }

    @Test
    fun `a past reference is pressure, not a new deadline`() {
        val m = MergePolicy.merge(
            task(due = nowMs + 3 * day),
            task(due = nowMs, signals = setOf(Signal.ESCALATION, Signal.OVERDUE_REFERENCE)),
        )
        assertEquals(nowMs + 3 * day, m.dueAt)
    }

    @Test
    fun `follow-up without any time keeps the original deadline`() {
        val m = MergePolicy.merge(task(due = nowMs + 3 * day), task(due = null, signals = setOf(Signal.BLOCKING)))
        assertEquals(nowMs + 3 * day, m.dueAt)
    }

    // ---- Escalation ----

    @Test
    fun `escalation raises priority but respects a priority the user set`() {
        val chase = task(signals = setOf(Signal.ESCALATION), priority = Priority.MEDIUM)
        assertEquals(Priority.HIGH, MergePolicy.merge(task(priority = Priority.MEDIUM), chase).priority)
        assertEquals(Priority.LOW, MergePolicy.merge(task(priority = Priority.LOW, byUser = true), chase).priority)
    }

    @Test
    fun `after escalation a second chase still updates the same reminder`() {
        val original = task(id = 5, priority = Priority.MEDIUM)
        val chase = task(title = "Ama is waiting for the report", tokens = setOf("report"), signals = setOf(Signal.ESCALATION))
        val m1 = MergePolicy.merge(original, chase)
        val afterFirst = original.copy(tokens = m1.tokens, priority = m1.priority, signals = m1.signals)

        val d = DuplicateMatcher.decide(chase, listOf(afterFirst), nowMs)
        assertEquals(5L, (d as MatchDecision.Merge).targetId)
        assertTrue(MergePolicy.merge(afterFirst, chase).priority >= Priority.HIGH)
    }

    // ---- Titles ----

    @Test
    fun `a concrete action title replaces a vague detected one but never a user-written one`() {
        val vague = task(title = "Ama is waiting for the report", tokens = setOf("report"))
        val concrete = task(title = "Send Ama the report")
        assertEquals("Send Ama the report", MergePolicy.merge(vague, concrete).title)
        assertEquals("Ama is waiting for the report", MergePolicy.merge(vague.copy(userWritten = true), concrete).title)
    }

    @Test
    fun `it is resolved from an earlier sentence`() {
        assertEquals("Send the quarterly budget", shared("The quarterly budget is late. Please send it today.").title)
    }

    // ---- Kinds, call-backs, time window ----

    @Test
    fun `call-backs merge only for the same known person`() {
        val call = task(kind = ReminderKind.CALL_BACK, tokens = emptySet(), title = "Call Ama back")
        assertTrue(DuplicateMatcher.decide(call, listOf(call.copy(id = 3)), nowMs) is MatchDecision.Merge)
        assertEquals(
            MatchDecision.NoMatch,
            DuplicateMatcher.decide(call.copy(senderKey = null), listOf(call.copy(id = 3, senderKey = null)), nowMs),
        )
    }

    @Test
    fun `someone else's promise is not merged with your task`() {
        val d = DuplicateMatcher.decide(task(kind = ReminderKind.FOLLOW_UP), listOf(task(id = 7)), nowMs)
        assertEquals(MatchDecision.NoMatch, d)
    }

    @Test
    fun `old reminders outside the window are not matched`() {
        val d = DuplicateMatcher.decide(task(), listOf(task(id = 7, ageMs = 30 * day)), nowMs)
        assertEquals(MatchDecision.NoMatch, d)
    }

    @Test
    fun `action families`() {
        assertTrue(Vocabulary.sameActionFamily("send", "submit"))
        assertTrue(Vocabulary.sameActionFamily("get", "send"))
        assertTrue(!Vocabulary.sameActionFamily("review", "submit"))
        assertTrue(!Vocabulary.sameActionFamily("pay", "send"))
    }
}
