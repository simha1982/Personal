package com.ishtah.puzzlealarm.puzzle

import com.ishtah.puzzlealarm.data.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Generates puzzles through any OpenAI-style chat-completions endpoint: OpenRouter, OpenAI, or a
 * self-hosted/compatible server. Throws on any failure; the caller falls back to offline puzzles.
 */
class OpenAiCompatiblePuzzleProvider {

    fun generate(baseUrl: String, apiKey: String, model: String, difficulty: Difficulty, avoid: List<String>): Puzzle {
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) { "Set a base URL for the provider" }
        require(model.isNotBlank()) { "Set a model name for the provider" }

        val body = JSONObject()
            .put("model", model)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", PuzzlePrompt.SYSTEM))
                    .put(JSONObject().put("role", "user").put("content", PuzzlePrompt.user(difficulty, avoid)))
            )

        val conn = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            // OpenRouter uses these to label the app on its dashboard; other servers ignore them.
            setRequestProperty("HTTP-Referer", "https://github.com/simha1982/Personal")
            setRequestProperty("X-Title", "Puzzle Alarm")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "HTTP $code: ${errorMessage(text)}" }
            return PuzzlePrompt.parse(content(text))
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 25_000

        /** The assistant text from a chat-completions response. */
        fun content(responseJson: String): String {
            val choice = JSONObject(responseJson).getJSONArray("choices").getJSONObject(0)
            require(choice.optString("finish_reason") != "length") { "Reply was cut off" }
            val content = choice.getJSONObject("message").opt("content")
            return when (content) {
                is String -> content
                // Some servers return content as an array of parts.
                is JSONArray -> (0 until content.length()).joinToString("") {
                    content.optJSONObject(it)?.optString("text").orEmpty()
                }
                else -> throw IllegalArgumentException("Empty reply")
            }
        }

        private fun errorMessage(text: String): String =
            runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                .getOrDefault(text.take(200))
    }
}
