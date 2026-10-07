package com.araujo.karaoke_ia.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Local persistent artwork cache and best-match lookup for songs in the external catalogue. */
class ArtworkRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("music_artwork_cache", Context.MODE_PRIVATE)
    private val artworkDirectory = File(appContext.filesDir, "music_artwork").apply { mkdirs() }
    private val requestLocks = ConcurrentHashMap<String, Mutex>()
    private val imageLocks = ConcurrentHashMap<String, Mutex>()
    private val networkSlots = Semaphore(3)
    private val musicBrainzRateLock = Mutex()
    private var lastMusicBrainzRequestAt = 0L

    suspend fun getArtworkFile(music: MusicFile, searchOnline: Boolean = true): File? = withContext(Dispatchers.IO) {
        val key = lookupKey(music)
        requestLocks.computeIfAbsent(key) { Mutex() }.withLock {
            val savedUrl = preferences.getString(urlPreference(key), null)
            if (savedUrl != null) {
                val cachedFile = artworkFile(savedUrl)
                if (cachedFile.isFile && cachedFile.length() > 0L) return@withLock cachedFile
                if (!searchOnline) return@withLock null
                if (!isOnline()) return@withLock null
                downloadArtwork(savedUrl)?.let { return@withLock it }
                preferences.edit().remove(urlPreference(key)).apply()
            }
            if (!searchOnline) return@withLock null
            if (isRecentMiss(key) || !isOnline()) return@withLock null

            networkSlots.withPermit {
                val itunesUrl = findItunesArtwork(music)
                if (itunesUrl != null) {
                    downloadArtwork(itunesUrl)?.let { file ->
                        preferences.edit().putString(urlPreference(key), itunesUrl).remove(missPreference(key)).apply()
                        return@withPermit file
                    }
                }

                // MusicBrainz + Cover Art Archive is a slower fallback for missing/untrusted iTunes matches.
                val coverArtArchiveUrl = findCoverArtArchiveArtwork(music)
                if (coverArtArchiveUrl != null) {
                    downloadArtwork(coverArtArchiveUrl)?.let { file ->
                        preferences.edit().putString(urlPreference(key), coverArtArchiveUrl).remove(missPreference(key)).apply()
                        return@withPermit file
                    }
                }

                preferences.edit().putLong(missPreference(key), System.currentTimeMillis() + MISS_CACHE_MILLIS).apply()
                null
            }
        }
    }

    /** True when a lookup already succeeded or recently failed, avoiding repeat batch requests. */
    fun hasLookupResult(music: MusicFile): Boolean {
        val key = lookupKey(music)
        return preferences.contains(urlPreference(key)) || isRecentMiss(key)
    }

    private fun findItunesArtwork(music: MusicFile): String? {
        val term = URLEncoder.encode("${music.title} ${music.artist}", Charsets.UTF_8.name())
        val json = requestJson("https://itunes.apple.com/search?term=$term&entity=song&limit=10") ?: return null
        val results = json.optJSONArray("results") ?: return null
        val best = (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("trackName")
            val artist = item.optString("artistName")
            val artwork = item.optString("artworkUrl100").takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: return@mapNotNull null
            val score = matchScore(music.title, music.artist, title, artist) ?: return@mapNotNull null
            score to artwork.replace(Regex("\\d+x\\d+bb"), "600x600bb")
        }.maxByOrNull { it.first }
        return best?.second
    }

    private suspend fun findCoverArtArchiveArtwork(music: MusicFile): String? {
        val title = URLEncoder.encode("\"${music.title}\"", Charsets.UTF_8.name())
        val artist = URLEncoder.encode("\"${music.artist}\"", Charsets.UTF_8.name())
        val query = "recording:$title%20AND%20artist:$artist"
        val json = musicBrainzRateLock.withLock {
            val waitMillis = MUSICBRAINZ_MIN_INTERVAL_MILLIS -
                (System.currentTimeMillis() - lastMusicBrainzRequestAt)
            if (waitMillis > 0) delay(waitMillis)
            lastMusicBrainzRequestAt = System.currentTimeMillis()
            requestJson(
                "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=5",
                userAgent = MUSICBRAINZ_USER_AGENT
            )
        } ?: return null
        val recordings = json.optJSONArray("recordings") ?: return null
        val best = (0 until recordings.length()).mapNotNull { index ->
            val recording = recordings.optJSONObject(index) ?: return@mapNotNull null
            val recordingTitle = recording.optString("title")
            val credits = recording.optJSONArray("artist-credit") ?: return@mapNotNull null
            val recordingArtist = buildString {
                for (creditIndex in 0 until credits.length()) {
                    val credit = credits.optJSONObject(creditIndex) ?: continue
                    if (isNotEmpty()) append(' ')
                    append(credit.optJSONObject("artist")?.optString("name").orEmpty())
                }
            }
            val score = matchScore(music.title, music.artist, recordingTitle, recordingArtist)
                ?: return@mapNotNull null
            val releases = recording.optJSONArray("releases") ?: return@mapNotNull null
            val releaseId = (0 until releases.length()).firstNotNullOfOrNull { releaseIndex ->
                releases.optJSONObject(releaseIndex)?.optString("id")?.takeIf { it.isNotBlank() }
            } ?: return@mapNotNull null
            score to releaseId
        }.maxByOrNull { it.first }
        return best?.second?.let { "https://coverartarchive.org/release/$it/front-250" }
    }

    private fun matchScore(queryTitle: String, queryArtist: String, resultTitle: String, resultArtist: String): Double? {
        val titleScore = similarity(normalize(queryTitle), normalize(resultTitle))
        val artistScore = similarity(normalize(queryArtist), normalize(resultArtist))
        val combined = titleScore * 0.68 + artistScore * 0.32
        return if (titleScore >= MIN_TITLE_SIMILARITY && artistScore >= MIN_ARTIST_SIMILARITY && combined >= MIN_MATCH_SCORE) {
            combined
        } else null
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        if (left.contains(right) || right.contains(left)) return 0.88
        val leftWords = left.split(' ').filter(String::isNotBlank).toSet()
        val rightWords = right.split(' ').filter(String::isNotBlank).toSet()
        val intersection = leftWords.intersect(rightWords).size.toDouble()
        return if (leftWords.isEmpty() || rightWords.isEmpty()) 0.0
        else 2.0 * intersection / (leftWords.size + rightWords.size)
    }

    private fun normalize(value: String): String {
        val withoutFeatures = value.replace(Regex("(?i)\\b(feat(?:uring)?|ft)\\.?\\s+.*$"), " ")
        val ascii = Normalizer.normalize(withoutFeatures.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return ascii.replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    }

    private fun lookupKey(music: MusicFile): String = sha256("${normalize(music.title)}|${normalize(music.artist)}")

    private fun artworkFile(url: String): File = File(artworkDirectory, "${sha256(url)}.img")

    private suspend fun downloadArtwork(url: String): File? {
        val imageKey = sha256(url)
        return imageLocks.computeIfAbsent(imageKey) { Mutex() }.withLock {
            val target = artworkFile(url)
            if (target.isFile && target.length() > 0L) return@withLock target
            val bytes = requestBytes(url, userAgent = DEFAULT_USER_AGENT) ?: return@withLock null
            val temporary = File(artworkDirectory, "$imageKey.tmp")
            try {
                temporary.outputStream().use { it.write(bytes) }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withLock null
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                target
            } catch (_: Exception) {
                null
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private fun requestJson(url: String, userAgent: String = DEFAULT_USER_AGENT): JSONObject? {
        val bytes = requestBytes(url, userAgent, MAX_JSON_BYTES) ?: return null
        return try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { null }
    }

    private fun requestBytes(url: String, userAgent: String, maxBytes: Int = MAX_IMAGE_BYTES): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "image/avif,image/webp,image/*,application/json;q=0.9,*/*;q=0.8")
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = manager?.activeNetwork
            val capabilities = network?.let { manager.getNetworkCapabilities(it) }
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isRecentMiss(key: String): Boolean {
        val expiration = preferences.getLong(missPreference(key), 0L)
        if (expiration <= System.currentTimeMillis()) {
            if (expiration != 0L) preferences.edit().remove(missPreference(key)).apply()
            return false
        }
        return true
    }

    private fun urlPreference(key: String) = "url_$key"
    private fun missPreference(key: String) = "miss_$key"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val READ_TIMEOUT_MILLIS = 7_000
        private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        private const val MAX_JSON_BYTES = 2 * 1024 * 1024
        private const val MISS_CACHE_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val MUSICBRAINZ_MIN_INTERVAL_MILLIS = 1_100L
        private const val MIN_TITLE_SIMILARITY = 0.58
        private const val MIN_ARTIST_SIMILARITY = 0.45
        private const val MIN_MATCH_SCORE = 0.60
        private const val DEFAULT_USER_AGENT = "KaraokeIA/1.0 (Android artwork lookup)"
        private const val MUSICBRAINZ_USER_AGENT = "KaraokeIA/1.0 (Android artwork lookup)"
    }
}
