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
        onPrimary    = Color.White,         // Texto branco sobre botões magenta
        onSecondary  = Color.White,
        onBackground = TextWhite,
        onSurface    = TextOffWhite
    )

    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = GoldPrimary,
            secondary = GoldAccent,
            tertiary = BronzeBorder,
            background = DarkCharcoalBg,
            surface = DarkCardSurface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = TextWhite,
            onSurface = TextOffWhite
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
