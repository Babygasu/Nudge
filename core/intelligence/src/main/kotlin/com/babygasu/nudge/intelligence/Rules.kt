package com.babygasu.nudge.intelligence

/**
 * Linguistic patterns used by [LocalEngine]. They describe *speech acts* — asking,
 * promising, blocking, chasing — rather than trigger words. Patterns run on lower-cased text
 * unless they are marked case-insensitive (those run on original text to build titles).
 */
internal object Rules {
    private val I = RegexOption.IGNORE_CASE

    /** One-time codes, promotions and newsletters: never actionable reminders. */
    val NOISE = Regex("""\b(?:otp|one[- ]time\s+(?:pass(?:word|code)?|code|pin)|verification\s+code|security\s+code|login\s+code|your\s+code\s+is|is\s+your\s+(?:\w+\s+)?code|unsubscribe|promo\s+code|discount\s+code|\d{1,2}\s?%\s+off|limited[- ]time\s+offer|sale\s+ends|offer\s+ends|newsletter|do\s+not\s+share\s+(?:this|your)\s+(?:code|otp|pin))\b""")

    /** Social phrases that look like imperatives but aren't tasks. */
    val CASUAL = Regex("""^(?:take\s+care|have\s+(?:a\s+(?:good|great|nice|lovely|safe)|fun)|enjoy|sleep\s+well|get\s+well|check\s+(?:this|it)\s+out|look\s+at\s+this|guess\s+what|imagine|thank(?:s|\s+you)|congrat|happy\s+(?:birthday|new\s+year|holidays)|good\s+(?:night|luck)|(?:please\s+)?(?:don't|dont|do\s+not)\s+worry|no\s+worries)""")

    val CALL_BACK = Regex("""\b(?:call|ring|phone|buzz)\s+(?:me|us)\b|\bgive\s+(?:me|us)\s+a\s+(?:call|ring|buzz)\b|\bcall\s+(?:me\s+)?back\b|\breturn\s+(?:my|the|our)\s+call\b|\bcall\s+when\s+you\b""")

    val COMMITMENT_START = Regex("""^(?:i'll|i\s+will|i'm\s+going\s+to|i\s+am\s+going\s+to|im\s+going\s+to|i\s+shall|i\s+promise\s+to|let\s+me\s+(?!know\b)|will\s+do\b)""")

    val SELF_TASK = Regex("""^(?:remind\s+me\s+to|i\s+(?:need|have|must|ought)\s+to|i've\s+got\s+to|i\s+gotta|i\s+should|note\s+to\s+self|to\s*-?\s*do\s*:|todo\s*:)""")

    val POLITE_REQUEST = Regex("""^(?:please|pls|plz|kindly)\b|\b(?:can|could|would|will)\s+(?:you|u)\s+(?:please\s+|kindly\s+)?(?!believe\b|imagine\b|guess\b|stop\b|not\b)\p{L}+|\b(?:i|we)\s+need\s+you\s+to\b|\b(?:i'd|we'd)\s+(?:like|love|appreciate\s+it\s+if)\s+(?:for\s+)?you\b|\b(?:make|be)\s+sure\s+(?:to|you)\b|\b(?:don't|dont|do\s+not)\s+forget\b|\bremember\s+to\b|\blet\s+me\s+know\b|\bget\s+back\s+to\s+me\b|\bplease\b""")

    val YOU_NEED = Regex("""\byou\s+(?:need|have|must|ought)\s+to\s+\p{L}+|\b(?:i|we)\s+(?:need|require)\s+(?:your|the)\b|\bwaiting\s+(?:for|on)\s+(?:you|your)\b""")

    val MEETING = Regex("""\b(?:meeting|meet(?:\s+up)?|appointment|appt|interview|catch[- ]up|zoom|google\s+meet|teams\s+call|conference|webinar|briefing|presentation|call\s+(?:at|on|tomorrow|today|this))\b""")

    val QUESTION_START = Regex("""^(?:can|could|would|will|shall|should|are|is|do|does|did|what|when|where|how|why|let's|lets)\b""")

