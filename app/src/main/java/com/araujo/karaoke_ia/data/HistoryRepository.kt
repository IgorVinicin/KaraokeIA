package com.araujo.karaoke_ia.data

import android.content.Context

/**
 * Persiste o histórico das últimas músicas tocadas (códigos) em SharedPreferences.
 * Mantém no máximo [MAX_HISTORY] entradas, sem duplicatas.
 */
class HistoryRepository(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val MAX_HISTORY = 20
        private const val KEY_HISTORY  = "history"
    }

    /** Retorna a lista de códigos em ordem do mais recente para o mais antigo. */
    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    /** Adiciona um código ao início do histórico, removendo duplicata e limitando o tamanho. */
    fun addToHistory(code: String) {
        val current = getHistory().toMutableList()
        current.remove(code)         // evita duplicata
        current.add(0, code)         // mais recente no início
        val trimmed = current.take(MAX_HISTORY)
        prefs.edit().putString(KEY_HISTORY, trimmed.joinToString(",")).apply()
    }

    fun importLegacyYouTube(codes: List<String>) {
        if (prefs.getBoolean("youtube_imported", false)) return
        prefs.edit().putString(KEY_HISTORY, (getHistory() + codes).distinct().take(MAX_HISTORY).joinToString(","))
            .putBoolean("youtube_imported", true).apply()
    }
}
