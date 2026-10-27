package com.example.netpause

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

internal data class AllowedPeriod(val start: String, val end: String, val days: String)
internal data class PauseSchedule(val startDate: String, val endDate: String, val deadline: Long, val periods: List<AllowedPeriod>)

internal fun routerClock(source: String): Calendar {
    val raw = Regex("\\b(?:var\\s+)?systemdsttime\\s*=\\s*['\"]([^'\"]+)['\"]").find(source)?.groupValues?.get(1)
        ?: throw RouterException(R.string.error_schema)
    val text = raw.replace("T", " ")
    val match = Regex("(\\d{4}-\\d{2}-\\d{2})[ ](\\d{2}:\\d{2}:\\d{2})(?:[ ]*)([+-]\\d{2}:?\\d{2})").find(text)
        ?: throw RouterException(R.string.error_schema)
    val zone = TimeZone.getTimeZone("GMT" + match.groupValues[3])
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { timeZone = zone; isLenient = false }
    val date = try { format.parse(match.groupValues[1] + " " + match.groupValues[2])!! }
        catch (_: Exception) { throw RouterException(R.string.error_clock) }
    return Calendar.getInstance(zone).apply { time = date }
}

internal fun indefiniteSchedule(clock: Calendar): PauseSchedule {
    // shortcut: firmware requires one 1-minute allowed slot per week (00:00-00:01 on the previous weekday), revisit if multi-week uninterrupted pauses need zero-slot blocking.
    val previousDay = ((clock.get(Calendar.DAY_OF_WEEK) + 4) % 7 + 1).toString()
    return PauseSchedule("", "", 0L, listOf(AllowedPeriod("00:00", "00:01", previousDay)))
}

internal fun timedSchedule(clock: Calendar, phoneNow: Long, minutes: Int): PauseSchedule {
    if (abs(clock.timeInMillis - phoneNow) > 90_000L) throw RouterException(R.string.error_clock)
    if (minutes < 1) throw RouterException(R.string.invalid_minutes)
    val end = clock.clone() as Calendar
    val exact = Math.addExact(clock.timeInMillis, minutes.toLong() * 60_000L)
    end.timeInMillis = Math.multiplyExact((exact + 59_999L) / 60_000L, 60_000L)
    fun format(pattern: String, date: Date) = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = clock.timeZone }.format(date)
    val date = format("yyyyMMdd", clock.time)
    val endDate = format("yyyyMMdd", end.time)
    // shortcut: date-expiration and midnight semantics are unverified, reject crossing dates until hardware validation.
    if (date != endDate || format("HH:mm", end.time) >= "23:59") throw RouterException(R.string.error_midnight)
    val day = ((clock.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1).toString()
    return PauseSchedule(date, date, end.timeInMillis, listOf(AllowedPeriod(format("HH:mm", end.time), "23:59", day)))
}
