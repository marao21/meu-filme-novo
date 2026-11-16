package com.example.catalogo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.SystemAccent
import com.example.ui.theme.SystemAccentStrong
import com.example.ui.theme.SystemBg
import com.example.ui.theme.SystemDanger
import com.example.ui.theme.SystemMuted
import com.example.ui.theme.SystemPanel
import com.example.ui.theme.SystemText
import com.example.ui.theme.SystemWarn

/*
 * A pagina de um titulo.
 *
 * Ela existe para responder duas perguntas antes de gastar banda: e este
 * mesmo o filme que eu quero, e qual episodio eu vou ver. Resolver isso aqui
 * e mais barato do que abrir o player e descobrir que era outro.
 *
 * O botao Assistir recebe o foco sozinho ao abrir. Numa TV, abrir uma tela e
 * ter de caçar o foco com a setinha e o tipo de atrito que faz a pessoa
 * desistir do aplicativo.
 */
@Composable
fun DetalheScreen(
    viewModel: CatalogoViewModel,
    onTocar: (PedidoDeToque) -> Unit,
    onVoltar: () -> Unit,
    onReserva: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by viewModel.detalhe.collectAsState()
    val item = estado.item ?: return

    BackHandler { onVoltar() }

    val focoDoAssistir = remember { FocusRequester() }
    LaunchedEffect(item.id) {
        runCatching { focoDoAssistir.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize().background(SystemBg)) {

        item.backdrop?.let { arte ->
            AsyncImage(
                model = arte,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.4f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xF2050912), Color(0x99050912)))
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 34.dp, end = 34.dp, top = 28.dp, bottom = 24.dp)
        ) {

            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 190.dp, height = 285.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SystemPanel)
            )

            Spacer(modifier = Modifier.width(26.dp))

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {

                Text(
                    text = item.title,
                    color = SystemText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    item.year?.let {
                        Text(text = it.toString(), color = SystemMuted, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    item.rating?.let {
                        Text(
                            text = "★ " + String.format(java.util.Locale.US, "%.1f", it),
                            color = SystemWarn,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = when (item.kind) {
                            "filme" -> "Filme"
                            "anime" -> "Anime"
                            else -> "Série"
                        },
                        color = SystemMuted,
                        fontSize = 15.sp
                    )
                }

                if (item.overview.isNotBlank()) {
                    Text(
                        text = item.overview,
                        color = SystemMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (item.kind == "filme") {
                    BotaoAssistir(
                        rotulo = if (estado.resolvendo) "Procurando o vídeo…" else "Assistir",
                        habilitado = !estado.resolvendo,
                        foco = focoDoAssistir
                    ) {
                        viewModel.pedirParaTocar(item, pronto = onTocar)
                    }
                } else {
                    Temporadas(
                        estado = estado,
                        onTemporada = { viewModel.escolherTemporada(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Episodios(
                        estado = estado,
                        focoDoPrimeiro = focoDoAssistir
                    ) { numeroDoEpisodio ->
                        viewModel.pedirParaTocar(
                            item = item,
                            temporada = estado.temporadaEscolhida,
                            episodio = numeroDoEpisodio,
                            pronto = onTocar
                        )
                    }
                }

                estado.erro?.let { recado ->
                    Text(
                        text = recado,
                        color = SystemDanger,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    /*
                     * A reserva, e so quando faz falta.
                     *
                     * Ha fonte que nao entrega o endereco a quem pergunta
                     * direto: ela quer ver uma pagina de verdade carregando o
                     * player dela. Para esses casos sobrou o navegador, que
                     * era a tela principal e agora e a ultima carta - e so
                     * aparece depois de a tentativa nativa falhar, porque e o
                     * caminho que pesa na caixa de TV e custa banda.
                     */
                    Text(
                        text = "Tentar pelo navegador interno",
                        color = SystemAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReserva() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                if (estado.resolvendo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SystemAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Pedindo o endereço do vídeo…",
                            color = SystemMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotaoAssistir(
    rotulo: String,
    habilitado: Boolean,
    foco: FocusRequester,
    onClique: () -> Unit
) {
    var focado by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (habilitado) SystemAccentStrong else SystemPanel)
            .then(
                if (focado) Modifier.border(3.dp, SystemText, RoundedCornerShape(10.dp))
                else Modifier
            )
            .focusRequester(foco)
            .onFocusChanged { focado = it.isFocused }
            .clickable(enabled = habilitado) { onClique() }
            .padding(horizontal = 34.dp, vertical = 14.dp)
    ) {
        Text(
            text = rotulo,
            color = if (habilitado) Color(0xFF06283D) else SystemMuted,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Temporadas(
    estado: EstadoDetalhe,
    onTemporada: (Int) -> Unit
) {
    if (estado.carregandoTemporadas) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = SystemAccent, modifier = Modifier.size(18.dp))
            Text(
                text = "Lendo as temporadas…",
                color = SystemMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        return
    }
    if (estado.temporadas.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(estado.temporadas) { temporada ->
            var focada by remember { mutableStateOf(false) }
            val escolhida = temporada.number == estado.temporadaEscolhida
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (escolhida) SystemAccent else SystemPanel)
                    .then(
                        if (focada) Modifier.border(3.dp, SystemText, RoundedCornerShape(999.dp))
                        else Modifier
                    )
                    .onFocusChanged { focada = it.isFocused }
                    .clickable { onTemporada(temporada.number) }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "T" + temporada.number,
                    color = if (escolhida) Color(0xFF06283D) else SystemText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Os episodios da temporada escolhida.
 *
 * A nossa API diz quantos episodios a temporada tem, e so. Nome e sinopse de
 * cada um seriam outro pedido por temporada, e numa caixa de TV isso custa
 * mais do que entrega: o numero e o que a pessoa usa para escolher.
 */
@Composable
private fun Episodios(
    estado: EstadoDetalhe,
    focoDoPrimeiro: FocusRequester,
    onEpisodio: (Int) -> Unit
) {
    val temporada = estado.temporadas.firstOrNull { it.number == estado.temporadaEscolhida }
        ?: return
    val numeros = (1..temporada.episodes).toList()

    Column {
        Text(
            text = temporada.name.ifBlank { "Temporada " + temporada.number },
            color = SystemText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(numeros) { numero ->
                var focado by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SystemPanel)
                        .then(
                            if (focado) Modifier.border(3.dp, SystemAccent, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .let { if (numero == 1) it.focusRequester(focoDoPrimeiro) else it }
                        .onFocusChanged { focado = it.isFocused }
                        .clickable { onEpisodio(numero) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ep " + numero,
                        color = SystemText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
