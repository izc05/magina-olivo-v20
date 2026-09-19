package com.isivoltpro.maginaolivo.data.local

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import java.time.Instant
import java.util.UUID

/** Explicit, production-excluded fixtures for previews and deterministic Android tests. */
object DevDatabaseSeeder {
    val workspaceId: UUID = UUID.fromString("10000000-0000-0000-0000-000000000010")
    val farmId: UUID = UUID.fromString("20000000-0000-0000-0000-000000000010")

    private val ownerId = UUID.fromString("90000000-0000-0000-0000-000000000010")
    private val fixtureInstant = Instant.parse("2026-09-19T12:00:00Z")
    private val fixtureMetadata = LocalMetadata(
        createdAt = fixtureInstant,
        updatedAt = fixtureInstant,
    )

    suspend fun seed(database: MaginaOlivoDatabase) {
        database.withTransaction {
            database.workspaceDao().upsert(
                WorkspaceEntity(
                    id = workspaceId,
                    name = "Mi olivar de muestra",
                    ownerUserId = ownerId,
                    countryCode = "ES",
                    timezone = "Europe/Madrid",
                    locale = "es-ES",
                    currency = "EUR",
                    metadata = fixtureMetadata,
                ),
            )
            database.farmDao().upsert(
                FarmEntity(
                    id = farmId,
                    workspaceId = workspaceId,
                    name = "La Solana",
                    municipality = "Huelma",
                    province = "Jaén",
                    notes = "Datos deterministas de desarrollo; nunca se insertan al arrancar.",
                    metadata = fixtureMetadata,
                ),
            )
        }
    }
}
