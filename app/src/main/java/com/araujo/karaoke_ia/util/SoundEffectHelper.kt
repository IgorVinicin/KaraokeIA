package com.araujo.karaoke_ia.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlin.math.sin

object SoundEffectHelper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Toca uma sequência sonora festiva de acordo com a pontuação obtida
     */
    fun playCelebrationSound(score: Int) {
        scope.launch {
            try {
                when {
                    score >= 90 -> playStarFanfare()
                    score >= 80 -> playGoodJobChime()
                    else        -> playEncouragementChime()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SoundEffectHelper", "Falha ao reproduzir efeito sonoro", e)
            }
        }
    }

    /**
     * Fanfarra triunfal para notas altas (>= 90)
     */
    private suspend fun playStarFanfare() {
        playNotesSequence(
            listOf(
                Pair(523.25, 120),  // Dó
                Pair(659.25, 120),  // Mi
                Pair(783.99, 140),  // Sol
                Pair(1046.50, 450)  // Dó agudo longo
            )
        )
    }

    /**
     * Acorde alegre para boas notas (80 - 89)
     */
    private suspend fun playGoodJobChime() {
        playNotesSequence(
            listOf(
                Pair(523.25, 150),  // Dó
                Pair(659.25, 150),  // Mi
                Pair(783.99, 350)   // Sol
            )
        )
    }

    /**
     * Toque suave para incentivo (< 80)
     */
    private suspend fun playEncouragementChime() {
        playNotesSequence(
            listOf(
                Pair(440.00, 180),  // Lá
                Pair(523.25, 300)   // Dó
            )
        )
    }

    private suspend fun playNotesSequence(notes: List<Pair<Double, Int>>) {
        val sampleRate = 44100
        val totalMs = notes.sumOf { it.second }
        val totalSamples = (sampleRate * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(totalSamples)

        var currentOffset = 0
        for ((freq, durationMs) in notes) {
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            for (i in 0 until numSamples) {
                if (currentOffset + i >= buffer.size) break
                val envelope = (1.0 - (i.toDouble() / numSamples)).coerceIn(0.0, 1.0)
                val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                buffer[currentOffset + i] = (sin(angle) * Short.MAX_VALUE * 0.7 * envelope).toInt().toShort()
            }
            currentOffset += numSamples
        }

        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            delay(totalMs.toLong() + 100)
        } finally {
            runCatching { track?.stop() }
            track?.release()
        }
    }
}
