package com.example.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** O que a tela do catalogo precisa saber para se desenhar. */
data class EstadoCatalogo(
    val aba: Aba = Aba.INICIO,
    val fileiras: List<Fileira> = emptyList(),
    val carregando: Boolean = false,
    val erro: String? = null,
    val termoDeBusca: String = "",
    val resultados: List<ItemCatalogo> = emptyList(),
    val buscando: Boolean = false
)

/** O que a tela de um titulo precisa saber. */
data class EstadoDetalhe(
    val item: ItemCatalogo? = null,
    val temporadas: List<Temporada> = emptyList(),
    val temporadaEscolhida: Int = 1,
    val carregandoTemporadas: Boolean = false,
    val resolvendo: Boolean = false,
    val erro: String? = null
)

/** Um filme pronto para tocar: endereco resolvido e nome para a tela. */
data class PedidoDeToque(
    val url: String,
    val titulo: String,
    val isHls: Boolean,
    val player: String
)

class CatalogoViewModel : ViewModel() {

    private val api = Rede.api

    private val _estado = MutableStateFlow(EstadoCatalogo())
    val estado: StateFlow<EstadoCatalogo> = _estado.asStateFlow()

    private val _detalhe = MutableStateFlow(EstadoDetalhe())
    val detalhe: StateFlow<EstadoDetalhe> = _detalhe.asStateFlow()

    /*
     * O que ja foi baixado fica guardado por aba.
     *
     * Trocar de aba e voltar e o movimento mais comum do controle remoto. Sem
     * esta memoria, cada ida e volta refazia cinco ou sete pedidos de rede e a
     * grade piscava inteira - numa caixa de TV isso e meio segundo de tela
     * vazia a cada toque.
     */
    private val guardadas = mutableMapOf<Aba, List<Fileira>>()

    init {
        escolherAba(Aba.INICIO)
    }

