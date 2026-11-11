package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testGenericTitleFiltering() {
    val genericTitles = listOf(
      "Baixar Vídeo Drama",
      "baixar video drama",
      "Baixar Vídeo",
      "FilmeMeu",
      "Untitled",
      "Filme Detectado",
      "Reproduzindo",
      "Site aberto",
      ""
    )
    for (title in genericTitles) {
      val lower = title.trim().lowercase(java.util.Locale.ROOT)
      val isGeneric = lower.isEmpty() ||
          lower == "baixar vídeo drama" ||
          lower == "baixar video drama" ||
          lower.startsWith("baixar vídeo") ||
          lower.startsWith("baixar video") ||
          lower == "filmemeu" ||
          lower == "filminhoflix" ||
          lower == "movflix" ||
          lower == "untitled" ||
          lower == "filme detectado" ||
          lower == "reproduzindo" ||
          lower == "site aberto" ||
          lower.length < 2
      assertTrue("Expected '$title' to be recognized as generic", isGeneric)
    }

    val realMovieTitles = listOf(
      "Batman: O Cavaleiro das Trevas",
      "Hope: O Primeiro Impacto",
      "Homem-Aranha: Sem Volta para Casa",
      "Oppenheimer"
    )
    for (movie in realMovieTitles) {
      val lower = movie.trim().lowercase(java.util.Locale.ROOT)
      val isGeneric = lower.isEmpty() ||
          lower == "baixar vídeo drama" ||
          lower == "baixar video drama" ||
          lower.startsWith("baixar vídeo") ||
          lower.startsWith("baixar video") ||
          lower == "filmemeu" ||
          lower == "filminhoflix" ||
          lower == "movflix" ||
          lower == "untitled" ||
          lower == "filme detectado" ||
          lower == "reproduzindo" ||
          lower == "site aberto" ||
          lower.length < 2
      assertFalse("Expected real movie title '$movie' to not be generic", isGeneric)
    }
  }
}
