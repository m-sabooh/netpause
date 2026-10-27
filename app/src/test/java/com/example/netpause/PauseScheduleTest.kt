package com.example.netpause

import org.junit.Assert.*
import org.junit.Test

class PauseScheduleTest {
    @Test fun indefiniteScheduleUsesPreviousRouterWeekdayMinuteSlot() {
        val sunday = routerClock("var systemdsttime = '2026-10-25T14:20:30+03:00';")
        val monday = routerClock("systemdsttime='2026-10-26 00:00:00+03:00'")
        assertEquals(PauseSchedule("", "", 0L, listOf(AllowedPeriod("00:00", "00:01", "6"))), indefiniteSchedule(sunday))
        assertEquals(PauseSchedule("", "", 0L, listOf(AllowedPeriod("00:00", "00:01", "7"))), indefiniteSchedule(monday))
    }
    @Test fun deadlineRoundsUpAndUsesRouterWeekday() {
        val clock = routerClock("var systemdsttime = '2026-10-25T14:20:30+03:00';")
        val schedule = timedSchedule(clock, clock.timeInMillis, 10)
        assertEquals(630_000, schedule.deadline - clock.timeInMillis)
        assertEquals("20261025", schedule.startDate)
        assertEquals(listOf(AllowedPeriod("14:31", "23:59", "7")), schedule.periods)
    }
    @Test fun minuteBoundaryDoesNotAddAnotherMinute() {
        val clock = routerClock("systemdsttime='2026-10-26 10:00:00+03:00'")
        val schedule = timedSchedule(clock, clock.timeInMillis, 20)
        assertEquals(1_200_000, schedule.deadline - clock.timeInMillis)
        assertEquals("1", schedule.periods.single().days)
    }
    @Test fun rejectClockSkewOverflowAndUnverifiedDateBoundaries() {
        val clock = routerClock("systemdsttime='2026-10-25 23:40:00+03:00'")
        assertThrows(RouterException::class.java) { timedSchedule(clock, clock.timeInMillis + 100_000, 1) }
        assertThrows(RouterException::class.java) { timedSchedule(clock, clock.timeInMillis, 20) }
        assertThrows(RouterException::class.java) { timedSchedule(clock, clock.timeInMillis, 19) }
        assertThrows(RouterException::class.java) { timedSchedule(clock, clock.timeInMillis, Int.MAX_VALUE) }
        assertThrows(RouterException::class.java) { timedSchedule(clock, clock.timeInMillis, 0) }
    }
}
