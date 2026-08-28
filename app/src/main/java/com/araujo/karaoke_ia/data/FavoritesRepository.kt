package com.araujo.karaoke_ia.data

import android.content.Context

/**
 * Persiste os códigos de músicas favoritadas em SharedPreferences.
 */
class FavoritesRepository(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    /** Alterna o estado de favorito. Retorna true se agora está favoritado. */
    fun toggleFavorite(code: String): Boolean {
        val current = getFavorites().toMutableSet()
        return if (current.contains(code)) {
            current.remove(code)
            prefs.edit().putStringSet("favorites", current).apply()
            false
        } else {
            current.add(code)
            prefs.edit().putStringSet("favorites", current).apply()
            true
        }
    }

    fun isFavorite(code: String): Boolean = getFavorites().contains(code)
}
