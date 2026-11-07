package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.DeliveryRetentionCodes
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport.RpcScope
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.sync.ManualSync
import com.isivoltpro.maginaolivo.feature.sync.ManualSyncState
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * «Sincronizar ahora»: one manual run that composes the EXISTING senders and receivers, in causal order, under the
 * single dispatch lease. It adds no write path of its own: every operation keeps its UUID, frozen bytes, receipt
 * correlation and retry rules exactly as its coordinator defines them.
 *
 * Send: workspace → farm → parcel (create, then mutations) → campaign CREATE → campaign changes (CAS) → Jornada →
 * Pesada (v3 + HEAD) and Pesada deletes. Receive (owner workspaces only): parents → campaigns listed by the server →
 * Jornadas with their Pesadas for each farm this device has consented.
 *
 * Never automatic: only [requestSync] starts a run, and only the button calls it. Parcel changes inside a campaign the
 * server already holds stay refused locally (`confirmed_campaign_parcels_locked`); they never reach this run.
 */
internal class ManualSyncCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val links: LocalFarmLinkRepository,
    private val workspaces: LocalWorkspaceRepository,
    private val ownerVerifier: WorkspaceOwnerVerifier,
    private val ids: IdGenerator,
    private val transport: (RpcScope) -> DeliveryRpcTransport,
    private val leaseDirectory: File,
    private val now: () -> Instant = Instant::now,
) : ManualSync {
    private val mutable = MutableStateFlow<ManualSyncState>(ManualSyncState.Idle)
    override val state: StateFlow<ManualSyncState> = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    override fun requestSync() {
        if (!running.compareAndSet(false, true)) return
        mutable.value = ManualSyncState.Running("preparando")
        scope.launch {
            try {
                mutable.value = run()
            } catch (cancel: CancellationException) { throw cancel
            } catch (failure: AuthFailure) {
                mutable.value = ManualSyncState.Failed(now(), authMessage(failure))
            } catch (_: Exception) {
                mutable.value = ManualSyncState.Failed(now(), "No se pudo completar la sincronización. Lo ya confirmado se conserva.")
            } finally { running.set(false) }
        }
    }

    private class Run { var sent = 0; var received = 0; val reasons = linkedSetOf<String>(); val notes = linkedSetOf<String>() }

    private fun step(text: String) { mutable.value = ManualSyncState.Running(text) }

    private suspend fun run(): ManualSyncState {
        val current = account.state.value
        val userId = current.userId ?: return ManualSyncState.Failed(now(), "Inicia sesión para sincronizar.")
        if (!current.online) return ManualSyncState.Failed(now(), "Sin conexión. Conéctate y vuelve a intentarlo.")
        val lease = DeliveryDispatchLease.tryAcquire(leaseDirectory)
            ?: return ManualSyncState.Failed(now(), "Ya hay una sincronización en curso en este teléfono.")
        val out = Run()
        try {
            step("comprobando permisos")
            workspaces.refreshAuthorizations()
            send(out, lease)
            receive(out, userId)
        } finally { lease.close() }

        val left = database.syncOutboxDao().listUnconfirmed()
        val other = left.filter { it.entityType !in SUPPORTED }
        if (other.isNotEmpty()) out.notes += "${other.size} registros de otros tipos (labores, gastos, documentos…) se quedan en este teléfono: no se sincronizan en la versión 1.0."
        // Operations a sender resolved on purpose without a request (adopted, derived only, never sent) leave nothing open.
        val open = left.filter { it.entityType in SUPPORTED && it.lastErrorCode !in HarvestReceiveCoordinator.SETTLED_CODES &&
            !(it.entityType == SyncEntityType.WORKSPACE && it.operation == OutboxOperation.CREATE && isEmptyLocalWorkspace(it.entityId)) }
        if (open.any { it.status == OutboxStatus.BLOCKED && it.lastErrorCode != DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD })
            out.reasons += "${open.count { it.status == OutboxStatus.BLOCKED }} cambios retenidos para revisión: el servidor no los aceptó tal cual. Se conservan en este teléfono."
        if (open.isNotEmpty() && out.reasons.isEmpty())
            out.reasons += "${open.size} cambios siguen pendientes de enviar. Vuelve a sincronizar."
        return if (out.reasons.isEmpty()) ManualSyncState.Completed(now(), out.sent, out.received, out.notes.toList())
        else ManualSyncState.Pending(now(), out.sent, out.received, (out.reasons + out.notes).toList())
    }

    /** Never confirmed by the server and without any farm (even deleted): the app's own empty starting workspace. */
    private suspend fun isEmptyLocalWorkspace(workspaceId: UUID): Boolean {
        val workspace = database.workspaceDao().findById(workspaceId) ?: return false
        return workspace.metadata.remoteVersion == null && database.farmDao().listIncludingDeleted(workspaceId).isEmpty()
    }

    // ---------------------------------------------------------------------------------------------------------- send

    private suspend fun open(type: SyncEntityType, vararg operations: OutboxOperation) =
        database.syncOutboxDao().listForManualSync().filter { it.entityType == type && it.operation in operations }

    private suspend fun send(out: Run, lease: DeliveryDispatchLease) {
        val ledger: (UUID) -> com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger? = { links.ledgerFor(it) }

        step("enviando espacio de trabajo")
        val workspaceSend = WorkspaceSendCoordinator(database, account, issuer, transport(RpcScope.WORKSPACE_SEND))
        for (op in open(SyncEntityType.WORKSPACE, OutboxOperation.CREATE)) {
            // An empty workspace the app opened by itself is never published: nothing of the user travels with it, and a
            // second phone would otherwise create a duplicate empty workspace on the server.
            if (isEmptyLocalWorkspace(op.entityId)) continue
            val staged = if (op.payloadJson == null) workspaceSend.stageOwned(op.id) else WorkspaceSendOutcome.STAGED
            val outcome = if (staged == WorkspaceSendOutcome.STAGED) workspaceSend.send(op.id, lease) else staged
            note(out, "El espacio de trabajo", outcome.name)
        }
        // The workspace just confirmed must appear in the server's grants before its farms can be staged.
        workspaces.refreshAuthorizations()

        step("enviando fincas")
        val farmSend = FarmSendCoordinator(database, account, issuer, transport(RpcScope.FARM_SEND), ledger)
        for (op in open(SyncEntityType.FARM, OutboxOperation.CREATE)) {
            val name = database.farmDao().findById(op.entityId)?.name ?: "sin nombre"
            val staged = if (op.payloadJson == null) farmSend.stage(op.id) else FarmSendOutcome.STAGED
            val outcome = if (staged == FarmSendOutcome.STAGED) farmSend.send(op.id, lease) else staged
            if (outcome == FarmSendOutcome.CONSENT_REQUIRED)
                out.reasons += "La finca «$name» no está vinculada a tu cuenta: confírmala en «Fincas y cuenta» y vuelve a sincronizar."
            else note(out, "La finca «$name»", outcome.name)
        }
        if (open(SyncEntityType.FARM, OutboxOperation.UPDATE, OutboxOperation.DELETE).isNotEmpty())
            out.reasons += "Los cambios en fincas ya sincronizadas todavía no se envían en la versión 1.0; se conservan en este teléfono."

        step("enviando parcelas")
        val parcelSend = ParcelSendCoordinator(database, account, issuer, transport(RpcScope.PARCEL_SEND), ledger)
        for (op in open(SyncEntityType.PARCEL, OutboxOperation.CREATE)) {
            val staged = if (op.payloadJson == null) parcelSend.stage(op.id) else ParcelSendOutcome.STAGED
            note(out, "Una parcela", (if (staged == ParcelSendOutcome.STAGED) parcelSend.send(op.id, lease) else staged).name)
        }
        val mutations = ParcelMutationSendCoordinator(database, account, issuer, transport(RpcScope.PARCEL_MUTATION_SEND), ledger)
        for (op in open(SyncEntityType.PARCEL, OutboxOperation.UPDATE, OutboxOperation.ARCHIVE, OutboxOperation.RESTORE)) {
            val staged = if (op.payloadJson == null) mutations.stage(op.id) else ParcelMutationOutcome.STAGED
            note(out, "Un cambio de parcela", (if (staged == ParcelMutationOutcome.STAGED) mutations.send(op.id, lease) else staged).name)
        }

        step("enviando campañas")
        val campaignCreate = CampaignCreateSendCoordinator(database, account, issuer, transport(RpcScope.CAMPAIGN_CREATE), ledger)
        for (op in open(SyncEntityType.CAMPAIGN, OutboxOperation.CREATE)) {
            val staged = if (op.payloadJson == null) campaignCreate.stage(op.id) else CampaignCreateOutcome.STAGED
            val outcome = if (staged == CampaignCreateOutcome.STAGED) campaignCreate.send(op.id, lease) else staged
            if (outcome == CampaignCreateOutcome.CONSENT_REQUIRED)
                out.reasons += "Una campaña pertenece a una finca sin vincular: confírmala en «Fincas y cuenta»."
            else note(out, "Una campaña", outcome.name)
        }
        // Campaign changes travel one at a time per campaign, in order, each with its CAS base.
        val cas = CampaignCasCoordinator(database, account, issuer, CampaignCasRpcAdapter(transport(RpcScope.CAMPAIGN_CAS)))
        for (campaign in open(SyncEntityType.CAMPAIGN, OutboxOperation.UPDATE).map { it.entityId }.distinct()) {
            var rounds = 0
            while (rounds++ < MAX_CHANGES_PER_CAMPAIGN) {
                val next = open(SyncEntityType.CAMPAIGN, OutboxOperation.UPDATE).firstOrNull { it.entityId == campaign } ?: break
                val staged = if (next.payloadJson == null) cas.stage(next.id) else CampaignCasOutcome.STAGED
                val outcome = if (staged == CampaignCasOutcome.STAGED) cas.dispatch(next.id, lease) else staged
                note(out, "Un cambio de campaña", outcome.name)
                if (outcome != CampaignCasOutcome.SYNCED) break
            }
        }

        // CR-015 P4: after campaigns, so a closed recollection reaches the server before its closed cycle.
        step("enviando campañas agrícolas")
        val cycles = cycleSync()
        for (farmId in database.agriculturalCycleDao().farmsWithCycles()) {
            if (!links.isFarmConsented(farmId)) continue
            val staged = cycles.stage(farmId)
            val outcome = if (staged == CycleSyncOutcome.STAGED) cycles.dispatch(farmId, lease) else staged
            cycleNote(out, outcome)
        }

        step("enviando jornadas")
        val harvests = HarvestSendCoordinator(database, account, issuer, transport(RpcScope.HARVEST_SEND), ledger, ownerVerifier, ids)
        for (op in open(SyncEntityType.HARVEST, OutboxOperation.CREATE, OutboxOperation.UPDATE, OutboxOperation.DELETE)) {
            val staged = if (op.payloadJson == null) harvests.stage(op.id) else HarvestSendOutcome.STAGED
            val outcome = if (staged == HarvestSendOutcome.STAGED) harvests.send(op.id, lease) else staged
            note(out, "Una jornada", outcome.name)
        }

        step("enviando pesadas")
        val worker = DeliveryDispatchWorker(database, links, DeliveryRpcAdapter(transport(RpcScope.DELIVERY)))
        for (op in open(SyncEntityType.DELIVERY, OutboxOperation.CREATE, OutboxOperation.UPDATE)) {
            var outcome = when {
                op.status == OutboxStatus.BLOCKED -> worker.resolveAwaitingHead(op.id, lease)
                op.status == OutboxStatus.PROCESSING -> worker.recoverLostResponse(op.id, lease)
                else -> worker.dispatch(op.id, lease)
            }
            if (outcome == DispatchOutcome.OUTCOME_UNKNOWN) outcome = worker.recoverLostResponse(op.id, lease)
            if (outcome == DispatchOutcome.V3_ACK_AWAITING_HEAD) outcome = worker.resolveAwaitingHead(op.id, lease)
            if (outcome == DispatchOutcome.PARENT_NOT_SYNCED)
                out.reasons += "Una pesada espera a que su jornada se confirme en el servidor. Vuelve a sincronizar."
            else note(out, "Una pesada", outcome.name)
        }
        val deletes = DeliveryDeleteCoordinator(database, account, issuer, transport(RpcScope.DELIVERY_DELETE), ledger, ownerVerifier)
        for (op in open(SyncEntityType.DELIVERY, OutboxOperation.DELETE)) {
            val staged = if (op.payloadJson == null) deletes.stage(op.id) else DeliveryDeleteOutcome.STAGED
            note(out, "El borrado de una pesada", (if (staged == DeliveryDeleteOutcome.STAGED) deletes.send(op.id, lease) else staged).name)
        }
    }

    private fun cycleSync() = AgriculturalCycleSyncCoordinator(database, account, issuer,
        AgriculturalCycleRpcAdapter(transport(RpcScope.CYCLE_SYNC)))

    private fun cycleNote(out: Run, outcome: CycleSyncOutcome, farm: String? = null) {
        val where = farm?.let { " de «$it»" } ?: ""
        when (outcome) {
            CycleSyncOutcome.SYNCED -> out.sent++
            CycleSyncOutcome.NOTHING_TO_SEND, CycleSyncOutcome.ALREADY_CURRENT, CycleSyncOutcome.IMPORTED -> Unit
            CycleSyncOutcome.CONFIRMED_LOCAL_PENDING -> { out.sent++; out.reasons += "La campaña agrícola$where cambió mientras se enviaba; el cambio nuevo saldrá en la próxima sincronización." }
            CycleSyncOutcome.WAITING_PARENTS -> out.reasons += "La campaña agrícola$where espera a que su finca y sus recolecciones se confirmen en el servidor. Vuelve a sincronizar."
            CycleSyncOutcome.OUTCOME_UNKNOWN -> out.reasons += "La campaña agrícola$where se envió pero no llegó la respuesta. Se reintentará con el mismo envío, sin duplicarlo."
            CycleSyncOutcome.CONFLICT -> out.reasons += "La campaña agrícola$where tiene cambios en este teléfono y en otro dispositivo. No se ha sobrescrito ninguno: queda pendiente de revisión."
            CycleSyncOutcome.NOT_AUTHORIZED -> out.reasons += "La campaña agrícola$where no se pudo sincronizar: solo el propietario del espacio puede hacerlo, o la sesión caducó."
            else -> out.reasons += "La campaña agrícola$where necesita revisión ($outcome). Se conserva en este teléfono."
        }
    }

    /** One coordinator outcome → the run's counters and, when it needs the user, one plain reason. */
    private fun note(out: Run, what: String, outcome: String) {
        when (outcome) {
            "SYNCED", "ALREADY_CONFIRMED", "RESOLVED_NOOP", "RESOLVED_NEVER_SENT", "REBASED" -> out.sent++
            "STAGED" -> Unit
            "CONFIRMED_LOCAL_PENDING" -> { out.sent++; out.reasons += "$what cambió mientras se enviaba; el cambio nuevo saldrá en la próxima sincronización." }
            "OUTCOME_UNKNOWN", "OFFLINE" -> out.reasons += "$what se envió pero no llegó la respuesta. Se reintentará con el mismo envío, sin duplicarlo."
            "PARENT_NOT_SYNCED", "WAITING_SIBLING", "WAITING_DEPENDENTS" -> out.reasons += "$what espera a que se confirme antes otro dato del que depende. Vuelve a sincronizar."
            "CONSENT_REQUIRED" -> out.reasons += "$what no está vinculado a tu cuenta: revisa «Fincas y cuenta»."
            "NOT_AUTHORIZED" -> out.reasons += "$what no se pudo enviar: tu cuenta no tiene permiso de escritura en este espacio, o la sesión caducó."
            "NEEDS_REBASE" -> out.reasons += "$what se cruzó con otro cambio del servidor; recibe primero y revisa el dato."
            else -> out.reasons += "$what necesita revisión ($outcome). Se conserva en este teléfono."
        }
    }

    // ------------------------------------------------------------------------------------------------------- receive

    private suspend fun receive(out: Run, userId: UUID) {
        step("recibiendo de tus otros dispositivos")
        workspaces.refreshAuthorizations()
        val at = now()
        val grant = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString())
            ?.let { runCatching { WorkspaceAuthorizationContract.parse(it.contractJson) }.getOrNull() }
            ?.takeIf { it.validFor(issuer, userId, at) }
        if (grant == null) { out.reasons += "No se pudieron comprobar tus permisos en el servidor; no se ha recibido nada."; return }
        // Owner-only receive (phase 1 decision): shared workspaces are not received in 1.0.
        val owned = grant.available(at).filter { it.role == "OWNER" }
        val parents = ParentReceiveCoordinator(database, account, issuer, transport(RpcScope.PARENT_RECEIVE))
        for (workspace in owned) {
            val report = parents.receive(workspace.id)
            out.received += report.inserted + report.updated
            when (report.outcome) {
                ParentReceiveOutcome.IMPORTED, ParentReceiveOutcome.ALREADY_CURRENT -> Unit
                ParentReceiveOutcome.CONFLICT -> out.reasons += "Algunos datos de «${workspace.name}» difieren de los del servidor y no se han sobrescrito (${report.conflicts.size})."
                else -> out.reasons += "No se pudieron recibir los datos de «${workspace.name}» (${report.outcome})."
            }
        }

        // Campaigns and harvest data are received for the workspace in use on this phone.
        val active = when (val selected = workspaces.activeWorkspace()) {
            // A phone that only holds its own empty starting workspace moves to the one workspace the account owns.
            is AppResult.Success -> selected.value.takeIf { it in owned.map { w -> w.id } || !isEmptyLocalWorkspace(it) }
                ?: owned.singleOrNull()?.id?.takeIf { workspaces.selectWorkspace(it) is AppResult.Success }
            is AppResult.Failure -> owned.singleOrNull()?.id?.takeIf { workspaces.selectWorkspace(it) is AppResult.Success }
        } ?: run {
            if (owned.size > 1) out.reasons += "Elige en «Workspace activo» el espacio en uso para recibir sus campañas."
            return
        }
        if (owned.none { it.id == active }) return

        step("recibiendo campañas")
        val listed = listCampaigns(active)
        val campaigns = CampaignReceiveCoordinator(database, account, issuer, transport(RpcScope.CAMPAIGN_RECEIVE))
        for (item in listed) {
            val local = database.campaignDao().findById(item.id)
            if (local?.metadata?.remoteVersion == item.version) continue
            when (val outcome = campaigns.receive(item.id)) {
                CampaignReceiveOutcome.IMPORTED, CampaignReceiveOutcome.UPDATED -> out.received++
                CampaignReceiveOutcome.ALREADY_CURRENT -> Unit
                CampaignReceiveOutcome.CONFLICT -> out.reasons += "Una campaña tiene cambios aquí y en el servidor; no se ha sobrescrito."
                else -> out.reasons += "Una campaña no se pudo recibir ($outcome)."
            }
        }

        step("recibiendo jornadas y pesadas")
        val receiver = HarvestReceiveCoordinator(database, account, issuer, transport(RpcScope.HARVEST_RECEIVE), { links.ledgerFor(it) },
            ownerVerifier, ids, tokenIssuer = "$issuer/auth/v1")
        val waitingConsent = linkedSetOf<String>()
        for (item in listed) {
            val farm = database.farmDao().findById(item.farmId) ?: continue
            if (database.campaignDao().findById(item.id) == null) continue
            if (!links.isFarmConsented(item.farmId, userId)) { waitingConsent += farm.name; continue }
            var cursor: ReceiveCursor? = null
            var pages = 0
            while (pages++ < MAX_RECEIVE_PAGES) {
                val report = receiver.receive(active, item.farmId, item.id, ReceiveLimit(), cursor)
                out.received += report.applied.size
                when (report.outcome) {
                    HarvestReceiveOutcome.PARTIAL -> cursor = report.next ?: break
                    HarvestReceiveOutcome.IMPORTED, HarvestReceiveOutcome.ALREADY_CURRENT -> break
                    HarvestReceiveOutcome.HELD -> { out.reasons += "${report.held.size} jornadas o pesadas de «${farm.name}» quedan retenidas para revisión; no se han sobrescrito."; break }
                    HarvestReceiveOutcome.CONSENT_REQUIRED -> { waitingConsent += farm.name; break }
                    else -> { out.reasons += "No se pudieron recibir las jornadas de «${farm.name}» (${report.outcome})."; break }
                }
            }
        }
        step("recibiendo campañas agrícolas")
        val cycles = cycleSync()
        for (farm in database.farmDao().listIncludingDeleted(active)) {
            if (farm.metadata.deletedAt != null || farm.metadata.remoteVersion == null) continue
            if (!links.isFarmConsented(farm.id, userId)) continue
            when (val outcome = cycles.receive(farm.id)) {
                CycleSyncOutcome.IMPORTED -> out.received++
                CycleSyncOutcome.ALREADY_CURRENT -> Unit
                else -> cycleNote(out, outcome, farm.name)
            }
        }
        waitingConsent.forEach { out.reasons += "La finca «$it» ha llegado de otro dispositivo: confírmala en «Fincas y cuenta» y vuelve a sincronizar para recibir sus jornadas y pesadas." }
    }

    private data class ListedCampaign(val id: UUID, val farmId: UUID, val version: Long)

    /** `list_own_campaigns_v1`: ids and versions only; the content still comes from the campaign receiver. */
    private suspend fun listCampaigns(workspaceId: UUID): List<ListedCampaign> =
        account.withServerVerifiedIdentity(remote = { session ->
            transport(RpcScope.CAMPAIGN_LIST).callRpcObject(LIST_RPC, """{"p_workspace_id":"$workspaceId"}""", session)
        }) { actor, root ->
            if (root["contract_version"]?.jsonPrimitive?.long != 1L || root["issuer"]?.jsonPrimitive?.content != "$issuer/auth/v1" ||
                root["subject"]?.jsonPrimitive?.content != actor.toString() ||
                root["workspaceId"]?.jsonPrimitive?.content != workspaceId.toString()) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
            (root["campaigns"] as? JsonArray ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)).map {
                val row = it as JsonObject
                ListedCampaign(UUID.fromString(row.getValue("id").jsonPrimitive.content),
                    UUID.fromString(row.getValue("farmId").jsonPrimitive.content), row.getValue("version").jsonPrimitive.long)
            }
        }

    private fun authMessage(failure: AuthFailure) = when (failure.kind) {
        AuthFailure.Kind.NETWORK -> "Sin conexión con el servidor. Tus datos siguen en este teléfono; vuelve a intentarlo."
        AuthFailure.Kind.EXPIRED -> "La sesión ha caducado. Vuelve a iniciar sesión y sincroniza de nuevo."
        else -> "No se pudo verificar tu sesión. Vuelve a iniciar sesión."
    }

    internal companion object {
        const val LIST_RPC = "list_own_campaigns_v1"
        val SUPPORTED = setOf(SyncEntityType.WORKSPACE, SyncEntityType.FARM, SyncEntityType.PARCEL, SyncEntityType.CAMPAIGN,
            SyncEntityType.HARVEST, SyncEntityType.DELIVERY)
        const val MAX_CHANGES_PER_CAMPAIGN = 20
        const val MAX_RECEIVE_PAGES = 50
    }
}
