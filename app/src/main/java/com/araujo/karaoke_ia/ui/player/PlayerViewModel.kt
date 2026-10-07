package com.araujo.karaoke_ia.ui.player

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.araujo.karaoke_ia.data.HistoryRepository
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.MusicRepository
import com.araujo.karaoke_ia.data.ScoreRepository

class PlayerViewModel(
    private val repository: MusicRepository,
    private val historyRepository: HistoryRepository,
    private val scoreRepository: ScoreRepository
) : ViewModel() {

    private val _musicFile = mutableStateOf<MusicFile?>(null)
    val musicFile: State<MusicFile?> = _musicFile

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error
    private var loadJob: Job? = null

    fun loadMusic(code: String) {
        loadJob?.cancel()
        _error.value = null
        _musicFile.value = null
        loadJob = viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { repository.findMusicByCode(code) }
                if (file != null) {
                    _musicFile.value = file
                    historyRepository.addToHistory(file.code)
                } else {
                    _error.value = "Música $code não encontrada no HD.\nVerifique se o HD está conectado."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = "Não foi possível ler a música. Verifique o HD e a permissão de armazenamento."
            }
        }
    }

    fun recordScore(score: Int) {
        val currentMusic = _musicFile.value ?: return
        scoreRepository.saveScore(
            musicCode = currentMusic.code,
            title = currentMusic.title,
            artist = currentMusic.artist,
            score = score
        )
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val repository: MusicRepository,
        private val historyRepository: HistoryRepository,
        private val scoreRepository: ScoreRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(repository, historyRepository, scoreRepository) as T
    }
}
