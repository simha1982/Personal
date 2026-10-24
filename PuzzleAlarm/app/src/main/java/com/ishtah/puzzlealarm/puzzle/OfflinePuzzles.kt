package com.ishtah.puzzlealarm.puzzle

import com.ishtah.puzzlealarm.data.Difficulty
import kotlin.random.Random

/**
 * Puzzles generated on the device, used whenever Claude can't be reached (no key, no network,
 * timeout, or an unusable reply) so the alarm can always be turned off.
 */
object OfflinePuzzles {

    private val words = listOf(
        "MORNING", "COFFEE", "SUNRISE", "BREAKFAST", "WINDOW", "GARDEN", "PLANET", "BICYCLE",
        "KITCHEN", "PENCIL", "ORANGE", "MONKEY", "JOURNEY", "BLANKET", "THUNDER", "LIBRARY",
        "DOLPHIN", "CASTLE", "SILVER", "PICTURE", "RAINBOW", "FREEDOM", "BALANCE", "HARVEST",
    )

    fun next(difficulty: Difficulty, random: Random = Random.Default): Puzzle =
        when (random.nextInt(4)) {
            0 -> arithmetic(difficulty, random)
            1 -> sequence(difficulty, random)
            2 -> scramble(difficulty, random)
            else -> wordProblem(difficulty, random)
        }

    private fun arithmetic(d: Difficulty, r: Random): Puzzle {
        val (a, b, c, e) = when (d) {
            Difficulty.EASY -> listOf(r.nextInt(3, 10), r.nextInt(3, 10), r.nextInt(10, 50), r.nextInt(1, 10))
            Difficulty.MEDIUM -> listOf(r.nextInt(6, 20), r.nextInt(6, 15), r.nextInt(20, 99), r.nextInt(10, 40))
            Difficulty.HARD -> listOf(r.nextInt(12, 40), r.nextInt(11, 30), r.nextInt(100, 500), r.nextInt(20, 99))
        }
        val answer = a * b + c - e
        return Puzzle(
            question = "What is $a × $b + $c − $e ?",
            answer = answer.toString(),
            answerType = AnswerType.NUMBER,
            hint = "Multiply first, then add and subtract left to right.",
            source = PuzzleSource.OFFLINE,
        )
    }

    private fun sequence(d: Difficulty, r: Random): Puzzle {
        val terms: List<Long>
        val hint: String
        when (if (d == Difficulty.EASY) 0 else r.nextInt(3)) {
            0 -> {
                val start = r.nextLong(1, 20); val step = r.nextLong(2, if (d == Difficulty.HARD) 25 else 12)
                terms = List(6) { start + step * it }; hint = "The gap between terms is constant."
            }
            1 -> {
                val start = r.nextLong(1, 5); val ratio = r.nextLong(2, 4)
                terms = generateSequence(start) { it * ratio }.take(6).toList(); hint = "Each term is multiplied by the same number."
            }
            else -> {
                val a = r.nextLong(1, 6); val b = r.nextLong(2, 9)
                terms = generateSequence(a to b) { (x, y) -> y to x + y }.map { it.first }.take(7).toList()
                hint = "Each term depends on the two before it."
            }
        }
        val shown = terms.dropLast(1).joinToString(", ")
        return Puzzle(
            question = "What number comes next?\n$shown, ?",
            answer = terms.last().toString(),
            answerType = AnswerType.NUMBER,
            hint = hint,
            source = PuzzleSource.OFFLINE,
        )
    }

    private fun scramble(d: Difficulty, r: Random): Puzzle {
        val pool = when (d) {
            Difficulty.EASY -> words.filter { it.length <= 6 }
            Difficulty.MEDIUM -> words.filter { it.length in 6..7 }
            Difficulty.HARD -> words.filter { it.length >= 7 }
        }
        val word = pool.random(r)
        var mixed: String
        do { mixed = word.toList().shuffled(r).joinToString("") } while (mixed == word)
        return Puzzle(
            question = "Unscramble this word:\n${mixed.toList().joinToString(" ")}",
            answer = word.lowercase(),
            answerType = AnswerType.WORD,
            hint = "It starts with \"${word.first()}\".",
            source = PuzzleSource.OFFLINE,
        )
    }

    private fun wordProblem(d: Difficulty, r: Random): Puzzle {
        val scale = when (d) { Difficulty.EASY -> 1; Difficulty.MEDIUM -> 2; Difficulty.HARD -> 4 }
        val apples = r.nextInt(5, 12) * scale
        val boxes = r.nextInt(3, 7)
        val eaten = r.nextInt(2, 9) * scale
        val answer = apples * boxes - eaten
        return Puzzle(
            question = "You have $boxes boxes with $apples apples in each. You give away $eaten apples. How many apples are left?",
            answer = answer.toString(),
            answerType = AnswerType.NUMBER,
            hint = "Total apples first, then subtract.",
            source = PuzzleSource.OFFLINE,
        )
    }
}
