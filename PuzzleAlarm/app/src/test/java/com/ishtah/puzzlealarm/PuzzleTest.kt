package com.ishtah.puzzlealarm

import com.ishtah.puzzlealarm.data.Difficulty
import com.ishtah.puzzlealarm.puzzle.AnswerType
import com.ishtah.puzzlealarm.puzzle.PuzzlePrompt
import com.ishtah.puzzlealarm.puzzle.OfflinePuzzles
import com.ishtah.puzzlealarm.puzzle.Puzzle
import com.ishtah.puzzlealarm.puzzle.PuzzleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleTest {

    @Test
    fun answersAreNormalized() {
        val p = Puzzle("q", "Echo", listOf("an echo"), AnswerType.WORD, source = PuzzleSource.AI)
        assertTrue(p.isCorrect("  echo "))
        assertTrue(p.isCorrect("ECHO."))
        assertTrue(p.isCorrect("An echo"))
        assertFalse(p.isCorrect("eco"))
        assertFalse(p.isCorrect(""))
    }

    @Test
    fun numericAnswersCompareByValue() {
        val p = Puzzle("q", "1,200", answerType = AnswerType.NUMBER, source = PuzzleSource.AI)
        assertTrue(p.isCorrect("1200"))
        assertTrue(p.isCorrect("1200.0"))
        assertFalse(p.isCorrect("120"))
    }

    @Test
    fun offlinePuzzlesAreSolvableWithTheirOwnAnswer() {
        val r = Random(7)
        repeat(500) {
            val d = Difficulty.entries[it % 3]
            val p = OfflinePuzzles.next(d, r)
            assertTrue(p.question, p.question.isNotBlank())
            assertTrue(p.question, p.isCorrect(p.answer))
        }
    }

    @Test
    fun parsesClaudeReplyEvenWithSurroundingText() {
        val reply = """
            Here you go:
            {"question": "What is 12 x 12 - 4?", "answer": 140, "accepted_answers": [], "answer_type": "number", "hint": "144 first"}
        """.trimIndent()
        val p = PuzzlePrompt.parse(reply)
        assertEquals(AnswerType.NUMBER, p.answerType)
        assertEquals(PuzzleSource.AI, p.source)
        assertTrue(p.isCorrect("140"))
    }

    @Test(expected = Exception::class)
    fun rejectsReplyWithoutJson() {
        PuzzlePrompt.parse("Sorry, I can't do that.")
    }
}
