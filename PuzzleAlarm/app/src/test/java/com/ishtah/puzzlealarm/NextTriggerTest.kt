package com.ishtah.puzzlealarm

import com.ishtah.puzzlealarm.alarm.AlarmScheduler
import com.ishtah.puzzlealarm.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class NextTriggerTest {

    // Wednesday 2026-10-21 08:00
    private fun now() = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 21, 8, 0, 0)
    }

    private fun Long.asCal() = Calendar.getInstance().apply { timeInMillis = this@asCal }

    @Test
    fun oneShotLaterTodayRingsToday() {
        val t = AlarmScheduler.nextTrigger(Alarm(1, 9, 30), now()).asCal()
        assertEquals(21, t.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, t.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, t.get(Calendar.MINUTE))
    }

    @Test
    fun oneShotEarlierTodayRingsTomorrow() {
        val t = AlarmScheduler.nextTrigger(Alarm(1, 7, 0), now()).asCal()
        assertEquals(22, t.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun sameMinuteIsNotNow() {
        val t = AlarmScheduler.nextTrigger(Alarm(1, 8, 0), now()).asCal()
        assertEquals(22, t.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun repeatingSkipsToNextSelectedDay() {
        val mondays = Alarm(1, 7, 0, days = setOf(Calendar.MONDAY))
        val t = AlarmScheduler.nextTrigger(mondays, now()).asCal()
        assertEquals(Calendar.MONDAY, t.get(Calendar.DAY_OF_WEEK))
        assertEquals(26, t.get(Calendar.DAY_OF_MONTH))
    }
}
