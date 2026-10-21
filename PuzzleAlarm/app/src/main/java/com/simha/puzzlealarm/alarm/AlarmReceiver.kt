package com.simha.puzzlealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simha.puzzlealarm.data.AlarmRepository

/** Fired by AlarmManager; starts the ringing service and lines up the next occurrence. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val repo = AlarmRepository.get(context)
        val alarm = repo.get(id) ?: return

        if (!isSnooze) {
            if (alarm.isRepeating) {
                AlarmScheduler.schedule(context, alarm)
            } else {
                repo.upsert(alarm.copy(enabled = false))
            }
        }

        AlarmService.start(context, alarmId = id, isSnooze = isSnooze)
    }
}
