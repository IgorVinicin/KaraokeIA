package com.araujo.karaoke_ia

import android.os.Bundle
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.araujo.karaoke_ia.data.ArtworkRepository
import com.araujo.karaoke_ia.data.FavoritesRepository
import com.araujo.karaoke_ia.data.HistoryRepository
import com.araujo.karaoke_ia.data.MusicRepositoryImpl
import com.araujo.karaoke_ia.data.QueueRepository
import com.araujo.karaoke_ia.data.ScoreRepository
import com.araujo.karaoke_ia.data.StorageManager
import com.araujo.karaoke_ia.remote.RemoteQueueServer
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
    private lateinit var artworkRepository: ArtworkRepository
    private lateinit var remoteQueueServer: RemoteQueueServer

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            storageManager.refreshRootPath()
            musicRepository.clearCache()
            recreate()
        }
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
        artworkRepository   = ArtworkRepository(this)
        remoteQueueServer   = RemoteQueueServer(musicRepository, queueRepository)
        remoteQueueServer.start()

        if (storageManager.rootPath == null) {
            storageManager.refreshRootPath()
        }

        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaReceiver, mediaFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(mediaReceiver, mediaFilter)
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
                            scoreRepository     = scoreRepository,
                            artworkRepository  = artworkRepository,
                            remoteUrl          = remoteQueueServer.localUrl()
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        remoteQueueServer.close()
        try {
            unregisterReceiver(mediaReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver may not have been registered when the activity is finishing after a failed init.
        }
        super.onDestroy()
    }
}
