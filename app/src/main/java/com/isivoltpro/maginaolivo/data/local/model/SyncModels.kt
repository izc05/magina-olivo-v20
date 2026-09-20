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

enum class ActivityStatus {
    DRAFT,
    PLANNED,
    COMPLETED,
    CANCELLED,
}
