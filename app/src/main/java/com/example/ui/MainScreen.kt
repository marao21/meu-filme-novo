package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.catalogo.CatalogoScreen
import com.example.catalogo.CatalogoViewModel
import com.example.catalogo.DetalheScreen
import com.example.catalogo.PedidoDeToque
import com.example.model.StreamMedia
import com.example.player.PlayerViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.CinemaBlack
import kotlinx.coroutines.launch

/*
 * Por onde o aplicativo anda.
 *
 * Tres telas, e nenhuma delas e um navegador: a grade, a pagina do titulo e o
 * player. Era aqui que ficava o WebView ocupando a tela inteira - o
 * aplicativo abria o nosso site e deixava a pagina se desenhar sozinha, com o
 * video tocando no player de HTML. Funcionava, mas era um navegador fingindo
 * ser aplicativo: pesava na caixa de TV, e cada filme assistido descia pelo
 * nosso servidor e entrava na conta da hospedagem.
 *
 * Agora a grade e desenhada pelo Android e o video toca no ExoPlayer, que
 * fala direto com a fonte. O nosso servidor so e consultado para dizer o que
 * existe no catalogo e qual e o endereco do filme - alguns kilobytes de
 * texto, em vez de gigabytes de video.
 *
 * O navegador continua no projeto, mas como reserva: ha fonte que so entrega
 * o endereco a quem chega por uma pagina de verdade, e para esses casos
 * existe a tela de reserva, que a pessoa so ve se pedir.
 */
private sealed interface Tela {
    data object Catalogo : Tela
    data object Detalhe : Tela
    data object Player : Tela
    data object Reserva : Tela
}

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val catalogo: CatalogoViewModel = viewModel()
    val escopo = rememberCoroutineScope()
    val tela = remember { mutableStateOf<Tela>(Tela.Catalogo) }

    /**
     * Leva o endereco resolvido ao player nativo.
     *
     * A posicao guardada e consultada antes de tocar: quem parou um filme na
     * metade quer voltar para a metade, e descobrir isso depois de o video
     * comecar faz o filme pular na cara da pessoa.
     */
    fun tocar(pedido: PedidoDeToque) {
        val media = StreamMedia(
            url = pedido.url,
            title = pedido.titulo,
            isHls = pedido.isHls,
            format = if (pedido.isHls) "M3U8 (HLS)" else "MP4",
            serverName = pedido.player
        )
        playerViewModel.repository.setCurrentMovieTitle(pedido.titulo)
        tela.value = Tela.Player
        escopo.launch {
            val onde = runCatching {
                playerViewModel.repository.getSavedPositionMs(media)
            }.getOrDefault(0L)
            playerViewModel.playStream(media, onde)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBlack)) {
        when (tela.value) {

            Tela.Catalogo -> CatalogoScreen(
                viewModel = catalogo,
                onAbrirTitulo = { item ->
                    catalogo.abrirDetalhe(item)
                    tela.value = Tela.Detalhe
                },
                modifier = Modifier.fillMaxSize()
            )

            Tela.Detalhe -> DetalheScreen(
                viewModel = catalogo,
                onTocar = { pedido -> tocar(pedido) },
                onVoltar = {
                    catalogo.fecharDetalhe()
                    tela.value = Tela.Catalogo
                },
                onReserva = { tela.value = Tela.Reserva },
                modifier = Modifier.fillMaxSize()
            )

            Tela.Player -> PlayerScreen(
                viewModel = playerViewModel,
                onNavigateBack = {
                    playerViewModel.stopPlayback()
                    tela.value = Tela.Detalhe
                },
                onNavigateToBrowse = {
                    playerViewModel.stopPlayback()
                    catalogo.fecharDetalhe()
                    tela.value = Tela.Catalogo
                },
                modifier = Modifier.fillMaxSize()
            )

            Tela.Reserva -> {
                // Sem isto a reserva seria um beco: o navegador tem o Voltar
                // dele para a navegacao da pagina, e nao para sair da tela.
                BackHandler { tela.value = Tela.Detalhe }
                BrowserScreen(
                    playerViewModel = playerViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
