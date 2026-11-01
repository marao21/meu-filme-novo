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
      isAbove15Min = true
    )
    assertTrue(fullMovie.isAbove15Min)
    assertEquals("1h 30m", fullMovie.formattedDuration)
  }
}
