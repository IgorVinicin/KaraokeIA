package com.araujo.karaoke_ia.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.araujo.karaoke_ia.online.OnlineRepository
import com.araujo.karaoke_ia.ui.home.HomeScreen
import com.araujo.karaoke_ia.ui.home.HomeTab
import com.araujo.karaoke_ia.ui.home.HomeViewModel
import com.araujo.karaoke_ia.ui.theme.KaraokeIATheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class HomeNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "navigation-test-${System.nanoTime()}"
    private val folder = File(target.cacheDir, prefix).apply { mkdirs() }
    private val preferences = mutableSetOf<String>()
    private val store = ViewModelStore()
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir() = File(folder, "private").apply { mkdirs() }
        override fun getFilesDir() = File(folder, "files").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            val scoped = "$prefix-$name"
            preferences.add(scoped)
            return target.getSharedPreferences(scoped, mode)
        }
    }

    @After fun clean() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        preferences.forEach { target.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        folder.deleteRecursively()
    }

    @Test fun dpadMovesBetweenTabsAndLocalSearchWorksWithoutNetwork() {
        val song = MusicFile("00001", "00001.mp4", "João", "Evidências", "", "/fixture/00001.mp4")
        val local = object : MusicRepository {
            override fun scanAllMusics(forceRefresh: Boolean) = listOf(song)
            override fun loadCachedCatalog() = listOf(song)
            override fun findMusicByCode(code: String) = song.takeIf { it.code == code }
        }
        lateinit var vm: HomeViewModel
        compose.runOnUiThread {
            val online = OnlineRepository(context)
            vm = HomeViewModel(HybridMusicRepository(local, online), FavoritesRepository(context),
                HistoryRepository(context), QueueRepository(context), ScoreRepository(context),
                ArtworkRepository(context), online, HybridSongSearch { error("Local search must stay offline") })
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            }).get(HomeViewModel::class.java)
        }
        compose.setContent { KaraokeIATheme { HomeScreen({}, vm) } }
        compose.waitUntil(10000) { !vm.isLoading.value && vm.allMusics.value.size == 1 }
        compose.onNodeWithText("Início").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Início").assertIsFocused()
        compose.onNodeWithText("Início").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Pesquisar").assertIsFocused()
        compose.onNodeWithText("Pesquisar").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { vm.selectedTab.value == HomeTab.PESQUISAR }
        compose.onNode(hasSetTextAction()).performTextInput("evidencias")
        compose.waitUntil(5000) { vm.filteredMusics.value == listOf(song) && !vm.isSearching.value }
        assertEquals("evidencias", vm.searchQuery.value)
        assertTrue(compose.onAllNodesWithText("Evidências").fetchSemanticsNodes().isNotEmpty())
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(target.filesDir, "home-navigation-search.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithContentDescription("Limpar busca").performClick()
        compose.waitUntil(5000) {
            vm.searchQuery.value.isEmpty() && !vm.isSearching.value && vm.filteredMusics.value == listOf(song)
        }
        assertEquals(listOf(song), vm.filteredMusics.value)
    }
}
