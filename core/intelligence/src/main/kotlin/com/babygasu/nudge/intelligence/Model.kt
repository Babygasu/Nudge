package com.babygasu.nudge.intelligence

import java.time.ZonedDateTime

enum class Priority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical");

    fun raised(): Priority = entries[minOf(ordinal + 1, entries.size - 1)]
}

enum class ReminderKind(val label: String) {
    TASK("Task"),
    COMMITMENT("Your commitment"),
    FOLLOW_UP("Follow-up"),
    MEETING("Meeting"),
    CALL_BACK("Call back"),
    REPLY("Reply"),
}

enum class DuePrecision { EXACT, DAY, APPROXIMATE }

enum class SourceKind { MESSAGE, EMAIL, MISSED_CALL, SHARED_BY_USER }

/**
 * Reasons Nudge can give for a reminder. Only these codes are stored — never the
 * message text that triggered them.
 */
enum class Signal(val description: String) {
    REQUEST("Someone asked you to do this"),
    USER_COMMITMENT("You said you would do this"),
    THEIR_COMMITMENT("Someone said they would do this, so it may need a follow-up"),
    DEADLINE("A deadline or time was mentioned"),
    OVERDUE_REFERENCE("It refers to a time that has already passed"),
    BLOCKING("Others say they can't move forward until this is done"),
    ESCALATION("It's a follow-up: they're still waiting"),
    URGENT_LANGUAGE("The message stresses urgency or importance"),
    CALL_BACK("Someone asked you to call them"),
    MISSED_CALL("You missed a call"),
    MEETING("It mentions a meeting or appointment"),
    QUESTION_FOR_YOU("Someone is waiting for your reply"),
    IMPORTANT_CONTACT("It's from someone you marked as important"),
    PROJECT_MATCH("It relates to something you marked as important"),
    LOW_PRESSURE("They said there's no rush"),
    MERGED("A related message was merged into this reminder instead of creating a duplicate"),
}

data class AnalysisInput(
    val text: String,
    /** Display name of the other person. Null when unknown or when the user hides sender names. */
    val senderName: String? = null,
    val sourceKind: SourceKind = SourceKind.MESSAGE,
    /** True when the text is a message the user sent (visible in some conversation notifications). */
    val authoredByUser: Boolean = false,
    val now: ZonedDateTime,
    val dayFirstDates: Boolean = true,
    val importantSender: Boolean = false,
    val importantKeywords: List<String> = emptyList(),
)

data class Candidate(
    val title: String,
    val kind: ReminderKind,
    val dueAt: ZonedDateTime?,
    val duePrecision: DuePrecision?,
    val priority: Priority,
    val signals: Set<Signal>,
    val confidence: Double,
    /** Tokens derived from the title (not the message) used for duplicate detection. */
    val matchTokens: Set<String>,
)
