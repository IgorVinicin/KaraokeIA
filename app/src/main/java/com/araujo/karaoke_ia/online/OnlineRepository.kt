package com.araujo.karaoke_ia.online

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.AtomicFile
import com.araujo.karaoke_ia.BuildConfig
import com.araujo.karaoke_ia.auth.AccessRepository
import androidx.core.text.HtmlCompat
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class OnlineSong(val videoId: String, val title: String, val channel: String)
data class OnlineQueueEntry(val id: String, val song: OnlineSong)

internal fun normalizeYouTubeKey(value: String): String {
    val normalized = value.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    require(Regex("AIza[A-Za-z0-9_-]{35}").matches(normalized)) {
        "Informe uma chave válida da API do YouTube."
    }
    return normalized
}

class OnlineRepository(context: Context, private val access: AccessRepository? = null) {
    // Device configuration stays private, outside backups and outside the distributed APK.
    private val keyFile = AtomicFile(File(context.applicationContext.noBackupFilesDir, "youtube_config"))
    @Volatile private var apiKey = runCatching {
        val settings = Properties()
        keyFile.openRead().use { settings.load(it) }
        normalizeYouTubeKey(settings.getProperty("YOUTUBE_KEY") ?: settings.getProperty("YOUTUBE_API_KEY", ""))
    }.getOrDefault("")
    private val prefs = context.applicationContext.getSharedPreferences("online_queue", Context.MODE_PRIVATE)
    private val mutableQueue = MutableStateFlow(readQueue())
    val queue = mutableQueue.asStateFlow()
    val usesSearchService = access != null || BuildConfig.SEARCH_SERVICE_URL.isNotBlank()
    private val mutableSearchConfigured = MutableStateFlow(usesSearchService || apiKey.isNotBlank())
    val searchConfigured = mutableSearchConfigured.asStateFlow()
    private val libraryPrefs = context.applicationContext.getSharedPreferences("online_library", Context.MODE_PRIVATE)
    private val mutableFavorites = MutableStateFlow(readSongs("favorites", MAX_FAVORITES))
    private val mutableHistory = MutableStateFlow(readSongs("history", MAX_HISTORY))
    private var knownSongs = readSongs("known_songs", Int.MAX_VALUE)
    val savedSongs: List<OnlineSong>
        @Synchronized get() = (knownSongs + mutableFavorites.value + mutableHistory.value +
            mutableQueue.value.map { it.song }).distinctBy { it.videoId }

    @Synchronized fun rememberSong(song: OnlineSong) {
        require(validId(song.videoId))
        if (knownSongs.firstOrNull { it.videoId == song.videoId } == song) return
        knownSongs = listOf(song) + knownSongs.filterNot { it.videoId == song.videoId }
        saveSongs("known_songs", knownSongs)
    }
    val favorites = mutableFavorites.asStateFlow()
    val history = mutableHistory.asStateFlow()
    private data class CachedSongs(val savedAt: Long, val songs: List<OnlineSong>)
    private val searchCache = linkedMapOf<String, CachedSongs>()

