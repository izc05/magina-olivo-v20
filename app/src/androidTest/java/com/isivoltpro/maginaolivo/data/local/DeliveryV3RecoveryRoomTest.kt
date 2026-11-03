package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryDispatchPrepareEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.DeliveryRetentionCodes
import com.isivoltpro.maginaolivo.data.repository.HistoricalDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.V3RecoveryCauses
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchWorker
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.DispatchOutcome
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SimulatedAccountAuthApi
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #808 recovery gate, Room only (API 35), with a controlled fake RPC transport. A Pesada held as
 * V3_ACK_AWAITING_HEAD is confirmed by its SAME operation only on a simultaneous strict match of the
 * owner check, this actor's receipt and `get_own_delivery_head_v3`. Every other case keeps it held
 * with a traceable cause. Nothing is ever re-sent; no scheduler; not wired in shipped code.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryV3RecoveryRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000008b1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000008b1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000008b1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000008b1")
    private val now = Instant.parse("2026-12-02T08:00:00.123Z")
    private val day = LocalDate.parse("2026-11-18")
    private val dbName = "delivery-v3-recovery"
    private val cleanup = mutableListOf<() -> Unit>()
    private val opened = mutableListOf<MaginaOlivoDatabase>()
    private lateinit var db: MaginaOlivoDatabase

    @Before fun before() = runBlocking<Unit> {
        context.deleteDatabase(dbName)
        db = MaginaOlivoDatabase.create(context, dbName).also { opened += it }
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(north, workspaceId, "Norte", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"),
            null, CampaignStatus.HARVEST, metadata = meta))
        db.campaignDao().upsertSnapshots(listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId,
            north, farmId, "La Solana", "Norte", metadata = meta)))
    }

    @After fun after() {
        cleanup.forEach { it() }
        opened.forEach { runCatching { it.close() } }
        context.deleteDatabase(dbName)
    }

    private class Device(val account: AccountRepository, val links: LocalFarmLinkRepository,
        val deliveries: OfflineFirstDeliveryRepository, val ledger: File)

    private var owner = true
    private val verifier = WorkspaceOwnerVerifier { _, ws -> owner && ws == workspaceId }
    private val activeWorkspace = object : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
    }

    private suspend fun device(withVerifier: Boolean = true): Device {
        val file = File(context.noBackupFilesDir, "g808-rec-${UUID.randomUUID()}.json")
        val store = AndroidFarmLinkLedger(file)
        val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
        val account = AccountRepository(api, MemoryAccountSessionStore())
        account.login("owner@example.test", "password42", true)
        val links = LocalFarmLinkRepository(context, db, account, "fixture-project", store, if (withVerifier) verifier else null)
        cleanup.add {
            store.read()?.consents?.forEach { File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively() }
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
        }
        links.consent(account.state.value.userId!!, links.choices().filter { it.id == farmId }, true)
        val deliveries = OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers,
            workspaceRepository = activeWorkspace, writeScope = links, payloadRoute = HistoricalDeliveryRoute { _, _ -> true }) { ZoneOffset.UTC }
        return Device(account, links, deliveries, file)
    }

    private fun lease(): DeliveryDispatchLease {
        val directory = File(context.noBackupFilesDir, "g808-rec-lease-${UUID.randomUUID()}")
        cleanup.add { directory.deleteRecursively() }
        return checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
    }

    /** Answers by name. No write is acceptable during recovery, and the v2 HEAD never is for v3. */
    private class Rpc(
        private val onV3: () -> DeliveryRpcResponse = { error("la recuperacion no escribe") },
        private val onCheck: () -> DeliveryRpcResponse,
        private val onHead: () -> DeliveryRpcResponse = { error("sin cabecera v3") },
    ) : DeliveryRpcTransport {
        val names = mutableListOf<String>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            names += name
            return when (name) {
                DeliveryRpcAdapter.SYNC_OWN_DELIVERY_V3 -> onV3()
                DeliveryRpcAdapter.CHECK_SYNC_OPERATIONS -> onCheck()
                DeliveryRpcAdapter.GET_OWN_DELIVERY_HEAD_V3 -> onHead()
                else -> error("RPC no permitida en la recuperacion v3: $name")
            }
        }
    }

    private fun hash(op: SyncOutboxEntity) = deliveryPayloadHash(op.payloadJson!!, 3)!!
    private fun written(op: SyncOutboxEntity) = DeliveryRpcResponse(200,
        """{"success":true,"operation_id":"${op.id}","entity_id":"${op.entityId}","status":"APPLIED","remote_version":1,"payload_version":3,"idempotent":false}""")
    private fun receipt(op: SyncOutboxEntity, version: Int = 3, h: String = hash(op), entity: String = "DELIVERY", remote: Long = 1) =
        DeliveryRpcResponse(200, """{"confirmed_operations":["${op.id}"],"confirmed_receipts":[{"operation_id":"${op.id}",
           "entity_id":"${op.entityId}","entity_type":"$entity","remote_version":$remote,"payload_version":$version,"payload_hash":"$h"}]}""")
    private fun noReceipt() = DeliveryRpcResponse(200, """{"confirmed_operations":[],"confirmed_receipts":[]}""")
    private fun head(op: SyncOutboxEntity, version: Long = 1, operation: String? = "\"${op.id}\"", pv: String = "3",
        h: String? = "\"${hash(op)}\"", deleted: Boolean = false) = DeliveryRpcResponse(200,
        """{"contract_version":1,"delivery_id":"${op.entityId}","version":$version,"snapshot_format":2,"deleted":$deleted,
           "head_operation_id":${operation ?: "null"},"head_payload_version":${if (operation == null) "null" else pv},
           "head_payload_hash":${if (operation == null) "null" else h}}""")

    private fun worker(device: Device, rpc: Rpc, database: MaginaOlivoDatabase = db) =
        DeliveryDispatchWorker(database, device.links, DeliveryRpcAdapter(rpc)) { now }

    /**
     * A Pesada that reached the server and is held, exactly as the #819 dispatch leaves it. Cross gate
     * #822 + #824: first the causal gate refuses it (its Jornada has no remote version), then the Jornada
     * is taken as confirmed by its own sender (#824, covered by HarvestSendRoomTest and the HTTPS probe).
     */
    private suspend fun held(device: Device, lease: DeliveryDispatchLease, net: Long = 2_800_000): SyncOutboxEntity {
        val id = when (val r = device.deliveries.create(DeliveryDraft(farmId = farmId, deliveryDate = day,
            destinationOrganizationId = null, destinationName = "Cooperativa San Isidro", netGrams = net,
            shares = listOf(DeliveryShareInput(north, null)), origin = PesadaOrigin.TREE))) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> error("create failed: ${r.error}")
        }
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        assertEquals(3, op.payloadVersion)
        val jornada = db.harvestDao().findById(db.deliveryDao().findById(id)!!.harvestId!!)!!
        // The causal gate holds only while the Jornada has no remote version; a later Pesada of an
        // already confirmed day goes straight on (no wait cycle).
        if (jornada.metadata.remoteVersion == null) {
            val silent = Rpc(onCheck = { fail("Sin Jornada confirmada no se lee ni se envia"); error("") })
            assertEquals(DispatchOutcome.PARENT_NOT_SYNCED, worker(device, silent).dispatch(op.id, lease))
            assertTrue(silent.names.isEmpty())
            assertNull(db.deliveryDispatchPrepareDao().findByOperation(op.id))
            db.harvestDao().upsert(jornada.copy(metadata = jornada.metadata.copy(remoteVersion = 1L)))
        }
        assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD,
            worker(device, Rpc(onV3 = { written(op) }, onCheck = { receipt(op) })).dispatch(op.id, lease))
        return op
    }

    private suspend fun assertStillHeld(op: SyncOutboxEntity, cause: String?) {
        val row = db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.BLOCKED, row.status)
        assertEquals(DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD, row.lastErrorCode)
        assertEquals(cause, row.lastErrorMessage)
        assertEquals(op.payloadJson, row.payloadJson)
        assertEquals(3, row.payloadVersion)
        assertNull("PREPARE sin liquidar", db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledAt)
        val delivery = db.deliveryDao().findById(op.entityId)!!
        assertEquals(SyncStatus.PENDING, delivery.metadata.syncStatus)
        assertNull(delivery.metadata.remoteVersion)
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId).size)
    }

    @Test
    fun aStrictMatchConfirmsTheSameOperationAndAResolveAgainIsIdempotent() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            val rpc = Rpc(onCheck = { receipt(op) }, onHead = { head(op) })
            assertEquals(DispatchOutcome.SYNCED, worker(device, rpc).resolveAwaitingHead(op.id, lease))
            assertEquals("Solo lecturas, nunca reenvio", listOf("check_sync_operations", "get_own_delivery_head_v3"), rpc.names)
            val row = db.syncOutboxDao().findById(op.id)!!
            assertEquals(OutboxStatus.CONFIRMED, row.status)
            assertEquals(op.payloadJson, row.payloadJson)
            assertEquals(op.id, row.id)
            assertEquals(DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED, db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledOutcome)
            val delivery = db.deliveryDao().findById(op.entityId)!!
            assertEquals(SyncStatus.SYNCED, delivery.metadata.syncStatus)
            assertEquals(1L, delivery.metadata.remoteVersion)
            val silent = Rpc(onCheck = { fail("Ya confirmada"); error("") })
            assertEquals(DispatchOutcome.SYNCED, worker(device, silent).resolveAwaitingHead(op.id, lease))
            assertTrue(silent.names.isEmpty())
        }
    }

    @Test
    fun everyMismatchKeepsTheOperationHeldWithItsCause() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val cases: List<Triple<String, (SyncOutboxEntity) -> DeliveryRpcResponse, (SyncOutboxEntity) -> DeliveryRpcResponse>> = listOf(
                Triple(V3RecoveryCauses.NO_RECEIPT, { _ -> noReceipt() }, { _ -> error("sin cabecera") }),
                Triple(V3RecoveryCauses.RECEIPT_MISMATCH, { o -> receipt(o, h = "0".repeat(64)) }, { _ -> error("") }),
                Triple(V3RecoveryCauses.RECEIPT_MISMATCH, { o -> receipt(o, version = 2) }, { _ -> error("") }),
                Triple(V3RecoveryCauses.RECEIPT_MISMATCH, { o -> receipt(o, entity = "EXPENSE") }, { _ -> error("") }),
                Triple(V3RecoveryCauses.HEAD_NULL, { o -> receipt(o) }, { o -> head(o, operation = null) }),
                Triple(V3RecoveryCauses.HEAD_SUPERSEDED, { o -> receipt(o) }, { o -> head(o, version = 2) }),
                Triple(V3RecoveryCauses.HEAD_SUPERSEDED, { o -> receipt(o) }, { o -> head(o, operation = "\"${UUID.randomUUID()}\"") }),
                Triple(V3RecoveryCauses.HEAD_MISMATCH, { o -> receipt(o) }, { o -> head(o, h = "\"${"b".repeat(64)}\"") }),
                Triple(V3RecoveryCauses.HEAD_MISMATCH, { o -> receipt(o) }, { o -> head(o, pv = "2") }),
                Triple(V3RecoveryCauses.DELETED, { o -> receipt(o) }, { o -> head(o, operation = null, deleted = true) }),
            )
            cases.forEachIndexed { index, (cause, check, headAnswer) ->
                val op = held(device, lease, net = 1_000_000L + index)
                val rpc = Rpc(onCheck = { check(op) }, onHead = { headAnswer(op) })
                assertEquals(cause, DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(device, rpc).resolveAwaitingHead(op.id, lease))
                assertTrue("Sin escritura", DeliveryRpcAdapter.SYNC_OWN_DELIVERY_V3 !in rpc.names)
                assertStillHeld(op, cause)
            }
        }
    }

    @Test
    fun withoutAFreshOwnerNothingIsReadOrConfirmed() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            owner = false
            val rpc = Rpc(onCheck = { fail("Sin propietario no se lee"); error("") })
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(device, rpc).resolveAwaitingHead(op.id, lease))
            assertTrue(rpc.names.isEmpty())
            assertStillHeld(op, V3RecoveryCauses.NOT_OWNER)
            owner = true
            // Once the server confirms ownership again, the SAME operation resolves.
            assertEquals(DispatchOutcome.SYNCED,
                worker(device, Rpc(onCheck = { receipt(op) }, onHead = { head(op) })).resolveAwaitingHead(op.id, lease))
        }
    }

    @Test
    fun theShippedConfigurationWithoutAnOwnerVerifierCanNeverConfirm() = runBlocking<Unit> {
        val sender = device()
        lease().use { lease ->
            val op = held(sender, lease)
            val unwired = LocalFarmLinkRepository(context, db, sender.account, "fixture-project", AndroidFarmLinkLedger(sender.ledger))
            val rpc = Rpc(onCheck = { fail("No se lee nada"); error("") })
            assertEquals(DispatchOutcome.NOT_AUTHORIZED,
                DeliveryDispatchWorker(db, unwired, DeliveryRpcAdapter(rpc)) { now }.resolveAwaitingHead(op.id, lease))
            assertTrue(rpc.names.isEmpty())
            assertStillHeld(op, null)
        }
    }

    @Test
    fun aRevokedConsentOrAnAccountChangeNeverConfirms() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            // The account disappears while the reads are in flight: nothing is committed, not even a cause.
            val logout = Rpc(onCheck = { receipt(op) }, onHead = { runBlocking { device.account.logout(false) }; head(op) })
            assertEquals(DispatchOutcome.NOT_AUTHORIZED, worker(device, logout).resolveAwaitingHead(op.id, lease))
            assertStillHeld(op, null)
        }
        val again = device()
        lease().use { lease ->
            val op = held(again, lease, net = 2_000_000)
            again.links.revokeConsentForFarm(farmId)
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD,
                worker(again, Rpc(onCheck = { receipt(op) }, onHead = { head(op) })).resolveAwaitingHead(op.id, lease))
            assertStillHeld(op, V3RecoveryCauses.CONSENT_REVOKED)
        }
    }

    @Test
    fun aLostResponseDuringRecoveryLeavesTheHoldUntouchedAndTheRetryConfirms() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            val cut = Rpc(onCheck = { receipt(op) }, onHead = { throw java.io.IOException("respuesta perdida") })
            assertEquals(DispatchOutcome.OUTCOME_UNKNOWN, worker(device, cut).resolveAwaitingHead(op.id, lease))
            assertStillHeld(op, null)
            assertEquals(DispatchOutcome.SYNCED,
                worker(device, Rpc(onCheck = { receipt(op) }, onHead = { head(op) })).resolveAwaitingHead(op.id, lease))
        }
    }

    @Test
    fun aRestartThenRecoveryConfirmsTheSameOperation() = runBlocking<Unit> {
        val device = device()
        val op = lease().use { lease -> held(device, lease) }
        db.close()
        val reopened = MaginaOlivoDatabase.create(context, dbName).also { opened += it }
        db = reopened
        val links = LocalFarmLinkRepository(context, reopened, device.account, "fixture-project", AndroidFarmLinkLedger(device.ledger), verifier)
        val rpc = Rpc(onCheck = { receipt(op) }, onHead = { head(op) })
        lease().use { lease ->
            assertEquals(DispatchOutcome.SYNCED,
                DeliveryDispatchWorker(reopened, links, DeliveryRpcAdapter(rpc)) { now }.resolveAwaitingHead(op.id, lease))
        }
        assertEquals(OutboxStatus.CONFIRMED, reopened.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(1, reopened.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId).size)
    }

    @Test
    fun aLocalEditAfterTheHoldConfirmsAsNeedsRebaseNeverAsSynced() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            val edited = OfflineFirstDeliveryRepository(db, FixedClock(now.plusSeconds(60)), RandomIds, TestDispatchers,
                workspaceRepository = activeWorkspace, writeScope = device.links) { ZoneOffset.UTC }
            assertTrue(edited.update(op.entityId, DeliveryDraft(farmId = farmId, deliveryDate = day, destinationOrganizationId = null,
                destinationName = "Cooperativa San Isidro", netGrams = 2_900_000, shares = listOf(DeliveryShareInput(north, null)),
                origin = PesadaOrigin.TREE)) is AppResult.Success)
            assertEquals(DispatchOutcome.NEEDS_REBASE,
                worker(device, Rpc(onCheck = { receipt(op) }, onHead = { head(op) })).resolveAwaitingHead(op.id, lease))
            assertEquals(SyncStatus.CONFLICT, db.deliveryDao().findById(op.entityId)!!.metadata.syncStatus)
            assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op.id)!!.status)
        }
    }

    @Test
    fun twoConcurrentResolutionsConfirmExactlyOnce() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            val outcomes = (1..2).map {
                async(Dispatchers.IO) {
                    worker(device, Rpc(onCheck = { receipt(op) }, onHead = { head(op) })).resolveAwaitingHead(op.id, lease)
                }
            }.awaitAll()
            assertEquals(listOf(DispatchOutcome.SYNCED, DispatchOutcome.SYNCED), outcomes)
            assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op.id)!!.status)
            assertEquals(DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED, db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledOutcome)
            assertEquals(1L, db.deliveryDao().findById(op.entityId)!!.metadata.remoteVersion)
        }
    }

    @Test
    fun aHeldV3PesadaIsNeverResentByDispatch() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            val rpc = Rpc(onCheck = { fail("Retenida: no se reenvia ni se lee"); error("") })
            assertEquals(DispatchOutcome.REJECTED, worker(device, rpc).dispatch(op.id, lease))
            assertTrue(rpc.names.isEmpty())
            assertStillHeld(op, null)
        }
    }

    @Test
    fun anIssuerChangeDuringRecoveryNeverConfirms() = runBlocking<Unit> {
        val device = device()
        lease().use { lease ->
            val op = held(device, lease)
            var ownerChecks = 0
            val countingVerifier = WorkspaceOwnerVerifier { session, ws -> ownerChecks++; verifier.isCurrentOwner(session, ws) }
            val foreign = LocalFarmLinkRepository(context, db, device.account, "other-project", AndroidFarmLinkLedger(device.ledger), countingVerifier)
            val rpc = Rpc(onCheck = { fail("Otro issuer: ninguna lectura"); error("") })
            val outboxBefore = db.syncOutboxDao().findById(op.id)
            val prepareBefore = db.deliveryDispatchPrepareDao().findByOperation(op.id)
            val deliveryBefore = db.deliveryDao().findById(op.entityId)
            assertEquals(DispatchOutcome.REJECTED,
                DeliveryDispatchWorker(db, foreign, DeliveryRpcAdapter(rpc)) { now }.resolveAwaitingHead(op.id, lease))
            assertTrue("Cero RPC", rpc.names.isEmpty())
            assertEquals("Ni siquiera la comprobacion de propietario", 0, ownerChecks)
            assertEquals(outboxBefore, db.syncOutboxDao().findById(op.id))
            assertEquals(prepareBefore, db.deliveryDispatchPrepareDao().findByOperation(op.id))
            assertEquals(deliveryBefore, db.deliveryDao().findById(op.entityId))
            assertStillHeld(op, null)
            // The original issuer still confirms the SAME operation.
            assertEquals(DispatchOutcome.SYNCED,
                worker(device, Rpc(onCheck = { receipt(op) }, onHead = { head(op) })).resolveAwaitingHead(op.id, lease))
        }
    }

    @Test
    fun onlyHeldV3OperationsAreResolvable() = runBlocking<Unit> {
        val device = device()
        val id = when (val r = device.deliveries.create(DeliveryDraft(farmId = farmId, deliveryDate = day,
            destinationOrganizationId = null, destinationName = "Cooperativa", netGrams = 1_000, shares = listOf(DeliveryShareInput(north, null)),
            origin = PesadaOrigin.TREE))) { is AppResult.Success -> r.value; is AppResult.Failure -> error("") }
        val pending = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        val rpc = Rpc(onCheck = { fail("Nada que leer"); error("") })
        lease().use { lease -> assertEquals(DispatchOutcome.REJECTED, worker(device, rpc).resolveAwaitingHead(pending.id, lease)) }
        assertTrue(rpc.names.isEmpty())
        assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(pending.id)!!.status)
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }
}
