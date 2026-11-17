package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.StreamMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FilmeMeu", appName)
  }

  @Test
  fun `stream above 15 min detection`() {
    val shortAd = StreamMedia(
      url = "https://example.com/ad.m3u8",
      title = "Anúncio",
      durationSeconds = 30L,
      isAbove15Min = false
    )
    assertFalse(shortAd.isAbove15Min)
    assertEquals("0m 30s", shortAd.formattedDuration)

    val fullMovie = StreamMedia(
      url = "https://example.com/movie.m3u8",
      title = "Filme Meu Drama",
      durationSeconds = 5400L,
      isAbove15Min = true,
      serverName = "WatchPlay",
      serverClicked = true
    )
    assertTrue(fullMovie.isAbove15Min)
    assertTrue(fullMovie.serverClicked)
    assertEquals("WatchPlay", fullMovie.serverName)
    assertEquals("1h 30m", fullMovie.formattedDuration)
  }

  @Test
  fun `repository derives movie title and ignores generic names`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.AppDatabase.getDatabase(context)
    val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    val repo = com.example.data.StreamRepository(db.historyDao(), testScope)

    // Set movie title from catalog
    repo.setCurrentMovieTitle("Batman: O Cavaleiro das Trevas")

    // Ingest stream with generic site title "Baixar Vídeo Drama"
    repo.onStreamCandidate(
      url = "https://example.com/stream/movie.m3u8",
      pageTitle = "Baixar Vídeo Drama",
      pageUrl = "https://filmemeu.vercel.app",
      initialDurationSec = 5400L
    )

    val detected = repo.detectedStreams.value
    assertEquals(1, detected.size)
    assertEquals("Batman: O Cavaleiro das Trevas", detected[0].title)
    assertTrue(detected[0].isAbove15Min)

    // Returning to catalog clears detected streams
    repo.clearDetected()
    assertTrue(repo.detectedStreams.value.isEmpty())
    assertEquals(null, repo.currentMovieTitle.value)
  }

  @Test
  fun `repository detects streams and applies filter`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.AppDatabase.getDatabase(context)
    val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    val repo = com.example.data.StreamRepository(db.historyDao(), testScope)

    repo.setCurrentMovieTitle("Hope: O Primeiro Impacto")

    repo.onStreamCandidate(
      url = "https://example.com/stream/full_movie.m3u8",
      pageTitle = "Hope",
      pageUrl = "https://filmemeu.vercel.app",
      initialDurationSec = 7200L
    )

    val detected = repo.detectedStreams.value
    assertEquals(1, detected.size)
    assertTrue(detected[0].isAbove15Min)
    assertEquals("Hope: O Primeiro Impacto", detected[0].title)

    repo.setFilterAbove15Min(true)
    assertTrue(repo.filterAbove15Min.value)
  }
}
