package com.simha.puzzlealarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.simha.puzzlealarm.AlarmActivity
import com.simha.puzzlealarm.R
import com.simha.puzzlealarm.data.AlarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the ringing screen needs to know about the alarm that is going off right now. */
data class RingingState(
    val alarmId: Int,
    val label: String,
    val snoozeAvailable: Boolean,
    val snoozeMinutes: Int,
    val isSnoozedRing: Boolean,
)

/**
 * Foreground service that keeps the alarm sounding until the puzzle is solved (dismiss) or the
 * single allowed snooze is used. There is deliberately no auto-silence timeout.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring(
                intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
                intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false),
            )
            ACTION_SNOOZE -> {
                _ringing.value?.takeIf { it.snoozeAvailable }?.let {
                    AlarmScheduler.scheduleSnooze(this, it.alarmId, it.snoozeMinutes)
                }
                stopRinging()
            }
            ACTION_DISMISS -> stopRinging()
            null -> {
                // Restarted by the system after being killed while ringing: keep ringing.
                val prefs = statePrefs(this)
                val id = prefs.getInt(KEY_ID, -1)
                if (id >= 0) ring(id, prefs.getBoolean(KEY_SNOOZE, false)) else stopSelf()
            }
        }
        return START_STICKY
    }

    private fun ring(alarmId: Int, isSnooze: Boolean) {
        val alarm = AlarmRepository.get(this).get(alarmId)
        val state = RingingState(
            alarmId = alarmId,
            label = alarm?.label.orEmpty(),
            // Only one snooze per alarm: a snoozed ring can never be snoozed again.
            snoozeAvailable = !isSnooze && (alarm?.snoozeEnabled ?: false),
            snoozeMinutes = alarm?.snoozeMinutes ?: 5,
            isSnoozedRing = isSnooze,
        )
        statePrefs(this).edit().putInt(KEY_ID, alarmId).putBoolean(KEY_SNOOZE, isSnooze).apply()
        _ringing.value = state

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(state), type)
        } catch (e: Exception) {
            // e.g. a sticky restart from the background on Android 12+, where FGS starts are restricted.
            Log.e(TAG, "Could not enter foreground", e)
            stopSelf()
            return
        }
        startSound()
        startVibration()
    }

    private fun buildNotification(state: RingingState): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Shown while an alarm is ringing"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(state.label.ifBlank { "Alarm" })
            .setContentText("Solve the puzzle to turn it off")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
    }

    private fun startSound() {
        if (player != null) return
        val audio = getSystemService(AudioManager::class.java)
        // A muted alarm stream would defeat the purpose; bring it up to a usable level.
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.6).toInt().coerceAtLeast(1), 0) }
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not play alarm sound", e)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        if (vibrator != null) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
        vibrator = v
    }

    private fun stopRinging() {
        statePrefs(this).edit().clear().apply()
        _ringing.value = null
        releaseMedia()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseMedia() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        releaseMedia()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_RING = "com.simha.puzzlealarm.RING"
        private const val ACTION_SNOOZE = "com.simha.puzzlealarm.SNOOZE"
        private const val ACTION_DISMISS = "com.simha.puzzlealarm.DISMISS"
        private const val KEY_ID = "id"
        private const val KEY_SNOOZE = "snooze"

        private val _ringing = MutableStateFlow<RingingState?>(null)
        val ringing: StateFlow<RingingState?> = _ringing.asStateFlow()

        private fun statePrefs(context: Context) =
            context.getSharedPreferences("ringing", Context.MODE_PRIVATE)

        fun start(context: Context, alarmId: Int, isSnooze: Boolean) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_RING)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            ContextCompat.startForegroundService(context, intent)
        }

        fun snooze(context: Context) =
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))

        /** Only called after the wake-up puzzles have been solved. */
        fun dismiss(context: Context) =
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS))
    }
}
