package com.imlupp.customizeliveupdate

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PickupItem::class,
        MealItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pickupDao(): PickupDao

    abstract fun mealDao(): MealDao
}
