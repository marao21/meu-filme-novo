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
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MARIOFLIX", appName)
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
      pageUrl = "https://filminhoflix.vercel.app",
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
      pageUrl = "https://filminhoflix.vercel.app",
      initialDurationSec = 7200L
    )

    val detected = repo.detectedStreams.value
    assertEquals(1, detected.size)
    assertTrue(detected[0].isAbove15Min)
    assertEquals("Hope: O Primeiro Impacto", detected[0].title)

    repo.setFilterAbove15Min(true)
    assertTrue(repo.filterAbove15Min.value)
  }

  @Test
  fun `apk update url detection and app updated state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gitHubApkUrl = "https://github.com/marao21/meu-filme-novo/releases/download/apk-latest/meu-filme-novo-debug.apk"
    val standardApk = "https://example.com/app/update.apk"
    val queryApk = "https://example.com/download?file=app.apk&v=2"
    val webPage = "https://example.com/about.html"

    assertTrue(com.example.update.BaixarAtualizacao.ehApk(gitHubApkUrl, null))
    assertTrue(com.example.update.BaixarAtualizacao.ehApk(standardApk, null))
    assertTrue(com.example.update.BaixarAtualizacao.ehApk(queryApk, null))
    assertFalse(com.example.update.BaixarAtualizacao.ehApk(webPage, "text/html"))
    assertTrue(com.example.update.BaixarAtualizacao.ehApk(webPage, "application/vnd.android.package-archive"))

    assertEquals(
      "https://github.com/marao21/meu-filme-novo/releases/download/apk-latest/meu-filme-novo-debug.apk",
      com.example.update.BaixarAtualizacao.APK_DEFAULT_URL
    )

    // With versionCode = 20, isAppAlreadyUpdated is true so the update banner doesn't keep appearing
    assertTrue(com.example.update.BaixarAtualizacao.isAppAlreadyUpdated(context))

    // Mark update as done persists
    com.example.update.BaixarAtualizacao.markUpdateAsDone(context)
    assertTrue(com.example.update.BaixarAtualizacao.isAppAlreadyUpdated(context))
  }

  @Test
  fun `mandatory update policy blocks access after 2 updates behind`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val currentCode = com.example.BuildConfig.VERSION_CODE // 20

    // 0 updates behind: fully up to date -> NOT blocked
    com.example.update.BaixarAtualizacao.setSimulatedRemoteVersion(context, currentCode, "2.9")
    val vInfo0 = com.example.update.BaixarAtualizacao.versionInfo.value
    assertFalse(vInfo0.isMandatoryBlock)
    assertFalse(com.example.update.BaixarAtualizacao.isMandatoryBlocked(context))
    assertEquals(0, vInfo0.updatesBehind)

    // 1 update behind (e.g. v2.9 code 20 vs remote v3.0 code 21): within 2 updates tolerance -> NOT blocked
    com.example.update.BaixarAtualizacao.setSimulatedRemoteVersion(context, currentCode + 1, "3.0")
    val vInfo1 = com.example.update.BaixarAtualizacao.versionInfo.value
    assertFalse(vInfo1.isMandatoryBlock)
    assertFalse(com.example.update.BaixarAtualizacao.isMandatoryBlocked(context))
    assertTrue(vInfo1.isUpdateAvailable)
    assertEquals(1, vInfo1.updatesBehind)

    // 2 updates behind (e.g. code 20 vs remote code 22): 2 updates reached -> BLOCKED ("não deixa acessar nada")
    com.example.update.BaixarAtualizacao.setSimulatedRemoteVersion(context, currentCode + 2, "3.1")
    val vInfo2 = com.example.update.BaixarAtualizacao.versionInfo.value
    assertTrue(vInfo2.isMandatoryBlock)
    assertTrue(com.example.update.BaixarAtualizacao.isMandatoryBlocked(context))
    assertEquals(2, vInfo2.updatesBehind)

    // 3+ updates behind (e.g. code 20 vs remote code 25): BLOCKED
    com.example.update.BaixarAtualizacao.setSimulatedRemoteVersion(context, currentCode + 5, "3.4")
    val vInfo5 = com.example.update.BaixarAtualizacao.versionInfo.value
    assertTrue(vInfo5.isMandatoryBlock)
    assertTrue(com.example.update.BaixarAtualizacao.isMandatoryBlocked(context))
    assertEquals(5, vInfo5.updatesBehind)
  }

  @Test
  fun `quality preference and adaptive auto quality controls`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.player.PlayerViewModel(application)

    // Initial state is AUTO
    assertEquals("AUTO", viewModel.playerState.value.selectedQualityPreference)

    // Manual quality set to 720p
    viewModel.setQualityPreference("720p")
    assertEquals("720p", viewModel.playerState.value.selectedQualityPreference)
    assertEquals("720p", viewModel.playerState.value.currentQualityLabel)

    // Manual quality set to 480p
    viewModel.setQualityPreference("480p")
    assertEquals("480p", viewModel.playerState.value.selectedQualityPreference)
    assertEquals("480p", viewModel.playerState.value.currentQualityLabel)

    // Manual quality set to 360p
    viewModel.setQualityPreference("360p")
    assertEquals("360p", viewModel.playerState.value.selectedQualityPreference)
    assertEquals("360p", viewModel.playerState.value.currentQualityLabel)

    // Back to AUTO mode
    viewModel.setQualityPreference("AUTO")
    assertEquals("AUTO", viewModel.playerState.value.selectedQualityPreference)
    assertTrue(viewModel.playerState.value.currentQualityLabel.startsWith("AUTO"))
  }
}
