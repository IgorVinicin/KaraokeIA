@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.araujo.karaoke_ia.R
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  Atalhos de cor
// ─────────────────────────────────────────────────────────────────────────────
private val Purple  = NeonPurple
private val Magenta = NeonMagenta
private val Gold    = DiscoGold
private val Green   = DiscoGreen
private val Pink    = NeonPink

// ─────────────────────────────────────────────────────────────────────────────
//  HomeScreen — 3 colunas TV Box
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (String) -> Unit,
    viewModel: HomeViewModel
) {
    val musicCode     by viewModel.musicCode
    val searchQuery   by viewModel.searchQuery
    val selectedTab   by viewModel.selectedTab
    val isLoading     by viewModel.isLoading
    val loadError     by viewModel.loadError
    val lastMusic     by viewModel.lastMusic
    val lastScore     by viewModel.lastScore
    val favorites     by viewModel.favorites

    // Fila de reprodução
    val queueCodes    by viewModel.queueCodes
    val queueMusics   by viewModel.queueMusics

    // Digitação rápida por controle remoto
    val quickCode          by viewModel.quickCode
    val quickMatchedMusic  by viewModel.quickMatchedMusic
    val showQuickCodeHud   by viewModel.showQuickCodeHud

    // Listas por aba
    val filteredMusics  by viewModel.filteredMusics
    val favoriteMusics  by viewModel.favoriteMusics
    val historyMusics   by viewModel.historyMusics
    val artistList      by viewModel.artistList
    val selectedArtist  by viewModel.selectedArtist
    val artistMusics    by viewModel.artistMusics

    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Fecha o HUD de código após 6 segundos sem digitar
    LaunchedEffect(quickCode, showQuickCodeHud) {
        if (showQuickCodeHud) {
            delay(6000)
            viewModel.clearQuickCode()
        }
    }

    // Auto-inicia após 5 dígitos digitados se houver música correspondente
    LaunchedEffect(quickCode) {
        if (quickCode.length == 5) {
            delay(500)
            if (showQuickCodeHud) {
                val codeToPlay = quickMatchedMusic?.code ?: quickCode
                viewModel.clearQuickCode()
                onNavigateToPlayer(codeToPlay)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode

                    // 1. Teclas numéricas 0 a 9 (Topo do teclado e teclado numérico)
                    val digit: Char? = when (keyCode) {
                        in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 ->
                            ('0'.code + (keyCode - android.view.KeyEvent.KEYCODE_0)).toChar()
                        in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 ->
                            ('0'.code + (keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0)).toChar()
                        else -> null
                    }

                    if (digit != null) {
                        viewModel.appendQuickDigit(digit)
                        return@onKeyEvent true
                    }

                    // 2. Tecla Apagar (DEL / Backspace)
                    if (keyCode == android.view.KeyEvent.KEYCODE_DEL && showQuickCodeHud) {
                        viewModel.deleteQuickDigit()
                        return@onKeyEvent true
                    }

                    // 3. Tecla Confirmar (Enter / OK / D-pad Center)
                    if ((keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                         keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                         keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) && showQuickCodeHud) {
                        if (quickCode.isNotEmpty()) {
                            val codeToPlay = quickMatchedMusic?.code ?: quickCode.padStart(5, '0')
                            viewModel.clearQuickCode()
                            onNavigateToPlayer(codeToPlay)
                            return@onKeyEvent true
                        }
                    }

                    // 4. Tecla Cancelar / Voltar
                    if ((keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                         keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) && showQuickCodeHud) {
                        viewModel.clearQuickCode()
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {

        // ── Fundo: foto do palco ─────────────────────────────────────────
        Image(
            painter     = painterResource(id = R.drawable.disco_stage_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier    = Modifier.fillMaxSize()
        )

        // ── Overlay escuro roxo para legibilidade da UI ──────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xCC050010),   // topo: muito escuro
                            0.3f to Color(0xAA0A0020),   // meio-topo: semi-transparente
                            0.7f to Color(0xBB050015),   // meio-base
                            1.0f to Color(0xEE020008)    // base: quase opaco
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {

            // ── 1. Sidebar ───────────────────────────────────────────────
            SidebarNavigation(
                selectedTab   = selectedTab,
                queueCount    = queueCodes.size,
                onTabSelected = { viewModel.onTabSelected(it) }
            )

            // ── 2. Coluna Central ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
            ) {
                WelcomeBanner()
                Spacer(modifier = Modifier.height(12.dp))

                // Campo de busca (sempre visível)
                SearchBar(
                    query    = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearch = {
                        val q = searchQuery.trim()
                        if (q.isNotEmpty()) onNavigateToPlayer(q)
                        else if (musicCode.isNotEmpty()) onNavigateToPlayer(musicCode)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Conteúdo dinâmico baseado na aba selecionada
                when {
                    isLoading -> LoadingState(modifier = Modifier.weight(1f))

                    loadError != null && selectedTab == HomeTab.PESQUISAR ->
                        ErrorState(
                            message  = loadError!!,
                            onRetry  = { viewModel.loadAllMusics() },
                            modifier = Modifier.weight(1f)
                        )

                    selectedTab == HomeTab.PESQUISAR ->
                        MusicListSection(
                            title        = "🎵  MÚSICAS",
                            musics       = filteredMusics,
                            favorites    = favorites,
                            queueCodes   = queueCodes,
                            onCantar     = { code ->
                                val m = filteredMusics.firstOrNull { it.code == code }
                                if (m != null) viewModel.onMusicPlayed(m, 86)
                                onNavigateToPlayer(code)
                            },
                            onFavorite   = { viewModel.toggleFavorite(it) },
                            onQueue      = { viewModel.addToQueue(it) },
                            emptyMessage = if (searchQuery.isNotBlank())
                                "Nenhuma música encontrada para\n\"$searchQuery\""
                            else "Nenhuma música no HD",
                            modifier     = Modifier.weight(1f)
                        )

                    selectedTab == HomeTab.FILTROS ->
                        FilterSection(
                            artistList     = artistList,
                            selectedArtist = selectedArtist,
                            artistMusics   = artistMusics,
                            favorites      = favorites,
                            queueCodes     = queueCodes,
                            onArtistClick  = { viewModel.onArtistSelected(it) },
                            onBack         = { viewModel.onArtistCleared() },
                            onCantar       = { code ->
                                val m = artistMusics.firstOrNull { it.code == code }
                                if (m != null) viewModel.onMusicPlayed(m, 86)
                                onNavigateToPlayer(code)
                            },
                            onFavorite     = { viewModel.toggleFavorite(it) },
                            onQueue        = { viewModel.addToQueue(it) },
                            modifier       = Modifier.weight(1f)
                        )

                    selectedTab == HomeTab.FAVORITOS ->
                        MusicListSection(
                            title        = "❤️  FAVORITOS",
                            musics       = favoriteMusics,
                            favorites    = favorites,
                            queueCodes   = queueCodes,
                            onCantar     = { code ->
                                val m = favoriteMusics.firstOrNull { it.code == code }
                                if (m != null) viewModel.onMusicPlayed(m, 86)
                                onNavigateToPlayer(code)
                            },
                            onFavorite   = { viewModel.toggleFavorite(it) },
                            onQueue      = { viewModel.addToQueue(it) },
                            emptyMessage = "Nenhuma música favoritada ainda.\nToque em ❤️ para adicionar!",
                            modifier     = Modifier.weight(1f)
                        )

                    selectedTab == HomeTab.HISTORICO ->
                        MusicListSection(
                            title        = "🕐  TOCADAS RECENTEMENTE",
                            musics       = historyMusics,
                            favorites    = favorites,
                            queueCodes   = queueCodes,
                            onCantar     = { code ->
                                val m = historyMusics.firstOrNull { it.code == code }
                                if (m != null) viewModel.onMusicPlayed(m, 86)
                                onNavigateToPlayer(code)
                            },
                            onFavorite   = { viewModel.toggleFavorite(it) },
                            onQueue      = { viewModel.addToQueue(it) },
                            emptyMessage = "Nenhuma música tocada ainda.\nComeçe a cantar!",
                            modifier     = Modifier.weight(1f)
                        )

                    selectedTab == HomeTab.FILA ->
                        QueueSection(
                            queueMusics    = queueMusics,
                            onStartQueue   = {
                                val next = viewModel.popNextInQueue()
                                if (next != null) onNavigateToPlayer(next)
                            },
                            onCantarItem   = { code ->
                                viewModel.removeFromQueue(code)
                                onNavigateToPlayer(code)
                            },
                            onRemoveItem   = { viewModel.removeFromQueue(it) },
                            onClearQueue   = { viewModel.clearQueue() },
                            modifier       = Modifier.weight(1f)
                        )
                }

                RemoteHint()
            }

            // ── 3. Painel Direito ────────────────────────────────────────
            RightPanel(lastMusic = lastMusic, lastScore = lastScore)
        }

        // ── 4. HUD Flutuante de Digitação de Código Rápido ───────────
        if (showQuickCodeHud) {
            QuickCodeHud(
                quickCode    = quickCode,
                matchedMusic = quickMatchedMusic,
                onCantar     = { code ->
                    viewModel.clearQuickCode()
                    onNavigateToPlayer(code)
                },
                onCancel     = { viewModel.clearQuickCode() },
                modifier     = Modifier.align(Alignment.Center)
            )
        }
    }   // fecha Root Box
}


// ─────────────────────────────────────────────────────────────────────────────
//  Sidebar
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarNavigation(
    selectedTab: HomeTab,
    queueCount: Int,
    onTabSelected: (HomeTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF130025), DiscoSidebar))
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Logo
        Text("🎤", fontSize = 36.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "KARAOKÊ",
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            color = NeonCyan,
            style = LocalTextStyle.current.copy(shadow = Shadow(NeonCyan, blurRadius = 12f))
        )
        Text(
            text = "DISCOTECA",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            color = Magenta,
            style = LocalTextStyle.current.copy(shadow = Shadow(Magenta, blurRadius = 12f))
        )

        Spacer(modifier = Modifier.height(24.dp))

        SidebarItem("🔍", "PESQUISAR",  null,           selectedTab == HomeTab.PESQUISAR)  { onTabSelected(HomeTab.PESQUISAR) }
        Spacer(Modifier.height(8.dp))
        SidebarItem("📋", "FILA",       if (queueCount > 0) "$queueCount na fila" else null, selectedTab == HomeTab.FILA) { onTabSelected(HomeTab.FILA) }
        Spacer(Modifier.height(8.dp))
        SidebarItem("🎙", "FILTROS",    "CANTORES",     selectedTab == HomeTab.FILTROS)    { onTabSelected(HomeTab.FILTROS) }
        Spacer(Modifier.height(8.dp))
        SidebarItem("❤", "FAVORITOS",  null,           selectedTab == HomeTab.FAVORITOS)  { onTabSelected(HomeTab.FAVORITOS) }
        Spacer(Modifier.height(8.dp))
        SidebarItem("🕐", "HISTÓRICO",  "MAIS RECENTES",selectedTab == HomeTab.HISTORICO) { onTabSelected(HomeTab.HISTORICO) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarItem(
    icon: String, label: String, subLabel: String?,
    selected: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected)
                    Brush.horizontalGradient(listOf(Purple, Color(0xFF6B00CC)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) Purple.copy(alpha = 0.7f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, fontSize = 18.sp)
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.75f))
            if (subLabel != null)
                Text(subLabel, fontSize = 10.sp, color = if (selected) DiscoGold else DiscoGreen, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Banner
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WelcomeBanner() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("SEJA BEM-VINDO!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Magenta,
                style = LocalTextStyle.current.copy(shadow = Shadow(Magenta, blurRadius = 8f)))
            Text("Pronto para brilhar?", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                style = LocalTextStyle.current.copy(shadow = Shadow(Purple, blurRadius = 10f)))
            Text("Digite o código ou pesquise pelo nome da música!",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Text("🪩", fontSize = 52.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Campo de Busca (texto livre com BasicTextField)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FFFFFF))
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(Purple.copy(alpha = 0.8f), Magenta.copy(alpha = 0.5f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🎵", fontSize = 18.sp)

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Magenta),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Código ou nome da música...",
                        fontSize = 15.sp, color = Color.White.copy(alpha = 0.38f))
                }
                inner()
            }
        )

        // Botão BUSCAR
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(Purple, Color(0xFF6B00CC))))
                .clickable { onSearch() }
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔍", fontSize = 13.sp)
                Text("BUSCAR", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Lista de Músicas (reutilizável para abas Pesquisar, Favoritos, Histórico)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MusicListSection(
    title: String,
    musics: List<MusicFile>,
    favorites: Set<String>,
    queueCodes: List<String>,
    onCantar: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onQueue: (String) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Cabeçalho da seção
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                style = LocalTextStyle.current.copy(shadow = Shadow(Gold, blurRadius = 5f)))
            Text("(${musics.size})", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        }

        if (musics.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center, lineHeight = 22.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(musics, key = { it.code }) { music ->
                    MusicCard(
                        music      = music,
                        isFavorite = favorites.contains(music.code),
                        isInQueue  = queueCodes.contains(music.code),
                        onCantar   = { onCantar(music.code) },
                        onFavorite = { onFavorite(music.code) },
                        onQueue    = { onQueue(music.code) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Aba FILA — Lista de reservas ordenada
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueSection(
    queueMusics: List<MusicFile>,
    onStartQueue: () -> Unit,
    onCantarItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Cabeçalho da Fila com ações
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📋  FILA DE RESERVAS", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    style = LocalTextStyle.current.copy(shadow = Shadow(DiscoGreen, blurRadius = 5f)))
                Text("(${queueMusics.size})", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            }

            if (queueMusics.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Botão Iniciar Fila
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(DiscoGreen, Color(0xFF00AA55))))
                            .clickable { onStartQueue() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("▶  INICIAR FILA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }

                    // Botão Limpar Fila
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FF0055))
                            .border(1.dp, NeonPink.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { onClearQueue() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🗑 Limpar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        if (queueMusics.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📋", fontSize = 40.sp)
                    Text("Nenhuma música na fila de reservas.",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                    Text("Toque em ➕ nas músicas do catálogo para montar a ordem da cantoria!",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(queueMusics, key = { index, music -> "${music.code}_$index" }) { index, music ->
                    QueueCard(
                        position    = index + 1,
                        music       = music,
                        onCantar    = { onCantarItem(music.code) },
                        onRemove    = { onRemoveItem(music.code) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card da Fila com número da posição
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueCard(
    position: Int,
    music: MusicFile,
    onCantar: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1E0040), Color(0xFF160030))))
            .border(1.dp, DiscoGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Posição na fila (1º, 2º, 3º...)
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (position == 1) Brush.verticalGradient(listOf(DiscoGreen, Color(0xFF00AA55))) else Brush.verticalGradient(listOf(Color(0xFF2A0060), Color(0xFF160030))))
                .border(1.dp, if (position == 1) DiscoGold else Purple.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${position}º",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (position == 1) Color.Black else Color.White
            )
        }

        // Código + Título + Artista
        Column(modifier = Modifier.weight(1f)) {
            Text(music.code, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Magenta,
                style = LocalTextStyle.current.copy(shadow = Shadow(Magenta, blurRadius = 5f)))
            Text(music.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(music.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.58f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Botão CANTAR AGORA
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(Purple, Color(0xFF6B00CC))))
                .clickable { onCantar() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("▶", fontSize = 10.sp)
                Text("CANTAR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        // Botão Remover da Fila
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Text("❌", fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Aba FILTROS — Grid de cantores
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterSection(
    artistList: List<String>,
    selectedArtist: String?,
    artistMusics: List<MusicFile>,
    favorites: Set<String>,
    queueCodes: List<String>,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
    onCantar: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onQueue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (selectedArtist == null) {
            // Grid de cantores
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)) {
                Text("🎙  CANTORES", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("(${artistList.size})", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            }

            if (artistList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhum cantor encontrado.\nConecte o HD com as músicas.",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f), textAlign = TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(artistList) { artist ->
                        ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                    }
                }
            }
        } else {
            // Lista de músicas do cantor selecionado
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("← Cantores", fontSize = 12.sp, color = Magenta, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("🎙  $selectedArtist", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Text("(${artistMusics.size})", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(artistMusics, key = { it.code }) { music ->
                    MusicCard(
                        music      = music,
                        isFavorite = favorites.contains(music.code),
                        isInQueue  = queueCodes.contains(music.code),
                        onCantar   = { onCantar(music.code) },
                        onFavorite = { onFavorite(music.code) },
                        onQueue    = { onQueue(music.code) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card de Cantor
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArtistCard(artist: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A0060), Color(0xFF160030))))
            .border(1.dp, Purple.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎤", fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(artist, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card de Música com botão [➕ Reservar]
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MusicCard(
    music: MusicFile,
    isFavorite: Boolean,
    isInQueue: Boolean,
    onCantar: () -> Unit,
    onFavorite: () -> Unit,
    onQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1E0040), Color(0xFF160030))))
            .border(1.dp, Purple.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Avatar com inicial do artista
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Purple.copy(0.65f), Color(0xFF2A0060)))),
            contentAlignment = Alignment.Center
        ) {
            Text(music.artist.firstOrNull()?.uppercase() ?: "?",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }

        // Código + Título + Artista
        Column(modifier = Modifier.weight(1f)) {
            Text(music.code, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Magenta,
                style = LocalTextStyle.current.copy(shadow = Shadow(Magenta, blurRadius = 5f)))
            Text(music.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(music.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.58f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Botão CANTAR
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(Purple, Color(0xFF6B00CC))))
                .clickable { onCantar() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🎵", fontSize = 11.sp)
                Text("CANTAR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        // Botão RESERVAR NA FILA
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isInQueue) Color(0x3300FF88) else Color(0x22FFFFFF))
                .border(1.dp, if (isInQueue) DiscoGreen else Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .clickable { onQueue() }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isInQueue) "✔" else "➕", fontSize = 11.sp)
                Text(if (isInQueue) "NA FILA" else "RESERVAR", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = if (isInQueue) DiscoGreen else Color.White)
            }
        }

        // Botão Favorito
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp,
                    if (isFavorite) Pink else Color.White.copy(alpha = 0.25f),
                    RoundedCornerShape(8.dp))
                .clickable { onFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Text(if (isFavorite) "❤️" else "🤍", fontSize = 15.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Painel Direito
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RightPanel(lastMusic: MusicFile?, lastScore: Int) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .padding(end = 16.dp, top = 16.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card "Escolha sua Música"
        Box(
            modifier = Modifier
                .fillMaxWidth().height(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A0060), Color(0xFF160030))))
                .border(1.5.dp, Purple.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart).padding(16.dp)) {
                Text("ESCOLHA", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Magenta,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Magenta, blurRadius = 10f)))
                Text("SUA MÚSICA", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Digite o código\ne comece a cantar!", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Text("🎤", fontSize = 46.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        }

        // Card "Última Música"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E003A), Color(0xFF130028))))
                .border(1.dp, Purple.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎵", fontSize = 13.sp)
                    Text("ÚLTIMA MÚSICA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Magenta)
                }
                Spacer(Modifier.height(10.dp))
                if (lastMusic != null) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(Purple.copy(0.7f), Color(0xFF2A0060)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(lastMusic.artist.firstOrNull()?.uppercase() ?: "?",
                                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lastMusic.title, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(lastMusic.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.58f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ScoreCircle(lastScore)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("ÓTIMA PERFORMANCE!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green)
                } else {
                    Text("Nenhuma música\ntocada ainda.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                }
            }
        }

        // Card "Dica da Discoteca"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0030), Color(0xFF0F0020))))
                .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⭐", fontSize = 13.sp)
                    Text("DICA DA DISCOTECA", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Gold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Aqueça sua voz, escolha sua música favorita e divirta-se!",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 18.sp)
                Text("🎶", fontSize = 26.sp, modifier = Modifier.align(Alignment.End))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Círculo de Pontuação
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ScoreCircle(score: Int) {
    val color = when {
        score >= 85 -> Green
        score >= 70 -> Gold
        else        -> Pink
    }
    Box(
        modifier = Modifier.size(50.dp).clip(CircleShape)
            .background(Color(0xFF0A0015)).border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text("pts", fontSize = 7.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Estados de Loading / Erro
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("💿", fontSize = 48.sp)
            Text("Lendo músicas do HD...", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium)
            Text("Aguarde um momento", fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📀", fontSize = 48.sp)
            Text(message, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, lineHeight = 22.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Purple, Color(0xFF6B00CC))))
                    .clickable { onRetry() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("🔄  TENTAR NOVAMENTE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rodapé — Dica do controle remoto
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RemoteHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0x44000000))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(30.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📱", fontSize = 13.sp)
                Text("Use o controle remoto para navegar", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RemoteHintChip("OK", "SELECIONAR")
                RemoteHintChip("←", "VOLTAR")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RemoteHintChip(prefix: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(prefix, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
        }
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HUD Flutuante de Digitação Direta por Controle Remoto (0-9)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QuickCodeHud(
    quickCode: String,
    matchedMusic: MusicFile?,
    onCantar: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.55f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF51A0035),
                        Color(0xF50D001A)
                    )
                )
            )
            .border(2.dp, NeonMagenta, RoundedCornerShape(24.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabeçalho
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔢", fontSize = 20.sp)
                Text(
                    text = "DIGITAÇÃO RÁPIDA DE CÓDIGO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan,
                    style = LocalTextStyle.current.copy(shadow = Shadow(NeonCyan, blurRadius = 8f))
                )
            }

            // 5 Caixas de Dígitos
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 5) {
                    val char = quickCode.getOrNull(i)
                    val isFilled = char != null
                    val isCurrent = i == quickCode.length

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isFilled) Color(0xFF2A0055)
                                else if (isCurrent) Color(0xFF1E003A)
                                else Color(0x33000000)
                            )
                            .border(
                                width = if (isFilled) 2.dp else if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isFilled) NeonMagenta else if (isCurrent) NeonCyan else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char?.toString() ?: if (isCurrent) "_" else "•",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isFilled) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // Informação da Música Encontrada
            if (matchedMusic != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x3300FF88))
                        .border(1.dp, DiscoGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🎵 ${matchedMusic.title.uppercase()}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "🎤 ${matchedMusic.artist.uppercase()}",
                            fontSize = 12.sp,
                            color = DiscoGreen,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (quickCode.length == 5) {
                Text(
                    text = "⚠️ Nenhuma música com este código no catálogo",
                    fontSize = 12.sp,
                    color = NeonPink,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Digite os 5 dígitos no controle remoto",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Atalhos no Rodapé do Modal
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (matchedMusic != null || quickCode.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.horizontalGradient(listOf(Purple, Magenta)))
                            .clickable {
                                val code = matchedMusic?.code ?: quickCode.padStart(5, '0')
                                onCantar(code)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("▶  CANTAR AGORA (OK)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable { onCancel() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("✖  CANCELAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}
