package com.imlupp.customizeliveupdate

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Query("SELECT * FROM MealItem")
    fun getAll(): Flow<List<MealItem>>

    @Insert
    suspend fun insert(item: MealItem)

    @Delete
    suspend fun delete(item: MealItem)
}
