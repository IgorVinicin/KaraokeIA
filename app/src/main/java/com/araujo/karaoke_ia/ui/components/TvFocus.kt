package com.araujo.karaoke_ia.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A single focus observer, placed before the clickable/button's focus target.
 * Animates only during interaction; idle screens have no animation loop. */
fun Modifier.tvFocusFeedback(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, tween(130), label = "tvFocus")
    onFocusChanged { focused = it.hasFocus }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(if (focused) 3.dp else 0.dp,
            if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
}
