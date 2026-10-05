package com.masstamilan.app.data.dao

import androidx.room.*
import com.masstamilan.app.data.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songKey = :key)")
    suspend fun isFavorite(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fav: FavoriteEntity)

    @Delete
    suspend fun delete(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}
