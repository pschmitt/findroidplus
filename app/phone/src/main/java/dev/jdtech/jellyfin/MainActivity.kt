package dev.jdtech.jellyfin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.viewmodels.DeepLinkViewModel
import dev.jdtech.jellyfin.viewmodels.MainViewModel
import dev.jdtech.jellyfin.work.EXTRA_OPEN_DOWNLOADS
import dev.jdtech.jellyfin.work.EXTRA_OPEN_ITEM_ID
import dev.jdtech.jellyfin.work.EXTRA_OPEN_ITEM_IS_MOVIE
import java.util.UUID

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val deepLinkViewModel: DeepLinkViewModel by viewModels()
    private var openDownloadsRequested by mutableStateOf(false)
    private var pendingQrPayload by mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Without this, download/relocate/delete progress notifications silently no-op on
        // Android 13+ (POST_NOTIFICATIONS is a runtime permission there) - there's no in-app
        // fallback for every one of those workers, so this is the only way some of them surface
        // progress at all.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

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
                                navController.navigate(SeasonRoute(seasonId = target.id.toString()))
                            is FindroidEpisode ->
                                navController.navigate(
                                    EpisodeRoute(episodeId = target.id.toString())
                                )
                            is FindroidMovie ->
                                navController.navigate(MovieRoute(movieId = target.id.toString()))
                            else -> Unit
                        }
                        if (deepLinkTarget != null) deepLinkViewModel.consumeTarget()
                    }
                    LaunchedEffect(openDownloadsRequested) {
                        if (openDownloadsRequested) {
                            navController.navigate(DownloadsRoute)
                            openDownloadsRequested = false
                        }
                    }
                    LaunchedEffect(pendingQrPayload) {
                        pendingQrPayload?.let {
                            navController.navigate(ScanQrRoute(rawPayload = it))
                            pendingQrPayload = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { deepLinkViewModel.resolve(it) }
        // findroidplus://setup?p=... - a QR provisioning code opened via some other scanner app
        // (see QrConfigCodec/AndroidManifest's intent-filter), not through this app's own camera.
        intent.data
            ?.takeIf { it.scheme == "findroidplus" }
            ?.let { pendingQrPayload = it.toString() }
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            openDownloadsRequested = true
        }
        // Tapping a "new item" notification action (see NewItemNotifier) - resolve straight to
        // the item by id, no fuzzy matching needed since the notification already knows exactly
        // which item it's about.
        intent.getStringExtra(EXTRA_OPEN_ITEM_ID)?.let { itemId ->
            runCatching { UUID.fromString(itemId) }
                .getOrNull()
                ?.let {
                    deepLinkViewModel.resolveItem(
                        it,
                        isMovie = intent.getBooleanExtra(EXTRA_OPEN_ITEM_IS_MOVIE, false),
                    )
                }
        }
    }
}
