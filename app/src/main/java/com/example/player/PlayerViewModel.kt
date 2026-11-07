package com.example.player

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.data.AppDatabase
import com.example.data.StreamRepository
import com.example.model.StreamMedia
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = StreamRepository(db.historyDao(), viewModelScope)

    val exoPlayer: ExoPlayer

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    enum class WebKeyAction {
        PLAY_PAUSE,
        REWIND,
        FORWARD,
        DPAD_UP,
        DPAD_DOWN,
        DPAD_LEFT,
        DPAD_RIGHT,
        DPAD_CENTER,
        RETURN_TO_AUDIO_SELECTION,
        HANDLE_BACK,
        TOGGLE_MENU
    }

    private val _webKeyEvents = kotlinx.coroutines.flow.MutableSharedFlow<WebKeyAction>(extraBufferCapacity = 1)
    val webKeyEvents: kotlinx.coroutines.flow.SharedFlow<WebKeyAction> = _webKeyEvents.asSharedFlow()

    fun sendWebKeyAction(action: WebKeyAction) {
        _webKeyEvents.tryEmit(action)
    }

    private val _playerControlsTrigger = MutableStateFlow(0L)
    val playerControlsTrigger: StateFlow<Long> = _playerControlsTrigger.asStateFlow()

    fun triggerShowControls() {
        _playerControlsTrigger.value = System.currentTimeMillis()
    }

    private var positionTrackerJob: Job? = null

    @Volatile
    private var pendingSeekPositionMs = 0L

    val trackSelector: DefaultTrackSelector
    val bandwidthMeter: DefaultBandwidthMeter

    private var reconnectionAttempts = 0

    private fun buildSmartLoadControl(lowMemoryMode: Boolean): DefaultLoadControl {
        // Smart Buffer specifications:
        // - Initial playback buffer: 5 to 8 seconds (5,000ms - 8,000ms)
        // - Normal buffer: 20 to 30 seconds
        // - Unstable network expansion: up to 45 seconds
        // - Back buffer: 15 seconds to prevent re-downloading on 10s rewinds
        // - TV Box low memory protection: 16MB to 32MB buffer ceiling
        val minBufferMs = if (lowMemoryMode) 15_000 else 20_000
        val maxBufferMs = if (lowMemoryMode) 25_000 else 45_000
        val bufferForPlaybackMs = 5_000
        val bufferForPlaybackAfterRebufferMs = 8_000
        val targetBufferBytes = if (lowMemoryMode) 16 * 1024 * 1024 else 32 * 1024 * 1024

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(15_000, true)
            .build()
    }

    init {
        trackSelector = DefaultTrackSelector(application, AdaptiveTrackSelection.Factory())
        bandwidthMeter = DefaultBandwidthMeter.Builder(application).build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://filmemeu.vercel.app/"))

        val errorHandlingPolicy = DefaultLoadErrorHandlingPolicy(5)

        val mediaSourceFactory = DefaultMediaSourceFactory(application)
            .setDataSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        exoPlayer = ExoPlayer.Builder(application)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(buildSmartLoadControl(false))
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val available = mutableListOf<String>()
                var hasMultiResolution = false
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        val count = group.length
                        if (count > 1) {
                            hasMultiResolution = true
                        }
                        for (i in 0 until count) {
                            val format = group.getTrackFormat(i)
                            val h = format.height
                            if (h > 0) {
                                val label = "${h}p"
                                if (!available.contains(label)) {
                                    available.add(label)
                                }
                            }
                        }
                    }
                }
                available.sortByDescending { it.replace("p", "").toIntOrNull() ?: 0 }
                val fullList = if (hasMultiResolution || available.size > 1) {
                    listOf("AUTO") + available
                } else if (available.isNotEmpty()) {
                    listOf("AUTO (${available.first()})")
                } else {
                    listOf("AUTO")
                }
                _playerState.update { it.copy(availableQualities = fullList) }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val h = videoSize.height
                if (h > 0) {
                    val currentRes = "${h}p"
                    val pref = _playerState.value.selectedQualityPreference
                    val label = if (pref == "AUTO") "AUTO · $currentRes" else currentRes
                    _playerState.update { it.copy(currentQualityLabel = label) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    Player.STATE_READY -> {
                        reconnectionAttempts = 0
                        val duration = exoPlayer.duration.coerceAtLeast(0L)
                        // Discard if stream has duration under 15 minutes (less than 900,000 ms)
                        if (duration in 1..899_999L) {
                            exoPlayer.stop()
                            _playerState.update {
                                it.copy(
                                    isLoading = false,
                                    isPlaying = false,
                                    currentStream = null,
                                    errorMessage = "Vídeo com menos de 15 minutos ignorado."
                                )
                            }
                            return
                        }
                        val seekTarget = pendingSeekPositionMs
                        if (seekTarget > 10_000L && kotlin.math.abs(exoPlayer.currentPosition - seekTarget) > 10_000L) {
                            pendingSeekPositionMs = 0L
                            exoPlayer.seekTo(seekTarget)
                        }
                        _playerState.update {
                            it.copy(
                                isLoading = false,
                                isReconnecting = false,
                                totalDurationMs = duration,
                                currentPositionMs = exoPlayer.currentPosition,
                                bufferedPositionMs = exoPlayer.bufferedPosition
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        _playerState.update { it.copy(isPlaying = false, isLoading = false, isReconnecting = false) }
                    }
                    Player.STATE_IDLE -> {
                        _playerState.update { it.copy(isLoading = false) }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionTracker()
                } else {
                    stopPositionTracker()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val isNetwork = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

                if (isNetwork && reconnectionAttempts < 3) {
                    reconnectionAttempts++
                    _playerState.update { it.copy(isLoading = true, isReconnecting = true, errorMessage = null) }
                    mainHandler.postDelayed({
                        val cur = _playerState.value.currentStream
                        val pos = exoPlayer.currentPosition
                        if (cur != null) {
                            playStream(cur, pos)
                        }
                    }, 2000)
                    return
                }

                reconnectionAttempts = 0
                val userMsg = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Falha de conexão com a rede. Verifique sua internet."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Servidor de streaming indisponível (HTTP error)."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Formato do stream corrompido ou inacessível."
                    else -> "Erro ao carregar o stream: ${error.localizedMessage ?: "desconhecido"}"
                }
                _playerState.update { it.copy(isLoading = false, isReconnecting = false, errorMessage = userMsg) }
            }
        })
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun updateCloudProgress(title: String, positionMs: Long, durationSec: Long = 0L) {
        if (positionMs <= 10_000L || repository.isGenericTitle(title)) return
        viewModelScope.launch {
            repository.saveProgressForTitle(title, positionMs, durationSec)
            val currentStream = _playerState.value.currentStream
            if (currentStream != null && exoPlayer.currentPosition < 30_000L && positionMs > 60_000L) {
                val rewindTarget = if (positionMs > 60_000L) positionMs - 60_000L else positionMs
                mainHandler.post {
                    exoPlayer.seekTo(rewindTarget)
                    val resumeFormatted = formatTimeMs(rewindTarget)
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Continuar assistindo: sincronizado de outro dispositivo (retomando de $resumeFormatted)",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun playStream(stream: StreamMedia, startPositionMs: Long = 0L) {
        viewModelScope.launch {
            var targetPosition = startPositionMs
            if (targetPosition <= 0L) {
                val savedMs = repository.getSavedPositionMs(stream)
                if (savedMs > 15_000L) {
                    // Smart Resume: rewind 1 minute (60,000 ms) so user easily catches back up with the scene:
                    // e.g. 30min -> 29min, 35min -> 34min
                    targetPosition = if (savedMs > 60_000L) (savedMs - 60_000L) else savedMs
                    mainHandler.post {
                        val resumeFormatted = formatTimeMs(targetPosition)
                        android.widget.Toast.makeText(
                            getApplication(),
                            "Continuar assistindo: retomando de $resumeFormatted",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                executePlayStream(stream, targetPosition)
            } else {
                mainHandler.post { executePlayStream(stream, targetPosition) }
            }
        }
    }

    private fun executePlayStream(stream: StreamMedia, startPositionMs: Long) {
        val uri = Uri.parse(stream.url)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(stream.id)
            .build()

        pendingSeekPositionMs = startPositionMs
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        _playerState.update {
            it.copy(
                currentStream = stream,
                isLoading = true,
                errorMessage = null,
                currentPositionMs = startPositionMs,
                totalDurationMs = if (stream.durationSeconds > 0) stream.durationSeconds * 1000 else 0L
            )
        }
    }

    fun togglePlayPause() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (exoPlayer.isPlaying) pause() else play()
        } else {
            mainHandler.post {
                if (exoPlayer.isPlaying) pause() else play()
            }
        }
    }

    fun play() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer.play()
        } else {
            mainHandler.post { exoPlayer.play() }
        }
    }

    fun pause() {
        val pos = exoPlayer.currentPosition
        val current = _playerState.value.currentStream
        if (current != null && pos > 5_000L) {
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            val durSec = if (dur > 0) dur / 1000L else current.durationSeconds
            viewModelScope.launch {
                repository.saveHistory(current.copy(durationSeconds = durSec), pos)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer.pause()
        } else {
            mainHandler.post { exoPlayer.pause() }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward10s() {
        val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
        seekTo(target)
    }

    fun seekBackward10s() {
        val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    fun cycleResizeMode() {
        _playerState.update {
            val next = (it.resizeMode + 1) % 3
            it.copy(resizeMode = next)
        }
    }

    fun toggleLock() {
        _playerState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun retryPlayback() {
        val current = _playerState.value.currentStream ?: return
        val pos = _playerState.value.currentPositionMs
        playStream(current, pos)
    }

    fun stopPlayback() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeStopPlayback()
        } else {
            mainHandler.post { executeStopPlayback() }
        }
    }

    private fun executeStopPlayback() {
        val current = _playerState.value.currentStream
        val pos = exoPlayer.currentPosition
        if (current != null) {
            if (pos > 5_000L) {
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                val durSec = if (dur > 0) dur / 1000L else current.durationSeconds
                viewModelScope.launch {
                    repository.saveHistory(current.copy(durationSeconds = durSec), pos)
                }
            }
            repository.notifyPlayerClosed(current.url)
        }
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _playerState.update {
            it.copy(
                currentStream = null,
                isPlaying = false,
                isLoading = false,
                currentPositionMs = 0L,
                totalDurationMs = 0L,
                errorMessage = null
            )
        }
    }

    fun setQualityPreference(quality: String) {
        _playerState.update { it.copy(selectedQualityPreference = quality) }
        if (quality == "AUTO" || quality.startsWith("ORIGINAL") || quality.startsWith("AUTO")) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearVideoSizeConstraints()
            )
            val currentH = exoPlayer.videoSize.height
            val label = if (currentH > 0) "AUTO · ${currentH}p" else "AUTO"
            _playerState.update { it.copy(currentQualityLabel = label) }
        } else {
            val targetHeight = quality.replace("p", "").toIntOrNull() ?: return
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(1920, targetHeight)
                    .setMinVideoSize(0, targetHeight)
            )
            _playerState.update { it.copy(currentQualityLabel = quality) }
        }
    }

    fun toggleLowMemoryMode() {
        val newMode = !_playerState.value.isLowMemoryMode
        _playerState.update { it.copy(isLowMemoryMode = newMode) }
        android.widget.Toast.makeText(
            getApplication(),
            if (newMode) "Modo TV Box ativado (Economia de Memória)" else "Modo TV Box desativado (Buffer Completo)",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private var lastSavedProgressMs = 0L

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = viewModelScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val buf = exoPlayer.bufferedPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _playerState.update {
                        it.copy(
                            currentPositionMs = pos,
                            bufferedPositionMs = buf,
                            totalDurationMs = if (dur > 0) dur else it.totalDurationMs
                        )
                    }
                    val curStream = _playerState.value.currentStream
                    if (curStream != null && pos > 5_000L && kotlin.math.abs(pos - lastSavedProgressMs) >= 4_000L) {
                        lastSavedProgressMs = pos
                        val durSec = if (dur > 0) dur / 1000L else curStream.durationSeconds
                        repository.saveHistory(curStream.copy(durationSeconds = durSec), pos)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun formatTimeMs(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionTracker()
        exoPlayer.release()
    }
}
