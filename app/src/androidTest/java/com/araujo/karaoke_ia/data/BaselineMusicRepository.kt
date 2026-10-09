package com.araujo.karaoke_ia.data

import android.util.Log
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BaselineMusicRepository internal constructor(
    private val rootPathProvider: () -> String,
    private val catalogCacheFile: File
) : MusicRepository {
    constructor(storageManager: StorageManager) : this(
        storageManager::refreshRootPath, storageManager.catalogCacheFile)

    @Volatile
    private var cachedMusics: List<MusicFile>? = null

    @Volatile
    private var cachedRootPath: String? = null
    @Volatile private var cachedByCode: Map<String, MusicFile> = emptyMap()
    private var cachedSignature: String? = null

    /**
     * Busca uma mÃºsica especÃ­fica pelo cÃ³digo.
     * Suporta cÃ³digos digitados com ou sem zeros Ã  esquerda (ex: 1007 e 01007).
     */
    override fun findMusicByCode(code: String): MusicFile? {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return null

        val rootPath = rootPathProvider()
        // A troca do pendrive invalida o caminho dos vÃ­deos em memÃ³ria.
        if (cachedRootPath == rootPath) {
            cachedByCode[cleanCode.trimStart('0').ifEmpty { "0" }]?.takeIf {
                it.videoPath?.let { path -> File(path).isFile } == true
            }?.let { return it }
        }

        // 2. Busca direto no disco
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
     * Varre todo o diretÃ³rio raiz do HD/USB e retorna todas as mÃºsicas encontradas.
     * Um arquivo Ã© considerado vÃ¡lido quando existe o par [code].txt + [code].mp4.
     */
    @Synchronized override fun scanAllMusics(forceRefresh: Boolean): List<MusicFile> {
        val rootPath = rootPathProvider()
        val rootDir = File(rootPath)
        if (!rootDir.isDirectory) {
            cachedMusics = null
            cachedByCode = emptyMap()
            cachedRootPath = null
            return emptyList()
        }

        val result = mutableListOf<MusicFile>()

        try {
            val metadataDir = File(rootDir, "dados").takeIf { it.isDirectory } ?: rootDir
            // Index existing videos once instead of probing six absent paths for each metadata file.
            val extensions = setOf("mp4", "mkv", "avi")
            val videos = rootDir.listFiles() ?: return emptyList()
            val metadata = metadataDir.listFiles() ?: return emptyList()
            val videoFiles = videos.filter { it.extension.lowercase() in extensions && it.isFile }
                .sortedBy { it.name }.associateBy { it.nameWithoutExtension }
            val metadataFiles = metadata.filter { it.extension.equals("txt", true) && it.isFile }
                .sortedBy { it.name }
            // Stat entries to detect edits/removals; unchanged catalogues need no TXT parsing.
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            (metadataFiles + videoFiles.values).forEach {
                digest.update("${it.name}\u0000${it.length()}\u0000${it.lastModified()}\n".toByteArray(Charsets.UTF_8))
            }
            val signature = digest.digest().joinToString("") { "%02x".format(it) }
            if (!forceRefresh && cachedRootPath == rootPath && cachedSignature == signature) {
                cachedMusics?.let { return it }
            }
            if (!forceRefresh) readCatalog(rootPath, signature)?.let {
                rememberCatalog(rootPath, signature, it)
                return it
            }
            metadataFiles.forEach { file ->
                val code = file.nameWithoutExtension
                videoFiles[code]?.let { video ->
                    parseMusicFile(file, video, code)?.let { result.add(it) }
                }
            }
            val sorted = result.sortedBy { it.title.lowercase() }
            rememberCatalog(rootPath, signature, sorted)
            writeCatalog(rootPath, signature, sorted)
            return sorted
        } catch (e: Exception) {
            Log.w("MusicRepository", "Falha ao ler o catÃ¡logo local", e)
        }

        return emptyList()
    }

    private fun rememberCatalog(root: String, signature: String, songs: List<MusicFile>) {
        cachedByCode = songs.associateBy { it.code.trimStart('0').ifEmpty { "0" } }
        cachedMusics = songs
        cachedSignature = signature
        cachedRootPath = root
    }

    private fun readCatalog(root: String, signature: String): List<MusicFile>? = runCatching {
        val json = AtomicFile(catalogCacheFile).openRead().bufferedReader().use { JSONObject(it.readText()) }
        if (json.getInt("version") != 1 || json.getString("root") != root || json.getString("signature") != signature) return null
        val songs = json.getJSONArray("songs")
        (0 until songs.length()).map { index ->
            val song = songs.getJSONObject(index)
            MusicFile(song.getString("code"), song.getString("fileName"), song.getString("artist"),
                song.getString("title"), song.getString("lyricsStart"), song.getString("videoPath"))
        }
    }.getOrNull()

    private fun writeCatalog(root: String, signature: String, songs: List<MusicFile>) {
        val cache = AtomicFile(catalogCacheFile)
        var stream: java.io.FileOutputStream? = null
        try {
            val entries = JSONArray()
            songs.forEach { song ->
                entries.put(JSONObject().put("code", song.code).put("fileName", song.fileName)
                    .put("artist", song.artist).put("title", song.title).put("lyricsStart", song.lyricsStart)
                    .put("videoPath", song.videoPath))
            }
            val json = JSONObject().put("version", 1).put("root", root).put("signature", signature).put("songs", entries)
            stream = cache.startWrite()
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            cache.finishWrite(stream)
        } catch (e: Exception) {
            cache.failWrite(stream)
            Log.w("MusicRepository", "CatÃ¡logo carregado sem cache persistente", e)
        }
    }

    private fun findVideoFile(rootDir: File, code: String): File? {
        return listOf("mp4", "MP4", "mkv", "MKV", "avi", "AVI")
            .map { File(rootDir, "$code.$it") }
            .firstOrNull { it.isFile }
    }

    // â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun parseMusicFile(txtFile: File, mp4File: File, code: String): MusicFile? {
        return try {
            if (txtFile.length() > 1024 * 1024) return null
            // Remove BOM (Byte Order Mark) e espaÃ§os extras caso existam
            val jsonString = txtFile.readText(Charsets.UTF_8).replace("\uFEFF", "").trim()
            val jsonObject = JSONObject(jsonString)

            // Suporta JSON aninhado com o cÃ³digo da mÃºsica ou chave direta
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

            val rawTitle = musicData.optString("musica", "MÃºsica $code").trim()
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
            Log.w("MusicRepository", "Metadados invÃ¡lidos para a mÃºsica $code", e)
            null
        }
    }
}

