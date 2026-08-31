package com.araujo.karaoke_ia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.araujo.karaoke_ia.data.FavoritesRepository
import com.araujo.karaoke_ia.data.HistoryRepository
import com.araujo.karaoke_ia.data.MusicRepositoryImpl
import com.araujo.karaoke_ia.data.QueueRepository
import com.araujo.karaoke_ia.data.ScoreRepository
import com.araujo.karaoke_ia.data.StorageManager
import com.araujo.karaoke_ia.navigation.NavGraph
import com.araujo.karaoke_ia.ui.theme.KaraokeIATheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var storageManager: StorageManager
    private lateinit var musicRepository: MusicRepositoryImpl
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var scoreRepository: ScoreRepository

    private fun findExternalStoragePath(): String {
        val storageDir = File("/storage")
        val externalDrive = storageDir.listFiles()
            ?.find { it.isDirectory && it.name != "emulated" && it.name != "self" }

        if (externalDrive != null) {
            listOf("MUSICAS", "musicas", "music", "Music", "KARAOKE", "Karaoke")
                .map { File(externalDrive, it) }
                .firstOrNull { it.exists() }
                ?.let { return it.absolutePath }

            return externalDrive.absolutePath
        }

        val extStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
        val possiblePaths = listOf(
            "$extStorage/Download/MUSICAS",
            "$extStorage/Download/musicas",
            "$extStorage/MUSICAS",
            "$extStorage/musicas",
            "$extStorage/Music",
            "/storage/emulated/0/Download/MUSICAS",
            "/storage/emulated/0/MUSICAS"
        )

        return possiblePaths.firstOrNull { File(it).exists() }
            ?: "$extStorage/MUSICAS"
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa repositórios
        storageManager      = StorageManager(this)
        musicRepository     = MusicRepositoryImpl(storageManager)
        favoritesRepository = FavoritesRepository(this)
        historyRepository   = HistoryRepository(this)
        queueRepository     = QueueRepository(this)
        scoreRepository     = ScoreRepository(this)

        if (storageManager.rootPath == null) {
            storageManager.rootPath = findExternalStoragePath()
        }

        if (!storageManager.hasAllFilesPermission()) {
            storageManager.requestAllFilesPermission()
        }

        setContent {
            KaraokeIATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape    = RectangleShape
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController       = navController,
                        musicRepository     = musicRepository,
                        favoritesRepository = favoritesRepository,
                        historyRepository   = historyRepository,
                        queueRepository     = queueRepository,
                        scoreRepository     = scoreRepository
                    )
                }
            }
        }
    }
}
