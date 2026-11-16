package com.example.catalogo

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/*
 * A nossa API, falada em Kotlin.
 *
 * Isto é o que torna o aplicativo nativo de verdade: o catálogo deixa de ser
 * uma página desenhada por um navegador e passa a ser dado puro, que a tela
 * do Android desenha sozinha. São dois endereços, e nenhum deles entrega
 * vídeo — só texto e endereços de capa.
 *
 * Repare no que NÃO está aqui: nenhum "/api/proxy". As capas apontam direto
 * para o image.tmdb.org e o vídeo vai direto da fonte para o aparelho. O
 * nosso servidor entrega alguns kilobytes de JSON por sessão, e é só isso que
 * entra na conta da hospedagem.
 */

/** Um título na grade: o bastante para desenhar o cartaz e abrir o play. */
@JsonClass(generateAdapter = true)
data class ItemCatalogo(
    val id: Int = 0,
    val kind: String = "filme",
    val title: String = "",
    val year: Int? = null,
    val rating: Double? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String = ""
)

@JsonClass(generateAdapter = true)
data class RespostaCatalogo(
    val kind: String = "filme",
    val page: Int = 1,
    val totalPages: Int = 1,
    val items: List<ItemCatalogo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Genero(val id: Int = 0, val name: String = "")

@JsonClass(generateAdapter = true)
data class RespostaGeneros(
    val kind: String = "filme",
    val genres: List<Genero> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Temporada(
    val number: Int = 1,
    val name: String = "",
    val episodes: Int = 0
)

@JsonClass(generateAdapter = true)
data class RespostaDetalhes(
    val id: Int = 0,
    val title: String = "",
    val seasons: List<Temporada> = emptyList()
)

/**
 * O endereço do vídeo, já resolvido pelo nosso servidor.
 *
 * Vem assinado e vence em poucos minutos, então não serve para guardar: é
 * pedido na hora de tocar e usado na hora. `tentativas` diz quais players
 * foram tentados antes, o que é o que a gente mostra quando nada abre.
 */
@JsonClass(generateAdapter = true)
data class RespostaFonte(
    val kind: String = "filme",
    val id: Int = 0,
    val player: String = "",
    val lang: String = "",
    val url: String? = null,
    val isHls: Boolean = true,
    val error: String? = null,
    val tentativas: List<String> = emptyList()
)

interface ApiMovflix {

    @GET("api/catalog")
    suspend fun catalogo(
        @Query("kind") kind: String,
        @Query("page") pagina: Int = 1,
        @Query("q") busca: String? = null,
        @Query("genre") genero: Int? = null
    ): RespostaCatalogo

    @GET("api/catalog")
    suspend fun generos(
        @Query("kind") kind: String,
        @Query("list") lista: String = "genres"
    ): RespostaGeneros

    @GET("api/catalog")
    suspend fun detalhes(
        @Query("kind") kind: String,
        @Query("details") id: Int
    ): RespostaDetalhes

    @GET("api/fonte")
    suspend fun fonte(
        @Query("kind") kind: String,
        @Query("id") id: Int,
        @Query("t") temporada: Int? = null,
        @Query("e") episodio: Int? = null,
        @Query("player") player: String? = null
    ): RespostaFonte
}
