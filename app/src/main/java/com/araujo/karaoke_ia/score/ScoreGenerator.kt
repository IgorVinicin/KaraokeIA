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
            chance < 10 -> { // 10%
                val score = Random.nextInt(60, 80)
                ScoreResult(score, "Pode melhorar! Continue praticando.")
            }
            chance < 45 -> { // 35% (10 + 35)
                val score = Random.nextInt(80, 90)
                ScoreResult(score, "Bom trabalho! Você está no caminho certo.")
            }
            chance < 85 -> { // 40% (45 + 40)
                val score = Random.nextInt(90, 96)
                ScoreResult(score, "Muito bom! Quase um profissional.")
            }
            else -> { // 15% (85 + 15)
                val score = Random.nextInt(96, 101)
                ScoreResult(score, "Incrível! Você é uma estrela!")
            }
        }
    }
}
