package com.araujo.karaoke_ia.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.araujo.karaoke_ia.R

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.soltavoz_mark), "Logo SoltaVoz", modifier)
}
