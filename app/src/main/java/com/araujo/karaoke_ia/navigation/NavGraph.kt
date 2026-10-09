package com.araujo.karaoke_ia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.araujo.karaoke_ia.data.ArtworkRepository
import com.araujo.karaoke_ia.data.FavoritesRepository
import com.araujo.karaoke_ia.data.HistoryRepository
import com.araujo.karaoke_ia.data.MusicRepository
import com.araujo.karaoke_ia.data.MusicFile
import com.araujo.karaoke_ia.data.QueueRepository
import com.araujo.karaoke_ia.data.ScoreRepository
import com.araujo.karaoke_ia.ui.home.HomeScreen
import com.araujo.karaoke_ia.ui.home.HomeViewModel
import com.araujo.karaoke_ia.ui.player.PlayerScreen
import com.araujo.karaoke_ia.ui.player.PlayerViewModel
import com.araujo.karaoke_ia.ui.score.ScoreScreen
import androidx.compose.ui.platform.LocalContext
import com.araujo.karaoke_ia.data.StorageManager
import com.araujo.karaoke_ia.online.OnlineRepository
import com.araujo.karaoke_ia.online.OnlineScreen
import com.araujo.karaoke_ia.online.OnlineSong
import com.araujo.karaoke_ia.ui.components.SearchConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Screen(val route: String) {
    object Home   : Screen("home")
    object Player : Screen("player/{musicCode}") {
        fun createRoute(musicCode: String) = "player/${Uri.encode(musicCode)}"
    }
    object Score  : Screen("score/{score}/{feedback}") {
        fun createRoute(score: Int, feedback: String) = "score/$score/$feedback"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    musicRepository: MusicRepository,
    onlineRepository: OnlineRepository,
    favoritesRepository: FavoritesRepository,
    historyRepository: HistoryRepository,
    queueRepository: QueueRepository,
        scoreRepository: ScoreRepository,
        artworkRepository: ArtworkRepository,
        remoteUrl: String? = null,
        storageRevision: Int = 0
) {
    val context = LocalContext.current
    fun playSong(code: String) {
        val videoId = code.removePrefix("youtube:")
        val route = if (code.startsWith("youtube:") && OnlineRepository.validId(videoId)) "youtube/$videoId"
            else Screen.Player.createRoute(code)
        navController.navigate(route) { launchSingleTop = true }
    }
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route,
        enterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(100)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { fadeOut(tween(100)) }
    ) {
        composable("youtube/{videoId}", arguments = listOf(navArgument("videoId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("videoId") ?: ""
            val music = remember(id) { musicRepository.findMusicByCode("youtube:$id") }
            OnlineScreen(onlineRepository, scoreRepository, remoteUrl,
                onModes = { navController.popBackStack() },
                initialSong = OnlineSong(id, music?.title ?: "Karaokê do YouTube", music?.artist ?: "YouTube"),
                onSongPlayed = { historyRepository.addToHistory("youtube:$id") },
                onPerformanceFinished = { score, feedback ->
                    queueRepository.popNextIfMatches("youtube:$id")
                    navController.navigate(Screen.Score.createRoute(score, feedback)) { popUpTo(Screen.Home.route) }
                })
        }
        // ── Home ──────────────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    musicRepository    = musicRepository,
                    favoritesRepo      = favoritesRepository,
                    historyRepo        = historyRepository,
                    queueRepo          = queueRepository,
                    scoreRepo          = scoreRepository,
                    artworkRepository = artworkRepository,
                    onlineRepository = onlineRepository
                )
            )
            LaunchedEffect(storageRevision) {
                homeViewModel.onStorageChanged(storageRevision)
            }
            HomeScreen(
                onNavigateToPlayer = { code ->
                    if (homeViewModel.prepareSong(code)) playSong(code)
                },
                viewModel = homeViewModel,
                onStorageAccess = { StorageManager(context).requestAllFilesPermission() },
                searchSettings = { SearchConfiguration(onlineRepository) }
            )
        }

        // ── Player ────────────────────────────────────────────────────────────
        composable(
            route     = Screen.Player.route,
            arguments = listOf(navArgument("musicCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val musicCode = backStackEntry.arguments?.getString("musicCode") ?: ""

            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModel.Factory(
                    repository        = musicRepository,
                    historyRepository = historyRepository,
                    scoreRepository   = scoreRepository
                )
            )

            PlayerScreen(
                musicCode = musicCode,
                    remoteUrl = remoteUrl,
                viewModel = playerViewModel,
                artworkRepository = artworkRepository,
                onBack    = { navController.popBackStack() },
                onFinish  = { score, feedback ->
                    playerViewModel.recordScore(score)
                    queueRepository.popNextIfMatches(musicCode)
                    navController.navigate(Screen.Score.createRoute(score, feedback)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // ── Score ─────────────────────────────────────────────────────────────
        composable(
            route     = Screen.Score.route,
            arguments = listOf(
                navArgument("score")    { type = NavType.IntType },
                navArgument("feedback") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val score    = backStackEntry.arguments?.getInt("score")       ?: 0
            val feedback = backStackEntry.arguments?.getString("feedback") ?: ""
            val scoreEntry = remember(backStackEntry) { scoreRepository.getAllScores().firstOrNull() }
            val rankingPosition = remember(scoreEntry) {
                scoreEntry?.let { scoreRepository.getPodiumPosition(it.id) }
            }

            var queuedCodes by remember(queueRepository) { mutableStateOf(queueRepository.getQueue()) }
            DisposableEffect(queueRepository) {
                val subscription = queueRepository.observeChanges { queuedCodes = queueRepository.getQueue() }
                queuedCodes = queueRepository.getQueue()
                onDispose { subscription.close() }
            }
            val nextCode = queuedCodes.firstOrNull()
            val nextMusic by key(nextCode) {
                produceState<MusicFile?>(null, nextCode, musicRepository) {
                    value = nextCode?.let { code -> withContext(Dispatchers.IO) { musicRepository.findMusicByCode(code) } }
                }
            }
            val scoredMusic by key(scoreEntry?.id) {
                produceState<MusicFile?>(null, scoreEntry?.id, musicRepository) {
                    value = scoreEntry?.let { entry -> withContext(Dispatchers.IO) { musicRepository.findMusicByCode(entry.musicCode) } }
                }
            }

            key(scoreEntry?.id ?: backStackEntry.id) {
            ScoreScreen(
                score        = score,
                feedback     = feedback,
                nextMusic    = nextMusic,
                allowPhoto   = rankingPosition != null && rankingPosition <= 3,
                rankingPosition = rankingPosition,
                scoredMusic = scoredMusic,
                artworkRepository = artworkRepository,
                onPhotoCaptured = { path -> scoreEntry?.let { scoreRepository.attachPhotoToScore(it.id, path) } },
                remoteUrl = remoteUrl,
                onPlayNext   = { codeToPlay ->
                    val next = nextCode?.takeIf { nextMusic?.code == codeToPlay }
                        ?.let { queueRepository.popNextIfMatches(it) }
                    if (next != null) {
                        navController.popBackStack(Screen.Home.route, false)
                        playSong(codeToPlay)
                    }
                },
                onBackToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
            }
        }
    }
}
