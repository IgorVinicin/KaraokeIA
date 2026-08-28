@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.araujo.karaoke_ia.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.ui.theme.*
import com.araujo.karaoke_ia.util.SoundEffectHelper

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScoreScreen(
    score: Int,
    feedback: String,
    nextMusic: MusicFile? = null,
    onPlayNext: ((String) -> Unit)? = null,
    onBackToHome: () -> Unit
) {
    val buttonFocusRequester = remember { FocusRequester() }

    // Toca efeito sonoro comemorativo baseado na pontuação ao abrir a tela
    LaunchedEffect(score) {
        SoundEffectHelper.playCelebrationSound(score)
        try {
            buttonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val scoreColor = when {
        score >= 90 -> DiscoGold
        score >= 80 -> DiscoGreen
        else        -> NeonMagenta
    }

    val emojiBadge = when {
        score >= 96 -> "👑 ESTRELA DA NOITE 🌟"
        score >= 90 -> "🔥 SHOW INCRÍVEL 🎉"
        score >= 80 -> "👏 MUITO BOM 🎤"
        else        -> "💪 CONTINUE PRATICANDO 🎵"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A0060), DiscoBg),
                    radius = 1600f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badge comemorativa
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0x33FFFFFF))
                    .border(1.5.dp, scoreColor.copy(alpha = 0.7f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = emojiBadge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = scoreColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SUA PONTUAÇÃO",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nota com Brilho Intenso e Círculo
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color(0x33000000))
                    .border(4.dp, scoreColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = score.toString(),
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Black,
                        color = scoreColor,
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(color = scoreColor, blurRadius = 25f)
                        )
                    )
                    Text(
                        text = "PONTOS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feedback em um Card Glass
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feedback,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            // Próxima Música da Fila (se houver)
            if (nextMusic != null && onPlayNext != null) {
                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x3300FF88))
                        .border(1.5.dp, DiscoGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📋 A SEGUIR NA FILA:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DiscoGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${nextMusic.title} • ${nextMusic.artist}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botões de Ação
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Se houver próxima na fila, botão de cantar próxima
                if (nextMusic != null && onPlayNext != null) {
                    val playNextInteraction = remember { MutableInteractionSource() }
                    val isNextFocused by playNextInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .focusRequester(buttonFocusRequester)
                            .focusable(interactionSource = playNextInteraction)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isNextFocused) Brush.horizontalGradient(listOf(DiscoGreen, Color(0xFF00AA55)))
                                else Brush.horizontalGradient(listOf(Color(0xFF00AA55), Color(0xFF006633)))
                            )
                            .border(
                                width = if (isNextFocused) 3.dp else 1.dp,
                                color = if (isNextFocused) DiscoGold else Color.White.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable(
                                interactionSource = playNextInteraction,
                                indication = null
                            ) { onPlayNext(nextMusic.code) }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("▶", fontSize = 16.sp)
                            Text(
                                text = "CANTAR PRÓXIMA DA FILA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Botão "VOLTAR AO CATÁLOGO"
                val homeInteraction = remember { MutableInteractionSource() }
                val isHomeFocused by homeInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .then(if (nextMusic == null) Modifier.focusRequester(buttonFocusRequester) else Modifier)
                        .focusable(interactionSource = homeInteraction)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isHomeFocused) Brush.horizontalGradient(listOf(NeonMagenta, NeonPurple))
                            else if (nextMusic == null) Brush.horizontalGradient(listOf(NeonPurple, Color(0xFF6B00CC)))
                            else Brush.horizontalGradient(listOf(Color(0x44FFFFFF), Color(0x22FFFFFF)))
                        )
                        .border(
                            width = if (isHomeFocused) 3.dp else 1.dp,
                            color = if (isHomeFocused) DiscoGold else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable(
                            interactionSource = homeInteraction,
                            indication = null
                        ) { onBackToHome() }
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎤", fontSize = 16.sp)
                        Text(
                            text = if (nextMusic != null) "VOLTAR AO CATÁLOGO" else "ESCOLHER NOVA MÚSICA",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
