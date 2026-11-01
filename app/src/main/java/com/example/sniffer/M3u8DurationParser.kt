package com.example.sniffer

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object M3u8DurationParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val extinfPattern = Pattern.compile("""#EXTINF:([0-9.]+)[,\s]""")

    data class M3u8Info(
        val durationSeconds: Long,
        val isLive: Boolean,
        val resolution: String = ""
    )

    suspend fun probeUrl(url: String, userAgent: String? = null): M3u8Info = withContext(Dispatchers.IO) {
        try {
            val lower = url.lowercase()
            if (!lower.contains(".m3u8")) {
                return@withContext M3u8Info(durationSeconds = 0L, isLive = false)
            }

            val requestBuilder = Request.Builder().url(url)
            if (!userAgent.isNullOrBlank()) {
                requestBuilder.header("User-Agent", userAgent)
            }
            requestBuilder.header("Referer", "https://filmemeu.vercel.app/")

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext M3u8Info(durationSeconds = 0L, isLive = false)
            }

            val body = response.body?.string() ?: return@withContext M3u8Info(0L, false)

            parseM3u8Content(url, body, userAgent)
        } catch (e: Exception) {
            M3u8Info(durationSeconds = 0L, isLive = false)
        }
    }

    private suspend fun parseM3u8Content(baseUrl: String, content: String, userAgent: String?): M3u8Info {
        var totalSeconds = 0.0
        val isEndListPresent = content.contains("#EXT-X-ENDLIST")
        val isMasterPlaylist = content.contains("#EXT-X-STREAM-INF")

        if (isMasterPlaylist) {
            // Find the first media playlist variant
            val lines = content.lines()
            var nextVariantUrl: String? = null
            var resolution = ""
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    if (line.contains("RESOLUTION=")) {
                        val resMatch = Regex("""RESOLUTION=(\d+x\d+)""").find(line)
                        if (resMatch != null) {
                            resolution = resMatch.groupValues[1]
                        }
                    }
                    if (i + 1 < lines.size) {
                        val candidate = lines[i + 1].trim()
                        if (candidate.isNotEmpty() && !candidate.startsWith("#")) {
                            nextVariantUrl = candidate
                            break
                        }
                    }
                }
            }

            if (!nextVariantUrl.isNullOrEmpty()) {
                val resolvedVariantUrl = resolveRelativeUrl(baseUrl, nextVariantUrl)
                val subInfo = probeUrl(resolvedVariantUrl, userAgent)
                return M3u8Info(
                    durationSeconds = subInfo.durationSeconds,
                    isLive = subInfo.isLive,
                    resolution = if (resolution.isNotEmpty()) resolution else subInfo.resolution
                )
            }
        }

        val matcher = extinfPattern.matcher(content)
        while (matcher.find()) {
            val secStr = matcher.group(1)
            val sec = secStr?.toDoubleOrNull() ?: 0.0
            totalSeconds += sec
        }

        val roundedSeconds = totalSeconds.toLong()
        val isLive = !isEndListPresent && roundedSeconds == 0L

        return M3u8Info(
            durationSeconds = roundedSeconds,
            isLive = isLive
        )
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        return try {
            if (relative.startsWith("http://") || relative.startsWith("https://")) {
                relative
            } else {
                val baseUri = Uri.parse(baseUrl)
                if (relative.startsWith("/")) {
                    "${baseUri.scheme}://${baseUri.authority}$relative"
                } else {
                    val path = baseUri.path ?: ""
                    val lastSlash = path.lastIndexOf('/')
                    val basePath = if (lastSlash != -1) path.substring(0, lastSlash + 1) else "/"
                    "${baseUri.scheme}://${baseUri.authority}$basePath$relative"
                }
            }
        } catch (e: Exception) {
            relative
        }
    }
}
