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
        // Gira junto com o aparelho: no celular isso importa, e na caixa de TV
        // nao faz diferenca porque a tela nunca gira.
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        // A caixa de TV apaga a tela por conta propria no meio do filme, e
        // alguns lancadores roubam o foco do controle. As duas linhas abaixo
        // seguram as duas coisas.
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
        // Impede o lancador da TV de ficar com o foco do controle remoto.
        if (!hasFocus) {
            window.decorView.post {
                window.decorView.requestFocus()
            }
        }
    }

    /**
     * O que cada tecla do controle faz.
     *
     * Antes, com a interface sendo uma pagina dentro de um WebView, todas as
     * teclas eram repassadas ao JavaScript e varias eram engolidas aqui -
     * inclusive o Voltar. Isso fazia sentido quando quem desenhava a tela era
     * a pagina.
     *
     * Agora quem desenha e o Compose, e engolir tecla e exatamente o que nao
     * se pode fazer: as setas movem o foco, o OK abre, e o Voltar fecha a
     * tela. Tudo isso o proprio sistema resolve, desde que a gente devolva
     * `false` e deixe passar.
     *
     * Ficam para nos apenas as teclas de midia, que nao movem foco nenhum e
     * precisam falar com o player: o controle da TV Box tem play, pause e os
     * dois avancos, e sem isto eles nao fariam nada.
     *
     * O aviso ao navegador continua sendo emitido, porque a tela de reserva
     * ainda e uma pagina e la dentro quem manda e ela. Emitir nao consome: a
     * tecla segue o caminho dela.
     */
    private fun handleRemoteKeyEvent(keyCode: Int, isDown: Boolean): Boolean {
        // So na descida da tecla. Agir na descida e na subida faz um toque
        // valer dois, e o play viraria pausa no mesmo aperto.
        if (!isDown) return false

        avisarAReserva(keyCode)

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                playerViewModel.togglePlayPause()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                playerViewModel.seekBackward10s()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                playerViewModel.seekForward10s()
                return true
            }
        }

        // Setas, OK, Voltar, Menu e volume seguem para o sistema, que e quem
        // sabe mover o foco e fechar tela.
        return false
    }

    /** Repassa a tecla a tela de reserva, que ainda e uma pagina. */
    private fun avisarAReserva(keyCode: Int) {
        val acao = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerViewModel.WebKeyAction.PLAY_PAUSE

            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlayerViewModel.WebKeyAction.REWIND

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlayerViewModel.WebKeyAction.FORWARD

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_SELECT -> PlayerViewModel.WebKeyAction.DPAD_CENTER

            KeyEvent.KEYCODE_DPAD_UP -> PlayerViewModel.WebKeyAction.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> PlayerViewModel.WebKeyAction.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> PlayerViewModel.WebKeyAction.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerViewModel.WebKeyAction.DPAD_RIGHT

            KeyEvent.KEYCODE_MENU -> PlayerViewModel.WebKeyAction.TOGGLE_MENU

            else -> null
        }
        if (acao != null) playerViewModel.sendWebKeyAction(acao)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleRemoteKeyEvent(event.keyCode, event.action == KeyEvent.ACTION_DOWN)) {
            return true
        }
        return super.dispatchKeyEvent(event)
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
