package com.ishtah.puzzlealarm.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ishtah.puzzlealarm.data.Alarm
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(
    initial: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val time = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    var days by remember { mutableStateOf(initial.days) }
    var label by remember { mutableStateOf(initial.label) }
    var snoozeEnabled by remember { mutableStateOf(initial.snoozeEnabled) }
    var snoozeMinutes by remember { mutableFloatStateOf(initial.snoozeMinutes.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!isNew) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(state = time)

            Text("Repeat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WEEK_ORDER.forEach { day ->
                    FilterChip(
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                        label = { Text(dayLetter(day)) },
                    )
                }
            }
            Text(daysSummary(days), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Snooze", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Allowed once per alarm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = snoozeEnabled, onCheckedChange = { snoozeEnabled = it })
            }
            if (snoozeEnabled) {
                Text("Snooze length: ${snoozeMinutes.roundToInt()} min", modifier = Modifier.fillMaxWidth())
                Slider(
                    value = snoozeMinutes,
                    onValueChange = { snoozeMinutes = it },
                    valueRange = Alarm.MIN_SNOOZE_MINUTES.toFloat()..Alarm.MAX_SNOOZE_MINUTES.toFloat(),
                    steps = Alarm.MAX_SNOOZE_MINUTES - Alarm.MIN_SNOOZE_MINUTES - 1,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            hour = time.hour,
                            minute = time.minute,
                            days = days,
                            label = label.trim(),
                            enabled = true,
                            snoozeEnabled = snoozeEnabled,
                            snoozeMinutes = snoozeMinutes.roundToInt(),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
