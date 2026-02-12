package com.imlupp.customizeliveupdate

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PickupItem::class,
        MealItem::class,
        ThemeEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pickupDao(): PickupDao

    abstract fun mealDao(): MealDao

    abstract fun themeDao(): ThemeDao
}
