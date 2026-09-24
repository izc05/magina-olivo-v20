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
import com.isivoltpro.maginaolivo.data.local.entity.FertilizationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IncidentDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationPriceSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.MaintenanceDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PruningDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.SoilWorkDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.AlertEntity
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalOrganizationEntity
import com.isivoltpro.maginaolivo.data.local.entity.DocumentOcrExtractionEntity
import com.isivoltpro.maginaolivo.data.local.entity.OrganizationRoleEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseItemEntity
import com.isivoltpro.maginaolivo.data.local.dao.ExpenseDao
import com.isivoltpro.maginaolivo.data.local.dao.OrganizationDao
import com.isivoltpro.maginaolivo.data.local.dao.DocumentOcrDao
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.DocumentEntity
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.dao.HarvestDao
import com.isivoltpro.maginaolivo.data.local.dao.DeliveryDao
import com.isivoltpro.maginaolivo.data.local.dao.MachineDao
import com.isivoltpro.maginaolivo.data.local.dao.AgendaDao
import com.isivoltpro.maginaolivo.data.local.entity.ActivityPlanningEntity
import com.isivoltpro.maginaolivo.data.local.entity.ReminderEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityMachineEntity
import com.isivoltpro.maginaolivo.data.local.entity.MachineEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryYieldAnalysisEntity
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
        PruningDetailEntity::class,
        FertilizationDetailEntity::class,
        PhytosanitaryDetailEntity::class,
        SoilWorkDetailEntity::class,
        IrrigationDetailEntity::class,
        IrrigationPriceSnapshotEntity::class,
        MaintenanceDetailEntity::class,
        IncidentDetailEntity::class,
        HarvestEntity::class,
        HarvestParcelEntity::class,
        DeliveryEntity::class,
        DeliveryParcelEntity::class,
        DeliveryYieldAnalysisEntity::class,
        MachineEntity::class,
        ActivityMachineEntity::class,
        ActivityPlanningEntity::class,
        ReminderEntity::class,
        ExpenseEntity::class,
        DocumentEntity::class,
        WeatherCacheEntity::class,
        AlertEntity::class,
        AgriculturalOrganizationEntity::class,
        OrganizationRoleEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        DocumentOcrExtractionEntity::class,
    ],
    version = 13,
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

    abstract fun expenseDao(): ExpenseDao

    abstract fun organizationDao(): OrganizationDao

    abstract fun documentOcrDao(): DocumentOcrDao

    abstract fun harvestDao(): HarvestDao

    abstract fun deliveryDao(): DeliveryDao

    abstract fun machineDao(): MachineDao

    abstract fun agendaDao(): AgendaDao

    companion object {
        const val DATABASE_NAME = "magina-olivo.db"
        const val VERSION = 12

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
