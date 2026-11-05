package com.babygasu.nudge.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * All messages here are invented test data. Real messages must never be added to this repository.
 * "Now" is Monday 2 November 2026, 10:00.
 */
class LocalEngineTest {
    private val now: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2026, 11, 2, 10, 0), ZoneId.of("UTC"))
    private val engine = LocalEngine()

    private fun analyze(
        text: String,
        sender: String? = "Ama",
        kind: SourceKind = SourceKind.MESSAGE,
        fromUser: Boolean = false,
        importantSender: Boolean = false,
        keywords: List<String> = emptyList(),
    ) = engine.analyze(
        AnalysisInput(
            text = text, senderName = sender, sourceKind = kind, authoredByUser = fromUser, now = now,
            importantSender = importantSender, importantKeywords = keywords,
        ),
    )

    @Test
    fun `polite request with deadline becomes a task`() {
        val c = analyze("Please send me the report before Friday.").single()
        assertEquals("Send Ama the report", c.title)
        assertEquals(ReminderKind.TASK, c.kind)
        assertEquals(Priority.MEDIUM, c.priority)
        assertEquals(now.plusDays(3).withHour(9), c.dueAt) // Thursday, the day before Friday
        assertTrue(Signal.REQUEST in c.signals)
        assertTrue(Signal.DEADLINE in c.signals)
    }

    @Test
    fun `important contact raises priority of the same request`() {
        val c = analyze("Please send me the report before Friday.", importantSender = true).single()
        assertEquals(Priority.HIGH, c.priority)
        assertTrue(Signal.IMPORTANT_CONTACT in c.signals)
    }

    @Test
    fun `chasing and blocking without the word urgent is critical`() {
        val c = analyze("I was expecting the report yesterday. We cannot proceed without it.").single()
        assertEquals("Ama is waiting for the report", c.title)
        assertEquals(Priority.CRITICAL, c.priority)
        assertTrue(Signal.ESCALATION in c.signals)
        assertTrue(Signal.BLOCKING in c.signals)
        assertTrue(Signal.OVERDUE_REFERENCE in c.signals)
    }

    @Test
    fun `blocking dependency names the action`() {
        val c = analyze("We cannot proceed until you submit the document", sender = null).single()
        assertEquals("Submit the document", c.title)
        assertEquals(Priority.HIGH, c.priority)
    }

    @Test
    fun `call back request that is important is high`() {
        val c = analyze("Call me when you are free. It's important.").single()
        assertEquals("Call Ama back", c.title)
        assertEquals(ReminderKind.CALL_BACK, c.kind)
        assertEquals(Priority.HIGH, c.priority)
    }

    @Test
    fun `casual chat with urgent word creates nothing`() {
        assertTrue(analyze("URGENT lol did you see the game last night?").isEmpty())
        assertTrue(analyze("Thanks! Have a great weekend").isEmpty())
        assertTrue(analyze("haha ok").isEmpty())
    }

    @Test
    fun `one time codes and promotions are ignored`() {
        assertTrue(analyze("Your OTP is 482913. Do not share this code.").isEmpty())
        assertTrue(analyze("Get 20% off today only! Unsubscribe anytime").isEmpty())
    }

    @Test
    fun `someone else's promise becomes a low follow-up`() {
        val c = analyze("I'll send it tomorrow").single()
        assertEquals(ReminderKind.FOLLOW_UP, c.kind)
        assertEquals("Follow up with Ama: they'll send it", c.title)
        assertEquals(Priority.LOW, c.priority)
        assertEquals(17, c.dueAt?.hour)
    }

    @Test
    fun `user's own promise is a commitment`() {
        val c = analyze("I will submit the proposal by 5pm tomorrow", sender = null, kind = SourceKind.SHARED_BY_USER).single()
        assertEquals(ReminderKind.COMMITMENT, c.kind)
        assertEquals("Submit the proposal", c.title)
        assertEquals(now.plusDays(1).withHour(17), c.dueAt)
        assertEquals(DuePrecision.EXACT, c.duePrecision)
    }

    @Test
    fun `user's own message in a conversation keeps the counterpart for vague titles`() {
        val c = analyze("Sure, I'll send it tonight", sender = "Kofi", fromUser = true).single()
        assertEquals(ReminderKind.COMMITMENT, c.kind)
        assertEquals("Send it (Kofi)", c.title)
    }

    @Test
    fun `missed call becomes call back`() {
        val c = analyze("Missed call", sender = "Ama", kind = SourceKind.MISSED_CALL).single()
        assertEquals("Call Ama back", c.title)
        assertTrue(Signal.MISSED_CALL in c.signals)
    }

    @Test
    fun `greeting and thanks are stripped from titles`() {
        val c = analyze("Hi Kofi, can you review the slides at 3pm today? Thanks!").single()
        assertEquals("Review the slides", c.title)
        assertEquals(now.withHour(15), c.dueAt)
    }

    @Test
    fun `no rush lowers priority and request is found mid sentence`() {
        val c = analyze("No rush, but could you send the invoice when you get a chance").single()
        assertEquals("Send the invoice", c.title)
        assertEquals(Priority.LOW, c.priority)
    }

    @Test
    fun `meetings with a time are captured`() {
        val c = analyze("Board meeting moved to 3pm tomorrow", sender = "Office").single()
        assertEquals(ReminderKind.MEETING, c.kind)
        assertEquals(now.plusDays(1).withHour(15), c.dueAt)
    }

    @Test
    fun `meeting question gets a readable title`() {
        val c = analyze("Can we meet tomorrow at 10?").single()
        assertEquals("Meeting with Ama", c.title)
        assertEquals(now.plusDays(1).withHour(10), c.dueAt)
    }

    @Test
    fun `hidden sender names produce neutral titles`() {
        val c = analyze("Please send me the report before Friday.", sender = null).single()
        assertEquals("Send them the report", c.title)
    }

    @Test
    fun `project keywords raise priority`() {
        val base = analyze("Please review the budget for Phoenix by Wednesday").single()
        val boosted = analyze("Please review the budget for Phoenix by Wednesday", keywords = listOf("phoenix")).single()
        assertTrue(Signal.PROJECT_MATCH in boosted.signals)
        assertEquals(Priority.MEDIUM, base.priority)
        assertEquals(Priority.HIGH, boosted.priority)
    }

    @Test
    fun `escalation title merges with original request tokens`() {
        val original = analyze("Please send me the report before Friday.").single()
        val chase = analyze("I was expecting the report yesterday. We cannot proceed without it.").single()
        assertTrue(Similarity.isSameTask(original.matchTokens, chase.matchTokens))
    }

    @Test
    fun `different actions on the same subject are not duplicates`() {
        val a = Similarity.tokens("Submit the proposal")
        val b = Similarity.tokens("Review the proposal")
        assertFalse(Similarity.isSameTask(a, b))
    }

    @Test
    fun `explanations exist for every signal`() {
        Signal.entries.forEach { assertNotNull(it.description); assertTrue(it.description.isNotBlank()) }
    }

    @Test
    fun `empty text produces nothing`() {
        assertTrue(analyze("   ").isEmpty())
        assertNull(analyze("ok").firstOrNull())
    }
}
