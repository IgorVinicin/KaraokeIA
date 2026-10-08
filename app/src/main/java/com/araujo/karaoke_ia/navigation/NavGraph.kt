package com.araujo.karaoke_ia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
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
import com.araujo.karaoke_ia.data.QueueRepository
import com.araujo.karaoke_ia.data.ScoreRepository
import com.araujo.karaoke_ia.ui.home.HomeScreen
import com.araujo.karaoke_ia.ui.home.HomeViewModel
import com.araujo.karaoke_ia.ui.player.PlayerScreen
import com.araujo.karaoke_ia.ui.player.PlayerViewModel
import com.araujo.karaoke_ia.ui.score.ScoreScreen
import androidx.compose.ui.platform.LocalContext
import com.araujo.karaoke_ia.data.StorageManager
import com.araujo.karaoke_ia.online.ModeScreen
import com.araujo.karaoke_ia.online.OnlineRepository
import com.araujo.karaoke_ia.online.OnlineScreen

sealed class Screen(val route: String) {
    object Home   : Screen("home")
    object Player : Screen("player/{musicCode}") {
        fun createRoute(musicCode: String) = "player/$musicCode"
    }
    object Score  : Screen("score/{score}/{feedback}") {
        fun createRoute(score: Int, feedback: String) = "score/$score/$feedback"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    musicRepository: MusicRepository,
    favoritesRepository: FavoritesRepository,
    historyRepository: HistoryRepository,
    queueRepository: QueueRepository,
        scoreRepository: ScoreRepository,
        artworkRepository: ArtworkRepository,
        remoteUrl: String? = null
) {
    val context = LocalContext.current
    val onlineRepository = remember { OnlineRepository(context.applicationContext) }
    NavHost(
        navController    = navController,
        startDestination = "modes"
    ) {
        composable("modes") {
            ModeScreen(onOffline = {
                navController.navigate(Screen.Home.route)
                val storage = StorageManager(context)
                if (!storage.hasAllFilesPermission()) storage.requestAllFilesPermission()
            }, onOnline = { navController.navigate("online") })
        }
        composable("online") {
            OnlineScreen(onlineRepository, scoreRepository, remoteUrl,
                onModes = { navController.popBackStack("modes", false) })
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
                    artworkRepository = artworkRepository
                )
            )
            HomeScreen(
                onNavigateToPlayer = { code ->
                    navController.navigate(Screen.Player.createRoute(code))
                },
                viewModel = homeViewModel,
                onModes = { navController.popBackStack("modes", false) }
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

            val nextCode = queueRepository.peekNext()
            val nextMusic = nextCode?.let { musicRepository.findMusicByCode(it) }

            key(scoreEntry?.id ?: backStackEntry.id) {
            ScoreScreen(
                score        = score,
                feedback     = feedback,
                nextMusic    = nextMusic,
                allowPhoto   = rankingPosition != null && rankingPosition <= 3,
                rankingPosition = rankingPosition,
                scoredMusic = scoreEntry?.let { musicRepository.findMusicByCode(it.musicCode) },
                artworkRepository = artworkRepository,
                onPhotoCaptured = { path -> scoreEntry?.let { scoreRepository.attachPhotoToScore(it.id, path) } },
                remoteUrl = remoteUrl,
                onPlayNext   = { codeToPlay ->
                    queueRepository.popNext()
                    navController.navigate(Screen.Player.createRoute(codeToPlay)) {
                        popUpTo(Screen.Home.route)
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
