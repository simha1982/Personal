package com.simha.puzzlealarm.puzzle

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.simha.puzzlealarm.data.Difficulty
import java.time.Duration

/** Asks Claude for a fresh wake-up puzzle. Throws on any failure; the caller falls back to offline puzzles. */
class ClaudePuzzleProvider {

    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private fun clientFor(apiKey: String): AnthropicClient {
        if (client == null || clientKey != apiKey) {
            client?.close()
            client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .maxRetries(1)
                .build()
            clientKey = apiKey
        }
        return client!!
    }

    fun generate(apiKey: String, model: String, difficulty: Difficulty, avoid: List<String>): Puzzle {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            .system(PuzzlePrompt.SYSTEM)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessage(PuzzlePrompt.user(difficulty, avoid))
        if (model in SERVER_FALLBACK_MODELS) {
            // If a safety classifier declines the request, let the API retry on a suitable model.
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }

        val message = clientFor(apiKey).messages().create(builder.build())
        val stop = message.stopReason().orElse(null)?.toString()
        require(stop != "refusal" && stop != "max_tokens") { "Unusable reply (stop_reason=$stop)" }

        val text = message.content()
            .mapNotNull { it.text().orElse(null)?.text() }
            .joinToString("")
        return PuzzlePrompt.parse(text)
    }

    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 25L

        private val SERVER_FALLBACK_MODELS = setOf(
            "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5",
        )
    }
}
