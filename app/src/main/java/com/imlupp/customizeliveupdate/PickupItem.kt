package com.imlupp.customizeliveupdate

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pickup_items")
data class PickupItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val location: String,
    val code: String
)