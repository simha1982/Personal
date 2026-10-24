package com.ishtah.puzzlealarm

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ishtah.puzzlealarm.alarm.AlarmScheduler
import com.ishtah.puzzlealarm.data.Alarm
import com.ishtah.puzzlealarm.data.AlarmRepository
import com.ishtah.puzzlealarm.ui.AlarmListScreen
import com.ishtah.puzzlealarm.ui.EditAlarmScreen
import com.ishtah.puzzlealarm.ui.PuzzleAlarmTheme
import com.ishtah.puzzlealarm.ui.SettingsScreen
import com.ishtah.puzzlealarm.ui.untilText
import java.util.Calendar

private sealed interface Screen {
    data object List : Screen
    data class Edit(val alarm: Alarm, val isNew: Boolean) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PuzzleAlarmTheme { App() } }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val repo = remember { AlarmRepository.get(context) }
    val alarms by repo.alarms.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun announce(alarm: Alarm) {
        val text = "Alarm set for ${untilText(AlarmScheduler.nextTrigger(alarm))} from now"
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }

    when (val s = screen) {
        Screen.List -> AlarmListScreen(
            alarms = alarms,
            onAdd = {
                val now = Calendar.getInstance()
                screen = Screen.Edit(
                    Alarm(id = repo.newId(), hour = now.get(Calendar.HOUR_OF_DAY), minute = now.get(Calendar.MINUTE)),
                    isNew = true,
                )
            },
            onEdit = { screen = Screen.Edit(it, isNew = false) },
            onToggle = { alarm, on ->
                val updated = alarm.copy(enabled = on)
                repo.upsert(updated)
                if (on) {
                    AlarmScheduler.schedule(context, updated)
                    announce(updated)
                } else {
                    AlarmScheduler.cancel(context, alarm.id)
                }
            },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Edit -> EditAlarmScreen(
            initial = s.alarm,
            isNew = s.isNew,
            onSave = { alarm ->
                repo.upsert(alarm)
                AlarmScheduler.cancel(context, alarm.id)
                AlarmScheduler.schedule(context, alarm)
                announce(alarm)
                screen = Screen.List
            },
            onDelete = {
                AlarmScheduler.cancel(context, s.alarm.id)
                repo.delete(s.alarm.id)
                screen = Screen.List
            },
            onBack = { screen = Screen.List },
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.List })
    }
}
