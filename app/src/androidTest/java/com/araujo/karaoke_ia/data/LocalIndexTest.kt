package com.araujo.karaoke_ia.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalIndexTest {
    private val base = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "local-index-${System.nanoTime()}").apply { mkdirs() }
    private val root = File(base, "usb").apply { mkdirs() }
    private val database = File(base, "catalog.db")
    private fun repository() = MusicRepositoryImpl({ root.absolutePath }, database)
    private fun song(code: String, title: String = "Canção $code", artist: String = "João") {
        File(root, "$code.mp4").writeBytes(byteArrayOf(0))
        File(root, "$code.txt").writeText("""{"musica":"$title","artista":"$artist"}""")
    }
    @After fun clean() { base.deleteRecursively() }

    @Test fun persistedCatalogueLoadsWithoutConsultingStorage() {
        song("00001")
        repository().scanAllMusics()
        var rootLookups = 0
        val reopened = MusicRepositoryImpl({ rootLookups++; root.absolutePath }, database)
        assertEquals("Canção 00001", reopened.loadCachedCatalog().single().title)
        assertEquals(0, rootLookups)
    }

    @Test fun normalizedTitleArtistSearchAndPaginationNeedNoStorageOrNetwork() {
        (1..25).forEach { song(it.toString().padStart(5, '0')) }
        repository().scanAllMusics()
        val reopened = MusicRepositoryImpl({ error("Search must not inspect USB") }, database)
        assertEquals(25, reopened.searchLocal("CANCAO", limit = 100).size)
        assertEquals(25, reopened.searchLocal("joao", limit = 100).size)
        val first = reopened.searchLocal("joao", limit = 10, offset = 0)
        val second = reopened.searchLocal("joao", limit = 10, offset = 10)
        assertEquals(10, first.size)
        assertEquals(10, second.size)
        assertTrue(first.map { it.videoPath }.intersect(second.map { it.videoPath }.toSet()).isEmpty())
        assertTrue(reopened.searchLocal("inexistente").isEmpty())
    }

    @Test fun disconnectKeepsCatalogueAndReconnectRestoresPlaybackPath() {
        song("00001")
        val repo = repository()
        repo.scanAllMusics()
        val unplugged = File(base, "unplugged")
        assertTrue(root.renameTo(unplugged))
        val missing = repo.scanAllMusics().single()
        assertFalse(missing.available)
        assertEquals("00001", repository().loadCachedCatalog().single().code)
        assertNull(repo.findMusicByCode("1"))
        assertTrue(unplugged.renameTo(root))
        assertTrue(repo.scanAllMusics().single().available)
        assertEquals(File(root, "00001.mp4").absolutePath, repo.findMusicByCode("1")?.videoPath)
    }

    @Test fun missingFallbackRootMarksPreviouslyConnectedVolumeUnavailable() {
        song("00001")
        var activeRoot = root.absolutePath
        val repo = MusicRepositoryImpl({ activeRoot }, database)
        repo.scanAllMusics()
        assertTrue(root.renameTo(File(base, "unplugged")))
        activeRoot = File(base, "nonexistent-fallback").absolutePath
        val retained = repo.scanAllMusics()
        assertEquals(1, retained.size)
        assertFalse(retained.single().available)
        assertEquals(1, repository().loadCachedCatalog().size)
    }

    @Test fun unreadableSubdirectoryDoesNotDeletePreviouslyIndexedFiles() {
        val nested = File(root, "nested").apply { mkdirs() }
        File(nested, "00001.mp4").writeBytes(byteArrayOf(0))
        File(nested, "00001.txt").writeText("""{"musica":"Preservada","artista":"Cantor"}""")
        val repo = repository()
        assertEquals(1, repo.scanAllMusics().size)
        assertTrue(nested.setReadable(false, false))
        try {
            org.junit.Assume.assumeTrue("Device user must obey directory permissions", nested.listFiles() == null)
            assertEquals(1, repo.scanAllMusics().size)
        } finally { nested.setReadable(true, false) }
    }

    @Test fun literalSqlWildcardsDoNotExpandSearchResults() {
        song("00001", "100% amor")
        song("00002", "Outro amor")
        song("00003", "Nome_com_underscore")
        val repo = repository()
        repo.scanAllMusics()
        assertEquals("00001", repo.searchLocal("%").single().code)
        assertEquals("00003", repo.searchLocal("_").single().code)
    }

    @Test fun simultaneousVolumesRemainAvailableAndDisconnectedVolumeCanReturn() {
        song("00001", "Memória interna")
        val usb = File(base, "second-volume").apply { mkdirs() }
        File(usb, "00002.mp4").writeBytes(byteArrayOf(0))
        File(usb, "00002.txt").writeText("""{"musica":"Pendrive","artista":"Cantor"}""")
        var roots = listOf(root.absolutePath, usb.absolutePath)
        val repo = MusicRepositoryImpl({ root.absolutePath }, database, { roots })
        assertEquals(2, repo.scanAllMusics().count { it.available })
        roots = listOf(root.absolutePath)
        val disconnected = repo.scanAllMusics()
        assertTrue(disconnected.single { it.code == "00001" }.available)
        assertFalse(disconnected.single { it.code == "00002" }.available)
        roots = listOf(root.absolutePath, usb.absolutePath)
        assertEquals(2, repo.scanAllMusics().count { it.available })
        assertEquals(File(usb, "00002.mp4").absolutePath, repo.findMusicByCode("2")?.videoPath)
    }

    @Test fun temporarilyIncompleteSidecarKeepsKnownMetadataUntilValidUpdate() {
        song("00001", "Nome original")
        val repo = repository()
        repo.scanAllMusics()
        File(root, "00001.txt").writeText("{gravação incompleta")
        assertEquals("Nome original", repo.scanAllMusics().single().title)
        song("00001", "Nome corrigido posteriormente")
        assertEquals("Nome corrigido posteriormente", repo.scanAllMusics().single().title)
    }

    @Test fun incrementalRefreshAddsRemovesRenamesAndReusesUnchangedMetadata() {
        (1..50).forEach { song(it.toString().padStart(5, '0')) }
        val repo = repository()
        assertEquals(50, repo.scanAllMusics().size)
        repo.scanAllMusics()
        assertEquals(0, repo.indexStats.metadataParsed)
        song("00051", "Nova música")
        assertTrue(File(root, "00001.mp4").delete())
        assertTrue(File(root, "00002.mp4").renameTo(File(root, "00052.mp4")))
        assertTrue(File(root, "00002.txt").renameTo(File(root, "00052.txt")))
        val changed = repo.scanAllMusics()
        assertEquals(50, changed.size)
        assertFalse(changed.any { it.code == "00001" || it.code == "00002" })
        assertTrue(changed.any { it.code == "00051" })
        assertTrue(changed.any { it.code == "00052" })
        assertTrue("Unchanged metadata must be reused", repo.indexStats.metadataParsed <= 2)
        song("00051", "Nome atualizado e diferente")
        assertEquals("Nome atualizado e diferente", repo.scanAllMusics().first { it.code == "00051" }.title)
    }
}
