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

    private val _isMouseModeEnabled = MutableStateFlow(false)
    val isMouseModeEnabled: StateFlow<Boolean> = _isMouseModeEnabled.asStateFlow()

    fun setMouseModeEnabled(enabled: Boolean) {
        _isMouseModeEnabled.value = enabled
    }

    fun toggleMouseMode(): Boolean {
        val next = !_isMouseModeEnabled.value
        _isMouseModeEnabled.value = next
        return next
    }

    private var positionTrackerJob: Job? = null
    private var autoQualityJob: Job? = null
    private var bufferingStartMs = 0L
    private var currentAutoCapHeight: Int? = null
    private var smoothPlaybackSeconds = 0
    private var noticeJob: Job? = null

    @Volatile
    private var pendingSeekPositionMs = 0L

    val trackSelector: DefaultTrackSelector
    val bandwidthMeter: DefaultBandwidthMeter

    private var reconnectionAttempts = 0

    private fun buildSmartLoadControl(lowMemoryMode: Boolean): DefaultLoadControl {
        // YouTube-style Adaptive Buffer specifications:
        // - Initial playback starts in 1.5s (prevents indefinite freezing on weak internet)
        // - Fast rebuffer recovery: 2.5s
        // - Safe buffer window: 15s to 35s
        // - Back buffer: 10s
        // - Memory protection: 16MB to 32MB
        val minBufferMs = if (lowMemoryMode) 12_000 else 15_000
        val maxBufferMs = if (lowMemoryMode) 25_000 else 35_000
        val bufferForPlaybackMs = 1_500
        val bufferForPlaybackAfterRebufferMs = 2_500
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
            .setBackBuffer(10_000, true)
            .build()
    }

    init {
        // Responsive ABR: drops quickly when buffer drains, requires 10s stability before upgrading
        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 10_000,
            /* maxDurationForQualityDecreaseMs = */ 8_000,
            /* minDurationToRetainAfterDiscardMs = */ 4_000,
            /* bandwidthFraction = */ 0.70f
        )
        trackSelector = DefaultTrackSelector(application, adaptiveTrackSelectionFactory)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setAllowMultipleAdaptiveSelections(true)
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setExceedVideoConstraintsIfNecessary(true)
            .setTunnelingEnabled(false)
            .build()

        bandwidthMeter = DefaultBandwidthMeter.Builder(application)
            .setInitialBitrateEstimate(1_200_000L) // 1.2 Mbps baseline (starts fast at 480p/720p without stalling)
            .setSlidingWindowMaxWeight(1000) // Lower sliding window weight adapts 2x faster to internet dips
            .setResetOnNetworkTypeChange(true)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(12000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://filmemeu.vercel.app/"))
            .setTransferListener(bandwidthMeter)

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
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        val count = group.length
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
                val standardTiers = listOf("1080p", "720p", "480p", "360p")
                val fullList = if (available.size > 1) {
                    listOf("AUTO") + available
                } else if (available.isNotEmpty()) {
                    listOf("AUTO") + (listOf(available.first()) + standardTiers).distinct()
                } else {
                    listOf("AUTO", "1080p", "720p", "480p", "360p")
                }
                _playerState.update { it.copy(availableQualities = fullList) }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val h = videoSize.height
                if (h > 0) {
                    val currentRes = "${h}p"
                    val pref = _playerState.value.selectedQualityPreference
                    val label = if (pref == "AUTO" || pref.startsWith("AUTO")) "AUTO ($currentRes)" else currentRes
                    _playerState.update { it.copy(currentQualityLabel = label) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (bufferingStartMs == 0L) {
                            bufferingStartMs = System.currentTimeMillis()
                        }
                        _playerState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    Player.STATE_READY -> {
                        bufferingStartMs = 0L
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

        currentAutoCapHeight = null
        bufferingStartMs = 0L
        smoothPlaybackSeconds = 0
        if (_playerState.value.selectedQualityPreference == "AUTO") {
            clearResolutionConstraint()
        }
        startPositionTracker()
        startAutoQualitySupervisor()

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
        autoQualityJob?.cancel()
        noticeJob?.cancel()
        currentAutoCapHeight = null
        bufferingStartMs = 0L
        smoothPlaybackSeconds = 0

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _playerState.update {
            it.copy(
                currentStream = null,
                isPlaying = false,
                isLoading = false,
                currentPositionMs = 0L,
                totalDurationMs = 0L,
                errorMessage = null,
                autoQualityNotice = null
            )
        }
    }

    fun showAutoQualityNotice(message: String) {
        noticeJob?.cancel()
        _playerState.update { it.copy(autoQualityNotice = message) }
        noticeJob = viewModelScope.launch {
            delay(3500)
            _playerState.update { it.copy(autoQualityNotice = null) }
        }
    }

    private fun applyResolutionConstraint(maxHeight: Int) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setMaxVideoSize(1920, maxHeight)
            .build()
    }

    private fun clearResolutionConstraint() {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearVideoSizeConstraints()
            .build()
    }

    private fun startAutoQualitySupervisor() {
        autoQualityJob?.cancel()
        autoQualityJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val pref = _playerState.value.selectedQualityPreference
                if (pref != "AUTO" && !pref.startsWith("AUTO")) {
                    continue
                }

                val playbackState = exoPlayer.playbackState
                val isPlaying = exoPlayer.isPlaying

                if (playbackState == Player.STATE_BUFFERING) {
                    smoothPlaybackSeconds = 0
                    if (bufferingStartMs == 0L) {
                        bufferingStartMs = System.currentTimeMillis()
                    }
                    val bufferWaitMs = System.currentTimeMillis() - bufferingStartMs

                    // Se a internet estiver fraca e o vídeo travar carregando por mais de 2.5s:
                    // Abaixa imediatamente para 720p, 480p ou 360p para nunca travar a reprodução!
                    if (bufferWaitMs >= 2500 && (currentAutoCapHeight == null || currentAutoCapHeight!! > 720)) {
                        currentAutoCapHeight = 720
                        applyResolutionConstraint(720)
                        showAutoQualityNotice("Internet lenta: ajustando para 720p...")
                    } else if (bufferWaitMs >= 5000 && (currentAutoCapHeight == null || currentAutoCapHeight!! > 480)) {
                        currentAutoCapHeight = 480
                        applyResolutionConstraint(480)
                        showAutoQualityNotice("Internet lenta: ajustando para 480p...")
                    } else if (bufferWaitMs >= 8000 && (currentAutoCapHeight == null || currentAutoCapHeight!! > 360)) {
                        currentAutoCapHeight = 360
                        applyResolutionConstraint(360)
                        showAutoQualityNotice("Internet instável: ajustando para 360p...")
                    }
                } else if (playbackState == Player.STATE_READY && isPlaying) {
                    bufferingStartMs = 0L
                    smoothPlaybackSeconds++

                    // Quando a internet se estabiliza e toca continuamente sem travar por 25s:
                    // Restaura gradualmente a qualidade de volta até 1080p Full HD!
                    if (smoothPlaybackSeconds >= 25 && currentAutoCapHeight != null) {
                        smoothPlaybackSeconds = 0
                        when (currentAutoCapHeight) {
                            360 -> {
                                currentAutoCapHeight = 480
                                applyResolutionConstraint(480)
                                showAutoQualityNotice("Internet normalizada: melhorando para 480p...")
                            }
                            480 -> {
                                currentAutoCapHeight = 720
                                applyResolutionConstraint(720)
                                showAutoQualityNotice("Internet normalizada: melhorando para 720p...")
                            }
                            720 -> {
                                currentAutoCapHeight = null
                                clearResolutionConstraint()
                                showAutoQualityNotice("Internet rápida: restaurando Full HD 1080p...")
                            }
                        }
                    }
                } else {
                    bufferingStartMs = 0L
                }
            }
        }
    }

    fun setQualityPreference(quality: String) {
        _playerState.update { it.copy(selectedQualityPreference = quality) }
        if (quality == "AUTO" || quality.startsWith("ORIGINAL") || quality.startsWith("AUTO")) {
            currentAutoCapHeight = null
            clearResolutionConstraint()
            val currentH = exoPlayer.videoSize.height
            val label = if (currentH > 0) "AUTO (${currentH}p)" else "AUTO"
            _playerState.update { it.copy(currentQualityLabel = label) }
            showAutoQualityNotice("Qualidade Automática ativada (modo YouTube)")
        } else {
            val targetHeight = quality.replace("p", "").toIntOrNull() ?: return
            currentAutoCapHeight = targetHeight
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(1920, targetHeight)
                .setMinVideoSize(0, targetHeight)
                .build()
            _playerState.update { it.copy(currentQualityLabel = quality) }
            showAutoQualityNotice("Qualidade fixada em $quality")
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
