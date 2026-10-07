package com.araujo.karaoke_ia.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import android.os.Build
import com.araujo.karaoke_ia.R
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography

@OptIn(ExperimentalTextApi::class)
val ReferenceHeadingFont = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(Font(R.font.unbounded, FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900))))
} else FontFamily.SansSerif

@OptIn(ExperimentalTextApi::class)
val ReferenceBodyFont = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(Font(R.font.manrope,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))))
} else FontFamily.SansSerif

// Set of Material typography styles to start with
@OptIn(ExperimentalTvMaterial3Api::class)
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
