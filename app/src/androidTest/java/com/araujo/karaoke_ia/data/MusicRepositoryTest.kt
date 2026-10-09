package com.araujo.karaoke_ia.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MusicRepositoryTest {
    private val base = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "catalog-test-${System.nanoTime()}").apply { mkdirs() }
    private val root = File(base, "usb").apply { mkdirs() }
    private val cache = File(base, "catalog.json")
    private fun repository() = MusicRepositoryImpl({ root.absolutePath }, cache)
    private fun song(code: String, title: String) {
        File(root, "$code.MP4").writeBytes(byteArrayOf(0))
        File(root, "$code.txt").writeText("""{"musica":"$title","artista":"Cantor"}""")
    }
    @After fun clean() { base.deleteRecursively() }

    @Test fun persistentCacheSurvivesNewRepositoryAndSupportsPaddedCode() {
        song("01007", "Evidências")
        val original = repository().scanAllMusics()
        assertTrue(cache.isFile)
        // Unchanged file identity must use the persisted snapshot, not reparse TXT.
        val metadata = File(root, "01007.txt")
        val stamp = metadata.lastModified()
        metadata.writeText(" ".repeat(metadata.length().toInt()))
        assertTrue(metadata.setLastModified(stamp))
        val reopened = repository()
        assertEquals(original, reopened.scanAllMusics())
        assertEquals("Evidências", reopened.findMusicByCode("1007")?.title)
    }

    @Test fun metadataEditsAndVideoRemovalInvalidateCache() {
        song("00001", "Primeira")
        val repo = repository()
        repo.scanAllMusics()
        song("00001", "Nome atualizado")
        assertEquals("Nome atualizado", repo.scanAllMusics().single().title)
        File(root, "00001.MP4").delete()
        assertTrue(repo.scanAllMusics().isEmpty())
        assertNull(repo.findMusicByCode("1"))
    }

    @Test fun corruptCacheRecoversFromSourceWithoutLosingCatalogue() {
        song("00001", "Primeira")
        repository().scanAllMusics()
        cache.writeText("{interrompido")
        assertEquals("Primeira", repository().scanAllMusics().single().title)
        assertTrue(cache.readText().contains("signature"))
    }

    @Test fun changingUsbRootDoesNotReuseAnotherDrivesCache() {
        song("00001", "Primeiro disco")
        var activeRoot = root.absolutePath
        val repo = MusicRepositoryImpl({ activeRoot }, cache)
        assertEquals("Primeiro disco", repo.scanAllMusics().single().title)
        val second = File(base, "usb2").apply { mkdirs() }
        File(second, "00001.mp4").writeBytes(byteArrayOf(0))
        File(second, "00001.txt").writeText("""{"musica":"Segundo disco","artista":"Cantor"}""")
        activeRoot = second.absolutePath
        assertEquals("Segundo disco", repo.scanAllMusics().single().title)
        assertEquals(File(second, "00001.mp4").absolutePath, repo.findMusicByCode("1")?.videoPath)
    }

    @Test fun forceRefreshReparsesMetadataEvenIfSizeAndTimestampAreUnchanged() {
        song("00001", "Primeira")
        val repo = repository()
        repo.scanAllMusics()
        val file = File(root, "00001.txt")
        val stamp = file.lastModified()
        file.writeText(file.readText().replace("Primeira", "Segundaa"))
        assertTrue(file.setLastModified(stamp))
        assertEquals("Segundaa", repo.scanAllMusics(forceRefresh = true).single().title)
    }
}
