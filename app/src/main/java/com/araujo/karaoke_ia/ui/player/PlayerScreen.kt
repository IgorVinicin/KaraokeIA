@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.araujo.karaoke_ia.score.ScoreGenerator
import com.araujo.karaoke_ia.remote.QrCodeGenerator
import com.araujo.karaoke_ia.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(
    musicCode: String,
        remoteUrl: String? = null,
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
                .background(DarkCharcoalBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ERRO AO CARREGAR",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GoldAccent
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error!!, color = Color.White)
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(GoldPrimary)
                        .clickable { onBack() }
                        .padding(horizontal = 28.dp, vertical = 12.dp)
                ) {
                    Text("VOLTAR", fontWeight = FontWeight.Bold, color = DarkCharcoalBg)
                }
            }
        }
        return
    }

    if (musicFile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCharcoalBg),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = GoldPrimary)
        }
        return
    }

    val currentMusic = musicFile ?: return
    val mediaPath = currentMusic.videoPath?.takeIf { it.isNotBlank() }
    if (mediaPath == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCharcoalBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Arquivo de música inválido ou ausente.", color = Color.White)
        }
        return
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(mediaPath)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val playPauseFocusRequester = remember { FocusRequester() }
    val remoteQr = remember(remoteUrl) { remoteUrl?.let { QrCodeGenerator.createBitmap(it) } }

    BackHandler {
        exoPlayer.stop()
        onBack()
    }

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

    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
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

        if (remoteQr != null) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    bitmap = remoteQr.asImageBitmap(),
                    contentDescription = "Controle remoto",
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))
                )
                Text(
                    text = "Aponte o celular para adicionar músicas",
                    color = Color.White,
                    fontSize = 9.sp
                )
            }
        }

        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x88000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${currentMusic.title} - ${currentMusic.artist}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // Controles OSD
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
                                Color(0x990A0B0E),
                                Color.Transparent,
                                Color(0xF20A0B0E)
                            )
                        )
                    )
                    .padding(32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = currentMusic.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextWhite
                        )
                        Text(
                            text = "${currentMusic.artist} • Código: ${currentMusic.code}",
                            fontSize = 13.5.sp,
                            color = GoldPrimary
                        )
                    }

                    PlayerControlButton(
                        icon = Icons.Default.Close,
                        label = "Sair",
                        onClick = {
                            exoPlayer.stop()
                            onBack()
                        },
                        onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerControlButton(
                            icon = Icons.Default.Replay,
                            label = "Reiniciar",
                            onClick = {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                        )

                        PlayerControlButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            label = if (isPlaying) "Pausar" else "Continuar",
                            isPrimary = true,
                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                        )

                        PlayerControlButton(
                            icon = Icons.Default.Star,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onInteracted: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgBrush = when {
        isFocused -> Brush.horizontalGradient(listOf(GoldGlow, GoldPrimary))
        isPrimary -> Brush.horizontalGradient(listOf(GoldAccent, GoldDark))
        else      -> Brush.horizontalGradient(listOf(Color(0xFF282015), Color(0xFF17110A)))
    }

    val borderColor = when {
        isFocused -> GoldGlow
        isPrimary -> GoldPrimary
        else      -> BronzeBorder
    }

    val contentColor = when {
        isFocused -> DarkCharcoalBg
        isPrimary -> DarkCharcoalBg
        else      -> TextWhite
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
            .padding(horizontal = if (isPrimary) 22.dp else 16.dp, vertical = if (isPrimary) 10.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(if (isPrimary) 18.dp else 15.dp)
            )
            Text(
                text = label,
                fontSize = if (isPrimary) 14.sp else 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor
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
