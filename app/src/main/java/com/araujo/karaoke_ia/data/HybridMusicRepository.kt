package com.araujo.karaoke_ia.data

import com.araujo.karaoke_ia.online.OnlineRepository
import com.araujo.karaoke_ia.online.OnlineSong
import java.util.concurrent.ConcurrentHashMap

fun OnlineSong.asMusicFile() = MusicFile("youtube:$videoId", "", channel, title, "")
fun MusicFile.asOnlineSong() = OnlineSong(code.removePrefix("youtube:"), title, artist)

/** Local scanning stays independent of the network. Chosen online songs survive restarts. */
class HybridMusicRepository(private val local: MusicRepository, private val online: OnlineRepository) : MusicRepository {
    private val searchResults = ConcurrentHashMap<String, MusicFile>()
    fun rememberResults(songs: List<MusicFile>) {
        searchResults.clear()
        songs.forEach { searchResults[it.code] = it }
    }
    override fun scanAllMusics(forceRefresh: Boolean) = local.scanAllMusics(forceRefresh)
    override fun loadCachedCatalog() = local.loadCachedCatalog()
    override fun searchLocal(query: String, limit: Int, offset: Int) = local.searchLocal(query, limit, offset)
    override val catalogUpdates get() = local.catalogUpdates
    override fun findMusicByCode(code: String): MusicFile? {
        if (!code.startsWith("youtube:")) return local.findMusicByCode(code)
        if (!OnlineRepository.validId(code.removePrefix("youtube:"))) return null
        val song = searchResults[code] ?: online.savedSongs.firstOrNull { "youtube:${it.videoId}" == code }?.asMusicFile()
        song?.let { online.rememberSong(it.asOnlineSong()) }
        return song
    }
}
