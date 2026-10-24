package com.ishtah.puzzlealarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ishtah.puzzlealarm.alarm.RingingState
import com.ishtah.puzzlealarm.puzzle.AnswerType
import com.ishtah.puzzlealarm.puzzle.Puzzle
import com.ishtah.puzzlealarm.puzzle.PuzzleRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private const val ATTEMPTS_BEFORE_HINT = 2

/**
 * The screen shown while an alarm rings. The only ways out are the single snooze (if this ring
 * allows it) or solving [puzzlesToSolve] puzzles.
 */
@Composable
fun RingingScreen(
    state: RingingState?,
    puzzlesToSolve: Int,
    puzzles: PuzzleRepository,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var solving by remember { mutableStateOf(false) }
    var solved by remember { mutableIntStateOf(0) }
    var puzzle by remember { mutableStateOf<Puzzle?>(null) }
    // Start fetching the first puzzle the moment the alarm rings so it is ready by the time the user is.
    var pending by remember { mutableStateOf<Deferred<Puzzle>?>(null) }
    LaunchedEffect(Unit) { pending = scope.async { puzzles.next() } }

    fun loadNext() {
        puzzle = null
        val job = pending ?: scope.async { puzzles.next() }
        pending = null
        scope.launch { puzzle = job.await() }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Clock()
            if (!state?.label.isNullOrBlank()) {
                Text(state!!.label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))

            if (!solving) {
                Text(
                    "Solve ${if (puzzlesToSolve == 1) "a puzzle" else "$puzzlesToSolve puzzles"} to turn off the alarm.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { solving = true; if (puzzle == null) loadNext() },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) { Text("I'm awake — show the puzzle", fontSize = 18.sp) }
                Spacer(Modifier.height(16.dp))
                if (state?.snoozeAvailable == true) {
                    OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Snooze ${state.snoozeMinutes} min (only once)")
                    }
                } else {
                    Text(
                        if (state?.isSnoozedRing == true) "Snooze already used." else "Snooze is off for this alarm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Puzzle ${solved + 1} of $puzzlesToSolve",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                val p = puzzle
                if (p == null) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Thinking up a puzzle…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PuzzleCard(
                        puzzle = p,
                        onSolved = {
                            solved += 1
                            if (solved >= puzzlesToSolve) onDismiss() else loadNext()
                        },
                        onSkip = { loadNext() },
                    )
                }
                Spacer(Modifier.height(24.dp))
                if (state?.snoozeAvailable == true) {
                    TextButton(onClick = { solving = false }) { Text("Back (snooze)") }
                }
            }
        }
    }
}

@Composable
private fun PuzzleCard(puzzle: Puzzle, onSolved: () -> Unit, onSkip: () -> Unit) {
    var answer by remember(puzzle) { mutableStateOf("") }
    var wrongAttempts by remember(puzzle) { mutableIntStateOf(0) }
    var feedback by remember(puzzle) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(puzzle) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (answer.isBlank()) return
        if (puzzle.isCorrect(answer)) {
            onSolved()
        } else {
            wrongAttempts += 1
            answer = ""
            feedback = "Not quite. Try again."
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(puzzle.source.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(
            puzzle.question,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.take(40); feedback = null },
            label = { Text(if (puzzle.answerType == AnswerType.NUMBER) "Your answer (number)" else "Your answer (one word)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (puzzle.answerType == AnswerType.NUMBER) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            isError = feedback != null,
            supportingText = feedback?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (wrongAttempts >= ATTEMPTS_BEFORE_HINT && puzzle.hint.isNotBlank()) {
            Text("Hint: ${puzzle.hint}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(),
        ) { Text("Submit") }
        TextButton(onClick = onSkip) { Text("Give me a different puzzle") }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(1_000)
        }
    }
    Text(
        formatTime(context, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)),
        fontSize = 72.sp,
        fontWeight = FontWeight.Light,
    )
}
