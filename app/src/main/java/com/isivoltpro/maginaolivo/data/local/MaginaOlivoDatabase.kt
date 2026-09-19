package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.isivoltpro.maginaolivo.data.local.dao.FarmDao
import com.isivoltpro.maginaolivo.data.local.dao.SyncOutboxDao
import com.isivoltpro.maginaolivo.data.local.dao.WorkspaceDao
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity

@Database(
    entities = [
        WorkspaceEntity::class,
        FarmEntity::class,
        SyncOutboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class MaginaOlivoDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    abstract fun farmDao(): FarmDao

    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        const val DATABASE_NAME = "magina-olivo.db"
        const val VERSION = 1

        fun create(context: Context): MaginaOlivoDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    MaginaOlivoDatabase::class.java,
                    DATABASE_NAME,
                ).build()
    }
}
