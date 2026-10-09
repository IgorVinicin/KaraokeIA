package com.araujo.karaoke_ia.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalIndexPlaybackTest {
    @Test fun indexedRealMp4HasDurationAndPlaysAfterRepositoryRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = File(instrumentation.targetContext.cacheDir, "playback-index-${System.nanoTime()}").apply { mkdirs() }
        try {
            val root = File(base, "usb").apply { mkdirs() }
            val video = File(root, "00001.mp4")
            createSilentMp4(video)
            File(root, "00001.txt").writeText("""{"musica":"Áudio de teste","artista":"Teste local"}""")
            val db = File(base, "index.db")
            val indexed = MusicRepositoryImpl({ root.absolutePath }, db).scanAllMusics().single()
            assertTrue("Container duration must be extracted", (indexed.durationMs ?: 0) >= 900)
            val reopened = MusicRepositoryImpl({ root.absolutePath }, db)
            assertEquals(indexed.durationMs, reopened.loadCachedCatalog().single().durationMs)
            val found = reopened.searchLocal("audio de teste").single()
            assertEquals(video.absolutePath, reopened.findMusicByCode(found.code)?.videoPath)
            val completed = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            lateinit var player: MediaPlayer
            instrumentation.runOnMainSync {
                player = MediaPlayer().apply {
                    setDataSource(found.videoPath)
                    setOnCompletionListener { completed.countDown() }
                    setOnErrorListener { _, what, extra -> error.set("$what/$extra"); completed.countDown(); true }
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            }
            try {
                assertTrue("Playback should complete", completed.await(15, TimeUnit.SECONDS))
                assertNull("Playback error", error.get())
            } finally { instrumentation.runOnMainSync { player.release() } }
        } finally { base.deleteRecursively() }
    }

    /** Generate a valid one-second AAC MP4 locally; no network or bundled media needed. */
    private fun createSilentMp4(file: File) {
        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        }
        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxing = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var supplied = 0
            var inputDone = false
            var outputDone = false
            var track = -1
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!outputDone && System.nanoTime() < deadline) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)!!
                        input.clear()
                        val count = minOf(input.remaining() / 2, 44100 - supplied)
                        repeat(count) { input.putShort(0) }
                        codec.queueInputBuffer(inputIndex, 0, count * 2, supplied * 1_000_000L / 44100,
                            if (count == 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        supplied += count
                        inputDone = count == 0
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                } else if (outputIndex >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val output = codec.getOutputBuffer(outputIndex)!!
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        muxer.writeSampleData(track, output, info)
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
            assertTrue("AAC encoder must finish", outputDone)
        } finally {
            codec.stop()
            codec.release()
            if (muxing) muxer.stop()
            muxer.release()
        }
    }
}
