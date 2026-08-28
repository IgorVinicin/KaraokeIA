@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.araujo.karaoke_ia.score.ScoreGenerator
import com.araujo.karaoke_ia.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(
    musicCode: String,
    onBack: () -> Unit,
    onFinish: (Int, String) -> Unit,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val musicFile by viewModel.musicFile
    val error by viewModel.error

    LaunchedEffect(musicCode) {
        viewModel.loadMusic(musicCode)
    }

    if (error != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ERRO AO CARREGAR",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error!!, color = Color.White)
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(NeonPurple, NeonMagenta)))
                        .clickable { onBack() }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("VOLTAR", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        return
    }

    if (musicFile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepSpace, NightBlue))),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = NeonMagenta)
        }
        return
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(musicFile!!.videoPath!!)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    // Estados do Player e OSD
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var showControls by remember { mutableStateOf(false) }
    var showInitialTitle by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val playPauseFocusRequester = remember { FocusRequester() }

    // Intercepta botão voltar do controle para fechar player com segurança
    BackHandler {
        exoPlayer.stop()
        onBack()
    }

    // Listener do ExoPlayer para fim da música e mudanças de estado
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val result = ScoreGenerator.generateScore()
                    onFinish(result.score, result.feedback)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.release()
        }
    }

    // Atualização contínua do tempo e barra de progresso
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(1L)
            delay(500)
        }
    }

    // Timer para sumir com os controles OSD após 4 segundos
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Timer para sumir com o título inicial após 5 segundos
    LaunchedEffect(Unit) {
        delay(5000)
        showInitialTitle = false
    }

    fun triggerControls() {
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        android.view.KeyEvent.KEYCODE_SPACE -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            triggerControls()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            exoPlayer.play()
                            triggerControls()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            exoPlayer.pause()
                            triggerControls()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!showControls) {
                                triggerControls()
                                try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else {
                                lastInteractionTime = System.currentTimeMillis()
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                triggerControls()
            }
    ) {
        // Vídeo ExoPlayer em tela cheia
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay de Informação da Música ao Iniciar
        AnimatedVisibility(
            visible = showInitialTitle && !showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column {
                    Text(
                        text = "🎵 ${musicFile!!.title.uppercase()}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🎤 ${musicFile!!.artist.uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Overlay OSD de Controles Interativos (Aparece ao apertar botões do controle)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xAA000000),
                                Color.Transparent,
                                Color(0xEE050015)
                            )
                        )
                    )
                    .padding(32.dp)
            ) {
                // Top Bar: Título e Código
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = musicFile!!.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${musicFile!!.artist} • Código: ${musicFile!!.code}",
                            fontSize = 14.sp,
                            color = NeonMagenta
                        )
                    }

                    // Botão rápido fechar/voltar
                    PlayerControlButton(
                        icon = "❌",
                        label = "Sair",
                        onClick = {
                            exoPlayer.stop()
                            onBack()
                        },
                        onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                    )
                }

                // Bottom Bar: Barra de Progresso + Botões de Controle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Barra de Progresso de Tempo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(NeonPurple, NeonMagenta, DiscoGold)))
                            )
                        }

                        Text(
                            text = formatTime(durationMs),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Botões de Ação
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Reiniciar
                        PlayerControlButton(
                            icon = "⏮",
                            label = "Reiniciar",
                            onClick = {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                        )

                        // 2. Play / Pause
                        PlayerControlButton(
                            icon = if (isPlaying) "⏸" else "▶",
                            label = if (isPlaying) "Pausar" else "Continuar",
                            isPrimary = true,
                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                        )

                        // 3. Encerrar e Ver Nota
                        PlayerControlButton(
                            icon = "🏁",
                            label = "Ver Nota",
                            onClick = {
                                exoPlayer.stop()
                                val result = ScoreGenerator.generateScore()
                                onFinish(result.score, result.feedback)
                            },
                            onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    onInteracted: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgBrush = when {
        isFocused -> Brush.horizontalGradient(listOf(NeonMagenta, NeonPurple))
        isPrimary -> Brush.horizontalGradient(listOf(NeonPurple, Color(0xFF6B00CC)))
        else      -> Brush.horizontalGradient(listOf(Color(0x552A0060), Color(0x55160030)))
    }

    val borderColor = when {
        isFocused -> DiscoGold
        isPrimary -> NeonMagenta
        else      -> Color.White.copy(alpha = 0.25f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .border(if (isFocused) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onInteracted()
                onClick()
            }
            .padding(horizontal = if (isPrimary) 22.dp else 16.dp, vertical = if (isPrimary) 12.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, fontSize = if (isPrimary) 18.sp else 15.sp)
            Text(
                text = label,
                fontSize = if (isPrimary) 15.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
