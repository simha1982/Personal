package com.simha.puzzlealarm

import com.simha.puzzlealarm.data.AiProvider
import com.simha.puzzlealarm.puzzle.OpenAiCompatiblePuzzleProvider
import com.simha.puzzlealarm.puzzle.PuzzlePrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTest {

    @Test
    fun detectsProviderFromKeyPrefix() {
        assertEquals(AiProvider.ANTHROPIC, AiProvider.detect("sk-ant-api03-abc"))
        assertEquals(AiProvider.OPENROUTER, AiProvider.detect(" sk-or-v1-abc "))
        assertEquals(AiProvider.OPENAI, AiProvider.detect("sk-proj-abc"))
        assertNull(AiProvider.detect("gsk_abc"))
    }

    @Test
    fun readsChatCompletionContent() {
        val response = """
            {"id":"x","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant",
            "content":"{\"question\":\"Next: 2, 4, 8, ?\",\"answer\":\"16\",\"accepted_answers\":[],\"answer_type\":\"number\",\"hint\":\"double\"}"}}]}
        """.trimIndent()
        val puzzle = PuzzlePrompt.parse(OpenAiCompatiblePuzzleProvider.content(response))
        assertTrue(puzzle.isCorrect("16"))
    }

    @Test
    fun readsContentGivenAsParts() {
        val response = """{"choices":[{"message":{"content":[{"type":"text","text":"{\"question\":\"q\","},{"type":"text","text":"\"answer\":\"echo\"}"}]}}]}"""
        assertTrue(PuzzlePrompt.parse(OpenAiCompatiblePuzzleProvider.content(response)).isCorrect("echo"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedReply() {
        OpenAiCompatiblePuzzleProvider.content("""{"choices":[{"finish_reason":"length","message":{"content":"{\"q"}}]}""")
    }
}
