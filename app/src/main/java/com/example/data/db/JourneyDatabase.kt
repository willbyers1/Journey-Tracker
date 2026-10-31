package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyPointEntity

@Database(
    entities = [JourneyEntity::class, JourneyPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JourneyDatabase : RoomDatabase() {
    abstract fun journeyDao(): JourneyDao

    companion object {
        @Volatile
        private var INSTANCE: JourneyDatabase? = null

        fun getInstance(context: Context): JourneyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JourneyDatabase::class.java,
                    "journey_tracker.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
