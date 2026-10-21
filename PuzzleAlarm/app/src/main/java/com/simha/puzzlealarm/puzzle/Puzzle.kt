package com.simha.puzzlealarm.puzzle

import java.text.Normalizer

enum class AnswerType { NUMBER, WORD }

enum class PuzzleSource(val label: String) { AI("AI puzzle"), OFFLINE("Offline puzzle") }

data class Puzzle(
    val question: String,
    val answer: String,
    val acceptedAnswers: List<String> = emptyList(),
    val answerType: AnswerType,
    val hint: String = "",
    val source: PuzzleSource,
) {
    fun isCorrect(input: String): Boolean {
        val given = normalize(input)
        if (given.isEmpty()) return false
        val candidates = (listOf(answer) + acceptedAnswers).map(::normalize)
        val givenNumber = given.toDoubleOrNull()
        return candidates.any { c ->
            c == given || (givenNumber != null && c.toDoubleOrNull()?.let { kotlin.math.abs(it - givenNumber) < 1e-9 } == true)
        }
    }

    companion object {
        fun normalize(s: String): String {
            val ascii = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            return ascii.lowercase()
                .replace(",", "")
                .replace(Regex("[^a-z0-9.\\- ]"), "")
                .trim()
                .removePrefix("the ").removePrefix("a ").removePrefix("an ")
                .trim()
                .trimEnd('.')
        }
    }
}
