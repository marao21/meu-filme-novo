package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val format: String,
    val isHls: Boolean,
    val durationSeconds: Long,
    val lastPositionMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val pageUrl: String = ""
) {
    val formattedDuration: String
        get() {
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
}
