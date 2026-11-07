package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.domain.cycle.CycleInterval
import com.isivoltpro.maginaolivo.domain.cycle.CycleRecordKind
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.LocalDate
import java.util.UUID

internal class InvalidAgriculturalCycle(val error: AppError): RuntimeException()

/** Called INSIDE the canonical transaction: fact + association commit together or neither. */
internal class AgriculturalCycleWriter(
    private val db: MaginaOlivoDatabase,
    private val workspaces: WorkspaceRepository?,
    private val clock: AppClock,
) {
    private val dao get() = db.agriculturalCycleDao()

    /** Editing money never silently changes the agricultural UUID, even when its parent is removed. */
    suspend fun preserveExpenseAssociation(before: ExpenseEntity, after: ExpenseEntity, selected: UUID?, now: java.time.Instant) {
        val old = before.farmId?.let { farm -> dao.records(farm,before.workspaceId).firstOrNull { it.id==before.id && it.kind=="EXPENSE" }?.cycleId }
        val records = after.farmId?.let { dao.records(it,after.workspaceId) }.orEmpty()
        val parentId = after.activityId ?: after.campaignId
        val parent = parentId?.let { id -> records.firstOrNull { it.id==id && it.kind==if(after.activityId!=null) "ACTIVITY" else "RECOLLECTION" }?.cycleId }
        requireCycle(selected==null || selected==old,"historical_cycle_review_required")
        if(old==null) {
            requireCycle(parent==null,"historical_cycle_review_required")
            return
        }
        requireCycle(before.farmId==after.farmId && before.workspaceId==after.workspaceId,"context_mismatch")
        if(parentId!=null) requireCycle(parent==old,"historical_cycle_review_required")
        else if(before.activityId!=null || before.campaignId!=null) {
            // The existing explicit «keep independent» command retains the same cycle and money.
            dao.link(AgriculturalCycleExpenseEntity(before.id,old))
            dao.insert(AgriculturalCycleOperationEntity(UUID.randomUUID(),old,workspaces?.currentOwnerId(),
                "KEEP_EXPENSE:${before.id}",now,"Se conserva el ciclo al desvincular el gasto de su trabajo."))
            dao.setRevision(AgriculturalCycleFarmStateEntity(after.farmId!!,Math.addExact(dao.revision(after.farmId) ?: 0,1)))
        }
    }

    suspend fun validateActivityDate(current: ActivityEntity, date: LocalDate, lastDate: LocalDate=date,
                                     requireOpen: Boolean=false) {
        val farm=current.farmId ?: return
        val cycleId=dao.records(farm,current.workspaceId).firstOrNull {it.id==current.id && it.kind=="ACTIVITY"}?.cycleId ?: return
        val cycle=dao.find(cycleId) ?: invalid("cycle_not_found")
        requireCycle(CycleInterval(cycle.startDate,cycle.effectiveEndDate).contains(date,lastDate),"outside_cycle")
        if(requireOpen) requireCycle(cycle.effectiveEndDate==null,"cycle_closed")
    }

    suspend fun validateNew(workspaceId: UUID, farmId: UUID?, selected: UUID?, date: LocalDate,
                            lastDate: LocalDate=date, campaignId: UUID?=null, activityId: UUID?=null): UUID? {
        if(farmId==null) {
            requireCycle(selected==null,"farm_required")
            return null
        }
        val cycles=dao.list(farmId)
        if(cycles.isEmpty() && selected==null) return null
        val parent=when {
            activityId!=null -> dao.records(farmId,workspaceId).firstOrNull { it.id==activityId && it.kind=="ACTIVITY" }?.cycleId
            campaignId!=null -> dao.records(farmId,workspaceId).firstOrNull { it.id==campaignId && it.kind=="RECOLLECTION" }?.cycleId
            else -> null
        }
        if(campaignId!=null || activityId!=null) requireCycle(parent!=null,"assign_parent_first")
        if(parent!=null && selected!=null) requireCycle(parent==selected,"parent_cycle_mismatch")
        val id=selected ?: parent
        requireCycle(id!=null,"cycle_selection_required")
        val cycle=dao.find(id!!) ?: invalid("cycle_not_found")
        requireCycle(cycle.workspaceId==workspaceId && cycle.farmId==farmId,"context_mismatch")
        val owner=workspaces?.currentOwnerId()
        val workspace=db.workspaceDao().findById(workspaceId) ?: invalid("workspace_not_found")
        // Shared ledger callers already validated their Workspace; they do not own Auth.
        if(workspaces!=null && (owner!=null && owner!=workspace.ownerUserId || owner==null && workspace.metadata.remoteVersion!=null))
            throw InvalidAgriculturalCycle(AppError.Permission("cycle_owner_required"))
        requireCycle(cycle.effectiveEndDate==null,"cycle_closed")
        requireCycle(CycleInterval(cycle.startDate,cycle.effectiveEndDate).contains(date,lastDate),"outside_cycle")
        return id
    }

    suspend fun bindNew(workspaceId: UUID, farmId: UUID, cycleId: UUID?, kind: CycleRecordKind, id: UUID,
                        inherited: Boolean=false, now: java.time.Instant=clock.nowInstant()) {
        if(cycleId==null || inherited) return
        when(kind) {
            CycleRecordKind.ACTIVITY -> dao.link(AgriculturalCycleActivityEntity(id,cycleId))
            CycleRecordKind.EXPENSE -> dao.link(AgriculturalCycleExpenseEntity(id,cycleId))
            CycleRecordKind.RECOLLECTION -> dao.link(AgriculturalCycleRecollectionEntity(id,cycleId))
        }
        val cycle=dao.find(cycleId) ?: invalid("cycle_not_found")
        requireCycle(cycle.farmId==farmId && cycle.workspaceId==workspaceId,"context_mismatch")
        dao.insert(AgriculturalCycleOperationEntity(UUID.randomUUID(),cycleId,workspaces?.currentOwnerId(),
            "NEW:$kind:$id",now,"Registro nuevo confirmado: $kind $id"))
        dao.setRevision(AgriculturalCycleFarmStateEntity(farmId,Math.addExact(dao.revision(farmId) ?: 0,1)))
    }

    private fun requireCycle(ok: Boolean,code: String) { if(!ok) invalid(code) }
    private fun invalid(code: String): Nothing = throw InvalidAgriculturalCycle(AppError.Validation("agriculturalCycleId",code))
}

/** Local physical writes only; receivers and transport contracts never call this guard. */
internal suspend fun requireOperationalAgriculturalCycle(
    db: MaginaOlivoDatabase, campaign: CampaignEntity, date: LocalDate,
) {
    val dao=db.agriculturalCycleDao()
    if(dao.list(campaign.farmId).isEmpty()) return
    fun invalid(code: String): Nothing = throw InvalidAgriculturalCycle(AppError.Validation("agriculturalCycleId",code))
    val id=dao.recollectionCycle(campaign.id) ?: invalid("assign_parent_first")
    val cycle=dao.find(id) ?: invalid("cycle_not_found")
    if(cycle.workspaceId!=campaign.workspaceId || cycle.farmId!=campaign.farmId) invalid("context_mismatch")
    if(cycle.effectiveEndDate!=null) invalid("cycle_closed")
    if(!CycleInterval(cycle.startDate,null).contains(date)) invalid("outside_cycle")
}
