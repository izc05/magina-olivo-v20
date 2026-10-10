package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.isivoltpro.maginaolivo.data.local.dao.FarmDao
import com.isivoltpro.maginaolivo.data.local.dao.ProfileSettingsDao
import com.isivoltpro.maginaolivo.data.local.dao.PhytosanitaryResourceDao
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
import com.isivoltpro.maginaolivo.data.local.entity.RecollectionRatesEntity
import com.isivoltpro.maginaolivo.data.local.dao.RecollectionRatesDao
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.dao.HarvestDao
import com.isivoltpro.maginaolivo.data.local.dao.DeliveryDao
import com.isivoltpro.maginaolivo.data.local.dao.EquipmentDao
import com.isivoltpro.maginaolivo.data.local.dao.WeatherCacheDao
import com.isivoltpro.maginaolivo.data.local.dao.LabourDao
import com.isivoltpro.maginaolivo.data.local.dao.MachineDao
import com.isivoltpro.maginaolivo.data.local.dao.AgendaDao
import com.isivoltpro.maginaolivo.data.local.entity.ActivityPlanningEntity
import com.isivoltpro.maginaolivo.data.local.entity.ReminderEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityMachineEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEquipmentEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestLabourEntity
import com.isivoltpro.maginaolivo.data.local.entity.MachineEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkerEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryYieldAnalysisEntity
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.ProfileSettingsEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.UserProfileEntity
import com.isivoltpro.maginaolivo.data.local.entity.WeatherCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.entity.AgronomicCredentialEntity
import com.isivoltpro.maginaolivo.data.local.dao.TerritorialDao
import com.isivoltpro.maginaolivo.data.local.entity.AgronomicPersonEntity
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryEquipmentInspectionEntity
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryEquipmentProfileEntity
import com.isivoltpro.maginaolivo.data.local.entity.TerritorialMunicipalityEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationCommunityEntity
import com.isivoltpro.maginaolivo.data.local.entity.CommunityWaterNoticeEntity
import com.isivoltpro.maginaolivo.data.local.entity.PersonalIrrigationPlanEntity

private const val DATABASE_VERSION = 26

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
        com.isivoltpro.maginaolivo.data.local.entity.LabourPaymentEntity::class,
        WorkerEntity::class,
        HarvestLabourEntity::class,
        HarvestEquipmentEntity::class,
        RecollectionRatesEntity::class,
        ProfileSettingsEntity::class,
        AgronomicPersonEntity::class,
        AgronomicCredentialEntity::class,
        PhytosanitaryEquipmentProfileEntity::class,
        PhytosanitaryEquipmentInspectionEntity::class,
        TerritorialMunicipalityEntity::class,
        IrrigationCommunityEntity::class,
        CommunityWaterNoticeEntity::class,
        PersonalIrrigationPlanEntity::class,
    ],
    version = DATABASE_VERSION,
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

    abstract fun labourPaymentDao(): com.isivoltpro.maginaolivo.data.local.dao.LabourPaymentDao

    abstract fun labourDao(): LabourDao

    abstract fun recollectionRatesDao(): RecollectionRatesDao

    abstract fun profileSettingsDao(): ProfileSettingsDao

    abstract fun equipmentDao(): EquipmentDao

    abstract fun weatherCacheDao(): WeatherCacheDao

    abstract fun phytosanitaryResourceDao(): PhytosanitaryResourceDao

    abstract fun territorialDao(): TerritorialDao

    companion object {
        const val DATABASE_NAME = "magina-olivo.db"
        const val VERSION = DATABASE_VERSION

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
