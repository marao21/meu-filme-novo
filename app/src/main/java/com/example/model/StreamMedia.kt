package com.example.model

import java.util.Locale
import java.util.UUID

data class StreamMedia(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val pageUrl: String = "",
    val format: String = "M3U8 (HLS)",
    val isHls: Boolean = true,
    val durationSeconds: Long = 0L,
    val isAbove15Min: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val resolution: String = "",
    val isLive: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (isLive) return "Ao Vivo"
            if (durationSeconds <= 0) return "Duração desc."
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
            } else {
                String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds)
            }
        }

    val typeLabel: String
        get() = when {
            isAbove15Min -> "Filme / Episódio (> 15m)"
            durationSeconds in 1..899 -> "Trecho / Clipe (< 15m)"
            else -> if (isHls) "Stream HLS" else "Vídeo Direto"
        }
}
