package com.babygasu.nudge.intelligence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A time reference found in text.
 * [spans] are character ranges of the time phrases so titles can omit them.
 */
data class TimeExpression(
    val at: ZonedDateTime,
    val precision: DuePrecision,
    val isPast: Boolean,
    val spans: List<IntRange>,
)

/**
 * Offline, rule-based parser for the time expressions people actually use in messages:
 * "by Friday", "tomorrow at 3pm", "end of day", "in 2 hours", "12 Nov", "next week", "yesterday".
 */
object TimeParser {

    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "a couple of" to 2, "a few" to 3,
    )

    private val HALF_HOUR = Regex("""\bin\s+half\s+an?\s+hour\b""")
    private val RELATIVE = Regex("""\bin\s+(\d{1,3}|an?|one|two|three|four|five|six|seven|eight|nine|ten|a\s+couple\s+of|a\s+few)\s+(minutes?|mins?|hours?|hrs?|days?|weeks?)\b""")
    private val PAST = Regex("""\b(yesterday|last\s+night|last\s+week|earlier\s+today)\b""")
    private val DAY_AFTER = Regex("""\b(?:the\s+)?day\s+after\s+tomorrow\b""")
    private val TOMORROW = Regex("""\b(?:tomorrow|tmrw|tmr|tomorow|2moro|2morrow)\b(?:\s+(morning|afternoon|evening|night))?""")
    private val TODAY_PARTS = Regex("""\b(tonight|this\s+morning|this\s+afternoon|this\s+evening|end\s+of\s+(?:the\s+)?day|end\s+of\s+today|eod|cob|close\s+of\s+business|today)\b""")
    private val WEEKDAY = Regex("""\b(?:(next|this\s+coming|coming|this)\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b(?:\s+(morning|afternoon|evening|night))?""")
    private val WEEK_PHRASES = Regex("""\b(end\s+of\s+(?:the\s+|this\s+)?week|eow|this\s+weekend|the\s+weekend|next\s+week|end\s+of\s+(?:the\s+|this\s+)?month|eom|next\s+month)\b""")
    private const val MONTHS = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private val DAY_MONTH = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)\b""")
    private val MONTH_DAY = Regex("""\b($MONTHS)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val NUMERIC_DATE = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{4}|\d{2}))?\b""")
    private val ORDINAL_DAY = Regex("""\bthe\s+(\d{1,2})(?:st|nd|rd|th)\b""")
    private val CLOCK = Regex("""\b(\d{1,2}):(\d{2})\s*(a\.?m\.?|p\.?m\.?)?(?!\p{L})""")
    private val DOT_CLOCK = Regex("""\b(\d{1,2})\.(\d{2})\s*(a\.?m\.?|p\.?m\.?)(?!\p{L})""")
    private val HOUR_AMPM = Regex("""\b(\d{1,2})\s*(a\.?m\.?|p\.?m\.?)(?!\p{L})""")
    private val OCLOCK = Regex("""\b(\d{1,2})\s+o'?clock\b""")
    private val AT_HOUR = Regex("""\b(?:at|by|around|before)\s+(\d{1,2})\b(?![:./\d])(?!\s*(?:%|days?|hours?|hrs?|mins?|minutes?|weeks?|months?|years?|people|persons|items?|copies|pieces|o'?clock|of\b))""")
    private val NOON = Regex("""\b(noon|midday|midnight|lunchtime)\b""")
    private val PART_OF_DAY = mapOf(
        "morning" to LocalTime.of(9, 0),
        "afternoon" to LocalTime.of(14, 0),
        "evening" to LocalTime.of(18, 0),
        "night" to LocalTime.of(20, 0),
    )
    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
    private const val PAST_DATE_WINDOW_DAYS = 60L

    fun parse(text: String, now: ZonedDateTime, dayFirst: Boolean = true): TimeExpression? {
        val lower = text.lowercase(Locale.ROOT).replace('’', '\'')
        val sameLength = lower.length == text.length
        val spans = mutableListOf<IntRange>()
        fun note(m: MatchResult) {
            if (sameLength) spans += m.range
        }
        fun precededByBefore(m: MatchResult): Boolean =
            lower.substring(0, m.range.first).trimEnd().endsWith("before")

        val today = now.toLocalDate()
        var date: LocalDate? = null
        var defaultTime: LocalTime? = null
        var precision = DuePrecision.DAY
        var time: LocalTime? = null
        var relative: ZonedDateTime? = null
        var relativeExact = false
        var past = false
        var before = false

        // Relative durations: "in 2 hours", "in half an hour"
        val half = HALF_HOUR.find(lower)
        if (half != null) {
            note(half)
            relative = now.plusMinutes(30)
            relativeExact = true
        } else {
            val m = RELATIVE.find(lower)
            if (m != null) {
                note(m)
                val q = m.groupValues[1].replace(Regex("\\s+"), " ")
                val n = (q.toIntOrNull() ?: NUMBER_WORDS[q] ?: 1).toLong()
                val unit = m.groupValues[2]
                relative = when {
                    unit.startsWith("min") -> { relativeExact = true; now.plusMinutes(n) }
                    unit.startsWith("h") -> { relativeExact = true; now.plusHours(n) }
                    unit.startsWith("d") -> now.plusDays(n)
                    else -> now.plusWeeks(n)
                }
            }
        }

        // Named days
        DAY_AFTER.find(lower)?.let { note(it); date = today.plusDays(2) }
        if (date == null) TOMORROW.find(lower)?.let { m ->
            note(m)
            date = today.plusDays(1)
            m.groups[1]?.value?.let { defaultTime = PART_OF_DAY[it] }
        }
        if (date == null) TODAY_PARTS.find(lower)?.let { m ->
            note(m)
            date = today
            val v = m.value
            defaultTime = when {
                v == "tonight" -> LocalTime.of(19, 0)
                v.endsWith("morning") -> LocalTime.of(9, 0)
                v.endsWith("afternoon") -> LocalTime.of(14, 0)
                v.endsWith("evening") -> LocalTime.of(18, 0)
                else -> LocalTime.of(17, 0) // today, end of day, eod, cob
            }
        }
        if (date == null) WEEKDAY.find(lower)?.let { m ->
            note(m)
            val dow = DayOfWeek.valueOf(m.groupValues[2].uppercase(Locale.ROOT))
            val modifier = m.groupValues[1]
            var diff = (dow.value - today.dayOfWeek.value + 7) % 7
            if (diff == 0 && (modifier.contains("next") || modifier.contains("coming"))) diff = 7
            date = today.plusDays(diff.toLong())
            m.groups[3]?.value?.let { defaultTime = PART_OF_DAY[it] }
            before = precededByBefore(m)
        }
        if (date == null) WEEK_PHRASES.find(lower)?.let { m ->
            note(m)
            val v = m.value
            when {
                v.contains("next week") -> {
                    date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    precision = DuePrecision.APPROXIMATE
                }
                v.contains("weekend") -> {
                    date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                    defaultTime = LocalTime.of(10, 0)
                    precision = DuePrecision.APPROXIMATE
                }
                v.contains("week") || v == "eow" -> {
                    date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                    defaultTime = LocalTime.of(17, 0)
                }
                v.contains("next month") -> {
                    date = today.plusMonths(1).withDayOfMonth(1)
                    precision = DuePrecision.APPROXIMATE
                }
                else -> {
                    date = today.with(TemporalAdjusters.lastDayOfMonth())
                    defaultTime = LocalTime.of(17, 0)
                }
            }
            before = precededByBefore(m)
        }

        // Explicit calendar dates
        fun acceptDate(m: MatchResult, d: LocalDate?) {
            if (d == null) return
            note(m)
            var resolved: LocalDate = d
            if (resolved.isBefore(today)) {
                if (ChronoUnit.DAYS.between(resolved, today) <= PAST_DATE_WINDOW_DAYS) past = true
                else resolved = resolved.plusYears(1)
            }
            date = resolved
            before = precededByBefore(m)
        }
        if (date == null) DAY_MONTH.find(lower)?.let { m ->
            acceptDate(m, safeDate(today.year, month(m.groupValues[2]), m.groupValues[1].toInt()))
        }
        if (date == null) MONTH_DAY.find(lower)?.let { m ->
            acceptDate(m, safeDate(today.year, month(m.groupValues[1]), m.groupValues[2].toInt()))
        }
        if (date == null) NUMERIC_DATE.find(lower)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val (day, mon) = if (dayFirst) a to b else b to a
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            val d = safeDate(year ?: today.year, mon, day)
            if (d != null && year != null) {
                note(m); date = d; past = d.isBefore(today); before = precededByBefore(m)
            } else {
                acceptDate(m, d)
            }
        }
        if (date == null) ORDINAL_DAY.find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            var d = safeDate(today.year, today.monthValue, day)
            if (d != null && d.isBefore(today)) d = safeDate(today.plusMonths(1).year, today.plusMonths(1).monthValue, day)
            if (d != null) { note(m); date = d; before = precededByBefore(m) }
        }

        // Clock times
        CLOCK.find(lower)?.let { m ->
            toTime(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3])?.let { note(m); time = it }
        }
        if (time == null) DOT_CLOCK.find(lower)?.let { m ->
            toTime(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3])?.let { note(m); time = it }
        }
        if (time == null) HOUR_AMPM.find(lower)?.let { m ->
            toTime(m.groupValues[1].toInt(), 0, m.groupValues[2])?.let { note(m); time = it }
        }
        if (time == null) OCLOCK.find(lower)?.let { m ->
            toTime(m.groupValues[1].toInt(), 0, "")?.let { note(m); time = it }
        }
        if (time == null) AT_HOUR.find(lower)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) {
                note(m)
                time = LocalTime.of(if (h <= 6) h + 12 else h, 0)
            }
        }
        if (time == null) NOON.find(lower)?.let { m ->
            note(m)
            time = when (m.value) {
                "midnight" -> LocalTime.of(23, 59)
                "lunchtime" -> LocalTime.of(12, 30)
                else -> LocalTime.NOON
            }
        }

        val pastMatch = PAST.find(lower)
        if (pastMatch != null) {
            note(pastMatch)
            if (date == null && relative == null && time == null) past = true
        }

        val zone = now.zone
        val rel = relative
        if (rel != null) {
            val at = if (relativeExact) rel else rel.toLocalDate().atTime(time ?: DEFAULT_TIME).atZone(zone)
            return TimeExpression(at, if (relativeExact) DuePrecision.EXACT else DuePrecision.DAY, false, spans)
        }

        val t = time
        var d = date
        if (d != null) {
            if (before && t == null) d = d.minusDays(1)
            var at = d.atTime(t ?: defaultTime ?: DEFAULT_TIME).atZone(zone)
            val prec = if (t != null) DuePrecision.EXACT else precision
            if (past) return TimeExpression(at, prec, true, spans)
            if (at.isBefore(now)) {
                if (t != null && d == today && at.isBefore(now.minusMinutes(15))) {
                    return TimeExpression(at, prec, true, spans) // e.g. "at 9am today", said at 11am
                }
                at = now.plusHours(1).truncatedTo(ChronoUnit.MINUTES)
            }
            return TimeExpression(at, prec, false, spans)
        }
        if (t != null) {
            var at = today.atTime(t).atZone(zone)
            if (at.isBefore(now.minusMinutes(15))) at = at.plusDays(1)
            return TimeExpression(at, DuePrecision.EXACT, false, spans)
        }
        if (past) return TimeExpression(now, DuePrecision.APPROXIMATE, true, spans)
        return null
    }

    private fun month(s: String): Int =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(s.take(3)) + 1

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun toTime(hour: Int, minute: Int, ampmRaw: String): LocalTime? {
        if (minute !in 0..59) return null
        val ampm = ampmRaw.replace(".", "")
        val h = when (ampm) {
            "am" -> if (hour in 1..12) hour % 12 else return null
            "pm" -> if (hour in 1..12) hour % 12 + 12 else return null
            else -> when (hour) {
                in 1..6 -> hour + 12 // "3:30" in a message almost always means afternoon
                in 0..23 -> hour
                else -> return null
            }
        }
        return LocalTime.of(h, minute)
    }
}
