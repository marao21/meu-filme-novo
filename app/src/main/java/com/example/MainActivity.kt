package com.example

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.player.PlayerViewModel
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(playerViewModel = playerViewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val playerState = playerViewModel.playerState.value
        val isPlayerActive = playerState.currentStream != null

        if (isPlayerActive) {
            val keyCode = event.keyCode
            if (event.action == KeyEvent.ACTION_DOWN) {
                // ANY key on the physical keyboard, TV remote, or gamepad wakes up controls for 8 seconds
                playerViewModel.triggerShowControls()

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        playerViewModel.togglePlayPause()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        playerViewModel.play()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        playerViewModel.pause()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                        playerViewModel.seekBackward10s()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                        playerViewModel.seekForward10s()
                        return true
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        playerViewModel.stopPlayback()
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.RETURN_TO_AUDIO_SELECTION)
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        return super.dispatchKeyEvent(event)
                    }
                    else -> {
                        // Any keyboard key (letters, numbers, etc.) wakes up the controls
                        return true
                    }
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        return super.dispatchKeyEvent(event)
                    }
                    else -> return true
                }
            }
        } else {
            // When browsing, let D-pad, Enter, and navigation keys pass directly to the WebView
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.PLAY_PAUSE)
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.REWIND)
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.FORWARD)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlaying = playerViewModel.playerState.value.isPlaying
            if (isPlaying) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    // Ignore if PiP fails
                }
            }
        }
    }
}
