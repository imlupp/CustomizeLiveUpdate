package com.imlupp.customizeliveupdate

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {

    @Query("SELECT * FROM ThemeEntity WHERE id = 0")
    suspend fun getTheme(): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTheme(theme: ThemeEntity)
}
