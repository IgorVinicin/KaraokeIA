@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.home

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.UserScoreEntry
import com.araujo.karaoke_ia.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun HomeScreen(
    onNavigateToPlayer: (String) -> Unit,
    viewModel: HomeViewModel
) {
    val searchQuery         by viewModel.searchQuery
    val selectedTab         by viewModel.selectedTab
    val isLoading           by viewModel.isLoading
    val loadError           by viewModel.loadError
    val allMusics           by viewModel.allMusics
    val filteredMusics      by viewModel.filteredMusics
    val selectedSearchMusic by viewModel.selectedSearchMusic
    val favorites           by viewModel.favorites
    val favoriteMusics      by viewModel.favoriteMusics
    val historyMusics       by viewModel.historyMusics
    val queueCodes          by viewModel.queueCodes
    val queueMusics         by viewModel.queueMusics
    val myBestScores        by viewModel.myBestScores
    val artistList          by viewModel.artistList
    val selectedArtist      by viewModel.selectedArtist
    val artistMusics        by viewModel.artistMusics

    val quickCode           by viewModel.quickCode
    val quickMatchedMusic   by viewModel.quickMatchedMusic
    val showQuickCodeHud    by viewModel.showQuickCodeHud

    val initialTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { initialTabFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(quickCode, showQuickCodeHud) {
        if (showQuickCodeHud) {
            delay(6000)
            viewModel.clearQuickCode()
        }
    }

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
            .drawBehind {
                // Fundo escuro luxuoso otimizado (0 alocação de bitmap para fluidez máxima)
                drawRect(color = Color(0xFF090A0D))

                // Luz ambiente dourada suave no topo direito (estilo palco / bokeh)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x30F3C768), Color(0x10A87A28), Color.Transparent),
                        center = Offset(size.width * 0.75f, size.height * 0.2f),
                        radius = size.width * 0.45f
                    )
                )

                // Vinheta inferior
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x99000000), Color(0xEE090A0D))
                    )
                )
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode

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

                    if (keyCode == android.view.KeyEvent.KEYCODE_DEL && showQuickCodeHud) {
                        viewModel.deleteQuickDigit()
                        return@onKeyEvent true
                    }

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

                    if ((keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                         keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) && showQuickCodeHud) {
                        viewModel.clearQuickCode()
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 18.dp)
        ) {
            // ── 1. Top Header ───────────────────────────────────────────────
            HeroHeaderSection(
                onCantarAgora = {
                    val codeToPlay = queueCodes.firstOrNull() ?: allMusics.firstOrNull()?.code ?: "01007"
                    onNavigateToPlayer(codeToPlay)
                },
                onGoHome = { viewModel.onTabSelected(HomeTab.INICIO) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 2. Barra de Navegação em Pílulas ─────────────────────────────
            PillNavigationBar(
                selectedTab = selectedTab,
                queueCount = queueCodes.size,
                initialTabFocusRequester = initialTabFocusRequester,
                onTabSelected = { viewModel.onTabSelected(it) },
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    viewModel.onSearchQueryChange(it)
                    if (selectedTab != HomeTab.PESQUISAR) {
                        viewModel.onTabSelected(HomeTab.PESQUISAR)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── 3. Visualização da Aba ───────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = GoldPrimary)
                        }
                    }

                    loadError != null && allMusics.isEmpty() -> {
                        ErrorView(
                            message = loadError!!,
                            onRetry = { viewModel.loadAllMusics() }
                        )
                    }

                    selectedTab == HomeTab.INICIO -> {
                        HomeDashboardView(
                            musics = allMusics,
                            recentMusics = if (historyMusics.isNotEmpty()) historyMusics else allMusics,
                            queueCount = queueCodes.size,
                            myBestScores = myBestScores,
                            onMusicClick = { onNavigateToPlayer(it.code) },
                            onGoToQueue = { viewModel.onTabSelected(HomeTab.FILA) },
                            onGoToRanking = { viewModel.onTabSelected(HomeTab.RANKING) }
                        )
                    }

                    selectedTab == HomeTab.MUSICAS -> {
                        CatalogView(
                            musics = if (selectedArtist != null) artistMusics else allMusics,
                            artistList = artistList,
                            selectedArtist = selectedArtist,
                            onArtistSelect = { viewModel.onArtistSelected(it) },
                            onArtistClear = { viewModel.onArtistCleared() },
                            onCantar = { onNavigateToPlayer(it) }
                        )
                    }

                    selectedTab == HomeTab.PESQUISAR -> {
                        SongSearchView(
                            query = searchQuery,
                            onQueryChange = { viewModel.onSearchQueryChange(it) },
                            results = filteredMusics,
                            featuredMusic = selectedSearchMusic ?: filteredMusics.firstOrNull(),
                            favorites = favorites,
                            queueCodes = queueCodes,
                            onSelectMusic = { viewModel.selectSearchMusic(it) },
                            onCantar = { onNavigateToPlayer(it) },
                            onQueue = { viewModel.addToQueue(it) },
                            onFavorite = { viewModel.toggleFavorite(it) }
                        )
                    }

                    selectedTab == HomeTab.FILA -> {
                        QueueScreenView(
                            queueMusics = queueMusics,
                            allMusics = allMusics,
                            onCantar = { onNavigateToPlayer(it) },
                            onRemove = { viewModel.removeFromQueue(it) },
                            onClearQueue = { viewModel.clearQueue() }
                        )
                    }

                    selectedTab == HomeTab.RANKING -> {
                        MyScoresScreenView(
                            myScores = myBestScores,
                            onCantar = { code -> onNavigateToPlayer(code) }
                        )
                    }

                    selectedTab == HomeTab.FAVORITOS -> {
                        FavoritesScreenView(
                            favorites = favoriteMusics,
                            queueCodes = queueCodes,
                            onCantar = { onNavigateToPlayer(it) },
                            onQueue = { viewModel.addToQueue(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                    }
                }
            }
        }

        // ── 4. HUD Flutuante de Digitação Direta ────────────────────────────
        AnimatedVisibility(
            visible = showQuickCodeHud,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) {
            QuickCodeHudView(
                code = quickCode,
                matchedMusic = quickMatchedMusic
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. HERO HEADER SECTION
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeroHeaderSection(
    onCantarAgora: () -> Unit,
    onGoHome: () -> Unit
) {
    val playInteraction = remember { MutableInteractionSource() }
    val isPlayFocused by playInteraction.collectIsFocusedAsState()

    val homeInteraction = remember { MutableInteractionSource() }
    val isHomeFocused by homeInteraction.collectIsFocusedAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isHomeFocused) GoldPrimary else Color(0x33FFFFFF))
                    .border(1.dp, if (isHomeFocused) GoldGlow else Color(0x33FFFFFF), CircleShape)
                    .focusable(interactionSource = homeInteraction)
                    .clickable(interactionSource = homeInteraction, indication = null) { onGoHome() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Início",
                    tint = if (isHomeFocused) DarkCharcoalBg else TextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (isPlayFocused) GoldBadgeBg else Color.Transparent)
                    .border(if (isPlayFocused) 2.5.dp else 0.dp, GoldPrimary, RoundedCornerShape(30.dp))
                    .focusable(interactionSource = playInteraction)
                    .clickable(interactionSource = playInteraction, indication = null) { onCantarAgora() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Cantar agora",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPlayFocused) GoldPrimary else TextWhite
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(GoldGlow, GoldAccent)))
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Tocar",
                        tint = DarkCharcoalBg,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0x33261D12))
                .border(1.5.dp, GoldBadgeBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Meu Perfil",
                tint = GoldPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. PILL NAVIGATION BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PillNavigationBar(
    selectedTab: HomeTab,
    queueCount: Int,
    initialTabFocusRequester: FocusRequester,
    onTabSelected: (HomeTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0x44141722))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(32.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillTabItem(
                label = "Início",
                icon = Icons.Default.Home,
                isSelected = selectedTab == HomeTab.INICIO,
                focusRequester = initialTabFocusRequester,
                onClick = { onTabSelected(HomeTab.INICIO) }
            )

            PillTabItem(
                label = "Música",
                icon = Icons.Default.MusicNote,
                isSelected = selectedTab == HomeTab.MUSICAS,
                onClick = { onTabSelected(HomeTab.MUSICAS) }
            )

            PillTabItem(
                label = "Pesquisar",
                icon = Icons.Default.Search,
                isSelected = selectedTab == HomeTab.PESQUISAR,
                onClick = { onTabSelected(HomeTab.PESQUISAR) }
            )

            PillTabItem(
                label = "Fila",
                icon = Icons.Default.QueueMusic,
                badgeCount = queueCount,
                isSelected = selectedTab == HomeTab.FILA,
                onClick = { onTabSelected(HomeTab.FILA) }
            )

            PillTabItem(
                label = "Pontuações",
                icon = Icons.Default.Leaderboard,
                isSelected = selectedTab == HomeTab.RANKING,
                onClick = { onTabSelected(HomeTab.RANKING) }
            )

            PillTabItem(
                label = "Favoritos",
                icon = Icons.Default.Favorite,
                isSelected = selectedTab == HomeTab.FAVORITOS,
                onClick = { onTabSelected(HomeTab.FAVORITOS) }
            )
        }

        QuickSearchPill(
            query = searchQuery,
            onQueryChange = onSearchQueryChange
        )
    }
}

