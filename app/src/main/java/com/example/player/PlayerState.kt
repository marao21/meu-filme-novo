package com.example.player

import com.example.model.StreamMedia

data class PlayerState(
    val currentStream: StreamMedia? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isReconnecting: Boolean = false,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val resizeMode: Int = 0, // 0 = FIT, 1 = FILL, 2 = ZOOM
    val isLocked: Boolean = false,
    val currentQualityLabel: String = "AUTO",
    val selectedQualityPreference: String = "AUTO",
    val availableQualities: List<String> = emptyList(),
    val isLowMemoryMode: Boolean = false,
    val isSmartBufferActive: Boolean = true,
    val autoQualityNotice: String? = null,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalDurationMs > 0) {
            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val bufferedFraction: Float
        get() = if (totalDurationMs > 0) {
            (bufferedPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val bufferSecondsAhead: Int
        get() = ((bufferedPositionMs - currentPositionMs) / 1000).coerceAtLeast(0L).toInt()
}

