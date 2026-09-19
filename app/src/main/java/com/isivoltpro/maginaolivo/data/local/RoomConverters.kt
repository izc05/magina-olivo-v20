package com.isivoltpro.maginaolivo.data.local

import androidx.room.TypeConverter
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RoomConverters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun syncStatusToString(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun stringToSyncStatus(value: String?): SyncStatus? = value?.let(SyncStatus::valueOf)

    @TypeConverter
    fun outboxOperationToString(value: OutboxOperation?): String? = value?.name

    @TypeConverter
    fun stringToOutboxOperation(value: String?): OutboxOperation? =
        value?.let(OutboxOperation::valueOf)

    @TypeConverter
    fun syncEntityTypeToString(value: SyncEntityType?): String? = value?.name

    @TypeConverter
    fun stringToSyncEntityType(value: String?): SyncEntityType? =
        value?.let(SyncEntityType::valueOf)

    @TypeConverter
    fun outboxStatusToString(value: OutboxStatus?): String? = value?.name

    @TypeConverter
    fun stringToOutboxStatus(value: String?): OutboxStatus? = value?.let(OutboxStatus::valueOf)

    @TypeConverter
    fun farmStatusToString(value: FarmStatus?): String? = value?.name

    @TypeConverter
    fun stringToFarmStatus(value: String?): FarmStatus? = value?.let(FarmStatus::valueOf)
}
