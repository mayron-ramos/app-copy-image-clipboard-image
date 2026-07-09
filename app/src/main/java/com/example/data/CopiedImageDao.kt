package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CopiedImageDao {
    @Query("SELECT * FROM copied_images ORDER BY timestamp DESC")
    fun getAllImages(): Flow<List<CopiedImage>>

    @Query("SELECT * FROM copied_images ORDER BY timestamp DESC")
    suspend fun getAllImagesDirect(): List<CopiedImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: CopiedImage): Long

    @Delete
    suspend fun deleteImage(image: CopiedImage)

    @Query("DELETE FROM copied_images WHERE id = :id")
    suspend fun deleteImageById(id: Int)

    @Query("DELETE FROM copied_images")
    suspend fun deleteAllImages()
}
