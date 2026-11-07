package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.domain.cycle.*
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/** Local-only operations. Never provisions a workspace, copies a canonical fact or queues sync. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalAgriculturalCycleRepository(
    private val db: MaginaOlivoDatabase,
    private val workspaces: WorkspaceRepository,
    private val clock: AppClock,
    private val account: com.isivoltpro.maginaolivo.feature.account.AccountRepository? = null,
) : AgriculturalCycleRepository {
    private val dao get() = db.agriculturalCycleDao()

    override fun observe(farmId: UUID): Flow<AppResult<CycleSnapshot>> =
        workspaces.observeLocalWorkspace().flatMapLatest { workspace ->
            if (workspace == null) flowOf(AppResult.Failure(AppError.Permission("cycle_read")))
            else combine(dao.observe(farmId), dao.observeRevision(farmId), dao.observeRecords(farmId, workspace)) { _, _, _ ->
                safe { db.withTransaction { snapshot(farmId) } }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun create(scope: CycleScope, label: String, start: LocalDate, operationId: UUID): AppResult<UUID> = transaction {
        val current = authorize(scope.farmId,write=true)
        sameIdentity(scope,current)
        val fingerprint = hash("CREATE|$scope|${label.trim()}|$start")
        replay(operationId,current,fingerprint)?.let { return@transaction it }
        requireRevision(scope,current)
        check(label.trim().isNotEmpty() && label.trim().length<=80,"label","invalid_label")
        val interval=CycleInterval(start,null)
        check(dao.list(scope.farmId).none { AgriculturalCycleRules.overlaps(it.interval(),interval) },"startDate","cycle_overlap")
        val cycle=AgriculturalCycleEntity(UUID.randomUUID(),scope.workspaceId,scope.farmId,label.trim(),start)
        dao.insert(cycle)
        record(operationId,current,cycle.id,fingerprint,"Crear ciclo")
        cycle.id
    }

    override suspend fun previewClose(cycleId: UUID, end: LocalDate, noRecollection: Boolean): AppResult<CycleClosePreview> = transaction {
        buildPreview(cycleId,end,noRecollection)
    }

    override suspend fun close(preview: CycleClosePreview, operationId: UUID): AppResult<Unit> = transaction {
        val scope=authorize(preview.scope.farmId,write=true)
        sameIdentity(preview.scope,scope)
        val fingerprint=hash("CLOSE|$preview")
        if(replay(operationId,scope,fingerprint)!=null) return@transaction Unit
        requireRevision(preview.scope,scope)
        val fresh=buildPreview(preview.cycle.id,preview.effectiveEndDate,preview.noRecollection)
        if(fresh!=preview) reject(AppError.Conflict("cycle_preview_changed"))
        check(fresh.blockers.isEmpty(),"endDate","cycle_close_blocked")
        val current=dao.find(preview.cycle.id) ?: reject(AppError.NotFound("cycle"))
        dao.update(current.copy(effectiveEndDate=preview.effectiveEndDate,closedAt=clock.nowInstant(),
            noRecollection=preview.noRecollection,version=Math.addExact(current.version,1)))
        record(operationId,scope,current.id,fingerprint,"Cerrar: ${preview.effectiveEndDate}")
    }

    override suspend fun assign(scope: CycleScope, cycleId: UUID, kind: CycleRecordKind, recordId: UUID,
                               reason: String, operationId: UUID): AppResult<Unit> = transaction {
        val current=authorize(scope.farmId,write=true)
        sameIdentity(scope,current)
        val fingerprint=hash("ASSIGN|$scope|$cycleId|$kind|$recordId|${reason.trim()}")
        if(replay(operationId,current,fingerprint)!=null) return@transaction Unit
        requireRevision(scope,current)
        check(reason.trim().isNotEmpty() && reason.length<=500,"reason","required")
        val cycle=dao.find(cycleId) ?: reject(AppError.NotFound("cycle"))
        check(cycle.farmId==scope.farmId && cycle.workspaceId==scope.workspaceId,"cycleId","context_mismatch")
        val source=dao.records(scope.farmId,scope.workspaceId).firstOrNull { it.id==recordId && it.kind==kind.name }
            ?: reject(AppError.NotFound("cycle_record"))
        // Parent ownership is authoritative; no second conflicting link may override it.
        val parent=when(kind) {
            CycleRecordKind.ACTIVITY -> db.activityDao().findById(recordId)?.campaignId
            CycleRecordKind.EXPENSE -> db.expenseDao().findById(recordId)?.let { it.campaignId ?: it.activityId }
            else -> null
        }
        check(parent==null,"recordId","assign_parent_first")
        // A closed cycle keeps its history: no record leaves it, and no money enters it by assignment.
        // Controlled reopening is out of scope; operations already recorded are never rewritten.
        source.cycleId?.takeIf { it!=cycleId }?.let { previous ->
            val origin=dao.find(previous) ?: reject(AppError.NotFound("cycle"))
            check(origin.effectiveEndDate==null,"recordId","source_cycle_closed")
        }
        if(kind==CycleRecordKind.EXPENSE) check(cycle.effectiveEndDate==null,"cycleId","cycle_closed")
        if(kind==CycleRecordKind.RECOLLECTION && cycle.effectiveEndDate!=null) {
            check(!cycle.noRecollection,"recordId","has_no_recollection")
            check(source.status=="CLOSED","recordId","recollections_not_closed")
        }
        if(kind!=CycleRecordKind.EXPENSE) check(cycle.interval().contains(source.date,source.lastDate),"recordId","outside_cycle")
        val original=source.cycleId?.toString() ?: "sin asignar"
        when(kind) {
            CycleRecordKind.ACTIVITY -> dao.link(AgriculturalCycleActivityEntity(recordId,cycleId))
            CycleRecordKind.EXPENSE -> dao.link(AgriculturalCycleExpenseEntity(recordId,cycleId))
            CycleRecordKind.RECOLLECTION -> dao.link(AgriculturalCycleRecollectionEntity(recordId,cycleId))
        }
        record(operationId,current,cycleId,fingerprint,"$kind $recordId: $original → $cycleId. ${reason.trim()}")
    }

    private suspend fun buildPreview(id: UUID,end: LocalDate,noRecollection: Boolean): CycleClosePreview {
        val cycle=dao.find(id) ?: reject(AppError.NotFound("cycle"))
        val state=snapshot(cycle.farmId)
        val linked=state.records.filter { it.cycleId==id }
        val physical=linked.filter { it.kind!=CycleRecordKind.EXPENSE.name && it.status!="CANCELLED" && it.status!="ARCHIVED" }
        val blockers=buildList {
            AgriculturalCycleRules.closeError(cycle.interval(),end,db.todayForWorkspace(cycle.workspaceId,clock),physical.maxOfOrNull { it.lastDate })?.let(::add)
            if(linked.any { it.kind=="RECOLLECTION" && it.status!="CLOSED" }) add("recollections_not_closed")
            if(noRecollection && linked.any { it.kind=="RECOLLECTION" }) add("has_recollection")
            if(!noRecollection && linked.none { it.kind=="RECOLLECTION" }) add("confirm_no_recollection")
        }
        return CycleClosePreview(cycle.domain(),state.scope,end,noRecollection,dao.lastDeliveryDate(id),linked,blockers,state.records.count { it.cycleId==null })
    }

    private suspend fun snapshot(farmId: UUID): CycleSnapshot {
        val scope=authorize(farmId)
        return CycleSnapshot(scope,dao.list(farmId).map { it.domain() },dao.records(farmId,scope.workspaceId),
            today=db.todayForWorkspace(scope.workspaceId,clock))
    }

    private suspend fun authorize(farmId: UUID,write: Boolean=false): CycleScope {
        val owner=workspaces.currentOwnerId()
        val workspace=when(val result=workspaces.activeWorkspace()) {
            is AppResult.Failure -> reject(result.error)
            is AppResult.Success -> result.value
        }
        if(owner!=workspaces.currentOwnerId()) reject(AppError.Permission("identity_changed"))
        val farm=db.farmDao().findById(farmId) ?: reject(AppError.NotFound("farm"))
        check(farm.workspaceId==workspace && farm.metadata.deletedAt==null,"workspaceId","context_mismatch")
        val ws=db.workspaceDao().findById(workspace) ?: reject(AppError.NotFound("workspace"))
        // P3 owns local cycles only. A selected UUID alone is never cross-account read evidence.
        if(owner!=null && owner!=ws.ownerUserId || owner==null && ws.metadata.remoteVersion!=null)
            reject(AppError.Permission("cycle_owner_required"))
        if(write) {
            check(farm.status==FarmStatus.ACTIVE,"farmId","archived_farm")
        }
        return CycleScope(workspace,farmId,owner,dao.revision(farmId) ?: 0)
    }

    private fun sameIdentity(expected: CycleScope,actual: CycleScope) {
        if(expected.copy(revision=actual.revision)!=actual) reject(AppError.Permission("cycle_context_changed"))
    }
    private fun requireRevision(expected: CycleScope,actual: CycleScope) {
        if(expected!=actual) reject(AppError.Conflict("cycle_revision_changed"))
    }
    private suspend fun replay(id: UUID,scope: CycleScope,fingerprint: String): UUID? {
        val previous=dao.operation(id) ?: return null
        val cycle=dao.find(previous.cycleId) ?: reject(AppError.NotFound("cycle"))
        check(previous.fingerprint==fingerprint && previous.ownerId==scope.ownerId &&
            cycle.farmId==scope.farmId && cycle.workspaceId==scope.workspaceId,"operationId","idempotency_conflict")
        return previous.cycleId
    }
    private suspend fun record(id: UUID,scope: CycleScope,cycle: UUID,fingerprint: String,reason: String) {
        // Recheck after all reads; rollback the entire transaction if the identity changed.
        val fresh=authorize(scope.farmId,write=true)
        requireRevision(scope,fresh)
        dao.insert(AgriculturalCycleOperationEntity(id,cycle,scope.ownerId,fingerprint,clock.nowInstant(),reason))
        dao.setRevision(AgriculturalCycleFarmStateEntity(scope.farmId,Math.addExact(scope.revision,1)))
    }
    private fun AgriculturalCycleEntity.interval()=CycleInterval(startDate,effectiveEndDate)
    private fun AgriculturalCycleEntity.domain()=AgriculturalCycle(id,workspaceId,farmId,label,startDate,effectiveEndDate,closedAt,noRecollection,version)
    private fun hash(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun check(ok: Boolean,field: String,code: String) { if(!ok) reject(AppError.Validation(field,code)) }
    private class Rejected(val error: AppError): RuntimeException()
    private fun reject(error: AppError): Nothing = throw Rejected(error)
    private suspend fun <T> safe(block: suspend () -> T): AppResult<T> = try { AppResult.Success(block()) }
        catch(e: CancellationException) { throw e }
        catch(e: Rejected) { AppResult.Failure(e.error) }
        catch(e: Exception) { AppResult.Failure(AppError.Storage("agricultural_cycle",e)) }
    private suspend fun <T> transaction(block: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) {
        safe {
            // Existing Auth lock, before Room: logout/account replacement cannot interleave a commit.
            if(account==null) db.withTransaction {block()}
            else account.withLocalWriteIdentity {db.withTransaction {block()}}
        }
    }
}
