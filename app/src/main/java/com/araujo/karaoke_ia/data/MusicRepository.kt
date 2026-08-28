package com.araujo.karaoke_ia.data

import org.json.JSONObject
import java.io.File

interface MusicRepository {
    fun findMusicByCode(code: String): MusicFile?
    fun scanAllMusics(forceRefresh: Boolean = false): List<MusicFile>
}

class MusicRepositoryImpl(private val storageManager: StorageManager) : MusicRepository {

    @Volatile
    private var cachedMusics: List<MusicFile>? = null

    /**
     * Busca uma música específica pelo código.
     * Suporta códigos digitados com ou sem zeros à esquerda (ex: 1007 e 01007).
     */
    override fun findMusicByCode(code: String): MusicFile? {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return null

        // 1. Tenta encontrar no cache da memória primeiro
        cachedMusics?.firstOrNull { it.code == cleanCode || it.code == cleanCode.padStart(5, '0') || it.code.trimStart('0') == cleanCode.trimStart('0') }?.let {
            return it
        }

        // 2. Busca direto no disco
        val rootPath = storageManager.rootPath ?: return null
        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) return null

        val candidateCodes = listOf(cleanCode, cleanCode.padStart(5, '0'), cleanCode.trimStart('0')).distinct()

        for (candidate in candidateCodes) {
            val mp4File = File(rootDir, "$candidate.mp4")
            val txtFile = File(rootDir, "$candidate.txt")
            if (mp4File.exists() && txtFile.exists()) {
                val parsed = parseMusicFile(txtFile, mp4File, candidate)
                if (parsed != null) return parsed
            }
        }

        return null
    }

    /**
     * Varre todo o diretório raiz do HD/USB e retorna todas as músicas encontradas.
     * Um arquivo é considerado válido quando existe o par [code].txt + [code].mp4.
     */
    override fun scanAllMusics(forceRefresh: Boolean): List<MusicFile> {
        if (!forceRefresh && cachedMusics != null) {
            return cachedMusics!!
        }

        val rootPath = storageManager.rootPath ?: return emptyList()
        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val result = mutableListOf<MusicFile>()

        try {
            rootDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.equals("txt", ignoreCase = true)) {
                    val code = file.nameWithoutExtension
                    val mp4File = File(rootDir, "$code.mp4")
                    if (mp4File.exists()) {
                        parseMusicFile(file, mp4File, code)?.let { result.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sorted = result.sortedBy { it.title.lowercase() }
        cachedMusics = sorted
        return sorted
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun parseMusicFile(txtFile: File, mp4File: File, code: String): MusicFile? {
        return try {
            // Remove BOM (Byte Order Mark) e espaços extras caso existam
            val jsonString = txtFile.readText(Charsets.UTF_8).replace("\uFEFF", "").trim()
            val jsonObject = JSONObject(jsonString)

            // Suporta JSON aninhado com o código da música ou chave direta
            val musicData: JSONObject = jsonObject.optJSONObject(code)
                ?: jsonObject.optJSONObject(code.padStart(5, '0'))
                ?: jsonObject.optJSONObject(code.trimStart('0'))
                ?: jsonObject.keys().asSequence().firstOrNull()?.let { jsonObject.optJSONObject(it) }
                ?: jsonObject

            val rawArtist = musicData.optString("artista", "Artista Desconhecido").trim()
            val formattedArtist = if (rawArtist.isNotEmpty()) {
                rawArtist.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            } else "Artista Desconhecido"

            val rawTitle = musicData.optString("musica", "Música $code").trim()
            val formattedTitle = rawTitle.replaceFirstChar { it.uppercase() }

            MusicFile(
                code        = code,
                fileName    = musicData.optString("arquivo", mp4File.name),
                artist      = formattedArtist,
                title       = formattedTitle,
                lyricsStart = musicData.optString("inicio", ""),
                videoPath   = mp4File.absolutePath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
