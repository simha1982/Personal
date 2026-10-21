package com.ishtah.puzzlealarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ishtah.puzzlealarm.alarm.AlarmService
import com.ishtah.puzzlealarm.data.SettingsStore
import com.ishtah.puzzlealarm.puzzle.PuzzleRepository
import com.ishtah.puzzlealarm.ui.PuzzleAlarmTheme
import com.ishtah.puzzlealarm.ui.RingingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Full-screen, shown-over-lock-screen activity for a ringing alarm. Back does nothing. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        val puzzles = PuzzleRepository(applicationContext)
        val settings = SettingsStore.get(this)

        setContent {
            PuzzleAlarmTheme(dark = true) {
                val state by AlarmService.ringing.collectAsState()
                val puzzlesToSolve = remember { settings.settings.value.puzzlesToSolve }

                BackHandler { /* The alarm is only turned off by solving the puzzle. */ }

                LaunchedEffect(Unit) {
                    // Give the service a moment to publish its state, then close once nothing is ringing.
                    delay(1_500)
                    AlarmService.ringing.first { it == null }
                    finish()
                }

                RingingScreen(
                    state = state,
                    puzzlesToSolve = puzzlesToSolve,
                    puzzles = puzzles,
                    onSnooze = {
                        AlarmService.snooze(this)
                        finish()
                    },
                    onDismiss = {
                        AlarmService.dismiss(this)
                        finish()
                    },
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
