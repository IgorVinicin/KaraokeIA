package com.araujo.karaoke_ia.score

import kotlin.random.Random

object ScoreGenerator {

    data class ScoreResult(
        val score: Int,
        val feedback: String
    )

    fun generateScore(): ScoreResult {
        val chance = Random.nextInt(100)
        
        return when {
            chance < 45 -> { // 45%
                val score = Random.nextInt(60, 80)
                ScoreResult(score, "Pode melhorar! Continue praticando.")
            }
            chance < 75 -> { // 30%
                val score = Random.nextInt(80, 91)
                ScoreResult(score, "Bom trabalho! Você está no caminho certo.")
            }
            chance < 95 -> { // 20%
                val score = Random.nextInt(91, 98)
                ScoreResult(score, "Muito bom! Quase um profissional.")
            }
            else -> { // 15% (85 + 15)
                val score = Random.nextInt(98, 101)
                ScoreResult(score, "Incrível! Você é uma estrela!")
            }
        }
    }
}
