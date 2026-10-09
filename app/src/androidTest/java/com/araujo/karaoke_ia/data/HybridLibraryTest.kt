package com.araujo.karaoke_ia.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.araujo.karaoke_ia.online.OnlineRepository
import com.araujo.karaoke_ia.online.OnlineSong
import com.araujo.karaoke_ia.ui.home.HomeViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HybridLibraryTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "hybrid-test-${System.nanoTime()}"
    private val folder = File(target.cacheDir, prefix).apply { mkdirs() }
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File = File(folder, "private").apply { mkdirs() }
        override fun getFilesDir(): File = File(folder, "files").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            preferenceNames.add("$prefix-$name")
            return target.getSharedPreferences("$prefix-$name", mode)
        }
    }
    private val store = ViewModelStore()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    private val localSong = MusicFile("00001", "song.mp4", "João", "Evidências", "", "/local/song.mp4")
    private val video = OnlineSong("abcdefghijk", "Outra música karaokê", "Canal")
    private fun local() = object : MusicRepository {
        override fun scanAllMusics(forceRefresh: Boolean) = listOf(localSong)
        override fun findMusicByCode(code: String) = localSong.takeIf { it.code == code }
    }
    @After fun clean() {
        gates.forEach { it.complete(Unit) }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        preferenceNames.forEach { target.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        folder.deleteRecursively()
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 6000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(20)
        assertTrue("State did not settle", condition())
    }
    private fun viewModel(search: HybridSongSearch, localRepository: MusicRepository = local()): HomeViewModel {
        lateinit var vm: HomeViewModel
        main {
            val online = OnlineRepository(context)
            vm = HomeViewModel(HybridMusicRepository(localRepository, online), FavoritesRepository(context),
                HistoryRepository(context), QueueRepository(context), ScoreRepository(context),
                ArtworkRepository(context), online, search)
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            }).get(HomeViewModel::class.java)
        }
        waitFor { !vm.isLoading.value }
        return vm
    }

    @Test fun cachedHomeAndSearchRemainResponsiveWhileUsbRefreshIsBlocked() {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val indexed = object : MusicRepository {
            override fun loadCachedCatalog() = listOf(localSong)
            override fun scanAllMusics(forceRefresh: Boolean): List<MusicFile> {
                started.countDown()
                release.await(15, java.util.concurrent.TimeUnit.SECONDS)
                return listOf(localSong)
            }
            override fun findMusicByCode(code: String) = localSong.takeIf { it.code == code }
        }
        try {
            val vm = viewModel(HybridSongSearch { error("Local results must not need internet") }, indexed)
            assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf(localSong), vm.allMusics.value)
            assertFalse(vm.isLoading.value)
            main { vm.onSearchQueryChange("evidencias") }
            waitFor { vm.filteredMusics.value == listOf(localSong) && !vm.isSearching.value }
        } finally { release.countDown() }
    }

    @Test fun lateYoutubeResponseCannotReplaceNewerLocalResults() {
        val started = java.util.concurrent.atomic.AtomicBoolean(false)
        val gate = CompletableDeferred<Unit>().also { gates.add(it) }
        val vm = viewModel(HybridSongSearch {
            started.set(true)
            withContext(NonCancellable) { gate.await() }
            listOf(video.asMusicFile())
        })
        main { vm.onSearchQueryChange("Outra música") }
        waitFor { started.get() }
        main { vm.onSearchQueryChange("evidencias") }
        waitFor { vm.filteredMusics.value == listOf(localSong) && !vm.isSearching.value }
        gate.complete(Unit)
        android.os.SystemClock.sleep(150)
        assertEquals(listOf(localSong), vm.filteredMusics.value)
        assertEquals("evidencias", vm.searchQuery.value)
    }

    @Test fun retryingSameQueryKeepsNewRequestBusyWhenCancelledRequestFinishes() {
        val count = java.util.concurrent.atomic.AtomicInteger(0)
        val first = CompletableDeferred<Unit>().also { gates.add(it) }
        val second = CompletableDeferred<Unit>().also { gates.add(it) }
        val vm = viewModel(HybridSongSearch {
            val gate = if (count.incrementAndGet() == 1) first else second
            withContext(NonCancellable) { gate.await() }
            listOf(video.asMusicFile())
        })
        main { vm.onSearchQueryChange("Outra música") }
        waitFor { count.get() == 1 }
        main { vm.onSearchQueryChange("Outra música") }
        waitFor { count.get() == 2 }
        first.complete(Unit)
        android.os.SystemClock.sleep(150)
        assertTrue(vm.isSearching.value)
        assertTrue(vm.filteredMusics.value.isEmpty())
        second.complete(Unit)
        waitFor { !vm.isSearching.value && vm.filteredMusics.value.isNotEmpty() }
        assertEquals(listOf(video.asMusicFile()), vm.filteredMusics.value)
    }

    @Test fun localMatchNeverQueriesYoutube() = runBlocking {
        var requests = 0
        val search = HybridSongSearch { requests++; listOf(video.asMusicFile()) }
        val matches = search.localResults("evidencias", listOf(localSong))
        assertEquals(listOf(localSong), search.searchMissing("evidencias", matches))
        assertEquals(0, requests)
    }

    @Test fun missingLocalSongFallsBackByName() = runBlocking {
        var submitted = ""
        val search = HybridSongSearch { submitted = it; listOf(video.asMusicFile()) }
        assertEquals(listOf(video.asMusicFile()), search.searchMissing(" Outra música ", search.localResults("Outra música", listOf(localSong))))
        assertEquals("Outra música", submitted)
    }

    @Test fun blankAndOneCharacterQueriesDoNotSpendNetworkRequests() = runBlocking {
        val search = HybridSongSearch { fail("Unexpected network request"); emptyList() }
        assertTrue(search.searchMissing("", emptyList()).isEmpty())
        assertTrue(search.searchMissing("a", emptyList()).isEmpty())
        assertEquals(listOf(localSong), search.localResults("", listOf(localSong)))
    }

    @Test fun localArtistAndCodeMatchingIgnoreAccents() {
        val search = HybridSongSearch { emptyList() }
        assertEquals(listOf(localSong), search.localResults("joao", listOf(localSong)))
        assertEquals(listOf(localSong), search.localResults("00001", listOf(localSong)))
    }

    @Test fun selectedYoutubeMetadataSurvivesRestartWithoutPollutingLocalScan() {
        val online = OnlineRepository(context)
        val repository = HybridMusicRepository(local(), online)
        repository.rememberResults(listOf(video.asMusicFile()))
        assertEquals(video.asMusicFile(), repository.findMusicByCode("youtube:abcdefghijk"))
        val reopened = HybridMusicRepository(local(), OnlineRepository(context))
        assertEquals(video.asMusicFile(), reopened.findMusicByCode("youtube:abcdefghijk"))
        assertEquals(listOf(localSong), reopened.scanAllMusics())
        assertNull(reopened.findMusicByCode("youtube:invalid"))
    }

    @Test fun mixedQueueKeepsOrderAndImportsLegacyOnlyOnce() {
        val queue = QueueRepository(context)
        queue.addToQueue(localSong.code)
        queue.importLegacyYouTube(listOf("youtube:abcdefghijk"))
        queue.importLegacyYouTube(listOf("youtube:abcdefghijk"))
        assertEquals(listOf("00001", "youtube:abcdefghijk"), queue.getQueue())
        assertNull(queue.popNextIfMatches("youtube:abcdefghijk"))
        assertEquals("00001", queue.popNextIfMatches("00001"))
        assertEquals("youtube:abcdefghijk", queue.popNextIfMatches("youtube:abcdefghijk"))
        queue.importLegacyYouTube(listOf("youtube:abcdefghijk"))
        assertTrue(queue.getQueue().isEmpty())
    }

    @Test fun removedLegacyFavoriteAndHistoryAreNotReimported() {
        val favorites = FavoritesRepository(context)
        favorites.importLegacyYouTube(setOf("youtube:abcdefghijk"))
        favorites.toggleFavorite("youtube:abcdefghijk")
        favorites.importLegacyYouTube(setOf("youtube:abcdefghijk"))
        assertFalse(favorites.isFavorite("youtube:abcdefghijk"))
        val history = HistoryRepository(context)
        history.addToHistory("00001")
        history.importLegacyYouTube(listOf("youtube:abcdefghijk"))
        history.importLegacyYouTube(listOf("youtube:abcdefghijk"))
        assertEquals(listOf("00001", "youtube:abcdefghijk"), history.getHistory())
    }
}
