package com.example.catalogo

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/*
 * Onde o catálogo nasce.
 *
 * Uma coisa só, guardada num `object`: o aparelho é uma caixa de TV com pouca
 * memória, e abrir conexão nova a cada tela é desperdício que ela sente.
 *
 * Os tempos de espera são generosos de propósito. A nossa API consulta o TMDB
 * por trás, e numa rede doméstica ruim os dez segundos padrão do OkHttp
 * viravam tela vazia quando era só lentidão.
 */
object Rede {

    const val BASE = "https://filminhoflix.vercel.app/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ApiMovflix = Retrofit.Builder()
        .baseUrl(BASE)
        .client(cliente)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ApiMovflix::class.java)
}

/** Uma fileira da grade: um nome e os títulos que vão nela. */
data class Fileira(
    val titulo: String,
    val itens: List<ItemCatalogo>
)

/**
 * O molde de uma fileira, antes de ter conteúdo.
 *
 * Separar o molde do resultado é o que deixa a tela carregar as fileiras em
 * paralelo: são cinco pedidos saindo juntos em vez de cinco em fila, e numa
 * caixa de TV essa diferença é de segundos.
 */
data class MoldeDeFileira(
    val titulo: String,
    val kind: String,
    val genero: Int? = null
)

/** As abas, iguais às do site — e na mesma ordem, para não desensinar ninguém. */
enum class Aba(val rotulo: String) {
    INICIO("Início"),
    FILMES("Filmes"),
    SERIES("Séries"),
    ANIMACOES("Animações"),
    BUSCA("Buscar")
}

object Grade {

    /*
     * Os gêneros vêm com número fixo porque são do TMDB e não mudam. Pedir a
     * lista de gêneros antes de montar a tela seria um pedido a mais só para
     * descobrir o que já se sabe.
     *
     * Atenção: filme e série usam números diferentes para coisas parecidas.
     * "Ação" em filme é 28; em série, o que existe é "Ação e Aventura",
     * 10759. Trocar um pelo outro devolve fileira vazia, sem dar erro.
     */
    fun moldes(aba: Aba): List<MoldeDeFileira> = when (aba) {
        Aba.INICIO -> listOf(
            MoldeDeFileira("Em alta", "filme"),
            MoldeDeFileira("Séries do momento", "serie"),
            MoldeDeFileira("Animes", "anime"),
            MoldeDeFileira("Ação", "filme", 28),
            MoldeDeFileira("Comédia", "filme", 35)
        )
        Aba.FILMES -> listOf(
            MoldeDeFileira("Em alta", "filme"),
            MoldeDeFileira("Ação", "filme", 28),
            MoldeDeFileira("Aventura", "filme", 12),
            MoldeDeFileira("Comédia", "filme", 35),
            MoldeDeFileira("Terror", "filme", 27),
            MoldeDeFileira("Drama", "filme", 18),
            MoldeDeFileira("Ficção científica", "filme", 878)
        )
        Aba.SERIES -> listOf(
            MoldeDeFileira("Em alta", "serie"),
            MoldeDeFileira("Ação e Aventura", "serie", 10759),
            MoldeDeFileira("Comédia", "serie", 35),
            MoldeDeFileira("Drama", "serie", 18),
            MoldeDeFileira("Ficção e Fantasia", "serie", 10765),
            MoldeDeFileira("Crime", "serie", 80)
        )
        Aba.ANIMACOES -> listOf(
            MoldeDeFileira("Animes em alta", "anime"),
            MoldeDeFileira("Ação", "anime", 10759),
            MoldeDeFileira("Comédia", "anime", 35),
            MoldeDeFileira("Drama", "anime", 18)
        )
        Aba.BUSCA -> emptyList()
    }
}
