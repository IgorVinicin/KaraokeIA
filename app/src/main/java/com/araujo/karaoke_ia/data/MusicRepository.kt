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

    @Volatile
    private var cachedRootPath: String? = null
    @Volatile private var cachedByCode: Map<String, MusicFile> = emptyMap()

    /**
     * Busca uma música específica pelo código.
     * Suporta códigos digitados com ou sem zeros à esquerda (ex: 1007 e 01007).
     */
    override fun findMusicByCode(code: String): MusicFile? {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return null

        // 1. Tenta encontrar no cache da memória primeiro
        cachedByCode[cleanCode.trimStart('0').ifEmpty { "0" }]?.let {
            return it
        }

        // 2. Busca direto no disco
        val rootPath = storageManager.refreshRootPath()
        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) return null
        val metadataDir = File(rootDir, "dados").takeIf { it.isDirectory } ?: rootDir

        val candidateCodes = listOf(cleanCode, cleanCode.padStart(5, '0'), cleanCode.trimStart('0')).distinct()

        for (candidate in candidateCodes) {
            val mp4File = findVideoFile(rootDir, candidate)
            val txtFile = File(metadataDir, "$candidate.txt")
            if (mp4File != null && txtFile.exists()) {
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
    @Synchronized override fun scanAllMusics(forceRefresh: Boolean): List<MusicFile> {
        val rootPath = storageManager.refreshRootPath()
        if (!forceRefresh && cachedMusics != null && cachedRootPath == rootPath) {
            return cachedMusics!!
        }

        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val result = mutableListOf<MusicFile>()

        try {
            val metadataDir = File(rootDir, "dados").takeIf { it.isDirectory } ?: rootDir
            // Index existing videos once instead of probing six absent paths for each metadata file.
            val videoFiles = rootDir.listFiles().orEmpty()
                .filter { it.extension.lowercase() in setOf("mp4", "mkv", "avi") && it.isFile }
                .associateBy { it.name }
            metadataDir.listFiles()?.forEach { file ->
                if (file.extension.equals("txt", ignoreCase = true)) {
                    val code = file.nameWithoutExtension
                    val mp4File = listOf("mp4", "MP4", "mkv", "MKV", "avi", "AVI")
                        .firstNotNullOfOrNull { videoFiles["$code.$it"] }
                    if (mp4File != null && file.isFile) {
                        parseMusicFile(file, mp4File, code)?.let { result.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sorted = result.sortedBy { it.title.lowercase() }
        cachedByCode = sorted.associateBy { it.code.trimStart('0').ifEmpty { "0" } }
        cachedMusics = sorted
        cachedRootPath = rootPath
        return sorted
    }

    private fun findVideoFile(rootDir: File, code: String): File? {
        return listOf("mp4", "MP4", "mkv", "MKV", "avi", "AVI")
            .map { File(rootDir, "$code.$it") }
            .firstOrNull { it.isFile }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun parseMusicFile(txtFile: File, mp4File: File, code: String): MusicFile? {
        return try {
            if (txtFile.length() > 1024 * 1024) return null
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
