@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.araujo.karaoke_ia.ui.player

import android.view.ViewGroup
import android.os.Build
import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.araujo.karaoke_ia.score.ScoreGenerator
import com.araujo.karaoke_ia.remote.QrCodeGenerator
import com.araujo.karaoke_ia.ui.theme.*
import com.araujo.karaoke_ia.data.ArtworkRepository
import com.araujo.karaoke_ia.ui.components.MusicArtwork
import com.araujo.karaoke_ia.ui.components.stageBackground
import kotlinx.coroutines.delay

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlayerScreen(
    musicCode: String,
        remoteUrl: String? = null,
    onBack: () -> Unit,
    onFinish: (Int, String) -> Unit,
    viewModel: PlayerViewModel,
    artworkRepository: ArtworkRepository
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val musicFile by viewModel.musicFile
    val error by viewModel.error

    LaunchedEffect(musicCode) {
        viewModel.loadMusic(musicCode)
    }

    if (error != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .stageBackground(),
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
                .stageBackground(),
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
                .stageBackground(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Arquivo de música inválido ou ausente.", color = Color.White)
        }
        return
    }

    var savedPosition by rememberSaveable(mediaPath) { mutableLongStateOf(0L) }
    val exoPlayer = remember(mediaPath) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        if (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
            // The emulator's goldfish AVC decoder can crash its vendor codec service.
            // Keep physical devices and secure/tunneled playback on the default selector.
            renderersFactory.setMediaCodecSelector { mimeType, secure, tunneling ->
                val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                if (mimeType == "video/avc" && !secure && !tunneling &&
                    decoders.any { it.softwareOnly }
                ) {
                    decoders.filterNot { it.name.contains("goldfish") }
                } else {
                    decoders
                }
            }
        }
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            val mediaItem = MediaItem.fromUri(mediaPath)
            setMediaItem(mediaItem)
            seekTo(savedPosition)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var playbackError by remember(mediaPath) { mutableStateOf<String?>(null) }
    var completed by remember(mediaPath) { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinish)

    val playPauseFocusRequester = remember { FocusRequester() }
    val playbackRetryFocusRequester = remember { FocusRequester() }
    val remoteQr = remember(remoteUrl) { remoteUrl?.let { QrCodeGenerator.createBitmap(it) } }

    BackHandler {
        exoPlayer.stop()
        onBack()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(exoPlayer, lifecycleOwner) {
        var resumePlayback = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                savedPosition = exoPlayer.currentPosition
                resumePlayback = exoPlayer.isPlaying
                exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_RESUME && resumePlayback) {
                resumePlayback = false
                exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !completed) {
                    completed = true
                    val result = ScoreGenerator.generateScore()
                    finish(result.score, result.feedback)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Não foi possível reproduzir este vídeo. Verifique o arquivo e o formato suportado pelo aparelho."
                showControls = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            savedPosition = exoPlayer.currentPosition
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Request focus after AnimatedVisibility has composed the controls.
    LaunchedEffect(showControls, playbackError, isTv) {
        if (isTv && showControls && playbackError == null) {
            playPauseFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(playbackError, isTv) {
        if (isTv && playbackError != null) {
            withFrameNanos { }
            playbackRetryFocusRequester.requestFocus()
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
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    bitmap = remoteQr.asImageBitmap(),
                    contentDescription = "Controle remoto",
                    modifier = Modifier.size(if (isTv) 120.dp else 64.dp).clip(RoundedCornerShape(8.dp))
                )
                if (isTv) Text(
                    text = "Aponte o celular para adicionar músicas",
                    color = Color.White,
                    fontSize = 9.sp
                )
            }
        }

        // Keep the title off the lyrics until the remote reveals the controls.
        if (showControls) Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 20.dp, start = 24.dp, end = if (remoteQr != null) { if (isTv) 240.dp else 112.dp } else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSlateSurface.copy(alpha = 0.8f))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${currentMusic.title} - ${currentMusic.artist}",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        .align(Alignment.TopCenter)
                        .padding(end = if (remoteQr != null) { if (isTv) 220.dp else 90.dp } else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        MusicArtwork(currentMusic, artworkRepository, Modifier.size(if (isTv) 48.dp else 40.dp).clip(RoundedCornerShape(8.dp)))
                        Text(
                            text = currentMusic.title,
                            fontSize = if (isTv) 22.sp else 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextWhite
                        )
                        Text(
                            text = "${currentMusic.artist} • Código: ${currentMusic.code}",
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                if (completed) return@PlayerControlButton
                                completed = true
                                val result = ScoreGenerator.generateScore()
                                onFinish(result.score, result.feedback)
                            },
                            onInteracted = { lastInteractionTime = System.currentTimeMillis() }
                        )
                    }
                }
            }
        }
        playbackError?.let { message ->
            Column(Modifier.align(Alignment.Center).background(Color(0xDD09090E)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = TextWhite, modifier = Modifier.widthIn(max = 420.dp))
                androidx.compose.material3.Button(
                    modifier = Modifier.focusRequester(playbackRetryFocusRequester),
                    onClick = { playbackError = null; exoPlayer.prepare(); exoPlayer.play() }
                ) {
                    androidx.compose.material3.Text("Tentar novamente")
                }
                androidx.compose.material3.Button(onClick = onBack) { androidx.compose.material3.Text("Voltar") }
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

    val bgBrush = Brush.horizontalGradient(listOf(if (isFocused || isPrimary) GoldPrimary else DarkCardElevated,
        if (isFocused || isPrimary) GoldPrimary else DarkCardElevated))

    val borderColor = when {
        isFocused -> GoldGlow
        isPrimary -> GoldPrimary
        else      -> BronzeBorder
    }

    val contentColor = TextWhite

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .border(if (isFocused) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
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
