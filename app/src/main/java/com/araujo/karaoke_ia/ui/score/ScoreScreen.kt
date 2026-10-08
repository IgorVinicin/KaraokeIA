@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.score

import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.ArtworkRepository
import com.araujo.karaoke_ia.ui.components.MusicArtwork
import com.araujo.karaoke_ia.ui.components.stageBackground
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.araujo.karaoke_ia.ui.theme.*
import com.araujo.karaoke_ia.util.SoundEffectHelper
import java.io.File
import java.util.UUID
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.araujo.karaoke_ia.remote.QrCodeGenerator
import com.araujo.karaoke_ia.remote.ScorePhotoExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScoreScreen(
    score: Int,
    feedback: String,
    nextMusic: MusicFile? = null,
    onPlayNext: ((String) -> Unit)? = null,
    allowPhoto: Boolean = false,
    rankingPosition: Int? = null,
    scoredMusic: MusicFile? = null,
    artworkRepository: ArtworkRepository? = null,
    remoteUrl: String? = null,
    onPhotoCaptured: (String) -> Unit = {},
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val photoName = rememberSaveable { UUID.randomUUID().toString() }
    var photoRequested by rememberSaveable { mutableStateOf(false) }
    var capturedPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var exportReady by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf(false) }
    var exportAttempt by remember { mutableStateOf(0) }
    val photoFile = remember(photoName) {
        File(context.filesDir, "score_photos/$photoName.jpg").also {
            it.parentFile?.mkdirs()
        }
    }
    val photoUri = remember(photoFile) {
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured ->
        if (captured && photoFile.exists() && photoFile.length() > 0) {
            onPhotoCaptured(photoFile.absolutePath)
            capturedPhoto = photoFile.absolutePath
        }
    }
    val openCamera: () -> Unit = {
        if (photoUri != null) {
            try { photoLauncher.launch(photoUri) } catch (_: Exception) { }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) openCamera() }
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(capturedPhoto, exportAttempt) {
        val source = capturedPhoto ?: return@LaunchedEffect
        exportError = false
        try {
            withContext(Dispatchers.IO) {
                ScorePhotoExport.create(context, File(source), photoName, score,
                    scoredMusic?.title ?: "Minha apresentação", rankingPosition)
            }
            exportReady = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { exportError = true }
    }
    val downloadUrl = if (exportReady) remoteUrl?.trimEnd('/')?.let { "$it/photo/$photoName" } else null
    val photoQr = remember(downloadUrl) { downloadUrl?.let { QrCodeGenerator.createBitmap(it) } }

    LaunchedEffect(score) {
        SoundEffectHelper.playCelebrationSound(score)
        if (allowPhoto && hasCamera && photoUri != null && !photoRequested) {
            photoRequested = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        try {
            buttonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    Column(
        modifier = Modifier.fillMaxSize().stageBackground()
            .verticalScroll(rememberScrollState()).padding(if (isTv) 32.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(if (isTv) 12.dp else 24.dp))
        Text("PALCO · RESULTADO", fontFamily = ReferenceHeadingFont, fontSize = 18.sp, color = GoldPrimary)
        if (scoredMusic != null) {
            if (artworkRepository != null) MusicArtwork(scoredMusic, artworkRepository, Modifier.size(if (isTv) 72.dp else 88.dp).clip(RoundedCornerShape(12.dp)))
            Text(scoredMusic.title, color = TextWhite, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text(scoredMusic.artist, color = TextMuted, fontSize = 13.sp)
        }
        Column(Modifier.widthIn(min = 240.dp, max = 480.dp)
            .background(DarkCardSurface.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
            .border(1.dp, GoldPrimary.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
            .padding(horizontal = 36.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SUA PONTUAÇÃO", fontFamily = ReferenceBodyFont, fontSize = 11.sp,
                letterSpacing = 2.sp, color = TextMuted)
            Text("$score", fontFamily = ReferenceHeadingFont, fontSize = if (isTv) 56.sp else 44.sp,
                fontWeight = FontWeight.Bold, color = TextWhite)
            Text("pontos", color = GoldPrimary, fontSize = 14.sp)
        }
        Text("Pontuação recreativa; não mede afinação ou precisão da voz.",
            fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center)
        Text(feedback, fontSize = 15.sp, color = TextWhite, textAlign = TextAlign.Center)
        rankingPosition?.let { Text("${it}º no ranking", fontSize = 15.sp, color = TextWhite) }
        if (capturedPhoto != null) {
            Text("Sua foto com moldura colorida", color = TextWhite, fontSize = 16.sp)
            if (photoQr != null) {
                Image(photoQr.asImageBitmap(), "QR Code para baixar sua foto",
                    Modifier.size(180.dp).background(Color.White).padding(8.dp))
                Text("Escaneie para baixar sua foto. Use a mesma rede Wi-Fi da TV.",
                    color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            } else if (exportError) {
                Text("Não foi possível preparar a imagem.", color = TextMuted)
                ResultButton("Tentar novamente", onClick = { exportAttempt++ })
            } else {
                Text(if (exportReady) "Conecte o aparelho à rede para disponibilizar o QR Code." else "Preparando sua foto…",
                    color = TextMuted, textAlign = TextAlign.Center)
            }
        }
        ResultButton("Escolher outra música", onBackToHome,
            Modifier.focusRequester(buttonFocusRequester))
        if (nextMusic != null && onPlayNext != null) {
            Text("A seguir: ${nextMusic.title} · ${nextMusic.artist}", color = TextMuted,
                fontSize = 13.sp, textAlign = TextAlign.Center)
            ResultButton("Cantar próxima", onClick = { onPlayNext(nextMusic.code) })
        }
    }
}

@Composable
private fun ResultButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    androidx.compose.material3.Button(onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).onFocusChanged { focused = it.hasFocus }
            .border(if (focused) 2.dp else 1.dp,
                if (focused) TextWhite else GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(24.dp))) {
        androidx.compose.material3.Text(label, fontFamily = ReferenceBodyFont)
    }
}
