package com.example.data

import com.example.model.StreamMedia
import com.example.sniffer.M3u8DurationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.Locale

class StreamRepository(
    private val historyDao: HistoryDao,
    private val scope: CoroutineScope
) {
    private val _detectedStreams = MutableStateFlow<List<StreamMedia>>(emptyList())
    val detectedStreams: StateFlow<List<StreamMedia>> = _detectedStreams.asStateFlow()

    private val _filterAbove15Min = MutableStateFlow(false)
    val filterAbove15Min: StateFlow<Boolean> = _filterAbove15Min.asStateFlow()

    private val _newMovieAlert = MutableSharedFlow<StreamMedia>(extraBufferCapacity = 1)
    val newMovieAlert: SharedFlow<StreamMedia> = _newMovieAlert.asSharedFlow()

    val historyList: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    private val knownUrls = mutableSetOf<String>()

    private val _currentMovieTitle = MutableStateFlow<String?>(null)
    val currentMovieTitle: StateFlow<String?> = _currentMovieTitle.asStateFlow()

    fun setCurrentMovieTitle(title: String?) {
        val clean = title?.trim()
        _currentMovieTitle.value = if (!clean.isNullOrEmpty() && !isGenericTitle(clean)) clean else null
    }

    fun isGenericTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val lower = title.trim().lowercase(Locale.ROOT)
        return lower == "baixar vídeo drama" ||
            lower == "baixar video drama" ||
            lower.startsWith("baixar vídeo") ||
            lower.startsWith("baixar video") ||
            lower == "filmemeu" ||
            lower == "untitled" ||
            lower == "filme detectado" ||
            lower == "reproduzindo" ||
            lower == "site aberto" ||
            lower == "vídeo filmemeu" ||
            lower == "video filmemeu" ||
            lower.length < 2
    }

    fun setFilterAbove15Min(enabled: Boolean) {
        _filterAbove15Min.value = enabled
    }

    fun clearDetected() {
        synchronized(knownUrls) {
            knownUrls.clear()
        }
        _detectedStreams.value = emptyList()
        _currentMovieTitle.value = null
    }

    fun notifyPlayerClosed(url: String) {
        // Can be used to track completed streams or cleanup if needed
    }

    fun onStreamCandidate(
        url: String,
        pageTitle: String,
        pageUrl: String,
        initialDurationSec: Long = 0L,
        userAgent: String? = null
    ) {
        try {
            val cleanUrl = url.trim()
            if (cleanUrl.isEmpty()) return

            val lower = cleanUrl.lowercase(Locale.ROOT)
            if (DOMAIN_BLACKLIST.any { lower.contains(it) }) return
            if (lower.contains(".ts") && !lower.contains(".m3u8")) return

            val isHls = lower.contains(".m3u8")
            val isMp4 = lower.contains(".mp4")
            val isMedia = isHls || isMp4 || lower.contains(".webm") || lower.contains(".m4s")

            if (!isMedia) return

            synchronized(knownUrls) {
                if (knownUrls.contains(cleanUrl)) return
                knownUrls.add(cleanUrl)
            }

            val format = when {
                isHls -> "M3U8 (HLS)"
                isMp4 -> "MP4"
                else -> "VÍDEO"
            }

            val derivedTitle = deriveTitle(cleanUrl, pageTitle)
            val initialAbove15 = initialDurationSec >= 900L

            val initialStream = StreamMedia(
                url = cleanUrl,
                title = derivedTitle,
                pageUrl = pageUrl,
                format = format,
                isHls = isHls,
                durationSeconds = initialDurationSec,
                isAbove15Min = initialAbove15
            )

            _detectedStreams.update { current ->
                listOf(initialStream) + current
            }

            if (initialAbove15) {
                _newMovieAlert.tryEmit(initialStream)
            }

            // Probe EXTINF asynchronously if HLS and duration not yet known
            if (isHls && initialDurationSec <= 0L) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val probeInfo = M3u8DurationParser.probeUrl(cleanUrl, userAgent)
                        val duration = probeInfo.durationSeconds
                        val above15 = duration >= 900L

                        _detectedStreams.update { list ->
                            list.map { item ->
                                if (item.url == cleanUrl) {
                                    item.copy(
                                        durationSeconds = duration,
                                        isAbove15Min = above15,
                                        resolution = probeInfo.resolution,
                                        isLive = probeInfo.isLive
                                    )
                                } else {
                                    item
                                }
                            }
                        }

                        if (above15) {
                            val updated = _detectedStreams.value.firstOrNull { it.url == cleanUrl }
                            if (updated != null) {
                                _newMovieAlert.tryEmit(updated)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore probe failures
                    }
                }
            }
        } catch (e: Exception) {
            // Never allow an exception to escape into the caller thread
        }
    }

    suspend fun saveHistory(stream: StreamMedia, lastPositionMs: Long) {
        try {
            val entity = HistoryEntity(
                id = stream.id,
                url = stream.url,
                title = stream.title,
                format = stream.format,
                isHls = stream.isHls,
                durationSeconds = stream.durationSeconds,
                lastPositionMs = lastPositionMs,
                timestamp = System.currentTimeMillis(),
                pageUrl = stream.pageUrl
            )
            historyDao.insertOrUpdate(entity)
        } catch (e: Exception) {
            // Ignore DB error
        }
    }

    suspend fun deleteHistory(id: String) {
        try {
            historyDao.deleteById(id)
        } catch (e: Exception) {
            // Ignore DB error
        }
    }

    suspend fun clearHistory() {
        try {
            historyDao.clearAll()
        } catch (e: Exception) {
            // Ignore DB error
        }
    }

    private fun deriveTitle(url: String, pageTitle: String): String {
        return try {
            val active = _currentMovieTitle.value
            if (!active.isNullOrBlank() && !isGenericTitle(active)) {
                return active.trim()
            }

            val cleanPage = pageTitle.trim().replace(Regex("""[\/\\:*?"<>|]"""), "")
            if (cleanPage.length > 2 && !isGenericTitle(cleanPage)) {
                return cleanPage
            }

            val lastPath = url.substringBefore("?").substringAfterLast("/")
            if (lastPath.length > 3) {
                val decoded = try { URLDecoder.decode(lastPath, "UTF-8") } catch (e: Exception) { lastPath }
                if (!isGenericTitle(decoded)) {
                    return decoded
                }
            }
            "Filme Selecionado"
        } catch (e: Exception) {
            _currentMovieTitle.value ?: "Filme Selecionado"
        }
    }

    companion object {
        private val DOMAIN_BLACKLIST = listOf(
            "youtube.com",
            "googlevideo.com",
            "ytimg.com",
            "doubleclick.net",
            "googleads.g.doubleclick.net",
            "facebook.com",
            "twitter.com",
            "instagram.com"
        )
    }
}