    val ADDRESSED_TO_YOU = Regex("""\b(?:you|your|u|ur)\b""")

    val BLOCKING = Regex("""\b(?:can't|cannot|can\s+not|unable\s+to|won't\s+be\s+able\s+to)\s+(?:proceed|continue|move\s+(?:forward|on|ahead)|go\s+ahead|start|finish|complete|close|submit|progress)\b|\bblock(?:ed|ing)\b|\bholding\s+(?:us|me|everyone|everything|things|the\s+\w+)\s+up\b|\b(?:waiting|depending|relying)\s+on\s+(?:you|your)\b|\buntil\s+(?:you|u)\s+\p{L}+""")

    val ESCALATION = Regex("""\b(?:was|were|am|are|i'm|we're)\s+(?:expecting|waiting\s+for)\b|\bstill\s+(?:waiting|haven't|have\s+not|no\b|need)|\bhaven't\s+(?:received|heard|got(?:ten)?|seen)\b|\bhave\s+not\s+(?:received|heard|got(?:ten)?|seen)\b|\bany\s+update\b|\bfollowing\s+up\b|\bjust\s+(?:checking|chasing)\b|\bchasing\b|\b(?:second|third|final|last)\s+(?:reminder|time\s+asking)\b|\bgentle\s+reminder\b|\breminder\s*:|\b(?:is|was|now)\s+(?:overdue|late)\b""")

    val URGENT = Regex("""\b(?:urgent(?:ly)?|asap|a\.s\.a\.p|immediately|right\s+away|right\s+now|as\s+soon\s+as\s+possible|important|critical|emergency|time[- ]sensitive|top\s+priority|high\s+priority)\b""")

    val LOW_PRESSURE = Regex("""\b(?:no\s+rush|no\s+hurry|whenever\s+(?:you\s+can|convenient)|not\s+urgent|no\s+pressure|when\s+you\s+get\s+a\s+chance|in\s+your\s+own\s+time)\b""")

    /** "...until you submit the document" → "submit the document". */
    val UNTIL_YOU = Regex("""\buntil\s+(?:you|u)\s+(\p{L}+\s+(?:the|your|this|that|our|my|an?)\s+[\p{L}\p{N}-]+(?:\s+[\p{L}\p{N}-]+)?)""")

    /** "...until you submit it" → "submit" (the "it" is resolved from earlier in the message). */
    val UNTIL_YOU_PRONOUN = Regex("""\buntil\s+(?:you|u)\s+(\p{L}+)\s+(?:it|this|that|them)\b""")

    /** Candidate antecedents for "it": "the project report", "your signed contract". */
    val NOUN_PHRASE = Regex("""\b(?:the|your|my|our|this|that)\s+[\p{L}\p{N}-]+(?:\s+[\p{L}\p{N}-]+){0,2}""", RegexOption.IGNORE_CASE)

    /** A standalone pronoun in a title that should point at something concrete. */
    val TITLE_PRONOUN = Regex("""\bit\b""", RegexOption.IGNORE_CASE)

    /** "...expecting the report yesterday" → "the report yesterday" (cleaned later). */
    val WAITING_OBJECT = Regex("""\b(?:expecting|waiting\s+for|waiting\s+on|update\s+on|received|receive|without)\s+((?:the|your|that|this|my|our|an?)\s+[\p{L}\p{N}-]+(?:\s+[\p{L}\p{N}-]+){0,2})""")

    // ---- Title building (case-insensitive, run on original text) ----

    val REQUEST_ANCHOR = Regex("""\b(?:please|pls|kindly|(?:can|could|would|will)\s+(?:you|u)|(?:i|we)\s+need\s+you\s+to|make\s+sure|(?:don't|dont|do\s+not)\s+forget|remember\s+to)\b""", I)

