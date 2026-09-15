package com.araujo.karaoke_ia.ui.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.araujo.karaoke_ia.data.FavoritesRepository
import com.araujo.karaoke_ia.data.HistoryRepository
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.MusicRepository
import com.araujo.karaoke_ia.data.QueueRepository
import com.araujo.karaoke_ia.data.ScoreRepository
import com.araujo.karaoke_ia.data.UserScoreEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Abas de Navegação Superior (Pill Tabs) ──────────────────────────────────
enum class HomeTab {
    INICIO,
    MUSICAS,
    PESQUISAR,
    FILA,
    RANKING,
    FAVORITOS;

    companion object {
        val FILTROS = MUSICAS
        val HISTORICO = INICIO
    }
}

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val favoritesRepo: FavoritesRepository,
    private val historyRepo: HistoryRepository,
    private val queueRepo: QueueRepository,
    private val scoreRepo: ScoreRepository
) : ViewModel() {

    // ── Busca / código ────────────────────────────────────────────────────────

    /** Código numérico digitado no campo de busca rápida */
    private val _musicCode = mutableStateOf("")
    val musicCode: State<String> = _musicCode

    /** Texto livre de busca (filtra nome, artista ou código) */
    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    /** Dígitos do painel flutuante de digitação direta por controle remoto */
    private val _quickCode = mutableStateOf("")
    val quickCode: State<String> = _quickCode

    /** Música encontrada pelo código rápido atual (se houver) */
    private val _quickMatchedMusic = mutableStateOf<MusicFile?>(null)
    val quickMatchedMusic: State<MusicFile?> = _quickMatchedMusic

    /** Visibilidade do popup/HUD de digitação de código */
    private val _showQuickCodeHud = mutableStateOf(false)
    val showQuickCodeHud: State<Boolean> = _showQuickCodeHud

    // ── Navegação ─────────────────────────────────────────────────────────────

    private val _selectedTab = mutableStateOf(HomeTab.INICIO)
    val selectedTab: State<HomeTab> = _selectedTab

    // ── Dados do HD ───────────────────────────────────────────────────────────

    /** Catálogo completo do HD */
    private val _allMusics = mutableStateOf<List<MusicFile>>(emptyList())
    val allMusics: State<List<MusicFile>> = _allMusics

    /** Lista exibida na aba PESQUISAR / MÚSICAS (filtrada por searchQuery) */
    private val _filteredMusics = mutableStateOf<List<MusicFile>>(emptyList())
    val filteredMusics: State<List<MusicFile>> = _filteredMusics

    /** Música em destaque na tela de busca */
    private val _selectedSearchMusic = mutableStateOf<MusicFile?>(null)
    val selectedSearchMusic: State<MusicFile?> = _selectedSearchMusic

    /** Estado de carregamento do HD */
    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    /** Mensagem de erro ao carregar */
    private val _loadError = mutableStateOf<String?>(null)
    val loadError: State<String?> = _loadError

    // ── Filtro por cantor ─────────────────────────────────────────────────────

    /** Lista de cantores únicos extraída do HD */
    private val _artistList = mutableStateOf<List<String>>(emptyList())
    val artistList: State<List<String>> = _artistList

    /** Cantor selecionado no filtro (null = mostra o grid de cantores) */
    private val _selectedArtist = mutableStateOf<String?>(null)
    val selectedArtist: State<String?> = _selectedArtist

    /** Músicas do cantor selecionado */
    private val _artistMusics = mutableStateOf<List<MusicFile>>(emptyList())
    val artistMusics: State<List<MusicFile>> = _artistMusics

    // ── Favoritos ─────────────────────────────────────────────────────────────

    private val _favorites = mutableStateOf<Set<String>>(emptySet())
    val favorites: State<Set<String>> = _favorites

    private val _favoriteMusics = mutableStateOf<List<MusicFile>>(emptyList())
    val favoriteMusics: State<List<MusicFile>> = _favoriteMusics

    // ── Histórico ─────────────────────────────────────────────────────────────

    private val _historyMusics = mutableStateOf<List<MusicFile>>(emptyList())
    val historyMusics: State<List<MusicFile>> = _historyMusics

    // ── Fila de Reprodução / Reservas ─────────────────────────────────────────

    private val _queueCodes = mutableStateOf<List<String>>(emptyList())
    val queueCodes: State<List<String>> = _queueCodes

    private val _queueMusics = mutableStateOf<List<MusicFile>>(emptyList())
    val queueMusics: State<List<MusicFile>> = _queueMusics

    // ── Minhas Pontuações Reais ───────────────────────────────────────────────

    private val _myBestScores = mutableStateOf<List<UserScoreEntry>>(emptyList())
    val myBestScores: State<List<UserScoreEntry>> = _myBestScores

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        _favorites.value = favoritesRepo.getFavorites()
        _queueCodes.value = queueRepo.getQueue()
        loadScores()
        loadAllMusics()
    }

    fun loadScores() {
        _myBestScores.value = scoreRepo.getBestScores()
    }

    // ── Carregamento do HD ────────────────────────────────────────────────────

    fun loadAllMusics(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null

            val musics = withContext(Dispatchers.IO) {
                try { musicRepository.scanAllMusics(forceRefresh) }
                catch (e: Exception) { emptyList() }
            }

            _allMusics.value = musics

            if (musics.isEmpty()) {
                _loadError.value = "Nenhuma música encontrada no HD.\nVerifique se o HD está conectado."
            }

            applySearchFilter(_searchQuery.value, musics)
            refreshArtistList(musics)
            refreshFavoriteMusics(musics)
            refreshHistoryMusics(musics)
            refreshQueueMusics(musics)
            loadScores()

            _isLoading.value = false
        }
    }

    // ── Digitação rápida por controle remoto ──────────────────────────────────

    fun appendQuickDigit(digit: Char) {
        if (!digit.isDigit()) return
        val current = _quickCode.value
        if (current.length < 5) {
            val updated = current + digit
            _quickCode.value = updated
            _showQuickCodeHud.value = true
            updateQuickMatchedMusic(updated)
        }
    }

    fun deleteQuickDigit() {
        val current = _quickCode.value
        if (current.isNotEmpty()) {
            val updated = current.dropLast(1)
            _quickCode.value = updated
            if (updated.isEmpty()) {
                _showQuickCodeHud.value = false
                _quickMatchedMusic.value = null
            } else {
                updateQuickMatchedMusic(updated)
            }
        } else {
            _showQuickCodeHud.value = false
        }
    }

    fun clearQuickCode() {
        _quickCode.value = ""
        _quickMatchedMusic.value = null
        _showQuickCodeHud.value = false
    }

    private fun updateQuickMatchedMusic(code: String) {
        val padded = code.padStart(5, '0')
        val match = _allMusics.value.firstOrNull { it.code == code || it.code == padded }
        _quickMatchedMusic.value = match
        if (match != null) {
            _selectedSearchMusic.value = match
        }
    }

    // ── Busca ─────────────────────────────────────────────────────────────────

    fun onCodeChange(newCode: String) {
        if (newCode.length <= 5 && newCode.all { it.isDigit() }) {
            _musicCode.value = newCode
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        applySearchFilter(query, _allMusics.value)
    }

    fun selectSearchMusic(music: MusicFile) {
        _selectedSearchMusic.value = music
    }

    private fun applySearchFilter(query: String, all: List<MusicFile>) {
        val filtered = if (query.isBlank()) {
            all
        } else {
            val q = query.trim().lowercase()
            all.filter {
                it.code.contains(q) ||
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q)
            }
        }
        _filteredMusics.value = filtered
        if (filtered.isNotEmpty()) {
            if (_selectedSearchMusic.value == null || !filtered.contains(_selectedSearchMusic.value)) {
                _selectedSearchMusic.value = filtered.first()
            }
        } else {
            _selectedSearchMusic.value = null
        }
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    fun onTabSelected(tab: HomeTab) {
        _selectedTab.value = tab
        if (tab == HomeTab.MUSICAS) {
            _selectedArtist.value = null
            _artistMusics.value   = emptyList()
        }
        if (tab == HomeTab.RANKING || tab == HomeTab.INICIO) {
            loadScores()
        }
    }

    // ── Filtro por cantor ─────────────────────────────────────────────────────

    fun onArtistSelected(artist: String) {
        _selectedArtist.value = artist
        _artistMusics.value = _allMusics.value.filter { it.artist == artist }
    }

    fun onArtistCleared() {
        _selectedArtist.value = null
        _artistMusics.value   = emptyList()
    }

    private fun refreshArtistList(musics: List<MusicFile>) {
        _artistList.value = musics.map { it.artist }.distinct().sorted()
    }

    // ── Favoritos ─────────────────────────────────────────────────────────────

    fun toggleFavorite(code: String) {
        favoritesRepo.toggleFavorite(code)
        val updated = favoritesRepo.getFavorites()
        _favorites.value = updated
        refreshFavoriteMusics(_allMusics.value)
    }

    private fun refreshFavoriteMusics(musics: List<MusicFile>) {
        val favCodes = _favorites.value
        _favoriteMusics.value = musics.filter { favCodes.contains(it.code) }
    }

    // ── Histórico ─────────────────────────────────────────────────────────────

    private fun refreshHistoryMusics(musics: List<MusicFile>) {
        val codes    = historyRepo.getHistory()
        val musicMap = musics.associateBy { it.code }
        _historyMusics.value = codes.mapNotNull { musicMap[it] }
    }

    // ── Fila de Reservas ──────────────────────────────────────────────────────

    fun addToQueue(code: String): Boolean {
        val added = queueRepo.addToQueue(code)
        _queueCodes.value = queueRepo.getQueue()
        refreshQueueMusics(_allMusics.value)
        return added
    }

    fun removeFromQueue(code: String) {
        queueRepo.removeFromQueue(code)
        _queueCodes.value = queueRepo.getQueue()
        refreshQueueMusics(_allMusics.value)
    }

    fun clearQueue() {
        queueRepo.clearQueue()
        _queueCodes.value = emptyList()
        _queueMusics.value = emptyList()
    }

    private fun refreshQueueMusics(musics: List<MusicFile>) {
        val codes    = queueRepo.getQueue()
        val musicMap = musics.associateBy { it.code }
        _queueMusics.value = codes.mapNotNull { musicMap[it] }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val musicRepository: MusicRepository,
        private val favoritesRepo: FavoritesRepository,
        private val historyRepo: HistoryRepository,
        private val queueRepo: QueueRepository,
        private val scoreRepo: ScoreRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(musicRepository, favoritesRepo, historyRepo, queueRepo, scoreRepo) as T
    }
}
