package com.ishtah.puzzlealarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    /** Blank means the provider's default model. */
    val model: String = "",
    /** Only used by [AiProvider.CUSTOM]; the other providers have fixed endpoints. */
    val baseUrl: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val puzzlesToSolve: Int = 1,
) {
    val effectiveModel: String get() = model.trim().ifEmpty { provider.defaultModel }
    val effectiveBaseUrl: String
        get() = (if (provider == AiProvider.CUSTOM) baseUrl.trim() else provider.defaultBaseUrl).trimEnd('/')
}

/** App-wide settings. The API key stays in app-private storage and is only sent to the chosen provider. */
class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString("provider", next.provider.name)
            .putString("apiKey", next.apiKey.trim())
            .putString("model", next.model.trim())
            .putString("baseUrl", next.baseUrl.trim())
            .putString("difficulty", next.difficulty.name)
            .putInt("puzzlesToSolve", next.puzzlesToSolve)
            .apply()
        _settings.value = load()
    }

    /** Recently asked questions, so the AI can be told not to repeat them. */
    fun recentQuestions(): List<String> =
        prefs.getString("recent", "")!!.split(SEP).filter { it.isNotBlank() }

    fun rememberQuestion(q: String) {
        val list = (listOf(q) + recentQuestions()).distinct().take(10)
        prefs.edit().putString("recent", list.joinToString(SEP)).apply()
    }

    private fun load() = AppSettings(
        provider = runCatching { AiProvider.valueOf(prefs.getString("provider", null)!!) }
            .getOrElse { AiProvider.detect(prefs.getString("apiKey", "")!!) ?: AiProvider.ANTHROPIC },
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", "") ?: "",
        baseUrl = prefs.getString("baseUrl", "") ?: "",
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
