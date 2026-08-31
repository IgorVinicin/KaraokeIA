package com.araujo.karaoke_ia.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UserScoreEntry(
    val id: String = System.currentTimeMillis().toString(),
    val musicCode: String,
    val title: String,
    val artist: String,
    val score: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class ScoreRepository(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("user_scores_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SCORES = "scores_json"
        private const val MAX_SCORES = 50
    }

    /** Salva uma nova pontuação do usuário */
    fun saveScore(musicCode: String, title: String, artist: String, score: Int) {
        val scores = getAllScores().toMutableList()
        val newEntry = UserScoreEntry(
            musicCode = musicCode,
            title = title,
            artist = artist,
            score = score
        )
        scores.add(0, newEntry) // Adiciona a mais recente no início
        val trimmed = scores.take(MAX_SCORES)

        val jsonArray = JSONArray()
        for (entry in trimmed) {
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("musicCode", entry.musicCode)
                put("title", entry.title)
                put("artist", entry.artist)
                put("score", entry.score)
                put("timestamp", entry.timestamp)
            }
            jsonArray.put(obj)
        }

        prefs.edit().putString(KEY_SCORES, jsonArray.toString()).apply()
    }

    /** Retorna todas as pontuações do usuário em ordem cronológica (mais recentes primeiro) */
    fun getAllScores(): List<UserScoreEntry> {
        val raw = prefs.getString(KEY_SCORES, null) ?: return emptyList()
        val list = mutableListOf<UserScoreEntry>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserScoreEntry(
                        id = obj.optString("id", i.toString()),
                        musicCode = obj.optString("musicCode", ""),
                        title = obj.optString("title", "Sem título"),
                        artist = obj.optString("artist", "Desconhecido"),
                        score = obj.optInt("score", 0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    /** Retorna as melhores pontuações do usuário (maior nota para a menor) */
    fun getBestScores(): List<UserScoreEntry> {
        return getAllScores().sortedByDescending { it.score }
    }
}
