package com.araujo.karaoke_ia.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KaraokeIATheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary      = NeonPurple,          // roxo neon — botões principais, seleção
        secondary    = NeonMagenta,         // magenta neon — logo accent, ícones
        tertiary     = DiscoGold,           // dourado — estrela, pontos
        background   = DiscoBg,            // fundo geral quase preto
        surface      = DiscoCard,          // superfície de cards
        onPrimary    = Color.White,
        onSecondary  = Color.White,
        onBackground = Color.White,
        onSurface    = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
