package com.babygasu.nudge.intelligence

import java.util.Locale

internal const val THEM = "them"
private const val MAX_TITLE = 80

internal object TextTools {
    private val I = RegexOption.IGNORE_CASE
    private val SENTENCE_BREAK = Regex("""(?<=[.!?])\s+|\n+""")
    private val GREETING = Regex("""^(?:hi|hey|hello|hiya|dear|good\s+(?:morning|afternoon|evening)|morning|afternoon|evening)\b[^,.!?\n]{0,30}[,!.:]\s*""", I)
    private val GREETING_BARE = Regex("""^(?:hi|hey|hello)\s+(?=(?:please|pls|can|could|would|kindly)\b)""", I)
    private val FILLER = Regex("""^(?:so|ok(?:ay)?|also|and|but|btw|just|um+|oh|yes|yeah|sure|alright|well|then)\b[,.!]?\s+""", I)
    private val CONNECTOR_TAIL = Regex("""\b(?:by|before|on|at|until|till|no\s+later\s+than|from|for|around|latest\s+by|in)\s*$""", I)
    private val TRAILING = Regex("""(?:[\s,;:]+(?:please|pls|plz|thanks|thank\s+you|thx|asap|urgently|for\s+me|if\s+possible|if\s+you\s+can|when\s+you\s+(?:can|are\s+free|get\s+a\s+chance)|as\s+soon\s+as\s+possible|latest|ok|okay))+\W*$""", I)
    private val TRAILING_JUNK = Regex("""[^\p{L}\p{N})'"]+$""")
    private val WORD = Regex("""[\p{L}\p{N}']+""")
    private val FIRST_WORD = Regex("""^[\p{L}']+""")
    private val SPACES = Regex("""\s+""")

    fun normalize(s: String): String = s
        .replace('’', '\'').replace('‘', '\'')
        .replace('“', '"').replace('”', '"')
        .replace(Regex("""[ \t]+"""), " ")
        .trim()

    fun sentences(text: String): List<String> =
        text.split(SENTENCE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }

    fun stripLead(s: String): String {
        var t = s.trim()
        repeat(3) {
            t = t.replace(GREETING, "").replace(GREETING_BARE, "").replace(FILLER, "").trim()
        }
        return t
    }

    fun firstWord(lower: String): String = FIRST_WORD.find(lower)?.value ?: ""

    fun words(s: String): List<String> = WORD.findAll(s.lowercase(Locale.ROOT)).map { it.value }.toList()

    /** Removes time phrases (and a connector such as "by"/"before" right before them). */
    fun removeSpans(s: String, spans: List<IntRange>): String {
        val valid = spans.filter { it.first >= 0 && it.last < s.length }.sortedBy { it.first }
        if (valid.isEmpty()) return s
        val merged = mutableListOf<IntRange>()
        for (r in valid) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
            else merged += r
        }
        val sb = StringBuilder(s)
        for (r in merged.asReversed()) {
            var start = r.first
            val head = sb.substring(0, start)
            CONNECTOR_TAIL.find(head)?.let { start = it.range.first }
            sb.delete(start, r.last + 1)
        }
        return sb.toString().replace(SPACES, " ").trim()
    }

    fun trimTrailing(s: String): String {
        var t = s.trim()
        repeat(3) {
            t = t.replace(TRAILING, "").trim()
            t = t.replace(TRAILING_JUNK, "").trim()
            t = t.replace(CONNECTOR_TAIL, "").trim()
        }
        return t
    }

    fun finish(raw: String): String? {
        var t = raw.replace(SPACES, " ").trim().trim(',', ';', ':', '-', ' ')
        if (t.length < 3) return null
        t = t.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        if (t.length > MAX_TITLE) {
            val cut = t.take(MAX_TITLE)
            val space = cut.lastIndexOf(' ')
            t = (if (space > 40) cut.take(space) else cut).trimEnd() + "…"
        }
        return t
    }
}

/** Duplicate detection helpers. Tokens are derived from reminder titles, never from messages. */
object Similarity {
    private val STOP = setOf(
        "the", "and", "for", "with", "that", "this", "your", "you", "our", "their", "them", "they", "from",
        "have", "has", "had", "was", "were", "are", "will", "would", "could", "should", "can", "about", "into",
        "then", "than", "just", "also", "some", "any", "all", "its", "it's", "his", "her", "him", "she", "who",
        "what", "when", "where", "which", "how", "why", "not", "but", "let", "know", "back", "someone",
        "waiting", "follow", "call", "reply", "they'll", "there", "here", "please", "today", "tomorrow", "asap",
        "thing", "things", "stuff", "get", "missed",
    )

    fun tokens(title: String, exclude: Collection<String> = emptyList()): Set<String> {
        val ex = exclude.map { it.lowercase(Locale.ROOT) }.toSet()
        val out = linkedSetOf<String>()
        // Titles are action-first, so only the first word can be the action ("Send the report");
        // elsewhere words like "report" or "file" are the subject.
        TextTools.words(title).forEachIndexed { index, w ->
            val word = w.trim('\'')
            if (word.length < 3 || word in ex || word in STOP) return@forEachIndexed
            out += if (index == 0 && word in Vocabulary.ACTION_VERBS) "v:$word" else stem(word)
        }
        return out
    }

    /**
     * Two reminders describe the same obligation when their subjects overlap and, if both
     * name an action, the actions agree ("send the report" vs "review the report" differ).
     */
    fun isSameTask(a: Set<String>, b: Set<String>, threshold: Double = 0.6): Boolean {
        val (verbsA, nounsA) = a.partition { it.startsWith("v:") }
        val (verbsB, nounsB) = b.partition { it.startsWith("v:") }
        if (nounsA.isEmpty() || nounsB.isEmpty()) return false
        if (verbsA.isNotEmpty() && verbsB.isNotEmpty() && verbsA.intersect(verbsB.toSet()).isEmpty()) return false
        val overlap = nounsA.intersect(nounsB.toSet()).size.toDouble() / minOf(nounsA.size, nounsB.size)
        return overlap >= threshold
    }

    private fun stem(w: String): String {
        for (suffix in listOf("ing", "ed", "es", "s")) {
            if (w.length > suffix.length + 3 && w.endsWith(suffix)) return w.dropLast(suffix.length)
        }
        return w
    }
}
