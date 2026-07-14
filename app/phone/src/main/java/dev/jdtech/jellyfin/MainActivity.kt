package dev.jdtech.jellyfin

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.viewmodels.DeepLinkViewModel
import dev.jdtech.jellyfin.viewmodels.MainViewModel

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val deepLinkViewModel: DeepLinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        intent?.data?.let { deepLinkViewModel.resolve(it) }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val deepLinkTarget by deepLinkViewModel.target.collectAsStateWithLifecycle()

            FindroidTheme(dynamicColor = state.isDynamicColors) {
                val navController = rememberNavController()
                if (!state.isLoading) {
                    CompositionLocalProvider(LocalOfflineMode provides state.isOfflineMode) {
                        NavigationRoot(
                            navController = navController,
                            hasServers = state.hasServers,
                            hasCurrentServer = state.hasCurrentServer,
                            hasCurrentUser = state.hasCurrentUser,
                        )
                    }
                    LaunchedEffect(deepLinkTarget) {
                        when (val target = deepLinkTarget) {
                            is FindroidShow ->
                                navController.navigate(ShowRoute(showId = target.id.toString()))
                            is FindroidSeason ->
                                navController.navigate(
                                    SeasonRoute(seasonId = target.id.toString())
                                )
                            is FindroidEpisode ->
                                navController.navigate(
                                    EpisodeRoute(episodeId = target.id.toString())
                                )
                            else -> Unit
                        }
                        if (deepLinkTarget != null) deepLinkViewModel.consumeTarget()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { deepLinkViewModel.resolve(it) }
    }
}
