package com.simha.puzzlealarm.puzzle

import com.simha.puzzlealarm.data.Difficulty
import org.json.JSONObject

/** The puzzle request and reply format, shared by every AI provider. */
object PuzzlePrompt {

    fun user(difficulty: Difficulty, avoid: List<String>): String = buildString {
        appendLine("Difficulty: ${difficulty.label}. ${DIFFICULTY_GUIDE.getValue(difficulty)}")
        appendLine("Puzzle style: ${CATEGORIES.random()}.")
        if (avoid.isNotEmpty()) {
            appendLine("Do not reuse or closely resemble any of these recent puzzles:")
            avoid.forEach { appendLine("- ${it.replace('\n', ' ')}") }
        }
        append("Reply with the JSON object only.")
    }

    val SYSTEM = """
        You write puzzles for an alarm clock app. The alarm keeps ringing until the person solves the puzzle,
        so it should take a just-woken adult roughly 30 to 90 seconds of genuine thought, and it must be
        solvable on a phone without pen and paper.

        Hard requirements:
        - Exactly one correct answer, and that answer is a whole number or a single common English word.
        - No trick wording, no cultural trivia, no ambiguity. Double-check the answer before replying.
        - The question is under 60 words and readable on a small screen.

        Reply with only a JSON object, no code fences:
        {"question": string, "answer": string, "accepted_answers": [string], "answer_type": "number" | "word", "hint": string}
        "accepted_answers" lists harmless alternate spellings or forms (it may be empty). "hint" nudges without giving the answer away.
    """.trimIndent()

    private val CATEGORIES = listOf(
        "multi-step mental arithmetic",
        "number sequence: find the next term",
        "logic riddle with a one-word answer",
        "unscramble a common word (give the scrambled letters)",
        "short word problem about time, money, or ages",
        "find the odd one out in a list of words",
        "letter or word pattern",
        "simple deduction puzzle about who/which, answered with a single name or word",
    )

    private val DIFFICULTY_GUIDE = mapOf(
        Difficulty.EASY to "Gentle: two simple steps at most.",
        Difficulty.MEDIUM to "Moderate: needs two or three steps of focused thinking.",
        Difficulty.HARD to "Challenging: several steps, but still doable in your head in under two minutes.",
    )

    fun parse(text: String): Puzzle {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON object in reply" }
        val o = JSONObject(text.substring(start, end + 1))
        val question = o.getString("question").trim()
        val answer = o.get("answer").toString().trim()
        require(question.isNotEmpty() && answer.isNotEmpty() && answer.length <= 40) { "Malformed puzzle" }
        val accepted = o.optJSONArray("accepted_answers")?.let { arr ->
            (0 until arr.length()).map { arr.get(it).toString() }
        }.orEmpty()
        val type = if (o.optString("answer_type") == "number" || answer.toDoubleOrNull() != null) {
            AnswerType.NUMBER
        } else AnswerType.WORD
        return Puzzle(
            question = question,
            answer = answer,
            acceptedAnswers = accepted,
            answerType = type,
            hint = o.optString("hint", ""),
            source = PuzzleSource.AI,
        )
    }
}
