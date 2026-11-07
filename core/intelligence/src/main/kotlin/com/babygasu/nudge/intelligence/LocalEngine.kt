package com.babygasu.nudge.intelligence

import java.time.LocalTime
import java.util.Locale

const val DEFAULT_MIN_CONFIDENCE = 0.55
private const val MAX_ACTIONS_PER_MESSAGE = 3

internal enum class ActionType { REQUEST, USER_COMMITMENT, THEIR_COMMITMENT, CALL_BACK, MEETING, REPLY }

/** Whose words these are: the user's own message, text the user shared, or someone else's message. */
internal enum class Perspective { USER, SHARED, OTHER }

internal data class Detected(
    val sentence: String,
    val type: ActionType,
    val confidence: Double,
    val time: TimeExpression?,
    /** Message text before this sentence, used to resolve "it" ("The report is late. Please send it."). */
    val preceding: String = "",
)

/**
 * Offline intelligence engine (Layer 1). Works entirely in memory: it receives text, returns
 * structured candidates, and keeps nothing. It looks at what a sentence *does* (asks, promises,
 * blocks, chases) and at time pressure and consequences, not only at trigger words.
 */
class LocalEngine(private val minConfidence: Double = DEFAULT_MIN_CONFIDENCE) {

    fun analyze(input: AnalysisInput): List<Candidate> {
        val text = TextTools.normalize(input.text)
        if (text.isBlank()) return emptyList()
        val lowerAll = text.lowercase(Locale.ROOT)
        val sender = input.senderName?.trim()?.takeIf { it.isNotEmpty() }
        val them = sender ?: THEM
        val senderTokens = sender?.let { TextTools.words(it) }.orEmpty()
        val importance = importanceSignals(input, lowerAll)

        if (input.sourceKind == SourceKind.MISSED_CALL) {
            val signals = setOf(Signal.MISSED_CALL, Signal.CALL_BACK) + importance
            val title = if (sender != null) "Call $sender back" else "Return a missed call"
            val priority = PriorityScorer.score(ReminderKind.CALL_BACK, signals, null, input.now)
            return listOf(Candidate(title, ReminderKind.CALL_BACK, null, null, priority, signals, 0.95, emptySet()))
        }
        if (Rules.NOISE.containsMatchIn(lowerAll)) return emptyList()

        val perspective = when {
            input.authoredByUser -> Perspective.USER
            input.sourceKind == SourceKind.SHARED_BY_USER -> Perspective.SHARED
            else -> Perspective.OTHER
        }
        val messageSignals = buildSet {
            if (Rules.URGENT.containsMatchIn(lowerAll)) add(Signal.URGENT_LANGUAGE)
            if (Rules.LOW_PRESSURE.containsMatchIn(lowerAll)) add(Signal.LOW_PRESSURE)
            // When the user is the one chasing or blocked, that's not pressure on the user.
            if (perspective != Perspective.USER) {
                if (Rules.BLOCKING.containsMatchIn(lowerAll)) add(Signal.BLOCKING)
                if (Rules.ESCALATION.containsMatchIn(lowerAll)) add(Signal.ESCALATION)
            }
            addAll(importance)
        }
        val messageTime = TimeParser.parse(text, input.now, input.dayFirstDates)

        val sentences = TextTools.sentences(text)
        val detected = sentences
            .mapIndexedNotNull { i, s ->
                detect(s, perspective, input)?.copy(preceding = sentences.take(i).joinToString(" "))
            }
            .take(MAX_ACTIONS_PER_MESSAGE)
        val candidates = detected
            .map { build(it, perspective, input, them, senderTokens, messageSignals, messageTime) }
            .toMutableList()

        if (candidates.isEmpty() && perspective != Perspective.USER &&
            (Signal.ESCALATION in messageSignals || Signal.BLOCKING in messageSignals)
        ) {
            candidates += waiting(lowerAll, input, them, senderTokens, messageSignals, messageTime)
        }
        return candidates
            .filter { it.confidence >= minConfidence }
            .distinctBy { it.title.lowercase(Locale.ROOT) }
    }

