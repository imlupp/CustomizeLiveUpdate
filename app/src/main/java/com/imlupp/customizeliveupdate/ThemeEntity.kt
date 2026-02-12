package com.imlupp.customizeliveupdate

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ThemeEntity(
    @PrimaryKey val id: Int = 0,   // 永远只存一条
    val mode: String                // "LIGHT", "DARK", "SYSTEM"
)
