package com.isivoltpro.maginaolivo.domain.cycle

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class AgriculturalCycle(
    val id: UUID, val workspaceId: UUID, val farmId: UUID, val label: String,
    val startDate: LocalDate, val effectiveEndDate: LocalDate?, val closedAt: Instant?,
    val noRecollection: Boolean, val version: Long,
) { val interval get() = CycleInterval(startDate, effectiveEndDate) }

/** Captured authorization context + per-farm revision; browsing never becomes a write selection. */
data class CycleScope(val workspaceId: UUID, val farmId: UUID, val ownerId: UUID?, val revision: Long)
enum class CycleRecordKind { ACTIVITY, EXPENSE, RECOLLECTION }
data class CycleRecord(
    val id: UUID, val kind: String, val title: String, val date: LocalDate,
    val lastDate: LocalDate, val cycleId: UUID?, val status: String, val version: Long,
)
/** P4 transport state of the farm's cycles; null when this phone never staged or received them. */
data class CycleSyncView(val status: String, val reason: String?)
data class CycleSnapshot(val scope: CycleScope, val cycles: List<AgriculturalCycle>, val records: List<CycleRecord>,
    val today: LocalDate? = null, val sync: CycleSyncView? = null)
data class CycleClosePreview(
    val cycle: AgriculturalCycle, val scope: CycleScope, val effectiveEndDate: LocalDate,
    val noRecollection: Boolean, val lastDeliveryDate: LocalDate?,
    val records: List<CycleRecord>, val blockers: List<String>, val unassignedCount: Int,
)

interface AgriculturalCycleRepository {
    fun observe(farmId: UUID): Flow<AppResult<CycleSnapshot>>
    suspend fun create(scope: CycleScope, label: String, start: LocalDate, operationId: UUID): AppResult<UUID>
    suspend fun previewClose(cycleId: UUID, end: LocalDate, noRecollection: Boolean): AppResult<CycleClosePreview>
    suspend fun close(preview: CycleClosePreview, operationId: UUID): AppResult<Unit>
    suspend fun assign(scope: CycleScope, cycleId: UUID, kind: CycleRecordKind, recordId: UUID,
                       reason: String, operationId: UUID): AppResult<Unit>
}
