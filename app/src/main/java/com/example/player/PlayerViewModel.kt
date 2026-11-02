package com.example.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
        RETURN_TO_AUDIO_SELECTION
    }

    private val _webKeyEvents = kotlinx.coroutines.flow.MutableSharedFlow<WebKeyAction>(extraBufferCapacity = 1)
    val webKeyEvents: kotlinx.coroutines.flow.SharedFlow<WebKeyAction> = _webKeyEvents.asSharedFlow()

    fun sendWebKeyAction(action: WebKeyAction) {
        _webKeyEvents.tryEmit(action)
    }

    private var positionTrackerJob: Job? = null

    init {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://filmemeu.vercel.app/"))

        val mediaSourceFactory = DefaultMediaSourceFactory(application)
            .setDataSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(application)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    Player.STATE_READY -> {
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
                        _playerState.update {
                            it.copy(
                                isLoading = false,
                                totalDurationMs = duration,
                                currentPositionMs = exoPlayer.currentPosition
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        _playerState.update { it.copy(isPlaying = false, isLoading = false) }
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
                val userMsg = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Falha de conexão com a rede."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Servidor de streaming indisponível (HTTP error)."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Formato do stream corrompido ou inacessível."
                    else -> "Erro ao carregar o stream: ${error.localizedMessage ?: "desconhecido"}"
                }
                _playerState.update { it.copy(isLoading = false, errorMessage = userMsg) }
            }
        })
    }

    fun playStream(stream: StreamMedia, startPositionMs: Long = 0L) {
        val uri = Uri.parse(stream.url)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(stream.id)
            .build()

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem)
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
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
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
        val current = _playerState.value.currentStream
        if (current != null) {
            repository.notifyPlayerClosed(current.url)
        }
        exoPlayer.stop()
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

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = viewModelScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _playerState.update {
                        it.copy(
                            currentPositionMs = pos,
                            totalDurationMs = if (dur > 0) dur else it.totalDurationMs
                        )
                    }
                }
                delay(1000)
            }
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
