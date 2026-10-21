package com.simha.puzzlealarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val puzzlesToSolve: Int = 1,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
    }
}

/** App-wide settings. The API key stays in app-private storage and never leaves the device except to api.anthropic.com. */
class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString("apiKey", next.apiKey.trim())
            .putString("model", next.model.trim().ifEmpty { AppSettings.DEFAULT_MODEL })
            .putString("difficulty", next.difficulty.name)
            .putInt("puzzlesToSolve", next.puzzlesToSolve)
            .apply()
        _settings.value = load()
    }

    /** Recently asked questions, so Claude can be told not to repeat them. */
    fun recentQuestions(): List<String> =
        prefs.getString("recent", "")!!.split(SEP).filter { it.isNotBlank() }

    fun rememberQuestion(q: String) {
        val list = (listOf(q) + recentQuestions()).distinct().take(10)
        prefs.edit().putString("recent", list.joinToString(SEP)).apply()
    }

    private fun load() = AppSettings(
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", AppSettings.DEFAULT_MODEL) ?: AppSettings.DEFAULT_MODEL,
        difficulty = runCatching { Difficulty.valueOf(prefs.getString("difficulty", "MEDIUM")!!) }
            .getOrDefault(Difficulty.MEDIUM),
        puzzlesToSolve = prefs.getInt("puzzlesToSolve", 1).coerceIn(1, 3),
    )

    companion object {
        private const val SEP = "\u001F"

        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
