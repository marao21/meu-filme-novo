package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.player.PlayerViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DetectedStreamsDialog
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.CinemaBlack

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val repository = playerViewModel.repository
    val playerState by playerViewModel.playerState.collectAsState()
    var showStreamsDialog by remember { mutableStateOf(false) }

    // Hardware back press: if playing, exit back to browser
    BackHandler(enabled = playerState.currentStream != null) {
        playerViewModel.stopPlayback()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
    ) {
        // Fullscreen Browser (Always running so movies and catalog are uninterrupted)
        BrowserScreen(
            playerViewModel = playerViewModel,
            onPlayStream = { stream ->
                playerViewModel.playStream(stream)
            },
            onNavigateToDetected = {
                showStreamsDialog = true
            },
            modifier = Modifier.fillMaxSize()
        )

        // Fullscreen Native TV Player overlay
        AnimatedVisibility(
            visible = playerState.currentStream != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerScreen(
                viewModel = playerViewModel,
                onNavigateBack = {
                    playerViewModel.stopPlayback()
                },
                onNavigateToBrowse = {
                    playerViewModel.stopPlayback()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Optional Detected Streams Dialog (for TV Box when user wants to see all streams)
        if (showStreamsDialog) {
            DetectedStreamsDialog(
                repository = repository,
                onPlayStream = { stream ->
                    showStreamsDialog = false
                    playerViewModel.playStream(stream)
                },
                onDismiss = { showStreamsDialog = false }
            )
        }
    }
}