@Composable
private fun PillTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badgeCount: Int = 0,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgBrush = remember(isFocused, isSelected) {
        when {
            isFocused  -> Brush.horizontalGradient(listOf(GoldAccent, GoldPrimary))
            isSelected -> Brush.horizontalGradient(listOf(Color(0x33F3C768), Color(0x1AF3C768)))
            else       -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
        }
    }

    val contentColor = when {
        isFocused  -> DarkCharcoalBg
        isSelected -> GoldPrimary
        else       -> TextMuted
    }

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(bgBrush)
            .border(if (isSelected || isFocused) 1.5.dp else 0.dp, if (isFocused) GoldGlow else GoldPrimary, RoundedCornerShape(24.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.Medium,
            color = contentColor
        )

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isFocused) DarkCharcoalBg else GoldAccent)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.toString(),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) GoldPrimary else TextWhite
                )
            }
        }
    }
}

@Composable
private fun QuickSearchPill(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x33141722))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) GoldPrimary else Color(0x22FFFFFF),
                shape = RoundedCornerShape(24.dp)
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Buscar",
            tint = if (isFocused) GoldPrimary else TextSubtle,
            modifier = Modifier.size(15.dp)
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TextWhite,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(GoldPrimary),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar música ou código...",
                        fontSize = 12.5.sp,
                        color = TextSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            },
            modifier = Modifier.weight(1f)
        )

        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Limpar",
                tint = TextMuted,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  3. HOME DASHBOARD VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeDashboardView(
    musics: List<MusicFile>,
    recentMusics: List<MusicFile>,
    queueCount: Int,
    myBestScores: List<UserScoreEntry>,
    onMusicClick: (MusicFile) -> Unit,
    onGoToQueue: () -> Unit,
    onGoToRanking: () -> Unit
) {
    val dashboardMusics = remember(musics) { musics.take(12) }
    val dashboardRecentMusics = remember(recentMusics) { recentMusics.take(12) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Coluna Esquerda: Músicas Populares & Tocadas Recentemente
        Column(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Músicas Populares")

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 2.dp)
                ) {
                    items(dashboardMusics, key = { it.code }) { music ->
                        SongCardItem(
                            music = music,
                            onClick = { onMusicClick(music) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Tocadas Recentemente")

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 2.dp)
                ) {
                    items(dashboardRecentMusics, key = { "recent_${it.code}" }) { music ->
                        SongCardItem(
                            music = music,
                            onClick = { onMusicClick(music) }
                        )
                    }
                }
            }
        }

        // Coluna Direita: Minha Fila e Minhas Melhores Pontuações
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(title = "Minha Fila")
            QueueSummaryWidget(
                queueCount = queueCount,
                onClick = onGoToQueue
            )

            SectionHeader(title = "Minhas Melhores Pontuações")
            MyScoresSummaryWidget(
                scores = myBestScores.take(4),
                onClick = onGoToRanking
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = TextWhite,
        modifier = Modifier.padding(start = 2.dp)
    )
}

