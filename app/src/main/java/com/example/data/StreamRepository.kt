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
            lower == "filminhoflix" ||
            lower == "movflix" ||
            lower == "untitled" ||
            lower == "filme detectado" ||
            lower == "reproduzindo" ||
            lower == "site aberto" ||
            lower == "vídeo filmemeu" ||
            lower == "video filmemeu" ||
            lower == "vídeo filminhoflix" ||
            lower == "video filminhoflix" ||
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

    suspend fun getSavedPositionMs(stream: StreamMedia): Long {
        return try {
            var entity = historyDao.findByUrlOrTitle(stream.url, stream.title)
            if (entity == null && !isGenericTitle(stream.title)) {
                entity = historyDao.findByTitle(stream.title)
            }
            if (entity == null) {
                val currentTitle = _currentMovieTitle.value
                if (!currentTitle.isNullOrBlank() && !isGenericTitle(currentTitle)) {
                    entity = historyDao.findByTitle(currentTitle)
                }
            }
            if (entity != null && entity.lastPositionMs > 10_000L) {
                val durMs = if (entity.durationSeconds > 0) entity.durationSeconds * 1000L else 0L
                if (durMs > 0 && entity.lastPositionMs >= (durMs * 0.95)) {
                    0L // Video was already finished (>95%)
                } else {
                    entity.lastPositionMs
                }
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getSavedPositionMsForTitle(title: String): Long {
        return try {
            val entity = historyDao.findByTitle(title)
            if (entity != null && entity.lastPositionMs > 10_000L) {
                val durMs = if (entity.durationSeconds > 0) entity.durationSeconds * 1000L else 0L
                if (durMs > 0 && entity.lastPositionMs >= (durMs * 0.95)) {
                    0L
                } else {
                    entity.lastPositionMs
                }
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun saveProgressForTitle(title: String, positionMs: Long, durationSec: Long = 0L) {
        if (positionMs < 5_000L || isGenericTitle(title)) return
        try {
            val existing = historyDao.findByTitle(title)
            // Guard: Never downgrade an existing watch progress (e.g. 40 minutes) to early seconds (< 30s)
            val effectivePos = if (existing != null && existing.lastPositionMs > 60_000L && positionMs < 30_000L) {
                existing.lastPositionMs
            } else {
                positionMs
            }
            val entity = HistoryEntity(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                url = existing?.url ?: "saved://$title",
                title = title,
                format = existing?.format ?: "HLS",
                isHls = existing?.isHls ?: true,
                durationSeconds = if (durationSec > 0) durationSec else (existing?.durationSeconds ?: 0L),
                lastPositionMs = effectivePos,
                timestamp = System.currentTimeMillis(),
                pageUrl = existing?.pageUrl ?: ""
            )
            historyDao.insertOrUpdate(entity)
        } catch (e: Exception) {
            // Ignore DB error
        }
    }

    suspend fun saveHistory(stream: StreamMedia, lastPositionMs: Long) {
        try {
            if (lastPositionMs < 5_000L) return
            val existing = historyDao.findByUrlOrTitle(stream.url, stream.title)
                ?: if (!isGenericTitle(stream.title)) historyDao.findByTitle(stream.title) else null
            val entityId = existing?.id ?: stream.id
            val resolvedTitle = if (!isGenericTitle(stream.title)) {
                stream.title
            } else {
                existing?.title ?: _currentMovieTitle.value ?: stream.title
            }
            // Guard: Never overwrite a solid progress position (e.g. 40 minutes) with the first seconds (<30s) of playback!
            val effectivePos = if (existing != null && existing.lastPositionMs > 60_000L && lastPositionMs < 30_000L) {
                existing.lastPositionMs
            } else {
                lastPositionMs
            }
            val durSec = if (stream.durationSeconds > 0) stream.durationSeconds else (existing?.durationSeconds ?: 0L)
            val entity = HistoryEntity(
                id = entityId,
                url = stream.url,
                title = resolvedTitle,
                format = stream.format,
                isHls = stream.isHls,
                durationSeconds = durSec,
                lastPositionMs = effectivePos,
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
