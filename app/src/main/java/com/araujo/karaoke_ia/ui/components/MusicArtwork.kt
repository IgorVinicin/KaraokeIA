package com.araujo.karaoke_ia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.araujo.karaoke_ia.data.ArtworkRepository
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.ui.theme.DarkCardElevated
import com.araujo.karaoke_ia.ui.theme.TextMuted
import java.io.File

@Composable
fun MusicArtwork(music: MusicFile, repository: ArtworkRepository, modifier: Modifier = Modifier) {
    if (music.isOnline) {
        Box(modifier.background(DarkCardElevated)) {
            ArtworkPlaceholder(music.artist)
            AsyncImage(model = "https://i.ytimg.com/vi/${music.code.removePrefix("youtube:")}/hqdefault.jpg",
                contentDescription = "Capa de ${music.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        return
    }
    val file by produceState<File?>(null, music.title, music.artist, repository) {
        value = repository.getThumbnailFile(music, edge = 256, searchOnline = false)
    }
    Box(modifier.background(DarkCardElevated), contentAlignment = Alignment.Center) {
        ArtworkPlaceholder(music.artist)
        file?.let {
            AsyncImage(model = it, contentDescription = "Capa de ${music.title}",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
