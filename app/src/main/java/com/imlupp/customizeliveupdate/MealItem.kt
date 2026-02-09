package com.imlupp.customizeliveupdate

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MealItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val location: String,
    val code: String
)
