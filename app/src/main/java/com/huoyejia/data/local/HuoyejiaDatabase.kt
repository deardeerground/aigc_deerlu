package com.huoyejia.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteEmbeddingEntity::class,
        NoteRelationEntity::class,
        ReviewCardEntity::class,
        UserStatsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HuoyejiaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun relationDao(): RelationDao
    abstract fun reviewCardDao(): ReviewCardDao
    abstract fun statsDao(): StatsDao

    companion object {
        fun create(context: Context): HuoyejiaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                HuoyejiaDatabase::class.java,
                "huoyejia.db"
            ).build()
        }
    }
}