    /** Call on an IO dispatcher; persists only in this device's private no-backup storage. */
    @Synchronized fun configureSearchKey(value: String) {
        val normalized = normalizeYouTubeKey(value)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = keyFile.startWrite()
            stream.write("YOUTUBE_KEY=$normalized\n".toByteArray(StandardCharsets.UTF_8))
            keyFile.finishWrite(stream)
        } catch (_: Exception) {
            runCatching { keyFile.failWrite(stream) }
            throw IllegalStateException("Não foi possível salvar a configuração do YouTube neste aparelho.")
        }
        apiKey = normalized
        searchCache.clear()
        mutableSearchConfigured.value = true
    }

    /** Call on an IO dispatcher. Links remain available after removing the search key. */
    @Synchronized fun clearSearchKey() {
        try {
            keyFile.delete()
            check(!keyFile.baseFile.exists() && !File(keyFile.baseFile.path + ".bak").exists() &&
                !File(keyFile.baseFile.path + ".new").exists())
        } catch (_: Exception) {
            throw IllegalStateException("Não foi possível remover a configuração do YouTube neste aparelho.")
        }
        apiKey = ""
        searchCache.clear()
        mutableSearchConfigured.value = usesSearchService
    }

    @Synchronized private fun cachedSearch(query: String): List<OnlineSong>? {
        val cached = searchCache[query] ?: return null
        if (SystemClock.elapsedRealtime() - cached.savedAt >= SEARCH_CACHE_MILLIS) {
            searchCache.remove(query)
            return null
        }
        return cached.songs
    }

    @Synchronized private fun rememberSearch(query: String, songs: List<OnlineSong>, requestKey: String) {
        if (requestKey != apiKey) return
        searchCache.remove(query)
        searchCache[query] = CachedSongs(SystemClock.elapsedRealtime(), songs)
        while (searchCache.size > MAX_CACHED_SEARCHES) searchCache.remove(searchCache.keys.first())
    }

    @Synchronized fun toggleFavorite(song: OnlineSong) {
        require(validId(song.videoId))
        val current = mutableFavorites.value
        val updated = if (current.any { it.videoId == song.videoId }) {
            current.filterNot { it.videoId == song.videoId }
        } else {
            check(current.size < MAX_FAVORITES) { "Você já tem 100 favoritos." }
            listOf(song) + current
        }
        saveSongs("favorites", updated)
        mutableFavorites.value = updated
    }

    @Synchronized fun recordPlayed(song: OnlineSong) {
        require(validId(song.videoId))
        val updated = (listOf(song) + mutableHistory.value.filterNot { it.videoId == song.videoId }).take(MAX_HISTORY)
        saveSongs("history", updated)
        mutableHistory.value = updated
    }

    private fun saveSongs(key: String, songs: List<OnlineSong>) {
        val array = JSONArray()
        songs.forEach { song -> array.put(JSONObject().put("videoId", song.videoId)
            .put("title", song.title).put("channel", song.channel)) }
        libraryPrefs.edit().putString(key, array.toString()).apply()
    }

    private fun readSongs(key: String, limit: Int): List<OnlineSong> = runCatching {
        val array = JSONArray(libraryPrefs.getString(key, "[]"))
        val seen = mutableSetOf<String>()
        (0 until minOf(array.length(), limit)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("videoId")
            if (!validId(id) || !seen.add(id)) null else OnlineSong(id, repairStoredText(item.optString("title")), repairStoredText(item.optString("channel")))
        }
    }.getOrDefault(emptyList())

    @Synchronized fun add(song: OnlineSong, first: Boolean = false): OnlineQueueEntry {
        require(validId(song.videoId))
        check(mutableQueue.value.size < MAX_QUEUE_SIZE) { "A fila já tem 100 músicas." }
        val entry = OnlineQueueEntry(UUID.randomUUID().toString(), song)
        save(if (first) listOf(entry) + mutableQueue.value else mutableQueue.value + entry)
        return entry
    }
    @Synchronized fun remove(id: String) { save(mutableQueue.value.filterNot { it.id == id }) }
    private fun save(entries: List<OnlineQueueEntry>) {
        val json = JSONArray()
        entries.forEach { entry -> json.put(JSONObject().put("id", entry.id).put("videoId", entry.song.videoId)
            .put("title", entry.song.title).put("channel", entry.song.channel)) }
        prefs.edit().putString("entries", json.toString()).apply()
        mutableQueue.value = entries
    }
    private fun readQueue(): List<OnlineQueueEntry> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        val seenIds = mutableSetOf<String>()
        (0 until minOf(array.length(), MAX_QUEUE_SIZE)).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val videoId = obj.optString("videoId")
            val entryId = obj.optString("id")
            if (!validId(videoId) || entryId.isBlank() || !seenIds.add(entryId)) null else OnlineQueueEntry(entryId,
                OnlineSong(videoId, repairStoredText(obj.optString("title")), repairStoredText(obj.optString("channel"))))
        }
    }.getOrDefault(emptyList())

    suspend fun fromLink(link: String): OnlineSong = withContext(Dispatchers.IO) {
        val id = parseVideoId(link) ?: throw IllegalArgumentException("Informe um link válido de vídeo do YouTube.")
        val json = request("https://www.youtube.com/oembed?format=json&url=" + encode("https://www.youtube.com/watch?v=$id"))
        OnlineSong(id, json.optString("title", "Vídeo do YouTube"), json.optString("author_name", "YouTube"))
    }
    suspend fun search(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val requestKey = apiKey
        check(usesSearchService || requestKey.isNotBlank()) { "A busca ainda não está configurada. Configure o servidor de pesquisa ou uma chave neste aparelho." }
        require(query.trim().length >= 2) { "Digite pelo menos dois caracteres." }
        require(query.trim().length <= 160) { "Use até 160 caracteres na pesquisa." }
        val cleanQuery = query.trim()
        cachedSearch(cleanQuery)?.let { return@withContext it }
        if (usesSearchService) {
            val json = access?.search(cleanQuery) ?: request(BuildConfig.SEARCH_SERVICE_URL + "/search?q=" + encode(cleanQuery))
            val items = json.optJSONArray("songs") ?: JSONArray()
            val songs = (0 until minOf(items.length(), 15)).mapNotNull { index ->
                val song = items.optJSONObject(index) ?: return@mapNotNull null
                val id = song.optString("videoId")
                if (!validId(id)) null else OnlineSong(id, decodeHtml(song.optString("title")), decodeHtml(song.optString("channel")))
            }
            rememberSearch(cleanQuery, songs, requestKey)
            return@withContext songs
        }
        val json = request("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true" +
            "&videoSyndicated=true&relevanceLanguage=pt&maxResults=15&q=${encode(cleanQuery + " karaoke")}&key=${encode(requestKey)}")
        val items = json.optJSONArray("items") ?: JSONArray()
        val songs = (0 until items.length()).mapNotNull { index ->
            val item = items.getJSONObject(index)
            val id = item.optJSONObject("id")?.optString("videoId") ?: ""
            val snippet = item.optJSONObject("snippet")
            if (!validId(id) || snippet == null) null else OnlineSong(id,
                decodeHtml(snippet.optString("title")), decodeHtml(snippet.optString("channelTitle")))
        }
        rememberSearch(cleanQuery, songs, requestKey)
        songs
    }
    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException(when (status) {
                400 -> "Confira o nome pesquisado ou o link do vídeo."
                403 -> "O YouTube recusou a solicitação. Confira a configuração e a cota da API."
                429 -> "Limite de pesquisas atingido. Aguarde um pouco e tente novamente."
                503 -> "A pesquisa está temporariamente indisponível. Tente novamente mais tarde."
                404 -> "Vídeo não encontrado ou indisponível."
                else -> "YouTube indisponível agora. Tente novamente."
            })
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: java.io.IOException) {
            throw IllegalStateException("Não foi possível conectar ao YouTube. Confira sua internet.")
        } finally { connection.disconnect() }
    }
    companion object {
        private const val MAX_QUEUE_SIZE = 100
        private const val MAX_FAVORITES = 100
        private const val MAX_HISTORY = 50
        private const val MAX_CACHED_SEARCHES = 12
        private const val SEARCH_CACHE_MILLIS = 2 * 60 * 1000L
        private val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")
        // Older Windows ADB transfers could misdecode UTF-8. Recover only strictly valid
        // round trips; genuine accents and text outside Windows-1252 remain untouched.
        private fun repairStoredText(value: String): String {
            var text = value
            repeat(2) {
                if (text.none { it == '\u00c3' || it == '\u00c2' || it == '\u00e2' }) return text
                val recovered = runCatching {
                    val bytes = Charset.forName("windows-1252").newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes).toString()
                }.getOrNull() ?: return text
                if (recovered == text) return text
                text = recovered
            }
            return text
        }
        fun validId(id: String) = VIDEO_ID_PATTERN.matches(id)
        fun isVideoLink(input: String): Boolean {
            val text = input.trim()
            val uri = Uri.parse(if (text.startsWith("https://") || text.startsWith("http://")) text else "https://$text")
            return uri.host?.lowercase() in setOf("youtu.be", "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com") &&
                parseVideoId(text) != null
        }
        private fun decodeHtml(value: String) = HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        fun parseVideoId(input: String): String? {
            val text = input.trim()
            if (validId(text)) return text
            val uri = Uri.parse(if (text.startsWith("https://") || text.startsWith("http://")) text else "https://$text")
            if (uri.scheme !in listOf("https", "http")) return null
            val host = uri.host?.lowercase() ?: return null
            val id = when (host) {
                "youtu.be" -> uri.pathSegments.firstOrNull()
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                    if (uri.path == "/watch") uri.getQueryParameter("v") else
                        if (uri.pathSegments.firstOrNull() in listOf("shorts", "live", "embed")) uri.pathSegments.getOrNull(1) else null
                else -> null
            }
            return id?.takeIf { validId(it) }
        }
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
