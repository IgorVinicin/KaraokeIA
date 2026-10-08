package com.araujo.karaoke_ia.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.araujo.karaoke_ia.ui.theme.ReferenceBodyFont
import com.araujo.karaoke_ia.ui.theme.ReferenceHeadingFont
import com.araujo.karaoke_ia.ui.components.stageBackground

private val ModePink = Color(0xFFFF2699)
private val ModeWhite = Color(0xFFF4F3FA)
private val ModeMuted = Color(0xFF9798B5)

@Composable
fun ModeScreen(onOffline: () -> Unit, onOnline: () -> Unit) {
    val onlineFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; onlineFocus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().stageBackground().safeDrawingPadding()) {
        val wide = maxWidth >= 600.dp
        val scale = if (wide) (maxHeight.value / 815f).coerceIn(0.6f, 1f) else 0.85f
        val cardMaxWidth = if (wide) 896.dp * (maxWidth.value / 1175f).coerceAtMost(1f) else 480.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (wide) 48.dp * scale else 24.dp, vertical = 28.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(56.dp * scale).background(ModePink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp * scale))
            }
            Spacer(Modifier.height(18.dp * scale))
            Text("KARAOKÊ", fontFamily = ReferenceHeadingFont, fontSize = (20f * scale).sp, color = ModeWhite)
            Spacer(Modifier.height(26.dp * scale))
            Text("Como você quer cantar?", fontFamily = ReferenceHeadingFont,
                fontSize = (if (wide) 48f * scale else 28f).sp, lineHeight = (if (wide) 60f * scale else 38f).sp,
                color = ModeWhite, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp * scale))
            Text("Escolha onde buscar suas músicas nesta sessão.", fontFamily = ReferenceBodyFont,
                fontSize = (16f * scale).sp, color = ModeMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp * scale))
            val cards = Modifier.widthIn(max = cardMaxWidth).fillMaxWidth()
            if (wide) {
                Row(cards, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    ModeCard("Modo online", "Pesquisa de vídeos de karaokê no YouTube.", "ENTRAR ONLINE",
                        Icons.Outlined.Wifi, true, scale, onOnline, Modifier.weight(1f).focusRequester(onlineFocus))
                    ModeCard("Modo offline", "Use somente as músicas disponíveis no catálogo local.", "USAR OFFLINE",
                        Icons.Outlined.WifiOff, false, scale, onOffline, Modifier.weight(1f))
                }
            } else {
                Column(cards, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModeCard("Modo online", "Pesquisa de vídeos de karaokê no YouTube.", "ENTRAR ONLINE",
                        Icons.Outlined.Wifi, true, scale, onOnline, Modifier.fillMaxWidth().focusRequester(onlineFocus))
                    ModeCard("Modo offline", "Use somente as músicas disponíveis no catálogo local.", "USAR OFFLINE",
                        Icons.Outlined.WifiOff, false, scale, onOffline, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, action: String, icon: ImageVector,
    online: Boolean, scale: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp * scale)
    Surface(onClick = onClick, modifier = modifier.onFocusChanged { focused = it.hasFocus }
        .clip(shape).border(if (focused) 2.dp else 1.dp,
            if (focused || online) ModePink.copy(alpha = if (focused) 1f else 0.6f) else Color(0xFF27232F), shape),
        shape = shape, color = if (online) Color(0xFF1D0615) else Color(0xFF0D0B14)) {
        Column(Modifier.padding(28.dp * scale).heightIn(min = 176.dp * scale)) {
            Box(Modifier.size(48.dp * scale).background(if (online) ModePink else Color(0xFF25212D), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ModeWhite, modifier = Modifier.size(26.dp * scale))
            }
            Spacer(Modifier.height(22.dp * scale))
            Text(title, fontFamily = ReferenceHeadingFont, fontSize = (20f * scale).sp, color = ModeWhite)
            Spacer(Modifier.height(10.dp * scale))
            Text(description, fontFamily = ReferenceBodyFont, fontSize = (14f * scale).sp,
                lineHeight = (21f * scale).sp, color = ModeMuted, minLines = 2)
            Spacer(Modifier.height(24.dp * scale))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(action, fontFamily = ReferenceBodyFont, fontWeight = FontWeight.Bold,
                    fontSize = (13f * scale).sp, color = ModeWhite)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                    tint = ModeWhite, modifier = Modifier.size(16.dp * scale))
            }
        }
    }
}
