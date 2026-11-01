package com.example.player

import com.example.model.StreamMedia

data class PlayerState(
    val currentStream: StreamMedia? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val resizeMode: Int = 0, // 0 = FIT, 1 = FILL, 2 = ZOOM
    val isLocked: Boolean = false,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalDurationMs > 0) {
            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
