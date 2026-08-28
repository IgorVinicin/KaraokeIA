package com.araujo.karaoke_ia.data

import android.content.Context

/**
 * Gerencia e persiste a fila de músicas reservadas para cantar.
 */
class QueueRepository(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("queue_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_QUEUE = "music_queue"
    }

    /** Retorna a lista de códigos de músicas na fila na ordem de execução. */
    fun getQueue(): List<String> {
        val raw = prefs.getString(KEY_QUEUE, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    /** Adiciona uma música ao final da fila de reservas. Retorna true se adicionou. */
    fun addToQueue(code: String): Boolean {
        val current = getQueue().toMutableList()
        current.add(code)
        saveQueue(current)
        return true
    }

    /** Remove uma música específica da fila. */
    fun removeFromQueue(code: String) {
        val current = getQueue().toMutableList()
        current.remove(code)
        saveQueue(current)
    }

    /** Retorna e remove a próxima música da fila. */
    fun popNext(): String? {
        val current = getQueue().toMutableList()
        if (current.isEmpty()) return null
        val next = current.removeAt(0)
        saveQueue(current)
        return next
    }

    /** Apenas espreita qual é a próxima música da fila sem removê-la. */
    fun peekNext(): String? = getQueue().firstOrNull()

    /** Limpa toda a fila. */
    fun clearQueue() {
        saveQueue(emptyList())
    }

    /** Verifica se uma música já está na fila. */
    fun isInQueue(code: String): Boolean = getQueue().contains(code)

    /** Quantidade de músicas na fila. */
    fun getCount(): Int = getQueue().size

    private fun saveQueue(list: List<String>) {
        prefs.edit().putString(KEY_QUEUE, list.joinToString(",")).apply()
    }
}
