package com.araujo.karaoke_ia.online

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.araujo.karaoke_ia.data.ScoreRepository
import com.araujo.karaoke_ia.data.UserScoreEntry
import com.araujo.karaoke_ia.data.podiumEntry
import androidx.compose.runtime.saveable.listSaver
import com.araujo.karaoke_ia.ui.theme.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class OnlinePanel(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Outlined.Home), SEARCH("Pesquisar", Icons.Outlined.Search),
    CATEGORIES("Categorias", Icons.Outlined.Folder), RANKING("Ranking", Icons.Outlined.BarChart),
    QUEUE("Fila", Icons.Outlined.QueueMusic), SETTINGS("Configurações", Icons.Outlined.Settings)
}

private fun Modifier.onlineFocus(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .border(if(focused) 2.dp else 0.dp, if(focused) GoldGlow else Color.Transparent, RoundedCornerShape(12.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnlineLibrary(repository: OnlineRepository, scores: ScoreRepository, message: String?,
    onModes: () -> Unit, onPlay: (OnlineSong) -> Unit, onAdd: (OnlineSong) -> Unit,
    onPlayQueued: (String) -> Unit, onMessage: (String?) -> Unit) {
    val queue by repository.queue.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val history by repository.history.collectAsState()
    var panelName by rememberSaveable { mutableStateOf(OnlinePanel.HOME.name) }
    val panel = OnlinePanel.valueOf(panelName)
    var query by rememberSaveable { mutableStateOf("") }
    val songSaver = remember { listSaver<List<OnlineSong>, String>(
        save = { songs -> songs.flatMap { listOf(it.videoId, it.title, it.channel) } },
        restore = { values -> values.chunked(3).filter { it.size == 3 }.map { OnlineSong(it[0],it[1],it[2]) } }) }
    var results by rememberSaveable(stateSaver = songSaver) { mutableStateOf<List<OnlineSong>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("Todas") }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreCoverFocus by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val isTv = LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val completedPerformances = remember { scores.getAllScores() }
    val playCounts = remember(completedPerformances) {
        completedPerformances.filter { it.musicCode.startsWith("youtube:") }
            .groupingBy { it.musicCode.removePrefix("youtube:") }.eachCount()
    }
    val completedSongs = remember(completedPerformances) { completedPerformances.filter { it.musicCode.startsWith("youtube:") }
        .mapNotNull { score -> score.musicCode.removePrefix("youtube:").takeIf(OnlineRepository::validId)
            ?.let { OnlineSong(it,score.title,score.artist) } } }
    val library = remember(queue, favorites, history, results, completedSongs, playCounts) {
        (queue.map { it.song } + history + favorites + results + completedSongs).distinctBy { it.videoId }
            .sortedByDescending { playCounts[it.videoId] ?: 0 }
    }
    val channels = remember(library) { library.map { it.channel }.filter { it.isNotBlank() }.distinct().sorted() }
    val songs = when (panel) {
        OnlinePanel.SEARCH -> results
        OnlinePanel.CATEGORIES -> when(filter) { "Favoritos" -> favorites; "Histórico" -> history; "Todas" -> library; else -> library.filter { it.channel == filter } }
        else -> library
    }
    val current = songs.firstOrNull { it.videoId == activeId } ?: songs.firstOrNull()
    val menuFocus = remember { FocusRequester() }
    val coverFocus = remember { FocusRequester() }
    val menuState = rememberLazyListState()
    val carouselState = rememberLazyListState()
    LaunchedEffect(panelName) {
        val isLibraryPanel = panel in listOf(OnlinePanel.HOME, OnlinePanel.SEARCH, OnlinePanel.CATEGORIES)
        if (restoreCoverFocus && current != null && isLibraryPanel) {
            carouselState.scrollToItem(songs.indexOfFirst { it.videoId == current.videoId }.coerceAtLeast(0))
            withFrameNanos { }
            withFrameNanos { }
            coverFocus.requestFocus()
            restoreCoverFocus = false
        } else {
            restoreCoverFocus = false
            menuState.scrollToItem(panel.ordinal)
            withFrameNanos { }
            menuFocus.requestFocus()
        }
    }
    BackHandler(panel != OnlinePanel.HOME) { restoreCoverFocus = false; panelName = OnlinePanel.HOME.name }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 650.dp
            val branding: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Mic, null, Modifier.size(34.dp), tint = GoldPrimary)
                    Column {
                        Text("PALCO ONLINE", fontFamily = ReferenceHeadingFont, color = GoldPrimary,
                            fontSize = if(compact) 20.sp else 18.sp)
                        Text("K A R A O K \u00ca   C L U B", color = TextMuted, fontSize = 9.sp)
                        Text("YouTube \u00b7 ${queue.size} na fila", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
            val navigation: @Composable (Modifier) -> Unit = { menuModifier ->
                LazyRow(menuModifier.background(Color.White.copy(alpha = .035f), RoundedCornerShape(14.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp)).padding(5.dp),
                    state = menuState,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(OnlinePanel.entries, key = { it.name }) { item ->
                        var focused by remember { mutableStateOf(false) }
                        Column(Modifier.then(if(item == panel) Modifier.focusRequester(menuFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if(item == panel || focused) GoldBadgeBg else Color.Transparent)
                            .border(if(focused) 2.dp else 1.dp, if(item == panel || focused) GoldPrimary else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { restoreCoverFocus = false; panelName = item.name; onMessage(null) }.padding(horizontal = if(compact) 14.dp else 12.dp, vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(item.icon, item.label, Modifier.size(22.dp), tint = if(item == panel) GoldPrimary else TextOffWhite)
                            Text(item.label, color = TextOffWhite, fontSize = 11.sp)
                        }
                    }
                }
            }
            if(compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    branding()
                    navigation(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.width(230.dp)) { branding() }
                    navigation(Modifier.weight(1f))
                }
            }
        }

        if(panel == OnlinePanel.SEARCH) {
            OutlinedTextField(query, { query = it.take(500) }, Modifier.fillMaxWidth().onlineFocus()
                .onPreviewKeyEvent { event ->
                    if (!isTv || event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                        Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                        else -> false
                    }
                },
                label = { Text(if(repository.searchConfigured) "Música, artista ou link do YouTube" else "Link do vídeo do YouTube") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Button(modifier = Modifier.onlineFocus(), enabled = !busy && query.isNotBlank(), onClick = {
                busy = true; onMessage(null)
                scope.launch {
                    try {
                        results = if(!repository.searchConfigured || OnlineRepository.parseVideoId(query) != null)
                            listOf(repository.fromLink(query)) else repository.search(query)
                        activeId = results.firstOrNull()?.videoId
                        if(results.isEmpty()) onMessage("Nenhum vídeo encontrado. Tente outra busca.")
                    } catch(cancelled: CancellationException) { throw cancelled }
                    catch(error: Exception) { onMessage(error.message ?: "Não foi possível buscar vídeos.") }
                    finally { busy = false }
                }
            }) { Text(if(busy) "Buscando…" else "Buscar músicas") }
        }
        message?.let { Text(it, color = TextOffWhite, fontSize = 12.sp) }
        when(panel) {
            OnlinePanel.QUEUE -> {
                Text("Sua fila · ${queue.size} músicas", color = TextWhite, fontSize = 21.sp, fontFamily = ReferenceHeadingFont)
                if(queue.isEmpty()) EmptyLibrary("Sua fila está vazia. Pesquise uma música para começar.") { panelName = OnlinePanel.SEARCH.name }
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(queue, key = { it.id }) { entry ->
                        BoxWithConstraints(Modifier.fillMaxWidth().background(DarkCardSurface.copy(alpha=.8f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                            val actions: @Composable () -> Unit = {
                                Button(modifier = Modifier.onlineFocus(), onClick={onPlayQueued(entry.id)}) { Text("Cantar") }
                                OutlinedButton(modifier = Modifier.onlineFocus(), onClick={repository.remove(entry.id)}) { Text("Remover") }
                            }
                            if(maxWidth < 480.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OnlineQueueInfo(entry.song, Modifier.fillMaxWidth())
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OnlineQueueInfo(entry.song, Modifier.weight(1f))
                                    actions()
                                }
                            }
                        }
                    }
                }
            }
            OnlinePanel.RANKING -> {
                Text("Pódio e músicas cantadas", color=TextWhite, fontSize=22.sp, fontFamily=ReferenceHeadingFont)
                val performances = remember { scores.getAllScores() }
                val popular = remember(performances) {
                    performances.filter { it.musicCode.startsWith("youtube:") &&
                        OnlineRepository.validId(it.musicCode.removePrefix("youtube:")) }
                        .groupBy { it.musicCode.removePrefix("youtube:") }
                        .entries.sortedWith(compareByDescending<Map.Entry<String, List<UserScoreEntry>>> { it.value.size }
                            .thenByDescending { entry -> entry.value.maxOf { it.timestamp } })
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    val podiumScores = performances.sortedByDescending { it.score }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                            listOf(2,1,3).forEach { place ->
                                OnlinePodium(place, podiumScores.podiumEntry(place), Modifier.weight(1f))
                            }
                        }
                    }
                    item { Text("Mais cantadas neste aparelho", color = TextWhite, fontSize = 18.sp) }
                    if(popular.isEmpty()) item { Text("Complete uma apresentação para aparecer aqui.", color = TextMuted) }
                    items(popular, key = { "popular:${it.key}" }) { entry ->
                        val performance = entry.value.maxBy { it.timestamp }
                        Row(Modifier.fillMaxWidth().background(DarkCardSurface, RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${entry.value.size}×", color = GoldPrimary, fontSize = 22.sp)
                            Column(Modifier.weight(1f)) {
                                Text(performance.title, color = TextWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(performance.artist, color = TextMuted, fontSize = 12.sp)
                            }
                            OutlinedButton(modifier = Modifier.onlineFocus(), onClick = {
                                onPlay(OnlineSong(entry.key, performance.title, performance.artist))
                            }) { Text("Cantar") }
                        }
                    }
                    item { Text("Cantadas recentemente", color=TextWhite, fontSize=18.sp) }
                    if(history.isEmpty()) item { Text("Seu histórico aparecerá após cantar uma música.", color=TextMuted) }
                    items(history, key={it.videoId}) { song -> TextButton(modifier = Modifier.onlineFocus(), onClick={onPlay(song)}) { Text(song.title, maxLines=2) } }
                }
            }
            OnlinePanel.SETTINGS -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Configurações da sessão", color=TextWhite, fontSize=22.sp, fontFamily=ReferenceHeadingFont)
                Text(if(repository.searchConfigured) "Pesquisa por nome disponível neste dispositivo." else "Pesquisa por link disponível. A pesquisa por nome requer a configuração do YouTube.", color=TextMuted)
                Text("Pontuação recreativa · 1º: 96–100 · 2º: 90–95 · 3º: 87–89", color=TextMuted)
                Text("Use as setas para navegar, OK para selecionar e Voltar para retornar.", color=TextMuted)
                Button(modifier = Modifier.onlineFocus(), onClick=onModes) { Text("Trocar modo online / offline") }
                OutlinedButton(modifier = Modifier.onlineFocus(), onClick={filter="Favoritos";panelName=OnlinePanel.CATEGORIES.name}) { Text("Meus favoritos") }
                OutlinedButton(modifier = Modifier.onlineFocus(), onClick={filter="Histórico";panelName=OnlinePanel.CATEGORIES.name}) { Text("Meu histórico") }
                }
            }
            else -> {
                if(panel == OnlinePanel.CATEGORIES) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(listOf("Todas", "Favoritos", "Histórico") + channels) { category ->
                        FilterChip(modifier = Modifier.onlineFocus(), selected=filter==category, onClick={filter=category;activeId=null}, label={Text(category)})
                    }
                }
                if(songs.isEmpty()) EmptyLibrary(if(panel==OnlinePanel.SEARCH) "Pesquise uma música ou cole um link para montar seu palco." else "Seu palco começa com as músicas que você escolher.") { panelName=OnlinePanel.SEARCH.name }
                else {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val coverHeight = (maxHeight * .76f).coerceIn(100.dp, 225.dp)
                        LazyRow(Modifier.fillMaxSize(), state=carouselState, verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp), contentPadding=PaddingValues(horizontal=4.dp)) {
                            items(songs, key={it.videoId}) { song ->
                                val selected = current?.videoId==song.videoId
                                val height by animateDpAsState(if(selected) coverHeight else coverHeight*.77f, animationSpec = tween(150), label="coverSize")
                                var focused by remember { mutableStateOf(false) }
                                OnlineCover(song, Modifier.height(height).width(height*.84f)
                                    .then(if(selected) Modifier.focusRequester(coverFocus) else Modifier)
                                    .onFocusChanged { focused=it.isFocused; if(it.isFocused) activeId=song.videoId }
                                    .border(if(selected || focused) 2.dp else 1.dp, if(selected || focused) GoldPrimary else DarkCardBorder, RoundedCornerShape(12.dp))
                                    .clickable { activeId=song.videoId })
                            }
                        }
                    }
                    current?.let { song ->
                        Text(song.title, color=TextWhite, fontFamily=ReferenceHeadingFont, fontSize=22.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
                        Text(song.channel, color=TextMuted, fontSize=15.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick={restoreCoverFocus = true; onPlay(song)}, modifier = Modifier.focusProperties { up = coverFocus }.onlineFocus(), shape=RoundedCornerShape(12.dp)) { Icon(Icons.Filled.PlayArrow,null); Spacer(Modifier.width(6.dp));Text("Cantar agora") }
                            OutlinedButton(modifier = Modifier.onlineFocus(), onClick={onAdd(song)}, shape=RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.PlaylistAdd,null);Spacer(Modifier.width(6.dp));Text("Adicionar à fila") }
                            OutlinedButton(modifier = Modifier.onlineFocus(), onClick={runCatching{repository.toggleFavorite(song)}.onFailure{onMessage(it.message)}}, shape=RoundedCornerShape(12.dp)) {
                                val saved=favorites.any{it.videoId==song.videoId}
                                Icon(if(saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,null)
                                Spacer(Modifier.width(6.dp));Text(if(saved) "Favorito" else "Favoritar")
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text("OK  Selecionar     ·     Voltar  Retornar", color=TextMuted, fontSize=10.sp)
            Text("PALCO ONLINE", color=TextSubtle, fontSize=10.sp)
        }
    }
}

@Composable
private fun OnlineQueueInfo(song: OnlineSong, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OnlineCover(song, Modifier.size(65.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines=2, overflow=TextOverflow.Ellipsis, color=TextWhite)
            Text(song.channel, color=TextMuted, fontSize=12.sp)
        }
    }
}

@Composable
private fun OnlinePodium(place: Int, score: UserScoreEntry?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(if(place == 1) 90.dp else 74.dp).clip(RoundedCornerShape(18.dp))
            .background(DarkCardSurface).border(2.dp, if(place == 1) GoldPrimary else DarkCardBorder, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center) {
            if(score?.photoPath != null) AsyncImage(File(score.photoPath), "Foto do cantor", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Outlined.Person, null, Modifier.size(36.dp), tint = TextMuted)
        }
        Text(score?.title ?: "Seu lugar no palco", color = TextOffWhite, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(score?.let { "${it.score} pts" } ?: "Aguardando", color = TextMuted, fontSize = 11.sp)
        Box(Modifier.fillMaxWidth().height(if(place == 1) 90.dp else if(place == 2) 65.dp else 45.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Brush.verticalGradient(listOf(if(place == 1) GoldPrimary.copy(alpha = .6f) else Color(0xFF5D3280), DarkCardSurface))),
            contentAlignment = Alignment.Center) {
            Text("${place}º", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyLibrary(text: String, search: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=26.dp), horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(44.dp), tint=GoldPrimary)
        Text(text, color=TextMuted, fontSize=14.sp)
        Button(modifier = Modifier.onlineFocus(), onClick=search) { Text("Pesquisar músicas") }
    }
}

@Composable
private fun OnlineCover(song: OnlineSong, modifier: Modifier) {
    val context=LocalContext.current
    val request=remember(song.videoId,context) {
        ImageRequest.Builder(context).data("https://i.ytimg.com/vi/${song.videoId}/hqdefault.jpg")
            .size(360,360).crossfade(false).build()
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF45235F),Color(0xFF160E29))))) {
        Icon(Icons.Outlined.Mic, null, Modifier.size(42.dp).align(Alignment.Center), tint=GoldPrimary.copy(alpha=.5f))
        AsyncImage(request, "Capa de ${song.title}", Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.8f)))))
        Text(song.title, Modifier.align(Alignment.BottomStart).padding(10.dp), color=TextWhite, fontSize=11.sp,
            maxLines=2, overflow=TextOverflow.Ellipsis, fontFamily=ReferenceBodyFont)
    }
}