    fun escolherAba(aba: Aba) {
        val jaTenho = guardadas[aba]
        if (jaTenho != null) {
            _estado.value = _estado.value.copy(
                aba = aba,
                fileiras = jaTenho,
                carregando = false,
                erro = null
            )
            return
        }
        if (aba == Aba.BUSCA) {
            _estado.value = _estado.value.copy(
                aba = aba,
                fileiras = emptyList(),
                carregando = false,
                erro = null
            )
            return
        }

        _estado.value = _estado.value.copy(
            aba = aba,
            fileiras = emptyList(),
            carregando = true,
            erro = null
        )

        viewModelScope.launch {
            try {
                /*
                 * As fileiras saem todas juntas, nao uma depois da outra.
                 * Sete pedidos em fila, a meio segundo cada, sao tres
                 * segundos e meio de tela preta; em paralelo e o tempo do
                 * mais lento.
                 */
                val moldes = Grade.moldes(aba)
                val prontas = moldes.map { molde ->
                    async {
                        try {
                            val r = api.catalogo(
                                kind = molde.kind,
                                pagina = 1,
                                genero = molde.genero
                            )
                            Fileira(molde.titulo, r.items.filter { it.poster != null })
                        } catch (e: Exception) {
                            // Uma fileira que falhou nao derruba a tela: ela
                            // simplesmente nao aparece.
                            Fileira(molde.titulo, emptyList())
                        }
                    }
                }.awaitAll().filter { it.itens.isNotEmpty() }

                if (prontas.isEmpty()) {
                    _estado.value = _estado.value.copy(
                        carregando = false,
                        erro = "Não deu para carregar o catálogo. Verifique a internet da casa."
                    )
                    return@launch
                }

                guardadas[aba] = prontas
                if (_estado.value.aba == aba) {
                    _estado.value = _estado.value.copy(
                        fileiras = prontas,
                        carregando = false,
                        erro = null
                    )
                }
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    carregando = false,
                    erro = e.message ?: "Falha ao carregar o catálogo."
                )
            }
        }
    }

    fun digitarBusca(texto: String) {
        _estado.value = _estado.value.copy(termoDeBusca = texto)
    }

    fun buscar() {
        val termo = _estado.value.termoDeBusca.trim()
        if (termo.length < 2) return
        _estado.value = _estado.value.copy(buscando = true, resultados = emptyList())
        viewModelScope.launch {
            try {
                /*
                 * Busca nos tres tipos ao mesmo tempo: quem digita o nome de
                 * uma serie nao sabe nem precisa saber se aquilo esta
                 * catalogado como serie ou como anime.
                 */
                val achados = listOf("filme", "serie", "anime").map { kind ->
                    async {
                        try {
                            api.catalogo(kind = kind, pagina = 1, busca = termo).items
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten().filter { it.poster != null }

                _estado.value = _estado.value.copy(buscando = false, resultados = achados)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(buscando = false, erro = e.message)
            }
        }
    }

    fun abrirDetalhe(item: ItemCatalogo) {
        _detalhe.value = EstadoDetalhe(item = item)
        if (item.kind == "filme") return

        _detalhe.value = _detalhe.value.copy(carregandoTemporadas = true)
        viewModelScope.launch {
            try {
                val d = api.detalhes(kind = item.kind, id = item.id)
                val comEpisodios = d.seasons.filter { it.episodes > 0 }
                _detalhe.value = _detalhe.value.copy(
                    temporadas = comEpisodios,
                    temporadaEscolhida = comEpisodios.firstOrNull()?.number ?: 1,
                    carregandoTemporadas = false
                )
            } catch (e: Exception) {
                _detalhe.value = _detalhe.value.copy(carregandoTemporadas = false)
            }
        }
    }

    fun escolherTemporada(numero: Int) {
        _detalhe.value = _detalhe.value.copy(temporadaEscolhida = numero)
    }

    fun fecharDetalhe() {
        _detalhe.value = EstadoDetalhe()
    }

    /**
     * Pede o endereco do video e avisa quando tiver.
     *
     * E aqui que o aplicativo deixa de depender de navegador: o endereco vem
     * como texto da nossa API e vai direto para o player nativo. Nenhuma
     * pagina e carregada, nenhum JavaScript roda, e o video nao passa pelo
     * nosso servidor.
     */
    fun pedirParaTocar(
        item: ItemCatalogo,
        temporada: Int? = null,
        episodio: Int? = null,
        pronto: (PedidoDeToque) -> Unit
    ) {
        _detalhe.value = _detalhe.value.copy(resolvendo = true, erro = null)
        viewModelScope.launch {
            try {
                val r = api.fonte(
                    kind = item.kind,
                    id = item.id,
                    temporada = temporada,
                    episodio = episodio
                )
                val url = r.url
                if (url.isNullOrBlank()) {
                    _detalhe.value = _detalhe.value.copy(resolvendo = false, erro = recado(r))
                    return@launch
                }

                val nome = buildString {
                    append(item.title)
                    if (temporada != null && episodio != null) {
                        append(" - T").append(temporada).append("E").append(episodio)
                    }
                }

                _detalhe.value = _detalhe.value.copy(resolvendo = false, erro = null)
                pronto(
                    PedidoDeToque(
                        url = url,
                        titulo = nome,
                        isHls = r.isHls,
                        player = r.player
                    )
                )
            } catch (e: Exception) {
                _detalhe.value = _detalhe.value.copy(
                    resolvendo = false,
                    erro = "Não deu para achar o vídeo agora. Tente outro título."
                )
            }
        }
    }

    /**
     * Explica a falha com o que a API contou, e nao com um "erro" seco.
     *
     * Quando nenhuma fonte abre, saber quais foram tentadas e a diferenca
     * entre "esta quebrado" e "este titulo nao tem fonte hoje" - que sao
     * problemas diferentes e levam a reacoes diferentes.
     */
    private fun recado(r: RespostaFonte): String {
        if (r.tentativas.isEmpty()) return "Este título não tem fonte disponível agora."
        return "Nenhuma fonte abriu. Tentadas: " + r.tentativas.joinToString(", ") + "."
    }
}
