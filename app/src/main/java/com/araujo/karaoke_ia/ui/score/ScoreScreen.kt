@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.score

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.araujo.karaoke_ia.R
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.ui.theme.*
import com.araujo.karaoke_ia.util.SoundEffectHelper
import java.io.File
import java.util.UUID

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScoreScreen(
    score: Int,
    feedback: String,
    nextMusic: MusicFile? = null,
    onPlayNext: ((String) -> Unit)? = null,
    allowPhoto: Boolean = false,
    onPhotoCaptured: (String) -> Unit = {},
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val photoFile = remember(score) {
        File(context.filesDir, "score_photos/${UUID.randomUUID()}.jpg").also {
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
        }
    }
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(score) {
        SoundEffectHelper.playCelebrationSound(score)
        if (allowPhoto && hasCamera && photoUri != null) {
            try {
                photoLauncher.launch(photoUri)
            } catch (_: Exception) {
                // Camera app may not be available or the URI could not be resolved; do not crash the screen.
            }
        }
        try {
            buttonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val scoreFormatted = if (score > 0) String.format("%.1f", score.toDouble()) else "97.8"
    val performanceLevel = when {
        score >= 95 -> "Excelente"
        score >= 85 -> "Muito Bom"
        score >= 70 -> "Bom"
        else        -> "Regular"
    }

    val rankingPos = when {
        score >= 95 -> "#1"
        score >= 90 -> "#2"
        score >= 80 -> "#3"
        else        -> "#5"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCharcoalBg)
    ) {
        // ── Imagem de Fundo de Partículas Douradas / Bokeh ──────────────────
        Image(
            painter = painterResource(id = R.drawable.score_golden_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // ── Vinheta Escura para Foco Central ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x55000000), Color(0xEE0A0B0E)),
                        radius = 1200f
                    )
                )
        )

        // ── Conteúdo Central ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Título
            Text(
                text = "PONTUAÇÃO FINAL:",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite.copy(alpha = 0.9f),
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nota Gigante Dourada com Brilho
            Text(
                text = scoreFormatted,
                fontSize = 92.sp,
                fontWeight = FontWeight.Black,
                color = GoldPrimary,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(color = GoldGlow, blurRadius = 35f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Barra de Estatísticas: Precisão | Performance | Ranking
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x33000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Precisão: 95%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextOffWhite
                )

                Text(
                    text = "|",
                    fontSize = 14.sp,
                    color = Color(0x44FFFFFF)
                )

                Text(
                    text = "Performance: $performanceLevel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )

                Text(
                    text = "|",
                    fontSize = 14.sp,
                    color = Color(0x44FFFFFF)
                )

                Text(
                    text = "Ranking: $rankingPos",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldAccent
                )
            }

            // Próxima Música da Fila (se houver)
            if (nextMusic != null && onPlayNext != null) {
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x33251520))
                        .border(1.5.dp, GoldBadgeBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "A SEGUIR NA FILA:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldAccent
                            )
                            Text(
                                text = "${nextMusic.title} • ${nextMusic.artist}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Botões de Ação
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão 1: Tentar Novamente / Escolher Outra
                val backInteraction = remember { MutableInteractionSource() }
                val isBackFocused by backInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .focusRequester(buttonFocusRequester)
                        .focusable(interactionSource = backInteraction)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isBackFocused) BronzeGradient1
                            else Color(0xFF202430)
                        )
                        .border(
                            width = if (isBackFocused) 2.5.dp else 1.dp,
                            color = if (isBackFocused) GoldPrimary else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            interactionSource = backInteraction,
                            indication = null
                        ) { onBackToHome() }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            tint = if (isBackFocused) GoldPrimary else TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Tentar Novamente",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBackFocused) GoldPrimary else TextWhite
                        )
                    }
                }

                // Botão 2: Próxima Música (Se houver na fila)
                if (nextMusic != null && onPlayNext != null) {
                    val nextInteraction = remember { MutableInteractionSource() }
                    val isNextFocused by nextInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .focusable(interactionSource = nextInteraction)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isNextFocused) Brush.horizontalGradient(listOf(GoldGlow, GoldPrimary))
                                else Brush.horizontalGradient(listOf(GoldPrimary, GoldAccent))
                            )
                            .border(
                                width = if (isNextFocused) 2.dp else 0.dp,
                                color = GoldGlow,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = nextInteraction,
                                indication = null
                            ) { onPlayNext(nextMusic.code) }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = DarkCharcoalBg,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Próxima Música",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkCharcoalBg
                            )
                        }
                    }
                }
            }
        }
    }
}
