package com.simha.puzzlealarm.puzzle

import android.content.Context
import android.util.Log
import com.simha.puzzlealarm.data.AiProvider
import com.simha.puzzlealarm.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive

/** Hands out the next puzzle: from the chosen AI provider when a key is set and the call succeeds, otherwise offline. */
class PuzzleRepository(context: Context) {

    private val settingsStore = SettingsStore.get(context)
    private val claude = ClaudePuzzleProvider()
    private val openAiCompatible = OpenAiCompatiblePuzzleProvider()

    /** The reason the last AI attempt failed, for display in Settings. Null if it worked or wasn't tried. */
    var lastError: String? = null
        private set

    suspend fun next(): Puzzle = withContext(Dispatchers.IO) {
        val s = settingsStore.settings.value
        val puzzle = if (s.apiKey.isBlank()) {
            lastError = "No API key set"
            OfflinePuzzles.next(s.difficulty)
        } else {
            val result = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
                runCatching {
                    runInterruptible {
                        val recent = settingsStore.recentQuestions()
                        when (s.provider) {
                            AiProvider.ANTHROPIC -> claude.generate(s.apiKey, s.effectiveModel, s.difficulty, recent)
                            else -> openAiCompatible.generate(s.effectiveBaseUrl, s.apiKey, s.effectiveModel, s.difficulty, recent)
                        }
                    }
                }
            } ?: Result.failure(IllegalStateException("Timed out waiting for the AI"))
            result.onSuccess { lastError = null }.getOrElse { e ->
                ensureActive() // propagate real cancellation; a timeout just means "use offline"
                Log.w(TAG, "AI puzzle failed, using offline puzzle", e)
                lastError = e.message ?: e.javaClass.simpleName
                OfflinePuzzles.next(s.difficulty)
            }
        }
        settingsStore.rememberQuestion(puzzle.question)
        puzzle
    }

    companion object {
        private const val TAG = "PuzzleRepository"
        private const val OVERALL_TIMEOUT_MS = 30_000L
    }
}
