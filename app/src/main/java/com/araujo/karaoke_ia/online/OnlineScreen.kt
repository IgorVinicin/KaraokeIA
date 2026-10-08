package com.araujo.karaoke_ia.online

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.araujo.karaoke_ia.ui.theme.*
import com.araujo.karaoke_ia.ui.components.stageBackground
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.ScoreRepository
import com.araujo.karaoke_ia.score.ScoreGenerator
import com.araujo.karaoke_ia.ui.score.ScoreScreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnlineScreen(repository: OnlineRepository, scoreRepository: ScoreRepository,
    remoteUrl: String? = null, onModes: () -> Unit) {
    val queue by repository.queue.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var pendingScoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var scoreFeedback by rememberSaveable { mutableStateOf("") }
    var playNextWhenReady by rememberSaveable { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var showControls by remember { mutableStateOf(false) }
    val libraryState = rememberSaveableStateHolder()
    val context = LocalContext.current

    val focus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val fullscreenFocus = remember { FocusRequester() }
    val overlayFocus = remember { FocusRequester() }
    val composeView = LocalView.current
    val isTv = LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val selected = queue.firstOrNull { it.id == selectedId }
    var historyRecorded by remember(selectedId) { mutableStateOf(false) }
    val completedScore = remember(pendingScoreId) {
        pendingScoreId?.let { id -> scoreRepository.getAllScores().firstOrNull { it.id == id } }
    }
    if (completedScore != null) {
        val place = remember(completedScore.id) { scoreRepository.getPodiumPosition(completedScore.id) }
        val next = queue.firstOrNull()
        BackHandler {
            pendingScoreId = null
            selectedId = null
            playNextWhenReady = false
        }
        key(completedScore.id) { ScoreScreen(
            score = completedScore.score,
            feedback = scoreFeedback,
            scoredMusic = MusicFile(completedScore.musicCode, "", completedScore.artist, completedScore.title, ""),
            nextMusic = next?.let { MusicFile(it.id, "", it.song.channel, it.song.title, "") },
            allowPhoto = place != null,
            rankingPosition = place,
            remoteUrl = remoteUrl,
            onPhotoCaptured = { scoreRepository.attachPhotoToScore(completedScore.id, it) },
            onPlayNext = { id ->
                val entry = repository.queue.value.firstOrNull { it.id == id }
                pendingScoreId = null
                selectedId = entry?.id
                playNextWhenReady = entry != null
            },
            onBackToHome = {
                pendingScoreId = null
                selectedId = null
                playNextWhenReady = false
            }
        ) }
        return
    }
    fun focusControls(requester: FocusRequester) {
        web?.clearFocus()
        composeView.requestFocus()
        requester.requestFocus()
    }
    LaunchedEffect(selectedId, retry) { fullscreen = false; ready = false; message = null; showControls = false; if (selectedId != null) { withFrameNanos { }; focus.requestFocus() } }
    LaunchedEffect(selectedId, retry, ready) {
        if (selectedId != null && !ready) {
            delay(25000)
            if (!ready && message == null) message = "O YouTube demorou para responder. Confira sua internet e tente novamente."
        }
    }
    LaunchedEffect(ready, fullscreen) { if (ready && isTv && !fullscreen) { withFrameNanos { }; focusControls(playFocus) } }
    LaunchedEffect(fullscreen) {
        withFrameNanos { }
        if (fullscreen) focusControls(fullscreenFocus) else if (ready && selected != null) focusControls(playFocus)
    }
    LaunchedEffect(showControls) {
        if (showControls) { withFrameNanos { }; focusControls(overlayFocus) }
        else if (fullscreen) { withFrameNanos { }; focusControls(fullscreenFocus) }
    }
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull()
    DisposableEffect(fullscreen, activity) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, composeView) }
        val previousBehavior = controller?.systemBarsBehavior
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
            }
        }
    }
    BackHandler(selected != null) {
        if (showControls) showControls = false else if (fullscreen) fullscreen = false else { selectedId = null; playNextWhenReady = false }
    }
    fun add(song: OnlineSong, play: Boolean) {
        try {
            val entry = repository.add(song, first = play)
            if (play) {
                selectedId = entry.id
                playNextWhenReady = true
            } else message = "Música adicionada à fila online."
        } catch (error: Exception) { message = error.message }
    }
    Box(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else DarkCharcoalBg)
        .onPreviewKeyEvent {
            if (fullscreen && !showControls && it.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar)) {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) {
                    showControls = !showControls
                }
                true
            } else false
        }.focusRequester(fullscreenFocus).focusable(enabled = fullscreen)) {
    Column(Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier
        .stageBackground()
        .safeDrawingPadding().padding(20.dp)),
        verticalArrangement = Arrangement.spacedBy(if (fullscreen) 0.dp else 12.dp)) {
        if (!fullscreen && selected != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(if (selected != null) "PALCO ONLINE" else "Modo online · YouTube",
                    fontFamily = ReferenceHeadingFont, color = if (selected != null) GoldPrimary else TextWhite,
                    fontSize = if (selected != null) 17.sp else 22.sp)
                if (selected != null) Text("Prepare a voz e solte o som", fontFamily = ReferenceBodyFont,
                    color = TextMuted, fontSize = 12.sp)
            }
            TextButton(onClick = { if (selected != null) { selectedId = null; playNextWhenReady = false } else onModes() },
                modifier = Modifier.heightIn(min = 48.dp).focusRequester(focus)) {
                Text(if (selected != null) "Voltar à fila" else "Trocar modo")
            }
        }
        }
        if (selected != null) {
            if (!fullscreen) Surface(color = DarkSlateSurface, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selected.song.title, fontFamily = ReferenceBodyFont, color = TextWhite,
                        fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selected.song.channel, fontFamily = ReferenceBodyFont, color = TextMuted,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            key(selected.id, retry) {
                YouTubePlayer(selected.song.videoId, Modifier.fillMaxWidth().weight(1f)
                    .then(if (fullscreen) Modifier else Modifier.clip(RoundedCornerShape(16.dp))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))),
                    onReady = {
                        ready = true
                        if (playNextWhenReady) {
                            playNextWhenReady = false
                            fullscreen = true
                            web?.evaluateJavascript("if(window.player)player.playVideo();", null)
                        }
                    }, onPlaying = { if (!historyRecorded) { repository.recordPlayed(selected.song); historyRecorded = true } }, onView = { playerView ->
                        web = playerView
                        playerView?.setOnKeyListener { _, code, event ->
                            if (isTv && event.action == android.view.KeyEvent.ACTION_DOWN) {
                                if (fullscreen) {
                                    if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                        code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                                        showControls = !showControls
                                        return@setOnKeyListener true
                                    }
                                    return@setOnKeyListener false
                                }
                                when (code) {
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { focusControls(playFocus); true }
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { focusControls(focus); true }
                                    else -> false
                                }
                            } else false
                        }
                    }, onError = { fullscreen = false; message = it },
                    onEnded = {
                        // Ignore duplicate ended events; each queue entry gets exactly one result.
                        if (pendingScoreId == null && selectedId == selected.id) {
                            val result = ScoreGenerator.generateScore()
                            val entry = scoreRepository.saveScore("youtube:${selected.song.videoId}",
                                selected.song.title, selected.song.channel, result.score)
                            pendingScoreId = entry.id
                            scoreFeedback = result.feedback
                            repository.remove(selected.id)
                            selectedId = null
                            fullscreen = false
                            playNextWhenReady = false
                        }
                    })
            }
            if (!fullscreen) {
            if (!ready && message == null) Text("Conectando ao YouTube…", color = TextMuted)
            message?.let { Text(it, color = TextMuted) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(enabled = ready, modifier = Modifier.focusRequester(playFocus),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { fullscreen = true; web?.evaluateJavascript("if(window.player)player.playVideo();", null) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp)); Text("Reproduzir em tela cheia")
                }
                OutlinedButton(enabled = ready, shape = RoundedCornerShape(12.dp), onClick = { web?.evaluateJavascript("if(window.player)player.pauseVideo();", null) }) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Pausar")
                }
                OutlinedButton(shape = RoundedCornerShape(12.dp), onClick = { retry++ }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Tentar novamente")
                }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${selected.song.videoId}"))) }
                    .onFailure { message = "Nenhum aplicativo disponível para abrir o YouTube." }
            }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text("YouTube")
            }
            }
            Text("Ao reproduzir, o vídeo ocupa toda a tela. Use Voltar para acessar os controles.",
                fontFamily = ReferenceBodyFont, color = TextMuted, fontSize = 11.sp)
            }
        } else {
            libraryState.SaveableStateProvider("library") {
            OnlineLibrary(repository, scoreRepository, message, onModes,
                onPlay = { add(it, true) }, onAdd = { add(it, false) },
                onPlayQueued = { selectedId = it; playNextWhenReady = true },
                onMessage = { message = it })
            }

        }
    }
    if (fullscreen && (showControls || !isTv)) {
        Row(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp)
            .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(16.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.focusRequester(overlayFocus), onClick = { web?.evaluateJavascript("if(window.player){if(player.getPlayerState()===1)player.pauseVideo();else player.playVideo();}", null) }) {
                Text("Reproduzir / Pausar")
            }
            OutlinedButton(onClick = { showControls = false; focusControls(fullscreenFocus) }) { Text("Ocultar controles") }
            OutlinedButton(onClick = { fullscreen = false; showControls = false }) { Text("Sair da tela cheia") }
        }
    }
    }
}
