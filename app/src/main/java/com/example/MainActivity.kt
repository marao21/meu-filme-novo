package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
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
        // Keep TV Box screen on and retain primary input focus in app window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.keepScreenOn = true
        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(playerViewModel = playerViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.keepScreenOn = true
        window.decorView.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Prevent TV Box launcher or system UI from stealing remote control focus
        if (!hasFocus) {
            window.decorView.post {
                window.decorView.requestFocus()
            }
        }
    }

    private fun handleRemoteKeyEvent(keyCode: Int, isDown: Boolean): Boolean {
        val playerState = playerViewModel.playerState.value
        val isPlayerActive = playerState.currentStream != null

        if (isPlayerActive) {
            if (isDown) {
                // ANY key on the physical keyboard, TV remote, or gamepad wakes up controls for 8 seconds
                playerViewModel.triggerShowControls()

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BUTTON_SELECT,
                    KeyEvent.KEYCODE_BUTTON_START,
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
                    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        playerViewModel.seekBackward10s()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        playerViewModel.seekForward10s()
                        return true
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_B -> {
                        playerViewModel.stopPlayback()
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.RETURN_TO_AUDIO_SELECTION)
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE -> {
                        return false // Let system handle volume
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_INFO,
                    KeyEvent.KEYCODE_GUIDE,
                    KeyEvent.KEYCODE_SETTINGS -> {
                        // Keep controls visible for 8 seconds and keep focus inside app
                        return true
                    }
                    else -> {
                        // Any other key on keyboard or TV box remote
                        return true
                    }
                }
            } else {
                // Key Up
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE -> return false
                    else -> return true
                }
            }
        } else {
            // When browsing on TV Box:
            if (isDown) {
                when (keyCode) {
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
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BUTTON_SELECT -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.DPAD_CENTER)
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.DPAD_UP)
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.DPAD_DOWN)
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.DPAD_LEFT)
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.DPAD_RIGHT)
                        return false
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_B -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.HANDLE_BACK)
                        return true
                    }
                    KeyEvent.KEYCODE_MENU -> {
                        playerViewModel.sendWebKeyAction(PlayerViewModel.WebKeyAction.TOGGLE_MENU)
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleRemoteKeyEvent(event.keyCode, event.action == KeyEvent.ACTION_DOWN)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleRemoteKeyEvent(keyCode, true)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleRemoteKeyEvent(keyCode, false)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        playerViewModel.pause()
    }

    override fun onStop() {
        super.onStop()
        playerViewModel.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.stopPlayback()
    }
}
