package com.araujo.karaoke_ia.data

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Same emulator, same 3,000 file pairs; fixture creation is outside measurements. */
class LocalIndexBenchmarkTest {
    @Test fun compareColdWarmRestartAndSearchAgainstPreviousRepository() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = File(context.cacheDir, "index-benchmark-${System.nanoTime()}").apply { mkdirs() }
        try {
            val root = File(base, "usb").apply { mkdirs() }
            repeat(3000) {
                val code = it.toString().padStart(5, '0')
                File(root, "$code.mp4").writeBytes(byteArrayOf(0))
                File(root, "$code.txt").writeText("""{"musica":"Canção $code","artista":"João $it"}""")
            }
            val values = linkedMapOf<String, Double>()
            fun measured(name: String, repetitions: Int = 1, operation: () -> Unit) {
                val samples = (1..repetitions).map {
                    val start = SystemClock.elapsedRealtimeNanos()
                    operation()
                    (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                }.sorted()
                values[name] = samples[samples.size / 2]
            }
            val oldCache = File(base, "old.json")
            val old = BaselineMusicRepository({ root.absolutePath }, oldCache)
            val database = File(base, "new.db")
            val current = MusicRepositoryImpl({ root.absolutePath }, database)
            measured("baseline_cold_ms") { assertEquals(3000, old.scanAllMusics().size) }
            measured("indexed_cold_ms") { assertEquals(3000, current.scanAllMusics().size) }
            measured("baseline_warm_scan_ms", 5) { assertEquals(3000, old.scanAllMusics().size) }
            measured("indexed_cached_load_ms", 5) { assertEquals(3000, current.loadCachedCatalog().size) }
            measured("indexed_incremental_verify_ms", 5) {
                assertEquals(3000, current.scanAllMusics().size)
                assertEquals(0, current.indexStats.metadataParsed)
            }
            measured("baseline_restart_ms", 5) {
                assertEquals(3000, BaselineMusicRepository({ root.absolutePath }, oldCache).scanAllMusics().size)
            }
            measured("indexed_restart_ms", 5) {
                assertEquals(3000, MusicRepositoryImpl({ root.absolutePath }, database).loadCachedCatalog().size)
            }
            val songs = old.scanAllMusics()
            val search = HybridSongSearch { emptyList() }
            measured("baseline_search_ms", 9) { assertEquals(1, search.localResults("canção 02999", songs).size) }
            measured("indexed_search_ms", 9) { assertEquals(1, current.searchLocal("canção 02999").size) }
            File(root, "02999.txt").writeText("""{"musica":"Título atualizado e mais longo","artista":"João 2999"}""")
            measured("baseline_one_metadata_edit_ms") { assertEquals(3000, old.scanAllMusics().size) }
            measured("indexed_one_metadata_edit_ms") {
                assertEquals(3000, current.scanAllMusics().size)
                assertEquals(1, current.indexStats.metadataParsed)
            }
            val report = values.entries.joinToString("\n") { "${it.key}=${it.value}" }
            File(context.filesDir, "local-index-benchmark.txt").writeText(report)
            Log.i("LocalIndexBenchmark", report)
        } finally { base.deleteRecursively() }
    }
}
