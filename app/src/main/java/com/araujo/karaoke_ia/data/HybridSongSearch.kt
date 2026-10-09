package com.araujo.karaoke_ia.data

import java.text.Normalizer
import java.util.Locale

/** Name matching ignores accents. Only missing local songs consume network requests. */
class HybridSongSearch(private val onlineSearch: suspend (String) -> List<MusicFile>) {
    fun localResults(query: String, songs: List<MusicFile>): List<MusicFile> {
        val term = normalize(query.trim())
        return if (term.isEmpty()) songs else songs.filter {
            it.code.contains(term) || normalize(it.title).contains(term) || normalize(it.artist).contains(term)
        }
    }
    suspend fun searchMissing(query: String, localResults: List<MusicFile>): List<MusicFile> {
        if (localResults.isNotEmpty() || query.trim().length < 2) return localResults
        return onlineSearch(query.trim())
    }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}
