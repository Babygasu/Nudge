package com.babygasu.nudge.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** "Now" is Monday 2 November 2026, 10:00. */
class TimeParserTest {
    private val now: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2026, 11, 2, 10, 0), ZoneId.of("UTC"))
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(LocalDateTime.of(2026, 11, day, hour, minute), ZoneId.of("UTC"))

    private fun parse(s: String, dayFirst: Boolean = true) = TimeParser.parse(s, now, dayFirst)

    @Test fun tomorrowWithTime() {
        val t = parse("tomorrow at 3pm")!!
        assertEquals(at(3, 15), t.at)
        assertEquals(DuePrecision.EXACT, t.precision)
    }

    @Test fun endOfDay() = assertEquals(at(2, 17), parse("by end of day")!!.at)

    @Test fun relativeHours() {
        val t = parse("in 2 hours")!!
        assertEquals(at(2, 12), t.at)
        assertEquals(DuePrecision.EXACT, t.precision)
    }

    @Test fun halfAnHour() = assertEquals(at(2, 10, 30), parse("in half an hour")!!.at)

    @Test fun weekday() = assertEquals(at(6, 9), parse("on Friday")!!.at)

    @Test fun beforeWeekdayMeansDayBefore() = assertEquals(at(5, 9), parse("before Friday")!!.at)

    @Test fun nextWeekIsApproximate() {
        val t = parse("sometime next week")!!
        assertEquals(at(9, 9), t.at)
        assertEquals(DuePrecision.APPROXIMATE, t.precision)
    }

    @Test fun namedDate() = assertEquals(at(12, 9), parse("on 12 November")!!.at)

    @Test fun monthFirstDate() = assertEquals(at(12, 14), parse("Nov 12th at 2pm")!!.at)

    @Test fun numericDateRespectsLocale() {
        assertEquals(at(12, 9), parse("due 12/11")!!.at)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 12, 11, 9, 0), ZoneId.of("UTC")), parse("due 12/11", dayFirst = false)!!.at)
    }

    @Test fun pastReference() {
        val t = parse("I was expecting it yesterday")!!
        assertTrue(t.isPast)
    }

    @Test fun bareHourAlreadyPassedMovesToTomorrow() = assertEquals(at(3, 9), parse("call at 9")!!.at)

    @Test fun afternoonGuessForSmallHours() = assertEquals(at(2, 15), parse("see you at 3")!!.at)

    @Test fun pricesAreNotTimes() = assertNull(parse("it costs 10.99 dollars"))

    @Test fun noTime() = assertNull(parse("send the report"))

    @Test fun spansCoverTimePhrases() {
        val text = "Send it tomorrow at 3pm"
        val t = TimeParser.parse(text, now)!!
        val stripped = TextTools.removeSpans(text, t.spans)
        assertEquals("Send it", stripped)
    }
}