    private fun importanceSignals(input: AnalysisInput, lowerAll: String): Set<Signal> = buildSet {
        if (input.importantSender) add(Signal.IMPORTANT_CONTACT)
        val words = TextTools.words(lowerAll).toSet()
        if (input.importantKeywords.any { kw ->
                val k = kw.trim().lowercase(Locale.ROOT)
                k.isNotEmpty() && (if (k.contains(' ')) lowerAll.contains(k) else k in words)
            }
        ) add(Signal.PROJECT_MATCH)
    }

    private fun detect(sentence: String, perspective: Perspective, input: AnalysisInput): Detected? {
        val lead = TextTools.stripLead(sentence)
        if (lead.length < 3) return null
        val lower = lead.lowercase(Locale.ROOT)
        if (Rules.CASUAL.containsMatchIn(lower)) return null
        val time = TimeParser.parse(lead, input.now, input.dayFirstDates)
        val first = TextTools.firstWord(lower)
        val fromOther = perspective != Perspective.USER

        val (type, confidence) = when {
            fromOther && Rules.CALL_BACK.containsMatchIn(lower) -> ActionType.CALL_BACK to 0.85
            Rules.COMMITMENT_START.containsMatchIn(lower) && Titles.commitmentHasAction(lead) ->
                if (perspective == Perspective.OTHER) ActionType.THEIR_COMMITMENT to 0.6
                else ActionType.USER_COMMITMENT to 0.7
            perspective != Perspective.OTHER && Rules.SELF_TASK.containsMatchIn(lower) -> ActionType.USER_COMMITMENT to 0.7
            fromOther && Rules.POLITE_REQUEST.containsMatchIn(lower) -> ActionType.REQUEST to 0.75
            fromOther && Rules.YOU_NEED.containsMatchIn(lower) -> ActionType.REQUEST to 0.7
            fromOther && first in Vocabulary.ACTION_VERBS -> ActionType.REQUEST to 0.65
            Rules.MEETING.containsMatchIn(lower) && time != null && !time.isPast -> ActionType.MEETING to 0.75
            perspective == Perspective.OTHER && lower.endsWith("?") && Rules.ADDRESSED_TO_YOU.containsMatchIn(lower) ->
                ActionType.REPLY to 0.4
            else -> return null
        }
        return Detected(lead, type, confidence, time)
    }

    private fun build(
        d: Detected,
        perspective: Perspective,
        input: AnalysisInput,
        them: String,
        senderTokens: List<String>,
        messageSignals: Set<Signal>,
        messageTime: TimeExpression?,
    ): Candidate {
        val time = d.time ?: messageTime
        val signals = messageSignals.toMutableSet()
        signals += when (d.type) {
            ActionType.REQUEST -> Signal.REQUEST
            ActionType.USER_COMMITMENT -> Signal.USER_COMMITMENT
            ActionType.THEIR_COMMITMENT -> Signal.THEIR_COMMITMENT
            ActionType.CALL_BACK -> Signal.CALL_BACK
            ActionType.MEETING -> Signal.MEETING
            ActionType.REPLY -> Signal.QUESTION_FOR_YOU
        }
        if (time != null) signals += if (time.isPast) Signal.OVERDUE_REFERENCE else Signal.DEADLINE

        val kind = when (d.type) {
            ActionType.REQUEST -> ReminderKind.TASK
            ActionType.USER_COMMITMENT -> ReminderKind.COMMITMENT
            ActionType.THEIR_COMMITMENT -> ReminderKind.FOLLOW_UP
            ActionType.CALL_BACK -> ReminderKind.CALL_BACK
            ActionType.MEETING -> ReminderKind.MEETING
            ActionType.REPLY -> ReminderKind.REPLY
        }
        var due = time?.let { if (it.isPast) input.now else it.at }
        if (kind == ReminderKind.FOLLOW_UP && time != null && !time.isPast && time.precision != DuePrecision.EXACT) {
            due = due?.with(LocalTime.of(17, 0)) // check in at the end of the day they promised
        }

        var confidence = d.confidence
        if (time != null && !time.isPast) confidence += 0.1
        if (Signal.ESCALATION in signals || Signal.BLOCKING in signals) confidence += 0.1
        if (d.type == ActionType.REPLY && Signal.URGENT_LANGUAGE in signals) confidence += 0.1

        val title = Titles.forAction(d, them, perspective)
        return Candidate(
            title = title,
            kind = kind,
            dueAt = due,
            duePrecision = time?.precision,
            priority = PriorityScorer.score(kind, signals, due, input.now),
            signals = signals,
            confidence = confidence.coerceAtMost(0.98),
            matchTokens = Similarity.tokens(title, senderTokens),
        )
    }

