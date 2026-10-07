package com.isivoltpro.maginaolivo.data.local.model

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    CONFLICT,
}

enum class OutboxOperation {
    CREATE,
    UPDATE,
    DELETE,
    UPLOAD_ATTACHMENT,
}

enum class SyncEntityType {
    USER_PROFILE,
    WORKSPACE,
    FARM,
    PARCEL,
    CAMPAIGN,
    ACTIVITY,
    HARVEST,
    EXPENSE,
    DOCUMENT,
    ALERT,
    ORGANIZATION,
    DOCUMENT_EXTRACTION,
    DELIVERY,
    DELIVERY_YIELD,
    MACHINE,
    WORKER,
    HARVEST_LABOUR,
    LABOUR_PAYMENT,
    HARVEST_EQUIPMENT,
    RECOLLECTION_RATES,
    PROFILE_SETTINGS,
    AGRONOMIC_PERSON,
    PHYTO_EQUIPMENT_PROFILE,
}

enum class OutboxStatus {
    PENDING,
    PROCESSING,
    FAILED,
    BLOCKED,
}

enum class FarmStatus {
    ACTIVE,
    ARCHIVED,
}

enum class RecordStatus {
    ACTIVE,
    ARCHIVED,
}

enum class CampaignStatus {
    PREPARATION,
    ACTIVE,
    HARVEST,
    CLOSED,
}

/**
 * CR-010: the farmer sees Borrador → Activa → Cerrada. HARVEST is a legacy state kept for existing
 * rows; it reads and behaves exactly like ACTIVE ("running": Pesadas, jornales, cierre).
 */
val CampaignStatus.isRunning: Boolean get() = this == CampaignStatus.ACTIVE || this == CampaignStatus.HARVEST

enum class ActivityStatus {
    DRAFT,
    PLANNED,
    COMPLETED,
    CANCELLED,
}