    val REQUEST_PREFIX = Regex("""^(?:(?:please|pls|plz|kindly)\s+|(?:can|could|would|will)\s+(?:you|u)\s+(?:please\s+|kindly\s+)?(?:be\s+able\s+to\s+)?|(?:i|we)\s+need\s+you\s+to\s+|(?:i'd|we'd)\s+(?:like|love|appreciate\s+it\s+if)\s+(?:for\s+)?you\s+(?:to\s+|could\s+)?|(?:make|be)\s+sure\s+(?:to\s+|you\s+)|(?:don't|dont|do\s+not)\s+forget\s+(?:to\s+)?|remember\s+to\s+|you\s+(?:need|have|must|ought)\s+to\s+|(?:i|we)\s+(?:need|require)\s+)""", I)

    val COMMITMENT_PREFIX = Regex("""^(?:i'll|i\s+will|i'm\s+going\s+to|i\s+am\s+going\s+to|im\s+going\s+to|i\s+shall|i\s+promise\s+(?:to\s+)?|let\s+me|will\s+do,?)\s*(?:try\s+(?:to\s+)?|definitely\s+|surely\s+|also\s+|quickly\s+)?""", I)

    val SELF_PREFIX = Regex("""^(?:remind\s+me\s+to|i\s+(?:need|have|must|ought)\s+to|i've\s+got\s+to|i\s+gotta|i\s+should|note\s+to\s+self\s*:?|to\s*-?\s*do\s*:|todo\s*:)\s*""", I)

    val VAGUE = Regex("""\b(?:it|this|that|them|these|those)\b""", I)
}

internal object Vocabulary {
    val ACTION_VERBS: Set<String> = setOf(
        "send", "resend", "call", "ring", "email", "mail", "text", "message", "ping", "reply", "respond",
        "submit", "sign", "pay", "bring", "buy", "get", "pick", "drop", "book", "schedule", "reschedule",
        "confirm", "review", "check", "finish", "complete", "prepare", "share", "forward", "update", "fix",
        "upload", "print", "collect", "deliver", "return", "renew", "register", "apply", "file", "cancel",
        "order", "fill", "write", "draft", "read", "approve", "contact", "follow", "remind", "join", "attend",
        "come", "meet", "take", "transfer", "clean", "visit", "organise", "organize", "arrange", "plan",
        "invite", "ask", "tell", "inform", "notify", "look", "sort", "handle", "settle", "finalise", "finalize",
        "proofread", "edit", "create", "make", "do", "start", "post", "ship", "install", "test", "provide",
        "give", "lend", "help", "fetch", "grab", "remember", "let", "decide", "choose", "report", "call-back",
    )

    /**
     * Verbs that describe the same obligation when applied to the same thing. "Send the report",
     * "submit the report" and "get the report to Ama" are one task; "review the report" is another.
     * A verb may belong to several families ("get" = deliver or buy).
     */
    private val ACTION_FAMILIES: List<Set<String>> = listOf(
        setOf("send", "resend", "submit", "deliver", "share", "forward", "email", "mail", "provide", "give",
            "upload", "post", "ship", "file", "get", "bring", "return", "hand"),
        setOf("review", "check", "proofread", "look", "read", "go"),
        setOf("call", "ring", "phone", "contact", "ping", "message", "text", "whatsapp"),
        setOf("reply", "respond", "tell", "inform", "notify", "let", "confirm", "answer"),
        setOf("pay", "settle", "transfer"),
        setOf("book", "schedule", "reschedule", "arrange", "organise", "organize", "plan", "set"),
        setOf("prepare", "draft", "write", "create", "make", "finish", "complete", "finalise", "finalize", "do"),
        setOf("buy", "order", "get", "pick", "collect", "fetch", "grab"),
        setOf("sign", "approve"),
        setOf("attend", "join", "come", "meet"),
        setOf("fix", "update", "edit", "correct"),
        setOf("renew", "register", "apply"),
    )

    fun sameActionFamily(a: String, b: String): Boolean =
        a == b || ACTION_FAMILIES.any { a in it && b in it }

    /** Words that start a noun phrase; "Need the report" → "Get the report to …". */
    val OBJECT_STARTERS: Set<String> = setOf("the", "your", "a", "an", "this", "that", "these", "those", "their")
}
