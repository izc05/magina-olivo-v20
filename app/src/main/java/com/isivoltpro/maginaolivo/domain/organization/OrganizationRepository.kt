package com.isivoltpro.maginaolivo.domain.organization

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * `DATA-MODEL-RC1.1-ADDENDUM` §3 and `RC1.2-PRODUCT-LOCK` §4. One organization carries every
 * role it plays, so a cooperative that is also a supplier is one row chosen from two flows.
 */
enum class OrganizationRole {
    COOPERATIVE,
    MILL,
    SUPPLIER,
    IRRIGATION_PROVIDER,
    WORKSHOP,
    SERVICE_PROVIDER,
    OTHER,
}

data class Organization(
    val id: UUID,
    val name: String,
    val roles: Set<OrganizationRole>,
    val taxId: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val phone: String? = null,
    val notes: String? = null,
)

data class OrganizationDraft(
    val name: String,
    val roles: Set<OrganizationRole>,
    val taxId: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val phone: String? = null,
    val notes: String? = null,
)

interface OrganizationRepository {
    fun observeAll(): Flow<List<Organization>>

    /** Organizations playing at least one of [roles]; each appears once. */
    fun observeWithAnyRole(roles: Set<OrganizationRole>): Flow<List<Organization>>

    suspend fun create(draft: OrganizationDraft): AppResult<UUID>

    suspend fun update(id: UUID, draft: OrganizationDraft): AppResult<Unit>

    suspend fun archive(id: UUID): AppResult<Unit>
}
