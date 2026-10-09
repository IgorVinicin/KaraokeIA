package com.araujo.karaoke_ia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Distinct local covers while real artwork is absent. No network, bitmap or animation. */
@Composable
fun ArtworkPlaceholder(artist: String) {
    val colors = remember(artist) {
        val palette = listOf(Color(0xFF7D226D), Color(0xFF384586), Color(0xFF176B70), Color(0xFF823B4C))
        listOf(palette[(artist.hashCode() and Int.MAX_VALUE) % palette.size], Color(0xFF160D2B))
    }
    val initials = remember(artist) { artist.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() } }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)).drawWithCache {
        val bars = List(9) { i ->
            val height = size.height * (.12f + ((i * 7) % 5) * .04f)
            val x = size.width * (.13f + i * .09f)
            Offset(x, size.height - height) to Offset(x, size.height)
        }
        onDrawBehind {
            bars.forEach { (start, end) -> drawLine(Color.White.copy(alpha = .08f), start, end, size.width * .035f) }
        }
    }, contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White.copy(alpha = .8f), fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
    }
}