private const val MUSIC_PAGE_SIZE = 24

@Composable
private fun PaginationControls(
    page: Int,
    totalItems: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val pageCount = (totalItems + MUSIC_PAGE_SIZE - 1) / MUSIC_PAGE_SIZE
    if (pageCount <= 1) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaginationButton("Anterior", page > 0, onPrevious)
        Text(
            text = "Página ${page + 1} de $pageCount",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        PaginationButton("Próxima", page < pageCount - 1, onNext)
    }
}

@Composable
private fun PaginationButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Color(0x44382C1A) else Color(0x22141722))
            .border(1.dp, if (enabled) GoldBadgeBorder else Color(0x22141722), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) GoldPrimary else TextSubtle, fontSize = 11.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  4. SONG CARD ITEM
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SongCardItem(
    music: MusicFile,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(135.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCardSurface)
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) GoldPrimary else DarkCardBorder,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            SongAlbumArt(
                code = music.code,
                title = music.title,
                artist = music.artist,
                modifier = Modifier.fillMaxSize()
            )

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x33000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(GoldGlow, GoldPrimary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Cantar",
                            tint = DarkCharcoalBg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${music.code} | ${music.title}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) GoldPrimary else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = music.artist,
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  5. SONG ALBUM ART
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SongAlbumArt(
    code: String,
    title: String,
    artist: String,
    modifier: Modifier = Modifier
) {
    val gradientColors = remember(code) {
        val hash = (code.hashCode() + artist.hashCode()).let { if (it < 0) -it else it }
        when (hash % 5) {
            0 -> listOf(Color(0xFF382314), Color(0xFF1E130A))
            1 -> listOf(Color(0xFF1A2234), Color(0xFF0F1420))
            2 -> listOf(Color(0xFF321B2B), Color(0xFF1B0E17))
            3 -> listOf(Color(0xFF1B2F2A), Color(0xFF0D1916))
            else -> listOf(Color(0xFF2B2214), Color(0xFF161108))
        }
    }

    Box(
        modifier = modifier.background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(85.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0x1FFFFFFF), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0x2AFFFFFF), CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (title.isNotBlank()) title.take(1).uppercase() else "K",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = GoldPrimary.copy(alpha = 0.9f)
            )
            Text(
                text = code,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite.copy(alpha = 0.8f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  6. WIDGETS DA COLUNA DIREITA
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QueueSummaryWidget(
    queueCount: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    if (isFocused) listOf(BronzeGradient1, Color(0xFF4A381E))
                    else listOf(Color(0xFF261D12), Color(0xFF17110A))
                )
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) GoldPrimary else BronzeBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (queueCount > 0) "${queueCount.toString().padStart(2, '0')}" else "00",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary
                )

                Column {
                    Text(
                        text = "MÚSICAS NA FILA",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldAccent
                    )
                    Text(
                        text = if (queueCount > 0) "Pronto para tocar" else "Fila vazia",
                        fontSize = 11.5.sp,
                        color = TextMuted
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Ver fila",
                tint = GoldPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MyScoresSummaryWidget(
    scores: List<UserScoreEntry>,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33141722))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) GoldPrimary else Color(0x22FFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (scores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma pontuação gravada ainda.\nCante para registrar suas notas!",
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    color = TextMuted
                )
            }
        } else {
            scores.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (index < 3 && entry.photoPath != null) {
                            val decoded = remember(entry.photoPath) { BitmapFactory.decodeFile(entry.photoPath) }
                            if (decoded != null) {
                                Image(
                                    bitmap = decoded.asImageBitmap(),
                                    contentDescription = "Foto do cantor",
                                    modifier = Modifier.size(34.dp).clip(CircleShape)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (index == 0) GoldBadgeBg else DarkCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}º",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0) GoldPrimary else TextMuted
                            )
                        }

                        Column {
                            Text(
                                text = "${entry.score} pts • ${entry.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.artist,
                                fontSize = 10.sp,
                                color = TextSubtle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  7. SONG SEARCH VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SongSearchView(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MusicFile>,
    featuredMusic: MusicFile?,
    favorites: Set<String>,
    queueCodes: List<String>,
    onSelectMusic: (MusicFile) -> Unit,
    onCantar: (String) -> Unit,
    onQueue: (String) -> Unit,
    onFavorite: (String) -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(results) { page = 0 }
    val pageResults = remember(results, page) {
        results.drop(page * MUSIC_PAGE_SIZE).take(MUSIC_PAGE_SIZE)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFFFFF))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(20.dp)
                )

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF111111),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(GoldPrimary),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Buscar Música, Artista ou Código...",
                                fontSize = 14.5.sp,
                                color = Color(0xFF888888)
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (featuredMusic != null) {
            FeaturedSearchCard(
                music = featuredMusic,
                isInQueue = queueCodes.contains(featuredMusic.code),
                onCantar = { onCantar(featuredMusic.code) },
                onQueue = { onQueue(featuredMusic.code) }
            )
        }

        Text(
            text = "Resultados (${results.size})",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(pageResults, key = { it.code }) { music ->
                SearchResultItem(
                    music = music,
                    isSelected = featuredMusic?.code == music.code,
                    onSelect = { onSelectMusic(music) },
                    onCantar = { onCantar(music.code) },
                    onQueue = { onQueue(music.code) }
                )
            }
        }

        PaginationControls(
            page = page,
            totalItems = results.size,
            onPrevious = { page = (page - 1).coerceAtLeast(0) },
            onNext = {
                page = (page + 1).coerceAtMost((results.size - 1) / MUSIC_PAGE_SIZE)
            }
        )
    }
}

