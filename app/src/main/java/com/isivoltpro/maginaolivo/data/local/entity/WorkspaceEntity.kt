package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "workspaces",
    indices = [Index(value = ["owner_user_id"])],
)
data class WorkspaceEntity(
    @PrimaryKey
    val id: UUID,
    val name: String,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: UUID,
    @ColumnInfo(name = "country_code")
    val countryCode: String,
    val timezone: String,
    val locale: String,
    val currency: String,
    @Embedded
    val metadata: LocalMetadata,
)
