package com.araujo.karaoke_ia.ui.home

import com.araujo.karaoke_ia.ui.components.stageBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.araujo.karaoke_ia.ui.components.BrandMark
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.UserScoreEntry
import com.araujo.karaoke_ia.data.podiumEntry
import com.araujo.karaoke_ia.ui.theme.*
import java.io.File
import kotlinx.coroutines.flow.collect

/** Touch layout independent of the TV focus navigation, with one shared catalogue and queue. */
@Composable
internal fun MobileHomeScreen(viewModel: HomeViewModel, onPlay: (String) -> Unit, onStorageAccess: () -> Unit = {}, searchSettings: @Composable () -> Unit = {}) {
    val selected by viewModel.selectedTab
    val query by viewModel.searchQuery
    val all by viewModel.allMusics
    val results by viewModel.filteredMusics
    val favorites by viewModel.favorites
    val queue by viewModel.queueCodes
    val scores by viewModel.myBestScores
    var historySelected by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf(HomeTab.INICIO, HomeTab.MUSICAS, HomeTab.FILA, HomeTab.FAVORITOS, HomeTab.RANKING)
    val labels = listOf("Início", "Músicas", "Fila", "Favoritos", "Ranking")
    val icons = listOf(Icons.Default.Home, Icons.Default.LibraryMusic, Icons.Default.QueueMusic,
        Icons.Default.Favorite, Icons.Default.Leaderboard)
    val colorScheme = darkColorScheme(primary = GoldPrimary, background = DarkCharcoalBg,
        surface = DarkCharcoalBg, onSurface = TextWhite, onPrimary = Color.White)
    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = DarkCardElevated, modifier = Modifier.navigationBarsPadding()) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(selected = selected == tab || (tab == HomeTab.MUSICAS && selected == HomeTab.PESQUISAR),
                        onClick = { historySelected = false; viewModel.onTabSelected(tab) },
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(if (tab == HomeTab.FILA && queue.isNotEmpty()) "Fila (${queue.size})" else labels[index],
                            maxLines = 1, fontSize = 10.sp) })
                }
            }
        }) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().stageBackground().padding(padding).statusBarsPadding()) {
                val wide = maxWidth >= 600.dp
                val contentWidth = if (wide) 840.dp else maxWidth
                Column(Modifier.widthIn(max = contentWidth).fillMaxSize().align(Alignment.TopCenter)
                    .padding(horizontal = if (wide) 24.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(Modifier.size(30.dp))
                        Text("SoltaVoz", Modifier.weight(1f).padding(start = 8.dp), fontFamily = ReferenceHeadingFont,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { historySelected = false; viewModel.onTabSelected(HomeTab.CONFIGURACOES) }) {
                            Icon(Icons.Default.Settings, "Configurações", tint = GoldPrimary)
                        }
                        if (selected == HomeTab.INICIO) TextButton(onClick = { historySelected = !historySelected }) {
                            Text(if (historySelected) "Início" else "Histórico")
                        }
                    }
                    if (selected != HomeTab.RANKING && selected != HomeTab.FILA) {
                        OutlinedTextField(value = query, onValueChange = {
                            historySelected = false
                            viewModel.onSearchQueryChange(it)
                            viewModel.onTabSelected(HomeTab.PESQUISAR)
                        }, placeholder = { Text("Música, artista ou código") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, "Limpar busca")
                            } })
                    }
                    if (viewModel.isIndexing.value) {
                        Text("Verificando músicas locais…", color = TextMuted, fontSize = 12.sp)
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    }
                    when {
                        selected == HomeTab.CONFIGURACOES -> {
                            Text("Configurações da sessão", fontFamily = ReferenceHeadingFont, color = TextWhite)
                            Text("Catálogo local: ${all.size} músicas", color = TextMuted)
                            Button(onClick = onStorageAccess) { Text("Acessar músicas do aparelho") }
                            searchSettings()
                            OutlinedButton(onClick = { viewModel.loadAllMusics(forceRefresh = true) }) { Text("Atualizar catálogo") }
                            OutlinedButton(enabled = viewModel.artworkBatchProgress.value == null,
                                onClick = { viewModel.fetchMissingArtwork() }) { Text("Buscar capas") }
                        }
                        selected == HomeTab.RANKING -> MobileRanking(scores, Modifier.weight(1f))
                        viewModel.isLoading.value && all.isEmpty() && viewModel.historyMusics.value.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        viewModel.loadError.value != null && all.isEmpty() && selected == HomeTab.INICIO && viewModel.historyMusics.value.isEmpty() -> {
                            Text("Pesquise pelo nome para encontrar seu karaokê ou conecte suas músicas locais.")
                            Button(onClick = { viewModel.onTabSelected(HomeTab.PESQUISAR) }) { Text("Pesquisar músicas") }
                        }
                        else -> {
                            val songs = when {
                                selected == HomeTab.FILA -> viewModel.queueMusics.value
                                selected == HomeTab.FAVORITOS -> viewModel.favoriteMusics.value
                                historySelected -> viewModel.historyMusics.value
                                selected == HomeTab.PESQUISAR -> results
                                else -> all
                            }
                            val title = when {
                                historySelected -> "Cantadas recentemente"
                                selected == HomeTab.FILA -> "Próximas músicas"
                                selected == HomeTab.FAVORITOS -> "Seus favoritos"
                                selected == HomeTab.PESQUISAR -> "Resultados · ${songs.size}"
                                else -> "Escolha sua próxima música"
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                if (selected == HomeTab.FILA && songs.isNotEmpty()) TextButton(onClick = { viewModel.clearQueue() }) { Text("Limpar") }
                            }
                            if (selected == HomeTab.MUSICAS) {
                                val progress by viewModel.artworkBatchProgress
                                TextButton(enabled = progress == null, onClick = { viewModel.fetchMissingArtwork() }) {
                                    Text(progress?.let { "Buscando capas: ${it.completed}/${it.total}" } ?: "Buscar capas das músicas")
                                }
                            }
                            if (selected == HomeTab.PESQUISAR) {
                                viewModel.searchStatus.value?.let { Text(it, color = TextMuted) }
                                if (viewModel.isSearching.value) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (!viewModel.isSearching.value && songs.isEmpty() && query.trim().length >= 2) TextButton(onClick = { viewModel.onSearchQueryChange(query) }) { Text("Tentar novamente") }
                            }
                            if (songs.isEmpty() && !viewModel.isSearching.value) Text(when {
                                selected == HomeTab.FILA -> "Adicione músicas pelo botão + no catálogo."
                                selected == HomeTab.FAVORITOS -> "Toque no coração para salvar suas favoritas."
                                historySelected -> "As músicas cantadas aparecem aqui."
                                else -> "Nenhuma música encontrada."
                            }, color = TextMuted)
                            val songListState = rememberLazyListState()
                            LaunchedEffect(selected, query, songs.size, viewModel.hasMoreSearchResults.value, viewModel.isSearching.value) {
                                snapshotFlow { songListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
                                    if (selected == HomeTab.PESQUISAR && last >= songs.size - 3 && songs.isNotEmpty() &&
                                        viewModel.hasMoreSearchResults.value && !viewModel.isSearching.value) {
                                        viewModel.loadMoreSearchResults()
                                    }
                                }
                            }
                            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = songListState, contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(songs, key = { index, song -> "$index:${song.code}" }) { _, song ->
                                    MobileSongRow(song, song.code in favorites, song.code in queue,
                                        selected == HomeTab.FILA, { onPlay(song.code) },
                                        { viewModel.toggleFavorite(song.code) }, {
                                            if (selected == HomeTab.FILA) viewModel.removeFromQueue(song.code) else viewModel.addToQueue(song.code)
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSongRow(song: MusicFile, favorite: Boolean, queued: Boolean, isQueue: Boolean,
    onPlay: () -> Unit, onFavorite: () -> Unit, onQueue: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(DarkCardElevated, MaterialTheme.shapes.medium).clickable(enabled = song.available, onClick = onPlay).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SongAlbumArt(song.code, song.title, song.artist, modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${song.artist} · ${song.sourceLabel}", color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                Text("${song.displayCode}", color = TextSubtle, fontSize = 11.sp)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                if (favorite) "Remover dos favoritos" else "Salvar favorita", tint = if (favorite) GoldPrimary else TextMuted) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onQueue, enabled = isQueue || (song.available && !queued), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(if (isQueue) Icons.Default.Remove else if (queued) Icons.Default.Check else Icons.Default.Add, null)
                Text(if (isQueue) "Remover" else if (queued) "Na fila" else "Adicionar")
            }
            Button(onClick = onPlay, enabled = song.available, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.PlayArrow, null); Text("Cantar")
            }
        }
    }
}

@Composable
private fun MobileRanking(scores: List<UserScoreEntry>, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Text("Pódio", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (scores.isEmpty()) item { Text("Cante uma música para registrar sua nota.", color = TextMuted) }
        else item {
            Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                listOf(1, 0, 2).forEach { index ->
                    val entry = scores.podiumEntry(index + 1)
                    val color = when (index) { 0 -> Color(0xFFFFCF64); 1 -> Color(0xFFACB6C8); else -> Color(0xFFCA946F) }
                    Column(Modifier.weight(1f).height(if (index == 0) 220.dp else if (index == 1) 195.dp else 175.dp)
                        .background(color.copy(alpha = 0.10f), MaterialTheme.shapes.medium).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(DarkCardElevated), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = TextMuted)
                            entry?.photoPath?.let { AsyncImage(File(it), "Foto do cantor", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Text("${index + 1}º", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(entry?.let { "${it.score} pts" } ?: "—", fontWeight = FontWeight.Bold)
                        Text(entry?.title ?: "Aguardando", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                    }
                }
            }
        }
        itemsIndexed(scores, key = { _, entry -> entry.id }) { index, entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}º", Modifier.width(32.dp), color = TextMuted)
                Column(Modifier.weight(1f)) {
                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.artist, fontSize = 12.sp, color = TextMuted)
                }
                Text("${entry.score} pts", fontWeight = FontWeight.Bold)
            }
        }
    }
}
