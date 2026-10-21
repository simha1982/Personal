package com.simha.puzzlealarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.simha.puzzlealarm.MainActivity
import com.simha.puzzlealarm.data.Alarm
import com.simha.puzzlealarm.data.AlarmRepository
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val SNOOZE_REQUEST_OFFSET = 100_000

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    /** Next time (epoch millis) this alarm should ring, strictly after [now]. */
    fun nextTrigger(alarm: Alarm, now: Calendar = Calendar.getInstance()): Long {
        val c = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!c.after(now)) c.add(Calendar.DAY_OF_YEAR, 1)
        if (alarm.isRepeating) {
            repeat(7) {
                if (c.get(Calendar.DAY_OF_WEEK) in alarm.days) return c.timeInMillis
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return c.timeInMillis
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, alarm: Alarm) {
        cancelMain(context, alarm.id)
        if (!alarm.enabled) return
        setAlarmClock(context, nextTrigger(alarm), alarm.id, isSnooze = false)
    }

    fun scheduleSnooze(context: Context, alarmId: Int, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAlarmClock(context, at, alarmId, isSnooze = true)
    }

    fun cancel(context: Context, alarmId: Int) {
        cancelMain(context, alarmId)
        context.getSystemService(AlarmManager::class.java)
            .cancel(operation(context, alarmId, isSnooze = true))
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.get(context).alarms.value.forEach { schedule(context, it) }
    }

    private fun cancelMain(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(operation(context, alarmId, isSnooze = false))
    }

    private fun setAlarmClock(context: Context, triggerAt: Long, alarmId: Int, isSnooze: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val op = operation(context, alarmId, isSnooze)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), op)
        } catch (e: SecurityException) {
            // Exact alarms not permitted (should not happen with USE_EXACT_ALARM); best effort instead.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
        }
    }

    private fun operation(context: Context, alarmId: Int, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_IS_SNOOZE, isSnooze)
        val request = if (isSnooze) alarmId + SNOOZE_REQUEST_OFFSET else alarmId
        return PendingIntent.getBroadcast(
            context, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
