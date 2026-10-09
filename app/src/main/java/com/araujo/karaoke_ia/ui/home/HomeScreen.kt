@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import com.araujo.karaoke_ia.ui.components.stageBackground
import com.araujo.karaoke_ia.ui.components.tvFocusFeedback
import com.araujo.karaoke_ia.ui.components.BrandMark
import com.araujo.karaoke_ia.ui.components.ArtworkPlaceholder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.araujo.karaoke_ia.data.ArtworkRepository
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.UserScoreEntry
import com.araujo.karaoke_ia.data.podiumEntry
import com.araujo.karaoke_ia.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import java.io.File

private val LocalArtworkRepository = staticCompositionLocalOf<ArtworkRepository?> { null }
private val LocalArtworkRevision = staticCompositionLocalOf { 0 }

@Composable
private fun Modifier.tvSearchFocusNavigation(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
            Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
            else -> false
        }
    }
}

@Composable
fun HomeScreen(
    onNavigateToPlayer: (String) -> Unit,
    viewModel: HomeViewModel,
    onStorageAccess: () -> Unit = {},
    searchSettings: @Composable () -> Unit = {}
) {
    val refreshOwner = LocalLifecycleOwner.current
    LaunchedEffect(refreshOwner) {
        refreshOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshLibrary()
            awaitCancellation()
        }
    }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTelevision = com.araujo.karaoke_ia.ui.components.usesTvNavigation(LocalContext.current, configuration)
    if (!isTelevision) {
        CompositionLocalProvider(LocalArtworkRepository provides viewModel.artworkRepository,
            LocalArtworkRevision provides viewModel.artworkRevision.value) {
            MobileHomeScreen(viewModel, onNavigateToPlayer, onStorageAccess, searchSettings)
        }
        return
    }
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
    val artworkBatchProgress by viewModel.artworkBatchProgress
    val artworkRevision by viewModel.artworkRevision

    val quickCode           by viewModel.quickCode
    val quickMatchedMusic   by viewModel.quickMatchedMusic
    val showQuickCodeHud    by viewModel.showQuickCodeHud

    val initialTabFocusRequester = remember { FocusRequester() }
    val selectedCoverFocusRequester = remember { FocusRequester() }
    val homeLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(homeLifecycleOwner) {
        homeLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // NavHost resumes this entry after its transition and player view disposal.
            withFrameNanos { }
            if (viewModel.selectedTab.value == HomeTab.INICIO && viewModel.allMusics.value.isNotEmpty()) {
                selectedCoverFocusRequester.requestFocus()
            } else initialTabFocusRequester.requestFocus()
            awaitCancellation()
        }
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

    CompositionLocalProvider(LocalArtworkRepository provides viewModel.artworkRepository,
        LocalArtworkRevision provides artworkRevision) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .stageBackground()
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

            // ── 2. Barra de Navegação em Pílulas ─────────────────────────────
            PillNavigationBar(
                selectedTab = selectedTab,
                queueCount = queueCodes.size,
                initialTabFocusRequester = initialTabFocusRequester,
                onTabSelected = { viewModel.onTabSelected(it) },

            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── 3. Visualização da Aba ───────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when {
                    selectedTab == HomeTab.CONFIGURACOES -> {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            SectionHeader("Sua biblioteca")
                            Text("Cat\u00e1logo local: ${allMusics.size} m\u00fasicas", color = TextMuted)
                            Text("M\u00fasicas e capas salvas no dispositivo. Pontua\u00e7\u00e3o recreativa.", color = TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PillTabItem("Favoritos", Icons.Default.Favorite, false,
                                    onClick = { viewModel.onTabSelected(HomeTab.FAVORITOS) })
                                PillTabItem("Hist\u00f3rico", Icons.Default.History, false,
                                    onClick = { viewModel.onTabSelected(HomeTab.INICIO) })
                                PillTabItem("Atualizar cat\u00e1logo", Icons.Default.Refresh, false,
                                    onClick = { viewModel.loadAllMusics(forceRefresh = true) })
                                PillTabItem("Buscar capas", Icons.Default.Image, false,
                                    onClick = { viewModel.fetchMissingArtwork() })
                            }
                            artworkBatchProgress?.let { Text("Capas: ${it.completed}/${it.total}", color = TextMuted) }
                            PillTabItem("Acessar músicas do aparelho", Icons.Default.Usb, false,
                                onClick = onStorageAccess)
                            searchSettings()
                        }
                    }

                    selectedTab == HomeTab.RANKING -> {
                        MyScoresScreenView(
                            myScores = myBestScores,
                            onCantar = { code -> onNavigateToPlayer(code) }
                        )
                    }

                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = GoldPrimary)
                        }
                    }

                    loadError != null && allMusics.isEmpty() && historyMusics.isEmpty() && selectedTab == HomeTab.INICIO -> {
                        ErrorView(
                            message = "Pesquise pelo nome para buscar seu karaokê ou conecte suas músicas locais.",
                            onRetry = { viewModel.onTabSelected(HomeTab.PESQUISAR) }
                        )
                    }

                    selectedTab == HomeTab.INICIO -> {
                        HomeDashboardView(
                            musics = allMusics,
                            recentMusics = historyMusics,
                            onMusicClick = { onNavigateToPlayer(it.code) },
                            favoriteCodes = favorites,
                            queueCodes = queueCodes,
                            onFavorite = { viewModel.toggleFavorite(it) },
                            onQueue = { viewModel.addToQueue(it) },
                            selectedCoverFocusRequester = selectedCoverFocusRequester
                        )
                    }

                    selectedTab == HomeTab.MUSICAS -> {
                        CatalogView(
                            musics = if (selectedArtist != null) artistMusics else allMusics,
                            artistList = artistList,
                            selectedArtist = selectedArtist,
                            onArtistSelect = { viewModel.onArtistSelected(it) },
                            onArtistClear = { viewModel.onArtistCleared() },
                            onCantar = { onNavigateToPlayer(it) },
                            artworkBatchProgress = artworkBatchProgress,
                            onFetchArtwork = { viewModel.fetchMissingArtwork() }
                        )
                    }

                    selectedTab == HomeTab.PESQUISAR -> {
                        SongSearchView(
                            query = searchQuery,
                            searchStatus = viewModel.searchStatus.value,
                            isSearching = viewModel.isSearching.value,
                            onRetrySearch = { viewModel.onSearchQueryChange(searchQuery) },
                            onSearchSettings = { viewModel.onTabSelected(HomeTab.CONFIGURACOES) },
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
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. HERO HEADER SECTION
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PillNavigationBar(
    selectedTab: HomeTab,
    queueCount: Int,
    initialTabFocusRequester: FocusRequester,
    onTabSelected: (HomeTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 18.dp)) {
                BrandMark(Modifier.size(32.dp))
                Spacer(Modifier.width(6.dp))
                Text("SoltaVoz", fontSize = 15.sp, fontFamily = ReferenceHeadingFont,
                    fontWeight = FontWeight.Black, color = TextWhite)
            }
            PillTabItem(
                label = "Início",
                icon = Icons.Default.Home,
                isSelected = selectedTab == HomeTab.INICIO,
                focusRequester = initialTabFocusRequester.takeIf { selectedTab == HomeTab.INICIO },
                onClick = { onTabSelected(HomeTab.INICIO) }
            )

            PillTabItem(
                label = "Pesquisar",
                icon = Icons.Default.Search,
                isSelected = selectedTab == HomeTab.PESQUISAR,
                focusRequester = initialTabFocusRequester.takeIf { selectedTab == HomeTab.PESQUISAR },
                onClick = { onTabSelected(HomeTab.PESQUISAR) }
            )

            PillTabItem(
                label = "Categorias",
                icon = Icons.Default.MusicNote,
                isSelected = selectedTab == HomeTab.MUSICAS,
                focusRequester = initialTabFocusRequester.takeIf { selectedTab == HomeTab.MUSICAS },
                onClick = { onTabSelected(HomeTab.MUSICAS) }
            )

            PillTabItem(
                label = "Ranking",
                icon = Icons.Default.Leaderboard,
                isSelected = selectedTab == HomeTab.RANKING,
                focusRequester = initialTabFocusRequester.takeIf { selectedTab == HomeTab.RANKING },
                onClick = { onTabSelected(HomeTab.RANKING) }
            )

            PillTabItem(
                label = "Fila",
                icon = Icons.Default.QueueMusic,
                badgeCount = queueCount,
                isSelected = selectedTab == HomeTab.FILA,
                focusRequester = initialTabFocusRequester.takeIf { selectedTab == HomeTab.FILA },
                onClick = { onTabSelected(HomeTab.FILA) }
            )

            PillTabItem(
                label = "Configura\u00e7\u00f5es",
                icon = Icons.Default.Settings,
                isSelected = selectedTab == HomeTab.CONFIGURACOES,
                focusRequester = initialTabFocusRequester.takeIf { selectedTab == HomeTab.CONFIGURACOES || selectedTab == HomeTab.FAVORITOS },
                onClick = { onTabSelected(HomeTab.CONFIGURACOES) }
            )
        }

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
            isSelected -> Brush.horizontalGradient(listOf(GoldBadgeBg, GoldBadgeBg.copy(alpha = 0.55f)))
            else       -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
        }
    }

    val contentColor = when {
        isFocused  -> TextWhite
        isSelected -> GoldPrimary
        else       -> TextMuted
    }

    Column(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusFeedback()
            .clip(RoundedCornerShape(10.dp))
            .background(bgBrush)
            .border(if (isFocused) 1.dp else 0.dp, if (isFocused) GoldGlow else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = ReferenceBodyFont,
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

// ─────────────────────────────────────────────────────────────────────────────
//  3. HOME DASHBOARD VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeDashboardView(
    musics: List<MusicFile>, recentMusics: List<MusicFile>,
    onMusicClick: (MusicFile) -> Unit,
    favoriteCodes: Set<String>, queueCodes: List<String>,
    onFavorite: (String) -> Unit, onQueue: (String) -> Unit,
    selectedCoverFocusRequester: FocusRequester
) {
    val dashboardMusics = remember(musics, recentMusics) { musics.ifEmpty { recentMusics }.take(30) }
    var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = dashboardMusics.firstOrNull { it.code == selectedCode } ?: dashboardMusics.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SEU PALCO · MÚSICAS DO APARELHO E YOUTUBE", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)) {
            items(dashboardMusics, key = { it.code }) { song ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val scale by animateFloatAsState(if (focused) 1.06f else if (selected?.code == song.code) 1f else .9f,
                    animationSpec = tween(150), label = "coverScale")
                Box(Modifier.size(170.dp)
                    .then(if (selected?.code == song.code) Modifier.focusRequester(selectedCoverFocusRequester) else Modifier)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .onFocusChanged { if (it.isFocused) selectedCode = song.code }
                    .tvFocusFeedback()
                    .clip(RoundedCornerShape(10.dp))
                    .border(if (focused || selected?.code == song.code) 2.dp else 1.dp,
                        if (focused || selected?.code == song.code) GoldPrimary else DarkCardBorder, RoundedCornerShape(10.dp))
                    .clickable(interactionSource = interaction, indication = null) { selectedCode = song.code; onMusicClick(song) }
                ) {
                    SongAlbumArt(song.code, song.title, song.artist, searchForArtwork = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
        selected?.let { song ->
            Text(song.title, color = TextWhite, fontFamily = ReferenceHeadingFont, fontWeight = FontWeight.Bold,
                fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${song.sourceLabel}", color = TextMuted, fontFamily = ReferenceBodyFont, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroActionButton("Cantar agora", true) { onMusicClick(song) }
                PillTabItem(if (song.code in favoriteCodes) "Favorita" else "Favoritar",
                    if (song.code in favoriteCodes) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    song.code in favoriteCodes, onClick = { onFavorite(song.code) })
                PillTabItem(if (song.code in queueCodes) "Na fila" else "Adicionar à fila", Icons.Default.QueueMusic,
                    song.code in queueCodes, onClick = { onQueue(song.code) })
            }
        }
        if (recentMusics.isNotEmpty()) {
            SectionHeader("Tocadas recentemente")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(recentMusics.take(12), key = { "recent_${it.code}" }) { song ->
                    SongCardItem(song) { onMusicClick(song) }
                }
            }
        }

    }
}

@Composable
private fun HeroActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val highlighted = primary || isFocused

    Row(
        modifier = Modifier
            .tvFocusFeedback()
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (highlighted) Brush.horizontalGradient(listOf(GoldPrimary, GoldAccent))
                else Brush.horizontalGradient(listOf(Color(0x221C1A22), Color(0x221C1A22)))
            )
            .border(
                width = if (isFocused && !primary) 2.dp else 1.dp,
                color = if (highlighted) GoldGlow else Color(0x55FFFFFF),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (primary) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlighted) Color.White else TextWhite
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontFamily = ReferenceHeadingFont,
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
            .tvFocusFeedback()
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) BronzeGradient1.copy(alpha = 0.55f) else Color(0x22141722))
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
            .tvFocusFeedback()
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
                searchForArtwork = false,
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
                text = "${music.displayCode} | ${music.title}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) GoldPrimary else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${music.artist} · ${music.sourceLabel}",
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
    searchForArtwork: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (code.startsWith("youtube:")) {
        val id = code.removePrefix("youtube:")
        Box(modifier.background(DarkCardElevated)) {
            ArtworkPlaceholder(artist)
            if (com.araujo.karaoke_ia.online.OnlineRepository.validId(id)) AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data("https://i.ytimg.com/vi/$id/hqdefault.jpg").size(256, 256).crossfade(false).build(),
                contentDescription = "Capa do YouTube: $title", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        return
    }
    val repository = LocalArtworkRepository.current
    val artworkRevision = LocalArtworkRevision.current
    val context = LocalContext.current
    val artworkPath by produceState<String?>(null, repository, code, title, artist, searchForArtwork, artworkRevision) {
        value = try {
            repository?.getThumbnailFile(
                MusicFile(code = code, fileName = "", artist = artist, title = title, lyricsStart = ""),
                edge = 256, searchOnline = searchForArtwork
            )?.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier.background(DarkCardElevated),
        contentAlignment = Alignment.Center
    ) {
        ArtworkPlaceholder(artist)

        artworkPath?.let { path ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(path))
                    .crossfade(true)
                    .build(),
                contentDescription = "Capa de $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SongSearchView(
    query: String,
    searchStatus: String?,
    isSearching: Boolean,
    onRetrySearch: () -> Unit,
    onSearchSettings: () -> Unit,
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
                .background(DarkCardElevated)
                .border(1.dp, GoldPrimary.copy(alpha = .35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextWhite,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(GoldPrimary),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Buscar Música, Artista ou Código...",
                                fontSize = 14.5.sp,
                                color = TextMuted
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f).tvSearchFocusNavigation()
                )
            }
        }

        searchStatus?.let { Text(it, color = TextMuted, fontSize = 12.sp) }
        if (isSearching) androidx.compose.material3.LinearProgressIndicator(color = GoldPrimary, modifier = Modifier.fillMaxWidth())
        if (!isSearching && results.isEmpty() && query.trim().length >= 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillTabItem("Tentar novamente", Icons.Default.Refresh, false, onClick = onRetrySearch)
                PillTabItem("Configurações", Icons.Default.Settings, false, onClick = onSearchSettings)
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
                    listOf(BronzeGradient1, BronzeGradient2)
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
                    text = music.displayCode,
                    fontSize = 26.sp,
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
                        text = "${music.artist} · ${music.sourceLabel}",
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
                        .tvFocusFeedback()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isQueueFocused) Brush.horizontalGradient(listOf(GoldAccent, GoldPrimary))
                            else Brush.horizontalGradient(listOf(BronzeGradient1, BronzeGradient2))
                        )
                        .border(
                            width = if (isQueueFocused) 2.dp else 1.dp,
                            color = if (isQueueFocused) GoldGlow else BronzeBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
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
                        .tvFocusFeedback()
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
            .tvFocusFeedback()
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
                        .onFocusChanged { if (it.isFocused) onSelect() }
            .clickable(interactionSource = interactionSource, indication = null) { onCantar() }
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
                    searchForArtwork = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = music.displayCode,
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
                    text = "${music.artist} · ${music.sourceLabel}",
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
                modifier = Modifier.tvFocusFeedback().size(36.dp)
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
                modifier = Modifier.tvFocusFeedback().size(36.dp)
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
                        .tvFocusFeedback()
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
                itemsIndexed(upcomingMusics, key = { index, it -> "queue_${index}_${it.code}" }) { index, music ->
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
                                text = "${music.displayCode} | ${music.title}",
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
            text = "Pódio dos melhores",
            fontFamily = ReferenceHeadingFont,
            fontSize = 22.sp,
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
            if (myScores.isNotEmpty()) {
                val first = myScores.podiumEntry(1)
                val second = myScores.podiumEntry(2)
                val third = myScores.podiumEntry(3)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    PodiumCard(
                        entry = second,
                        place = "2º",
                        bgColor = Color(0xFF282C38),
                        borderColor = Color(0xFF7A839E),
                        modifier = Modifier.weight(1f).height(205.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    PodiumCard(
                        entry = first,
                        place = "1º",
                        bgColor = Color(0xFF302510),
                        borderColor = Color(0xFFFFCF64),
                        isFirst = true,
                        modifier = Modifier.weight(1.15f).height(230.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    PodiumCard(
                        entry = third,
                        place = "3º",
                        bgColor = Color(0xFF251E18),
                        borderColor = Color(0xFF9E6B4A),
                        modifier = Modifier.weight(1f).height(185.dp)
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
                            .background(if (index == 0) GoldBadgeBg else Color(0x33141722))
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
    entry: UserScoreEntry?,
    place: String,
    bgColor: Color,
    borderColor: Color,
    isFirst: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Brush.verticalGradient(listOf(borderColor.copy(alpha = 0.22f), bgColor)))
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
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
        ) {
            if (isFirst) {
                Icon(Icons.Default.EmojiEvents, contentDescription = "Primeiro lugar",
                    tint = borderColor, modifier = Modifier.size(24.dp))
            }
            SingerPhoto(entry?.photoPath, modifier = Modifier.size(if (isFirst) 76.dp else 62.dp), borderColor = borderColor)
            Text(
                text = place,
                fontFamily = ReferenceHeadingFont,
                fontSize = if (isFirst) 20.sp else 17.sp,
                fontWeight = FontWeight.Black,
                color = borderColor
            )
            Text(
                text = entry?.let { "${it.score} pts" } ?: "Aguardando cantor",
                fontSize = if (isFirst) 19.sp else 16.sp,
                fontWeight = FontWeight.Black,
                color = if (isFirst) borderColor else TextWhite
            )

            Text(
                text = entry?.title ?: "Cante para entrar no pódio",
                fontSize = 11.5.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry != null) {
                Text(entry.artist, fontSize = 10.sp, color = TextSubtle, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SingerPhoto(photoPath: String?, modifier: Modifier = Modifier, borderColor: Color = GoldPrimary) {
    Box(modifier = modifier.clip(CircleShape).background(DarkCardElevated)
        .border(2.dp, borderColor, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Person, contentDescription = "Cantor sem foto", tint = TextMuted,
            modifier = Modifier.fillMaxSize(0.55f))
        if (!photoPath.isNullOrBlank()) {
            AsyncImage(model = File(photoPath), contentDescription = "Foto do cantor",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .fillMaxWidth()
            .tvFocusFeedback()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCardSurface)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) GoldPrimary else DarkCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onCantar)
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
                    searchForArtwork = false,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(24.dp)
                        .tvFocusFeedback()
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
                text = "${music.displayCode} | ${music.title}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) GoldPrimary else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${music.artist} · ${music.sourceLabel}",
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
                            .tvFocusFeedback()
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
                            .tvFocusFeedback()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BronzeGradient1)
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
    onCantar: (String) -> Unit,
    artworkBatchProgress: ArtworkBatchProgress?,
    onFetchArtwork: () -> Unit
) {
    var page by remember(selectedArtist) { mutableIntStateOf(0) }
    val pageMusics = remember(musics, page) {
        musics.drop(page * MUSIC_PAGE_SIZE).take(MUSIC_PAGE_SIZE)
    }

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
                text = "Catálogo de músicas",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
            val batchInteraction = remember { MutableInteractionSource() }
            val isBatchFocused by batchInteraction.collectIsFocusedAsState()
            Box(
                modifier = Modifier
                    .tvFocusFeedback()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isBatchFocused) GoldPrimary else BronzeGradient1)
                    .border(1.dp, if (isBatchFocused) GoldGlow else GoldBadgeBorder, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = batchInteraction,
                        indication = null,
                        enabled = artworkBatchProgress == null,
                        onClick = onFetchArtwork
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = artworkBatchProgress?.let { "Buscando capas ${it.completed}/${it.total}" }
                        ?: "Buscar capas das músicas",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBatchFocused) DarkCharcoalBg else GoldPrimary
                )
            }
        }

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
            .tvFocusFeedback()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isFocused) GoldPrimary
                else if (isSelected) GoldBadgeBg
                else Color(0x33141722)
            )
            .border(
                width = if (isFocused || isSelected) 1.5.dp else 1.dp,
                color = if (isFocused || isSelected) GoldPrimary else Color(0x22FFFFFF),
                shape = RoundedCornerShape(20.dp)
            )
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
                    .tvFocusFeedback()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GoldPrimary)
                    .clickable { onRetry() }
                    .padding(horizontal = 22.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "Pesquisar músicas",
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalBg
                )
            }
        }
    }
}
