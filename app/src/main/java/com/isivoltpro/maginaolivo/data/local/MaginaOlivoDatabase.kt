package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.isivoltpro.maginaolivo.data.local.dao.FarmDao
import com.isivoltpro.maginaolivo.data.local.dao.ActivityDao
import com.isivoltpro.maginaolivo.data.local.dao.CampaignDao
import com.isivoltpro.maginaolivo.data.local.dao.DocumentDao
import com.isivoltpro.maginaolivo.data.local.dao.ParcelDao
import com.isivoltpro.maginaolivo.data.local.dao.SyncOutboxDao
import com.isivoltpro.maginaolivo.data.local.dao.WorkspaceDao
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity
import com.isivoltpro.maginaolivo.data.local.entity.AlertEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.DocumentEntity
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.UserProfileEntity
import com.isivoltpro.maginaolivo.data.local.entity.WeatherCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity

@Database(
    entities = [
        WorkspaceEntity::class,
        FarmEntity::class,
        SyncOutboxEntity::class,
        UserProfileEntity::class,
        ParcelEntity::class,
        FarmParcelMembershipEntity::class,
        CampaignEntity::class,
        CampaignParcelSnapshotEntity::class,
        ActivityEntity::class,
        ActivityParcelTargetEntity::class,
        HarvestEntity::class,
        ExpenseEntity::class,
        DocumentEntity::class,
        WeatherCacheEntity::class,
        AlertEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class MaginaOlivoDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    abstract fun farmDao(): FarmDao

    abstract fun documentDao(): DocumentDao

    abstract fun parcelDao(): ParcelDao

    abstract fun campaignDao(): CampaignDao

    abstract fun activityDao(): ActivityDao

    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        const val DATABASE_NAME = "magina-olivo.db"
        const val VERSION = 4

        @Volatile
        private var instance: MaginaOlivoDatabase? = null

        fun getInstance(context: Context): MaginaOlivoDatabase =
            instance ?: synchronized(this) {
                instance ?: create(context).also { created -> instance = created }
            }

        fun create(
            context: Context,
            databaseName: String = DATABASE_NAME,
        ): MaginaOlivoDatabase =
                Room
                .databaseBuilder(
                    context.applicationContext,
                    MaginaOlivoDatabase::class.java,
                    databaseName,
                ).addMigrations(*DatabaseMigrations.all)
                .build()
    }
}
