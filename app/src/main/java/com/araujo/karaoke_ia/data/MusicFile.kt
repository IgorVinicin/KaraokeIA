package com.araujo.karaoke_ia.data

/**
 * Representa os metadados de uma música extraídos do arquivo .txt (JSON).
 */
data class MusicFile(
    val code: String,
    val fileName: String,
    val artist: String,
    val title: String,
    val lyricsStart: String,
    val videoPath: String? = null
)