    private fun waiting(
        lowerAll: String,
        input: AnalysisInput,
        them: String,
        senderTokens: List<String>,
        messageSignals: Set<Signal>,
        messageTime: TimeExpression?,
    ): Candidate {
        val title = Titles.waiting(lowerAll, them)
        val signals = messageSignals.toMutableSet()
        if (messageTime != null) signals += if (messageTime.isPast) Signal.OVERDUE_REFERENCE else Signal.DEADLINE
        val due = messageTime?.let { if (it.isPast) input.now else it.at }
        return Candidate(
            title = title,
            kind = ReminderKind.TASK,
            dueAt = due,
            duePrecision = messageTime?.precision,
            priority = PriorityScorer.score(ReminderKind.TASK, signals, due, input.now),
            signals = signals,
            confidence = 0.8,
            matchTokens = Similarity.tokens(title, senderTokens),
        )
    }
}

/** Builds short, action-first titles. Pronouns are rewritten so "send me" becomes "send Ama". */
internal object Titles {
    private val I = RegexOption.IGNORE_CASE
    private val ME = Regex("""\bme\b""", I)
    private val MY = Regex("""\bmy\b""", I)
    private val US = Regex("""\bus\b""", I)
    private val OUR = Regex("""\bour\b""", I)
    private val MYSELF = Regex("""\bmyself\b""", I)
    private val OBJECT_STOP = setOf(
        "yesterday", "today", "tomorrow", "from", "by", "yet", "already", "since", "last", "this", "on", "at",
        "before", "until", "and", "or", "but", "so", "for", "to", "in", "please", "asap", "now", "soon", "again",
        "we", "i", "you", "is", "was", "of", "with", "about", "it", "will", "are", "has", "have", "be", "can", "cannot",
        "can't", "should", "must", "needs", "need",
    )

    fun forAction(d: Detected, them: String, perspective: Perspective): String {
        val withoutTime = TextTools.removeSpans(d.sentence, d.time?.spans.orEmpty())
        val cleaned = TextTools.stripLead(withoutTime)
        val raw = when (d.type) {
            ActionType.CALL_BACK -> "Call $them back"
            ActionType.REPLY -> "Reply to $them"
            ActionType.MEETING -> meeting(cleaned, them)
            ActionType.THEIR_COMMITMENT -> {
                val rest = swapPossessives(commitmentRest(cleaned))
                when {
                    rest.isBlank() -> "Follow up with $them"
                    them == THEM -> "Follow up: they'll $rest"
                    else -> "Follow up with $them: they'll $rest"
                }
            }
            ActionType.USER_COMMITMENT -> selfTask(cleaned, them, perspective, d.preceding)
            ActionType.REQUEST -> resolvePronoun(request(cleaned, them), d.preceding)
        }
        return TextTools.finish(raw) ?: fallback(d.type, them)
    }

    /** Most recent concrete noun phrase in [text], e.g. "the project report". */
    fun antecedent(text: String): String? =
        Rules.NOUN_PHRASE.findAll(text).toList().asReversed()
            .firstNotNullOfOrNull { cleanObject(it.value.lowercase(Locale.ROOT)) }
            ?.let(::swapPossessives)

    /** "Send it" + earlier "the project report" → "Send the project report". */
    private fun resolvePronoun(title: String, preceding: String): String {
        if (preceding.isBlank() || !Rules.TITLE_PRONOUN.containsMatchIn(title)) return title
        val noun = antecedent(preceding) ?: return title
        return title.replaceFirst(Rules.TITLE_PRONOUN, noun)
    }

    fun commitmentHasAction(lead: String): Boolean =
        TextTools.firstWord(commitmentRest(lead).lowercase(Locale.ROOT)) in Vocabulary.ACTION_VERBS

