package com.araujo.karaoke_ia.ui.player

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.araujo.karaoke_ia.data.HistoryRepository
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.MusicRepository

class PlayerViewModel(
    private val repository: MusicRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _musicFile = mutableStateOf<MusicFile?>(null)
    val musicFile: State<MusicFile?> = _musicFile

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    fun loadMusic(code: String) {
        val file = repository.findMusicByCode(code)
        if (file != null) {
            _musicFile.value = file
            // Registra no histórico assim que a música é carregada com sucesso
            historyRepository.addToHistory(code)
        } else {
            _error.value = "Música $code não encontrada no HD.\nVerifique se o HD está conectado."
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val repository: MusicRepository,
        private val historyRepository: HistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(repository, historyRepository) as T
    }
}
