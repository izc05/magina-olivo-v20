package com.isivoltpro.maginaolivo.domain.phytosanitary

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class RegulatoryResourceSource { MANUAL, REAFA }

data class AgronomicPerson(
    val id: UUID,
    val displayName: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val taxId: String? = null,
    val isAdvisor: Boolean = false,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
    val archived: Boolean = false,
    val version: Long = 1,
)

data class AgronomicPersonDraft(
    val displayName: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val taxId: String? = null,
    val isAdvisor: Boolean = false,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
)

data class AgronomicCredential(
    val id: UUID,
    val personId: UUID,
    /** Dynamic semantic code finalized by #536/#558. */
    val credentialType: String,
    val number: String,
    val categoryCode: String? = null,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
    val version: Long = 1,
)

data class AgronomicCredentialDraft(
    val credentialType: String,
    val number: String,
    val categoryCode: String? = null,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
)

data class PhytosanitaryEquipmentProfile(
    val machineId: UUID,
    val romaRegistration: String? = null,
    val censusReference: String? = null,
    val acquisitionDate: LocalDate? = null,
    /** Dynamic official code; resolved by #536/#558. */
    val regulatoryTypeCode: String? = null,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
    val version: Long = 1,
)

data class PhytosanitaryEquipmentProfileDraft(
    val romaRegistration: String? = null,
    val censusReference: String? = null,
    val acquisitionDate: LocalDate? = null,
    val regulatoryTypeCode: String? = null,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
)

data class PhytosanitaryEquipmentInspection(
    val id: UUID,
    val machineId: UUID,
    val inspectionDate: LocalDate,
    val resultCode: String? = null,
    val certificateReference: String? = null,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
    val version: Long = 1,
)

data class PhytosanitaryEquipmentInspectionDraft(
    val inspectionDate: LocalDate,
    val resultCode: String? = null,
    val certificateReference: String? = null,
    val source: RegulatoryResourceSource = RegulatoryResourceSource.MANUAL,
    val externalId: String? = null,
    val sourceVersion: String? = null,
    val fetchedAt: Instant? = null,
)

interface PhytosanitaryResourceRepository {
    fun observeActivePeople(): Flow<List<AgronomicPerson>>
    fun observeArchivedPeople(): Flow<List<AgronomicPerson>>
    fun observeCredentials(personId: UUID): Flow<List<AgronomicCredential>>
    fun observeEquipmentProfile(machineId: UUID): Flow<PhytosanitaryEquipmentProfile?>
    fun observeEquipmentInspections(machineId: UUID): Flow<List<PhytosanitaryEquipmentInspection>>

    suspend fun createPerson(draft: AgronomicPersonDraft): AppResult<UUID>
    suspend fun updatePerson(id: UUID, draft: AgronomicPersonDraft): AppResult<Unit>
    suspend fun archivePerson(id: UUID): AppResult<Unit>
    suspend fun restorePerson(id: UUID): AppResult<Unit>
    suspend fun addCredential(personId: UUID, draft: AgronomicCredentialDraft): AppResult<UUID>

    suspend fun saveEquipmentProfile(
        machineId: UUID,
        draft: PhytosanitaryEquipmentProfileDraft,
    ): AppResult<Unit>

    suspend fun addEquipmentInspection(
        machineId: UUID,
        draft: PhytosanitaryEquipmentInspectionDraft,
    ): AppResult<UUID>
}
