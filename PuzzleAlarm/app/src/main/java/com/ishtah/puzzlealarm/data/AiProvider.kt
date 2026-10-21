package com.ishtah.puzzlealarm.data

/** Where puzzles are generated. Everything except [ANTHROPIC] speaks the OpenAI chat-completions API. */
enum class AiProvider(val label: String, val defaultModel: String, val defaultBaseUrl: String) {
    ANTHROPIC("Claude", "claude-opus-5-5", ""),
    OPENROUTER("OpenRouter", "openrouter/auto", "https://openrouter.ai/api/v1"),
    OPENAI("OpenAI", "gpt-4o-mini", "https://api.openai.com/v1"),
    CUSTOM("Other (OpenAI-compatible)", "", "");

    companion object {
        /** Guess the provider from the key's prefix; null when it can't be told. */
        fun detect(apiKey: String): AiProvider? {
            val k = apiKey.trim()
            return when {
                k.startsWith("sk-ant-") -> ANTHROPIC
                k.startsWith("sk-or-") -> OPENROUTER
                k.startsWith("sk-") -> OPENAI
                else -> null
            }
        }
    }
}
