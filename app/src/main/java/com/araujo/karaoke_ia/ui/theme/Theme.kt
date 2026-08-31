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
        primary      = GoldPrimary,         // Dourado primário — foco, seleção
        secondary    = GoldAccent,          // Dourado âmbar — destaques
        tertiary     = BronzeBorder,        // Bronze
        background   = DarkCharcoalBg,      // Fundo ardósia escuro
        surface      = DarkCardSurface,     // Superfície de cards
        onPrimary    = DarkCharcoalBg,      // Texto escuro sobre botão dourado
        onSecondary  = Color.White,
        onBackground = TextWhite,
        onSurface    = TextOffWhite
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
