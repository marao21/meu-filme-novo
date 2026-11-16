package com.example.catalogo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.SystemAccent
import com.example.ui.theme.SystemBg
import com.example.ui.theme.SystemDanger
import com.example.ui.theme.SystemHeaderTab
import com.example.ui.theme.SystemMuted
import com.example.ui.theme.SystemPanel
import com.example.ui.theme.SystemText
import com.example.ui.theme.SystemWarn

/*
 * O catalogo, desenhado pelo Android.
 *
 * Nenhum navegador participa desta tela. Nao ha HTML, nao ha CSS, nao ha
 * JavaScript: sao fileiras que o proprio sistema desenha, e e por isso que
 * ela anda numa caixa de TV barata onde a pagina web engasgava.
 *
 * O foco e o centro de tudo aqui, porque numa TV nao existe cursor. Quem
 * manda e o controle remoto, e o que estiver aceso e o que vai abrir. Por
 * isso cada cartaz cresce e ganha anel azul quando recebe o foco - sem isso
 * a pessoa se perde na grade e nao sabe onde esta.
 */

/** Quanto um cartaz cresce ao receber o foco. */
private const val CRESCIMENTO = 1.12f

@Composable
fun CatalogoScreen(
    viewModel: CatalogoViewModel,
    onAbrirTitulo: (ItemCatalogo) -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by viewModel.estado.collectAsState()

    /*
     * O titulo em foco comanda o fundo da tela.
     *
     * E o que da profundidade sem custar nada: a capa larga que a nossa API
     * ja manda em `backdrop` entra atras da grade, com um veu escuro por
     * cima para o texto continuar legivel.
     */
    var emFoco by remember { mutableStateOf<ItemCatalogo?>(null) }

    val primeiraAba = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Sem isso, a TV abre o aplicativo sem nada aceso e o controle parece
        // nao funcionar: o primeiro toque so serve para achar o foco.
        runCatching { primeiraAba.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize().background(SystemBg)) {

        FundoDoTitulo(emFoco)

        Column(modifier = Modifier.fillMaxSize()) {

            BarraDeAbas(
                abaAtual = estado.aba,
                focoDaPrimeira = primeiraAba,
                onAba = { viewModel.escolherAba(it) }
            )

            CabecalhoDoFoco(emFoco)

            when {
                estado.carregando -> Carregando()
                estado.erro != null -> Recado(estado.erro!!)
                estado.aba == Aba.BUSCA -> TelaDeBusca(viewModel, onAbrirTitulo) { emFoco = it }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 28.dp
                    )
                ) {
                    items(estado.fileiras) { fileira ->
                        FileiraDeCartazes(
                            fileira = fileira,
                            onAbrir = onAbrirTitulo,
                            onFoco = { emFoco = it }
                        )
                    }
                }
            }
        }
    }
}

/**
 * As abas do cabecalho, que abrem a secao so com o foco.
 *
 * Nao precisa apertar OK: passar por cima ja troca a secao, igual ao site.
 * Numa TV isso economiza um toque em cada navegacao, e sao muitos toques ao
 * longo de uma noite.
 */
