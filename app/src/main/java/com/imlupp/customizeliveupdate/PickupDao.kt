package com.imlupp.customizeliveupdate

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PickupDao {
    @Insert
    suspend fun insert(item: PickupItem)

    @Delete
    suspend fun delete(item: PickupItem)

    @Query("SELECT * FROM pickup_items")
    fun getAll(): Flow<List<PickupItem>>
}