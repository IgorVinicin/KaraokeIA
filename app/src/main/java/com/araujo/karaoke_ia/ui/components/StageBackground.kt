package com.araujo.karaoke_ia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** Cached, static waves: no bitmap decoding, blur layers or continuous frame invalidation. */
fun Modifier.stageBackground(): Modifier = drawWithCache {
    val base = Brush.linearGradient(listOf(Color(0xFF080513), Color(0xFF1E0E38), Color(0xFF11091F)))
    val light = Brush.radialGradient(listOf(Color(0x409B5DE5), Color.Transparent),
        center = Offset(size.width * .87f, size.height * .18f), radius = size.width * .62f)
    val waves = List(5) { index ->
        val y = size.height * (.47f + index * .09f)
        Path().apply {
            moveTo(-size.width * .1f, y)
            cubicTo(size.width * .24f, y - size.height * .3f,
                size.width * .42f, y + size.height * .25f,
                size.width * .68f, y + size.height * .06f)
            cubicTo(size.width * .9f, y - size.height * .1f,
                size.width * 1.03f, y + size.height * .2f,
                size.width * 1.1f, y + size.height * .03f)
        }
    }
    val waveBrush = Brush.horizontalGradient(listOf(Color(0x059B5DE5), Color(0x269B5DE5), Color(0x20FF2DAB), Color(0x059B5DE5)))
    onDrawBehind {
        drawRect(base)
        drawRect(light)
        waves.forEachIndexed { index, path ->
            drawPath(path, waveBrush, style = Stroke(width = size.height * (.025f + index * .006f)))
            drawPath(path, Color(0x129B5DE5), style = Stroke(width = 1.5f))
        }
    }
}

@Composable
fun StageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.stageBackground(), content = content)
}