@Composable
private fun BarraDeAbas(
    abaAtual: Aba,
    focoDaPrimeira: FocusRequester,
    onAba: (Aba) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xD90A0F1C))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "MovFlix",
            color = SystemAccent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(end = 28.dp)
        )

        Aba.entries.forEachIndexed { indice, aba ->
            var focada by remember { mutableStateOf(false) }
            val marcada = aba == abaAtual

            Text(
                text = aba.rotulo,
                color = if (marcada || focada) SystemText else SystemHeaderTab,
                fontSize = 16.sp,
                fontWeight = if (marcada) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (marcada) Color(0x24EBF0F8) else Color.Transparent)
                    .then(
                        if (focada) Modifier.border(
                            3.dp,
                            SystemAccent,
                            RoundedCornerShape(999.dp)
                        ) else Modifier
                    )
                    .let { if (indice == 0) it.focusRequester(focoDaPrimeira) else it }
                    .onFocusChanged { estado ->
                        focada = estado.isFocused
                        if (estado.isFocused && aba != abaAtual) onAba(aba)
                    }
                    .clickable { onAba(aba) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/** O nome, o ano, a nota e a sinopse do titulo que esta aceso agora. */
@Composable
private fun CabecalhoDoFoco(item: ItemCatalogo?) {
    if (item == null) {
        Spacer(modifier = Modifier.height(18.dp))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 180.dp, top = 18.dp, bottom = 14.dp)
    ) {
        Text(
            text = item.title,
            color = SystemText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(modifier = Modifier.padding(top = 6.dp)) {
            item.year?.let {
                Text(text = it.toString(), color = SystemMuted, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(14.dp))
            }
            item.rating?.let {
                Text(
                    text = "★ " + String.format(java.util.Locale.US, "%.1f", it),
                    color = SystemWarn,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (item.overview.isNotBlank()) {
            Text(
                text = item.overview,
                color = SystemMuted,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** A capa larga do titulo em foco, atras de tudo, com veu escuro. */
@Composable
private fun FundoDoTitulo(item: ItemCatalogo?) {
    val arte = item?.backdrop ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = arte,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.35f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x99050912), Color(0xF2050912))
                    )
                )
        )
    }
}

@Composable
private fun FileiraDeCartazes(
    fileira: Fileira,
    onAbrir: (ItemCatalogo) -> Unit,
    onFoco: (ItemCatalogo) -> Unit
) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
        Text(
            text = fileira.titulo,
            color = SystemText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp)
        ) {
            items(fileira.itens) { item ->
                Cartaz(item = item, onAbrir = onAbrir, onFoco = onFoco)
            }
        }
    }
}

/**
 * Um cartaz na grade.
 *
 * O crescimento ao focar nao e enfeite: numa TV vista de longe, borda fina
 * sozinha nao se enxerga do sofa. O tamanho e o que diz onde a pessoa esta,
 * e o anel azul confirma.
 */
@Composable
private fun Cartaz(
    item: ItemCatalogo,
    onAbrir: (ItemCatalogo) -> Unit,
    onFoco: (ItemCatalogo) -> Unit
) {
    var focado by remember { mutableStateOf(false) }
    val tamanho by animateFloatAsState(
        targetValue = if (focado) CRESCIMENTO else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "cartaz"
    )

    Box(
        modifier = Modifier
            .padding(vertical = 14.dp)
            .scale(tamanho)
            .size(width = 124.dp, height = 186.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SystemPanel)
            .then(
                if (focado) Modifier.border(3.dp, SystemAccent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .onFocusChanged { estado ->
                focado = estado.isFocused
                if (estado.isFocused) onFoco(item)
            }
            .clickable { onAbrir(item) }
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun Carregando() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = SystemAccent)
            Text(
                text = "Montando o catálogo…",
                color = SystemMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun Recado(texto: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = texto, color = SystemDanger, fontSize = 16.sp)
    }
}

/**
 * A busca, com teclado da TV.
 *
 * Um campo e uma grade embaixo. Pesquisar nos tres tipos de uma vez e de
 * proposito: quem digita o nome de uma serie nao pensa em qual gaveta ela
 * esta guardada.
 */
@Composable
private fun TelaDeBusca(
    viewModel: CatalogoViewModel,
    onAbrir: (ItemCatalogo) -> Unit,
    onFoco: (ItemCatalogo) -> Unit
) {
    val estado by viewModel.estado.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {

        OutlinedTextField(
            value = estado.termoDeBusca,
            onValueChange = { viewModel.digitarBusca(it) },
            label = { Text("Nome do filme ou da série", color = SystemMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.buscar() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SystemAccent,
                unfocusedBorderColor = SystemMuted,
                focusedTextColor = SystemText,
                unfocusedTextColor = SystemText,
                cursorColor = SystemAccent
            ),
            modifier = Modifier.fillMaxWidth(0.6f).padding(top = 8.dp, bottom = 14.dp)
        )

        when {
            estado.buscando -> Carregando()

            estado.resultados.isEmpty() && estado.termoDeBusca.length >= 2 -> Text(
                text = "Nada encontrado para “" + estado.termoDeBusca + "”.",
                color = SystemMuted,
                fontSize = 15.sp
            )

            estado.resultados.isEmpty() -> Text(
                text = "Digite e aperte OK para buscar.",
                color = SystemMuted,
                fontSize = 15.sp
            )

            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.resultados) { item ->
                    Cartaz(item = item, onAbrir = onAbrir, onFoco = onFoco)
                }
            }
        }
    }
}
