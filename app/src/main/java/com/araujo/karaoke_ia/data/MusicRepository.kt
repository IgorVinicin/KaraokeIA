package com.araujo.karaoke_ia.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

internal fun normalizeMusicSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

interface MusicRepository {
    fun findMusicByCode(code: String): MusicFile?
    fun scanAllMusics(forceRefresh: Boolean = false): List<MusicFile>
    /** Reads only internal storage. Call disk methods on Dispatchers.IO. */
    fun loadCachedCatalog(): List<MusicFile> = scanAllMusics()
    fun searchLocal(query: String, limit: Int = 200, offset: Int = 0): List<MusicFile> =
        loadCachedCatalog().filter { normalizeMusicSearch("${it.code} ${it.title} ${it.artist}").contains(normalizeMusicSearch(query.trim())) }
            .drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 1000))
    val catalogUpdates: StateFlow<List<MusicFile>?>? get() = null
}

data class IndexStats(val filesVisited: Int = 0, val metadataParsed: Int = 0, val reused: Int = 0, val durationMs: Long = 0)

/** Persistent incremental index. A USB scan never runs from a search or code lookup. */
class MusicRepositoryImpl internal constructor(
    private val rootPathProvider: () -> String,
    private val catalogCacheFile: File,
    private val rootPathsProvider: (() -> List<String>)? = null
) : MusicRepository {
    constructor(storageManager: StorageManager) : this(storageManager::refreshRootPath, storageManager.catalogCacheFile, storageManager::catalogRoots)

    private val updates = MutableStateFlow<List<MusicFile>?>(null)
    override val catalogUpdates: StateFlow<List<MusicFile>?> = updates
    @Volatile private var byCode: Map<String, MusicFile> = emptyMap()
    @Volatile var indexStats = IndexStats()
        private set
    private val scanLock = Any()
    private val databaseLock = Any()
    private fun codeKey(code: String): String {
        val clean = code.trim()
        return if (clean.all { it.isDigit() }) clean.trimStart('0').ifEmpty { "0" } else clean
    }

    private fun <T> database(block: (SQLiteDatabase) -> T): T = synchronized(databaseLock) {
        catalogCacheFile.parentFile?.mkdirs()
        // Preserve obsolete JSON/corrupt files for inspection instead of deleting a catalogue.
        if (catalogCacheFile.isFile && catalogCacheFile.length() > 0) {
            val header = catalogCacheFile.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
            if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                check(catalogCacheFile.renameTo(File(catalogCacheFile.parentFile, "${catalogCacheFile.name}.legacy-${System.nanoTime()}")))
            }
        }
        SQLiteDatabase.openOrCreateDatabase(catalogCacheFile, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS songs (path TEXT PRIMARY KEY, root TEXT NOT NULL, code TEXT NOT NULL, code_key TEXT NOT NULL, file_name TEXT NOT NULL, artist TEXT NOT NULL, title TEXT NOT NULL, lyrics TEXT NOT NULL, duration INTEGER, signature TEXT NOT NULL, video_signature TEXT NOT NULL, search_text TEXT NOT NULL, available INTEGER NOT NULL, seen INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS songs_root ON songs(root)")
            db.execSQL("CREATE INDEX IF NOT EXISTS songs_code ON songs(code_key, available)")
            db.execSQL("CREATE INDEX IF NOT EXISTS songs_title ON songs(title, path)")
            block(db)
        }
    }

    private fun Cursor.song() = MusicFile(getString(2), getString(4), getString(5), getString(6), getString(7), getString(0),
        available = getInt(12) == 1, durationMs = if (isNull(8)) null else getLong(8))

    private fun publish(songs: List<MusicFile>): List<MusicFile> {
        // Duplicate numeric codes on different volumes resolve to the connected copy.
        byCode = songs.sortedBy { !it.available }.distinctBy { codeKey(it.code) }.associateBy { codeKey(it.code) }
        updates.value = songs
        return songs
    }

    override fun loadCachedCatalog(): List<MusicFile> = synchronized(databaseLock) { updates.value ?: publish(database { db ->
        db.rawQuery("SELECT * FROM songs ORDER BY title COLLATE NOCASE, path", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.song()) }
                .sortedBy { !it.available }.distinctBy { codeKey(it.code) }.sortedBy { normalizeMusicSearch(it.title) }
        }
    }) }

    override fun searchLocal(query: String, limit: Int, offset: Int): List<MusicFile> = database { db ->
        val term = normalizeMusicSearch(query.trim()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        db.rawQuery("SELECT * FROM songs s WHERE path = (SELECT path FROM songs other WHERE other.code_key = s.code_key ORDER BY available DESC, path LIMIT 1) AND search_text LIKE ? ESCAPE '\\' ORDER BY title COLLATE NOCASE, path LIMIT ? OFFSET ?",
            arrayOf("%$term%", limit.coerceIn(1, 1000).toString(), offset.coerceAtLeast(0).toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.song()) }
        }
    }

    override fun findMusicByCode(code: String): MusicFile? {
        if (code.isBlank()) return null
        val music = byCode[codeKey(code)] ?: run {
            loadCachedCatalog()
            byCode[codeKey(code)]
        }
        return music?.takeIf { it.available && it.videoPath?.let { path -> File(path).isFile } == true }
    }

    private data class Previous(val signature: String, val videoSignature: String, val song: MusicFile)
    private data class Candidate(val video: File, val metadata: File?)

    override fun scanAllMusics(forceRefresh: Boolean): List<MusicFile> = synchronized(scanLock) {
        val roots = rootPathsProvider?.invoke() ?: listOf(rootPathProvider())
        var totals = IndexStats()
        roots.distinct().forEach { path ->
            scanRoot(File(path).absoluteFile, forceRefresh)
            totals = IndexStats(totals.filesVisited + indexStats.filesVisited, totals.metadataParsed + indexStats.metadataParsed,
                totals.reused + indexStats.reused, totals.durationMs + indexStats.durationMs)
        }
        database { db ->
            if (roots.isEmpty()) db.execSQL("UPDATE songs SET available = 0 WHERE available = 1")
            else db.execSQL("UPDATE songs SET available = 0 WHERE root NOT IN (${roots.joinToString(",") { "?" }}) AND available = 1", roots.toTypedArray())
        }
        updates.value = null
        indexStats = totals
        loadCachedCatalog()
    }

    private fun scanRoot(root: File, forceRefresh: Boolean): List<MusicFile> = synchronized(scanLock) {
        val started = SystemClock.elapsedRealtime()
        loadCachedCatalog()
        val previous = database { db ->
            db.rawQuery("SELECT * FROM songs WHERE root = ?", arrayOf(root.path)).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(0), Previous(cursor.getString(9), cursor.getString(10), cursor.song())) }
            }
        }
        if (!root.isDirectory) {
            database { it.execSQL("UPDATE songs SET available = 0 WHERE root = ? AND available = 1", arrayOf(root.path)) }
            updates.value = null
            val result = loadCachedCatalog()
            indexStats = IndexStats(durationMs = SystemClock.elapsedRealtime() - started)
            return@synchronized result
        }
        var complete = true
        var visited = 0
        var parsed = 0
        var reused = 0
        val generation = System.nanoTime()
        val candidates = mutableListOf<Candidate>()
        val directories = java.util.ArrayDeque<File>().apply { add(root) }
        val seenDirectories = hashSetOf<String>()
        while (directories.isNotEmpty()) {
            val directory = directories.removeFirst()
            if (!seenDirectories.add(directory.canonicalPath)) continue
            val files = directory.listFiles()
            if (files == null) { complete = false; continue }
            visited += files.size
            val metadata = files.filter { it.isFile && it.extension.equals("txt", true) }.associateBy { it.nameWithoutExtension }
            val dataDirectory = File(directory, "dados")
            val dataFiles = if (dataDirectory.isDirectory) dataDirectory.listFiles() else emptyArray()
            if (dataFiles == null) complete = false
            val sidecars = dataFiles.orEmpty().filter { it.extension.equals("txt", true) }.associateBy { it.nameWithoutExtension }
            files.forEach { file ->
                if (file.isDirectory && !file.name.equals("dados", true) && !file.name.startsWith('.')) directories.add(file)
                else if (file.isFile && file.extension.lowercase(Locale.ROOT) in setOf("mp4", "mkv", "avi")) {
                    candidates.add(Candidate(file, sidecars[file.nameWithoutExtension] ?: metadata[file.nameWithoutExtension]))
                }
            }
        }
        // Batch transactions keep progress visible and do not hold a database lock for the entire scan.
        candidates.chunked(1000).forEach { batch ->
            val rows = batch.mapNotNull { candidate ->
                val video = candidate.video
                val metadata = candidate.metadata
                val videoSignature = "${video.length()}:${video.lastModified()}"
                val signature = "$videoSignature:${metadata?.absolutePath.orEmpty()}:${metadata?.length() ?: 0}:${metadata?.lastModified() ?: 0}"
                val old = previous[video.absolutePath]
                val song = if (!forceRefresh && old?.signature == signature) { reused++; old.song.copy(available = true) }
                else { parsed++; parseMusicFile(metadata, video, if (old?.videoSignature == videoSignature) old.song else null) ?: old?.song?.copy(available = true) }
                song?.let { Triple(it, signature, videoSignature) }
            }
            database { db ->
                db.beginTransaction()
                try {
                    rows.forEach { (song, signature, videoSignature) ->
                        if (previous[song.videoPath]?.signature == signature && !forceRefresh) {
                            if (previous[song.videoPath]?.song?.available == false)
                                db.execSQL("UPDATE songs SET available = 1 WHERE path = ?", arrayOf(song.videoPath))
                            return@forEach
                        }
                        val values = ContentValues().apply {
                            put("path", song.videoPath); put("root", root.path); put("code", song.code); put("code_key", codeKey(song.code))
                            put("file_name", song.fileName); put("artist", song.artist); put("title", song.title); put("lyrics", song.lyricsStart)
                            if (song.durationMs == null) putNull("duration") else put("duration", song.durationMs)
                            put("signature", signature); put("video_signature", videoSignature)
                            put("search_text", normalizeMusicSearch("${song.code} ${song.title} ${song.artist}"))
                            put("available", 1); put("seen", generation)
                        }
                        check(db.insertWithOnConflict("songs", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            if (rows.any { (song, signature, _) -> previous[song.videoPath]?.signature != signature || previous[song.videoPath]?.song?.available == false }) {
                updates.value = null
                loadCachedCatalog()
            }
        }
        // Never interpret a failed or interrupted USB enumeration as confirmed deletion.
        database { db ->
            if (!root.isDirectory) db.execSQL("UPDATE songs SET available = 0 WHERE root = ?", arrayOf(root.path))
            if (root.isDirectory) {
                val present = candidates.mapTo(hashSetOf()) { it.video.absolutePath }
                db.beginTransaction()
                try {
                    previous.keys.filterNot { it in present }.forEach { path ->
                        if (complete) db.delete("songs", "path = ?", arrayOf(path))
                        else db.execSQL("UPDATE songs SET available = 0 WHERE path = ?", arrayOf(path))
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
        }
        updates.value = null
        val result = loadCachedCatalog()
        indexStats = IndexStats(visited, parsed, reused, SystemClock.elapsedRealtime() - started)
        Log.i("LocalIndex", "Indexação: $indexStats; catálogo=${result.size}")
        result
    }

    private fun parseMusicFile(txt: File?, video: File, previousVideo: MusicFile?): MusicFile? {
        return try {
            val code = video.nameWithoutExtension
            if (txt != null && txt.length() > 1024 * 1024) return null
            val json = txt?.let { JSONObject(it.readText(Charsets.UTF_8).removePrefix("\uFEFF").trim()) }
            val data = json?.optJSONObject(code) ?: json?.optJSONObject(code.padStart(5, '0'))
                ?: json?.optJSONObject(code.trimStart('0'))
                ?: json?.keys()?.asSequence()?.firstOrNull()?.let { json.optJSONObject(it) } ?: json
            val media = if (previousVideo != null) MediaDetails(previousVideo.durationMs, previousVideo.title, previousVideo.artist)
                else readMediaDetails(video)
            val duration = data?.optLong("duracao_ms", 0)?.takeIf { it > 0 } ?: media.duration
            MusicFile(code, data?.optString("arquivo", video.name) ?: video.name,
                data?.optString("artista")?.trim()?.takeIf { it.isNotEmpty() } ?: media.artist ?: "Artista Desconhecido",
                data?.optString("musica")?.trim()?.takeIf { it.isNotEmpty() } ?: media.title ?: code,
                data?.optString("inicio", "") ?: "", video.absolutePath, durationMs = duration)
        } catch (error: Exception) {
            Log.w("LocalIndex", "Não foi possível ler ${video.name}", error)
            null
        }
    }

    private data class MediaDetails(val duration: Long? = null, val title: String? = null, val artist: String? = null)
    private fun readMediaDetails(video: File): MediaDetails {
        if (video.length() < 32) return MediaDetails()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            MediaDetails(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() })
        } catch (_: Exception) { MediaDetails() } finally { retriever.release() }
    }
}
