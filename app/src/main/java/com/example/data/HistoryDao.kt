package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: HistoryEntity)

    @Query("DELETE FROM playback_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()

    @Query("SELECT * FROM playback_history WHERE LOWER(TRIM(url)) = LOWER(TRIM(:url)) LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntity?

    @Query("SELECT * FROM playback_history WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) ORDER BY timestamp DESC LIMIT 1")
    suspend fun findByTitle(title: String): HistoryEntity?

    @Query("SELECT * FROM playback_history WHERE LOWER(TRIM(url)) = LOWER(TRIM(:url)) OR (LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND title != 'Filme Selecionado') ORDER BY timestamp DESC LIMIT 1")
    suspend fun findByUrlOrTitle(url: String, title: String): HistoryEntity?
}