@Composable
private fun FeaturedSearchCard(
    music: MusicFile,
    isInQueue: Boolean,
    onCantar: () -> Unit,
    onQueue: () -> Unit
) {
    val cantarInteraction = remember { MutableInteractionSource() }
    val isCantarFocused by cantarInteraction.collectIsFocusedAsState()

    val queueInteraction = remember { MutableInteractionSource() }
    val isQueueFocused by queueInteraction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2C2214), Color(0xFF1A140B))
                )
            )
            .border(1.5.dp, GoldBadgeBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkCardSurface)
                ) {
                    SongAlbumArt(
                        code = music.code,
                        title = music.title,
                        artist = music.artist,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = music.code,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary
                )

                Column {
                    Text(
                        text = music.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextWhite
                    )
                    Text(
                        text = music.artist,
                        fontSize = 12.5.sp,
                        color = TextMuted
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isQueueFocused) Brush.horizontalGradient(listOf(GoldAccent, GoldPrimary))
                            else Brush.horizontalGradient(listOf(Color(0xFF4A381E), Color(0xFF2C2214)))
                        )
                        .border(
                            width = if (isQueueFocused) 2.dp else 1.dp,
                            color = if (isQueueFocused) GoldGlow else BronzeBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .focusable(interactionSource = queueInteraction)
                        .clickable(interactionSource = queueInteraction, indication = null) { onQueue() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isInQueue) "Na Fila ✓" else "Adicionar à Fila",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isQueueFocused) DarkCharcoalBg else TextWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCantarFocused) Brush.horizontalGradient(listOf(GoldGlow, GoldPrimary))
                            else Brush.horizontalGradient(listOf(GoldPrimary, GoldAccent))
                        )
                        .border(
                            width = if (isCantarFocused) 2.dp else 0.dp,
                            color = GoldGlow,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .focusable(interactionSource = cantarInteraction)
                        .clickable(interactionSource = cantarInteraction, indication = null) { onCantar() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DarkCharcoalBg,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Cantar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkCharcoalBg
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    music: MusicFile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onCantar: () -> Unit,
    onQueue: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) Color(0xFF222838)
                else if (isSelected) Color(0xFF191F2C)
                else Color(0x22141722)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) GoldPrimary else Color(0x1AFFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onSelect() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                SongAlbumArt(
                    code = music.code,
                    title = music.title,
                    artist = music.artist,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = music.code,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GoldPrimary
            )

            Column {
                Text(
                    text = music.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = music.artist,
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick = onQueue,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Adicionar à Fila",
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onCantar,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Cantar",
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  8. QUEUE SCREEN VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QueueScreenView(
    queueMusics: List<MusicFile>,
    allMusics: List<MusicFile>,
    onCantar: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearQueue: () -> Unit
) {
    val currentMusic = queueMusics.firstOrNull() ?: allMusics.firstOrNull()
    val nextMusic = if (queueMusics.size > 1) queueMusics[1] else null
    val upcomingMusics = if (queueMusics.size > 2) queueMusics.drop(2) else emptyList()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fila de Reprodução (${queueMusics.size})",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite
            )

            if (queueMusics.isNotEmpty()) {
                Text(
                    text = "Limpar Fila",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeartRed,
                    modifier = Modifier
                        .clickable { onClearQueue() }
                        .padding(6.dp)
                )
            }
        }

        if (currentMusic != null) {
            Text("MÚSICA ATUAL", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = GoldPrimary)
            FeaturedSearchCard(
                music = currentMusic,
                isInQueue = true,
                onCantar = { onCantar(currentMusic.code) },
                onQueue = {}
            )
        }

        if (nextMusic != null) {
            Text("PRÓXIMA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = GoldAccent)
            SearchResultItem(
                music = nextMusic,
                isSelected = false,
                onSelect = {},
                onCantar = { onCantar(nextMusic.code) },
                onQueue = { onRemove(nextMusic.code) }
            )
        }

        if (upcomingMusics.isNotEmpty()) {
            Text("EM BREVE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(upcomingMusics, key = { _, it -> "queue_${it.code}" }) { index, music ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33141722))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${index + 3}º",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                            Text(
                                text = "${music.code} | ${music.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "• ${music.artist}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        IconButton(
                            onClick = { onRemove(music.code) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Remover",
                                tint = HeartRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        } else if (queueMusics.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nenhuma música na fila.\nAdicione músicas pelo catálogo ou busca!",
                        textAlign = TextAlign.Center,
                        color = TextMuted,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  9. MY SCORES SCREEN VIEW (Minhas Pontuações Reais do Usuário)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MyScoresScreenView(
    myScores: List<UserScoreEntry>,
    onCantar: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Minhas Melhores Pontuações",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextWhite
        )

        if (myScores.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Você ainda não cantou nenhuma música.\nSuas notas gravadas aparecerão no pódio aqui!",
                        textAlign = TextAlign.Center,
                        color = TextMuted,
                        fontSize = 13.5.sp
                    )
                }
            }
        } else {
            // Pódio Top 3 das Melhores Notas do Usuário
            if (myScores.size >= 3) {
                val first = myScores[0]
                val second = myScores[1]
                val third = myScores[2]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    PodiumCard(
                        entry = second,
                        place = "2º",
                        bgColor = Color(0xFF282C38),
                        borderColor = Color(0xFF7A839E),
                        modifier = Modifier.weight(1f).height(115.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    PodiumCard(
                        entry = first,
                        place = "1º 👑",
                        bgColor = Color(0xFF3D2E17),
                        borderColor = GoldPrimary,
                        isFirst = true,
                        modifier = Modifier.weight(1.15f).height(135.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    PodiumCard(
                        entry = third,
                        place = "3º",
                        bgColor = Color(0xFF251E18),
                        borderColor = Color(0xFF9E6B4A),
                        modifier = Modifier.weight(1f).height(100.dp)
                    )
                }
            }

            // Tabela com Histórico das Notas
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(myScores, key = { _, it -> it.id }) { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (index == 0) Color(0x33F3C768) else Color(0x33141722))
                            .border(
                                width = 1.dp,
                                color = if (index == 0) GoldBadgeBorder else Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${index + 1}º",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0) GoldPrimary else TextMuted
                            )

                            Text(
                                text = "${entry.title} - ${entry.artist}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }

                        Text(
                            text = "${entry.score} pts",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (index == 0) GoldPrimary else TextOffWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumCard(
    entry: UserScoreEntry,
    place: String,
    bgColor: Color,
    borderColor: Color,
    isFirst: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(bgColor)
            .border(
                width = if (isFirst) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = place,
                fontSize = if (isFirst) 14.sp else 12.sp,
                fontWeight = FontWeight.Black,
                color = if (isFirst) GoldPrimary else TextWhite
            )

            if (entry.photoPath != null) {
                val decoded = remember(entry.photoPath) { BitmapFactory.decodeFile(entry.photoPath) }
                if (decoded != null) {
                    Image(
                        bitmap = decoded.asImageBitmap(),
                        contentDescription = "Foto do cantor",
                        modifier = Modifier.size(if (isFirst) 42.dp else 32.dp).clip(CircleShape)
                    )
                }
            }

            Text(
                text = "${entry.score} pts",
                fontSize = if (isFirst) 15.sp else 12.5.sp,
                fontWeight = FontWeight.Black,
                color = if (isFirst) GoldPrimary else TextWhite
            )

            Text(
                text = entry.title,
                fontSize = 10.5.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  10. FAVORITES SCREEN VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FavoritesScreenView(
    favorites: List<MusicFile>,
    queueCodes: List<String>,
    onCantar: (String) -> Unit,
    onQueue: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(favorites) { page = 0 }
    val pageFavorites = remember(favorites, page) {
        favorites.drop(page * MUSIC_PAGE_SIZE).take(MUSIC_PAGE_SIZE)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Favoritos (${favorites.size})",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextWhite
        )

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Você ainda não favoritou nenhuma música.\nClique no coração nas músicas para salvá-las aqui!",
                        textAlign = TextAlign.Center,
                        color = TextMuted,
                        fontSize = 12.5.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(pageFavorites, key = { it.code }) { music ->
                    FavoriteGridCard(
                        music = music,
                        isInQueue = queueCodes.contains(music.code),
                        onCantar = { onCantar(music.code) },
                        onQueue = { onQueue(music.code) },
                        onRemoveFavorite = { onToggleFavorite(music.code) }
                    )
                }
            }
            PaginationControls(
                page = page,
                totalItems = favorites.size,
                onPrevious = { page = (page - 1).coerceAtLeast(0) },
                onNext = {
                    page = (page + 1).coerceAtMost((favorites.size - 1) / MUSIC_PAGE_SIZE)
                }
            )
        }
    }
}

@Composable
private fun FavoriteGridCard(
    music: MusicFile,
    isInQueue: Boolean,
    onCantar: () -> Unit,
    onQueue: () -> Unit,
    onRemoveFavorite: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCardSurface)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) GoldPrimary else DarkCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .padding(9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                SongAlbumArt(
                    code = music.code,
                    title = music.title,
                    artist = music.artist,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0x88000000))
                        .clickable { onRemoveFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorito",
                        tint = HeartRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "${music.code} | ${music.title}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) GoldPrimary else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = music.artist,
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isFocused) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GoldPrimary)
                            .clickable { onCantar() }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cantar", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = DarkCharcoalBg)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF382C1A))
                            .clickable { onQueue() }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isInQueue) "Na Fila" else "+ Fila", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  11. CATALOG VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CatalogView(
    musics: List<MusicFile>,
    artistList: List<String>,
    selectedArtist: String?,
    onArtistSelect: (String) -> Unit,
    onArtistClear: () -> Unit,
    onCantar: (String) -> Unit
) {
    var page by remember(selectedArtist) { mutableIntStateOf(0) }
    val pageMusics = remember(musics, page) {
        musics.drop(page * MUSIC_PAGE_SIZE).take(MUSIC_PAGE_SIZE)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                ArtistChip(
                    name = "Todos os Artistas",
                    isSelected = selectedArtist == null,
                    onClick = onArtistClear
                )
            }

            items(artistList, key = { it }) { artist ->
                ArtistChip(
                    name = artist,
                    isSelected = selectedArtist == artist,
                    onClick = { onArtistSelect(artist) }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(pageMusics, key = { it.code }) { music ->
                SongCardItem(
                    music = music,
                    onClick = { onCantar(music.code) }
                )
            }
        }
        PaginationControls(
            page = page,
            totalItems = musics.size,
            onPrevious = { page = (page - 1).coerceAtLeast(0) },
            onNext = {
                page = (page + 1).coerceAtMost((musics.size - 1) / MUSIC_PAGE_SIZE)
            }
        )
    }
}

@Composable
private fun ArtistChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isFocused) GoldPrimary
                else if (isSelected) Color(0x44F3C768)
                else Color(0x33141722)
            )
            .border(
                width = if (isFocused || isSelected) 1.5.dp else 1.dp,
                color = if (isFocused || isSelected) GoldPrimary else Color(0x22FFFFFF),
                shape = RoundedCornerShape(20.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 13.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isFocused) DarkCharcoalBg else if (isSelected) GoldPrimary else TextWhite
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  12. QUICK CODE HUD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QuickCodeHudView(
    code: String,
    matchedMusic: MusicFile?
) {
    Box(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF22A2012), Color(0xF2140F08))
                )
            )
            .border(2.dp, GoldPrimary, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DIGITE O CÓDIGO DA MÚSICA",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GoldAccent
            )

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                for (i in 0 until 5) {
                    val digit = code.getOrNull(i)?.toString() ?: "_"
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x44000000))
                            .border(1.dp, if (i < code.length) GoldPrimary else Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = if (i < code.length) GoldPrimary else TextSubtle
                        )
                    }
                }
            }

            if (matchedMusic != null) {
                Text(
                    text = "${matchedMusic.title} • ${matchedMusic.artist}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (code.length >= 4) {
                Text(
                    text = "Nenhuma música com este código",
                    fontSize = 11.5.sp,
                    color = HeartRed
                )
            }

            Text(
                text = "Pressione OK para cantar ou Voltar para cancelar",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  13. ERROR VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(40.dp)
            )

            Text(
                text = message,
                textAlign = TextAlign.Center,
                fontSize = 14.5.sp,
                color = TextWhite
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GoldPrimary)
                    .clickable { onRetry() }
                    .padding(horizontal = 22.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "Tentar Novamente",
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalBg
                )
            }
        }
    }
}
