package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CopiedImage::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun copiedImageDao(): CopiedImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "copy_image_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun resetInstance() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                INSTANCE = null
            }
        }
    }
}