    fun waiting(lowerAll: String, them: String): String {
        Rules.UNTIL_YOU.find(lowerAll)?.let { m ->
            TextTools.finish(swapPossessives(TextTools.trimTrailing(m.groupValues[1])))?.let { return it }
        }
        val obj = Rules.WAITING_OBJECT.find(lowerAll)?.groupValues?.get(1)?.let(::cleanObject)
        // "...until you submit it": the action, with "it" resolved to what they're waiting for.
        Rules.UNTIL_YOU_PRONOUN.find(lowerAll)?.let { m ->
            val subject = obj ?: antecedent(lowerAll.substring(0, m.range.first))
            if (subject != null) TextTools.finish("${m.groupValues[1]} ${swapPossessives(subject)}")?.let { return it }
        }
        val who = if (them == THEM) "Someone" else them
        val raw = when {
            obj != null -> "$who is waiting for ${swapPossessives(obj)}"
            them == THEM -> "Someone is waiting on you"
            else -> "Follow up with $them"
        }
        return TextTools.finish(raw) ?: "Follow up"
    }

    private fun commitmentRest(s: String): String =
        TextTools.trimTrailing(s.trim().replace(Rules.COMMITMENT_PREFIX, "").trim())

    private fun selfTask(cleaned: String, them: String, perspective: Perspective, preceding: String): String {
        var t = cleaned.trim().replace(Rules.COMMITMENT_PREFIX, "").replace(Rules.SELF_PREFIX, "").trim()
        t = resolvePronoun(TextTools.trimTrailing(t), preceding)
        if (perspective == Perspective.USER && them != THEM && Rules.VAGUE.containsMatchIn(t)) t = "$t ($them)"
        return t
    }

    private fun request(cleaned: String, them: String): String {
        var t = cleaned
        val anchor = Rules.REQUEST_ANCHOR.find(t)
        if (anchor != null && anchor.range.first > 0) {
            val tail = stripRequestPrefixes(t.substring(anchor.range.first))
            if (TextTools.words(TextTools.trimTrailing(tail)).size >= 2) t = tail
        }
        t = stripRequestPrefixes(t)
        t = TextTools.trimTrailing(t)
        t = swapPronouns(t, them)
        if (t.isBlank()) return ""
        if (TextTools.firstWord(t.lowercase(Locale.ROOT)) in Vocabulary.OBJECT_STARTERS) t = "Get $t to $them"
        return t
    }

    private fun meeting(cleaned: String, them: String): String {
        val t = TextTools.trimTrailing(cleaned)
        val lower = t.lowercase(Locale.ROOT)
        if (t.length in 4..50 && !Rules.QUESTION_START.containsMatchIn(lower) && !lower.startsWith("i ")) {
            return swapPronouns(t, them)
        }
        val word = Rules.MEETING.find(lower)?.value ?: "meeting"
        val noun = if (word.startsWith("meet") || word.startsWith("call")) "Meeting"
        else word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        return if (them == THEM) noun else "$noun with $them"
    }

    private fun stripRequestPrefixes(s: String): String {
        var t = s.trim()
        repeat(3) { t = t.replace(Rules.REQUEST_PREFIX, "").trim() }
        return t
    }

    private fun swapPronouns(t: String, them: String): String {
        val possessive = if (them == THEM) "their" else "$them's"
        return t.replace(MYSELF, them).replace(ME, them).replace(MY, possessive).replace(US, "them").replace(OUR, "their")
    }

    private fun swapPossessives(t: String): String = t.replace(MY, "their").replace(OUR, "their")

    private fun cleanObject(phrase: String): String? {
        val words = phrase.trim().split(Regex("""\s+"""))
        if (words.size < 2) return null
        val out = mutableListOf(words[0])
        for (w in words.drop(1)) {
            if (w in OBJECT_STOP) break
            out += w
        }
        return if (out.size >= 2) out.joinToString(" ") else null
    }

    private fun fallback(type: ActionType, them: String): String = when (type) {
        ActionType.CALL_BACK -> "Call $them back"
        ActionType.REPLY -> "Reply to $them"
        ActionType.MEETING -> "Meeting"
        ActionType.USER_COMMITMENT -> "Something you promised to do"
        else -> if (them == THEM) "Follow up on a message" else "Follow up with $them"
    }.let { TextTools.finish(it) ?: "Follow up" }
}
