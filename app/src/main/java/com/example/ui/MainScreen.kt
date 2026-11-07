package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.player.PlayerViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.theme.CinemaBlack

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
    ) {
        // Fullscreen Browser - the website's player plays directly without our native player overlay
        BrowserScreen(
            playerViewModel = playerViewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}
