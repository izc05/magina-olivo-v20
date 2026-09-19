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
