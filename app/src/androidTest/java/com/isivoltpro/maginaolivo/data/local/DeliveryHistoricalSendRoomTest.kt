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
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.DELIVERY_V3_KEYS
import com.isivoltpro.maginaolivo.data.repository.DeliveryPayloadRoute
import com.isivoltpro.maginaolivo.data.repository.DeliveryRetentionCodes
import com.isivoltpro.maginaolivo.data.repository.DispatchIdentity
import com.isivoltpro.maginaolivo.data.repository.HistoricalDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LegacyDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.PrepareOutcome
import com.isivoltpro.maginaolivo.data.repository.ServerWorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.deliveryOriginCreatedAt
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.prepareDeliveryDispatch
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #808 Android transport, Room only (API 35), with a controlled fake RPC transport. Proves that the
 * v3 Pesada writer is chosen per operation and frozen, that only a server-confirmed workspace owner
 * can prepare it, that a v3 receipt is NEVER turned into an ACK without a v3-compatible HEAD read,
 * and that `sync review` rejections are retained with their cause. No network, no scheduler.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryHistoricalSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000008a1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000008a1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000008a1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000008a1")
    private val now = Instant.parse("2026-12-02T08:00:00.123Z")
    private val day = LocalDate.parse("2026-11-18")
    private val databases = mutableListOf<Pair<MaginaOlivoDatabase, String>>()
    private val cleanup = mutableListOf<() -> Unit>()
    private lateinit var db: MaginaOlivoDatabase

    @Before fun before() = runBlocking<Unit> { db = open("delivery-v3-a") }

    @After fun after() {
        cleanup.forEach { it() }
        databases.forEach { (database, name) -> runCatching { database.close() }; context.deleteDatabase(name) }
    }

    private suspend fun open(name: String): MaginaOlivoDatabase {
        context.deleteDatabase(name)
        val database = MaginaOlivoDatabase.create(context, name)
        databases += database to name
        val meta = LocalMetadata(now, now)
        database.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        database.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
        database.parcelDao().upsert(ParcelEntity(north, workspaceId, "Norte", source = "MANUAL", metadata = meta))
        database.campaignDao().upsert(CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"),
            null, CampaignStatus.HARVEST, metadata = meta))
        database.campaignDao().upsertSnapshots(listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId,
            north, farmId, "La Solana", "Norte", metadata = meta)))
        return database
    }

    private data class Device(
        val db: MaginaOlivoDatabase,
        val account: AccountRepository,
        val links: LocalFarmLinkRepository,
        val deliveries: OfflineFirstDeliveryRepository,
        val ledger: File,
    )

    /** Real account repository (fake Auth transport), real ledger and consent, real writer. */
    private suspend fun device(
        database: MaginaOlivoDatabase = db,
        email: String = "owner@example.test",
        route: DeliveryPayloadRoute = HistoricalDeliveryRoute { _, _ -> true },
        verifier: WorkspaceOwnerVerifier? = WorkspaceOwnerVerifier { _, ws -> ws == workspaceId },
    ): Device {
        val file = File(context.noBackupFilesDir, "g808-ledger-${UUID.randomUUID()}.json")
        val store = AndroidFarmLinkLedger(file)
        val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
        val account = AccountRepository(api, MemoryAccountSessionStore())
        account.login(email, "password42", true)
        // Integration #796 + #808: farms are linkable only from the signed-in owner's own workspace (#796); the fixture's
        // workspace belongs to that account, as a real device's does.
        database.openHelper.writableDatabase.execSQL("UPDATE workspaces SET owner_user_id = ? WHERE id = ?",
            arrayOf<Any>(account.state.value.userId.toString(), workspaceId.toString()))
        val links = LocalFarmLinkRepository(context, database, account, "fixture-project", store, verifier)
        cleanup.add {
            store.read()?.consents?.forEach { File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively() }
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
        }
        links.consent(account.state.value.userId!!, links.choices().filter { it.id == farmId }, true)
        val deliveries = OfflineFirstDeliveryRepository(database, FixedClock(now), RandomIds, TestDispatchers,
            workspaceRepository = activeWorkspace, writeScope = links, payloadRoute = route) { ZoneOffset.UTC }
        return Device(database, account, links, deliveries, file)
    }

    private fun draft(net: Long = 2_800_000, time: java.time.LocalTime? = null) = DeliveryDraft(
        farmId = farmId, deliveryDate = day, destinationOrganizationId = null, destinationName = "Cooperativa San Isidro",
        netGrams = net, shares = listOf(DeliveryShareInput(north, null)), origin = PesadaOrigin.TREE, deliveryTime = time,
    )

    private suspend fun create(device: Device, draft: DeliveryDraft = draft()): SyncOutboxEntity {
        val id = when (val result = device.deliveries.create(draft)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> error("create failed: ${result.error}")
        }
        // #808 causal order: these tests start where the Jornada sender left off, with the Jornada confirmed.
        device.db.deliveryDao().findById(id)?.harvestId?.let { jornada ->
            device.db.harvestDao().findById(jornada)?.let {
                device.db.harvestDao().upsert(it.copy(metadata = it.metadata.copy(remoteVersion = it.metadata.remoteVersion ?: 1L)))
            }
        }
        return device.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single { it.status == OutboxStatus.PENDING }
    }

    private fun lease(): DeliveryDispatchLease {
        val directory = File(context.noBackupFilesDir, "g808-lease-${UUID.randomUUID()}")
        cleanup.add { directory.deleteRecursively() }
        return checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
    }

    private fun leaseIn(directory: File): DeliveryDispatchLease = checkNotNull(DeliveryDispatchLease.tryAcquire(directory))

    /** Answers by name. The v2 HEAD read is never acceptable for a v3 operation. */
    private class Rpc(
        private val onV3: () -> DeliveryRpcResponse = { error("sin escritura v3") },
        private val onCheck: () -> DeliveryRpcResponse = { DeliveryRpcResponse(200, """{"confirmed_receipts":[]}""") },
        private val onV2: () -> DeliveryRpcResponse = { error("sin escritura v2") },
        private val onHead: () -> DeliveryRpcResponse = { error("get_own_delivery_v2 no puede confirmar una v3") },
    ) : DeliveryRpcTransport {
        val names = mutableListOf<String>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            names += name
            return when (name) {
                DeliveryRpcAdapter.SYNC_OWN_DELIVERY_V3 -> onV3()
                DeliveryRpcAdapter.SYNC_OWN_DELIVERY_V2 -> onV2()
                DeliveryRpcAdapter.CHECK_SYNC_OPERATIONS -> onCheck()
                else -> onHead()
            }
        }
    }

    private fun written(op: SyncOutboxEntity, version: Int = 3) = DeliveryRpcResponse(200,
        """{"success":true,"operation_id":"${op.id}","entity_id":"${op.entityId}","status":"APPLIED","remote_version":1,"payload_version":$version,"idempotent":false}""")

    private fun receipt(op: SyncOutboxEntity, version: Int = 3, hash: String? = deliveryPayloadHash(op.payloadJson!!, 3),
        entity: String = "DELIVERY", deliveryId: UUID = op.entityId) = DeliveryRpcResponse(200,
        """{"confirmed_operations":["${op.id}"],"confirmed_receipts":[{"operation_id":"${op.id}","entity_id":"$deliveryId",
           "entity_type":"$entity","remote_version":1,"payload_version":$version,"payload_hash":"$hash"}]}""")

    private fun reviewAnswer(code: String, cause: String) = DeliveryRpcResponse(if (code == "22023") 400 else 403,
        """{"code":"$code","details":null,"hint":null,"message":"sync review: $cause"}""")

    private fun worker(device: Device, rpc: Rpc) = DeliveryDispatchWorker(device.db, device.links, DeliveryRpcAdapter(rpc)) { now }

    private fun keys(payload: String) = (Json.parseToJsonElement(payload) as JsonObject).keys

    @Test
    fun theLegacyRouteKeepsEveryNewPesadaOnV2ExactlyAsBefore() = runBlocking<Unit> {
        val legacy = device(route = LegacyDeliveryRoute)
        val op = create(legacy)
        assertEquals(2, op.payloadVersion)
        assertTrue("v2 no lleva originCreatedAt", "originCreatedAt" !in keys(op.payloadJson!!))
        assertNotNull(deliveryPayloadHash(op.payloadJson!!, 2))
        assertNull(deliveryPayloadHash(op.payloadJson!!, 3))
        // A non-owner hint keeps the historical route on v2 too.
        val notOwner = device(route = HistoricalDeliveryRoute { _, _ -> false })
        assertEquals(2, create(notOwner).payloadVersion)
    }

    @Test
    fun theHistoricalRouteFreezesV3WithEveryKeyAndTheLocalCreationInstantAcrossEdits() = runBlocking<Unit> {
        val owner = device()
        val op = create(owner)
        assertEquals(3, op.payloadVersion)
        assertEquals(OutboxOperation.CREATE, op.operation)
        assertNull(op.baseRemoteVersion)
        assertEquals(DELIVERY_V3_KEYS, keys(op.payloadJson!!))
        val created = owner.db.deliveryDao().findById(op.entityId)!!.metadata.createdAt
        val origin = deliveryOriginCreatedAt(created)
        assertTrue(op.payloadJson!!.contains("\"originCreatedAt\":\"$origin\""))
        assertNotNull(deliveryPayloadHash(op.payloadJson!!, 3))
        // An edit before the CREATE is confirmed: a new operation, still v3, still the SAME origin,
        // even through a writer whose route is now legacy (the version follows the operation).
        val legacyEditor = OfflineFirstDeliveryRepository(owner.db, FixedClock(now.plusSeconds(3600)), RandomIds,
            TestDispatchers, workspaceRepository = activeWorkspace, writeScope = owner.links) { ZoneOffset.UTC }
        assertTrue(legacyEditor.update(op.entityId, draft(net = 2_900_000)) is AppResult.Success)
        val edited = owner.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId).single()
        assertEquals(3, edited.payloadVersion)
        assertTrue(edited.id != op.id)
        assertTrue(edited.payloadJson!!.contains("\"originCreatedAt\":\"$origin\""))
        assertTrue(edited.payloadJson!!.contains("\"netGrams\":2900000"))
    }

    @Test
    fun aV2OperationKeepsItsBytesUuidVersionAndRpcAfterTheRouteChanges() = runBlocking<Unit> {
        val legacy = device(route = LegacyDeliveryRoute)
        val op = create(legacy)
        // The route is switched afterwards: only a NEW Pesada takes v3; the queued v2 intent is untouched.
        val switched = OfflineFirstDeliveryRepository(legacy.db, FixedClock(now), RandomIds, TestDispatchers,
            workspaceRepository = activeWorkspace, writeScope = legacy.links, payloadRoute = HistoricalDeliveryRoute { _, _ -> true }) { ZoneOffset.UTC }
        val newer = create(legacy.copy(deliveries = switched), draft(net = 1_500_000))
        assertEquals(3, newer.payloadVersion)
        val before = legacy.db.syncOutboxDao().findById(op.id)!!
        assertEquals(op.payloadJson, before.payloadJson)
        assertEquals(2, before.payloadVersion)
        val rpc = Rpc(onV2 = { written(op, 2) }, onCheck = { receipt(op, 2, deliveryPayloadHash(op.payloadJson!!, 2)) },
            onHead = { DeliveryRpcResponse(200, """{"delivery":{"version":1},"head_payload_hash":"${deliveryPayloadHash(op.payloadJson!!, 2)}"}""") })
        lease().use { lease -> assertEquals(DispatchOutcome.SYNCED, worker(legacy, rpc).dispatch(op.id, lease)) }
        assertEquals(listOf("sync_own_delivery_v2", "check_sync_operations", "get_own_delivery_v2"), rpc.names)
        val after = legacy.db.syncOutboxDao().findById(op.id)!!
        assertEquals(before.payloadJson, after.payloadJson)
        assertEquals(2, after.payloadVersion)
        assertEquals(OutboxStatus.CONFIRMED, after.status)
        assertEquals(op.id, after.id)
    }

    @Test
    fun onlyAServerConfirmedOwnerPreparesV3AndANonOwnerIsNeverRedirected() = runBlocking<Unit> {
        val verifiers = listOf<WorkspaceOwnerVerifier?>(null, WorkspaceOwnerVerifier { _, _ -> false },
            WorkspaceOwnerVerifier { _, ws -> ws != workspaceId })
        verifiers.forEachIndexed { index, verifier ->
            val device = device(database = open("delivery-v3-gate-$index"), verifier = verifier)
            val op = create(device)
            val rpc = Rpc()
            lease().use { lease -> assertEquals(DispatchOutcome.NOT_AUTHORIZED, worker(device, rpc).dispatch(op.id, lease)) }
            assertTrue("Sin peticion", rpc.names.isEmpty())
            assertNull("Sin PREPARE", device.db.deliveryDispatchPrepareDao().findByOperation(op.id))
            val kept = device.db.syncOutboxDao().findById(op.id)!!
            assertEquals(OutboxStatus.PENDING, kept.status)
            assertEquals("Nunca se reconvierte a v2", 3, kept.payloadVersion)
            assertEquals(op.payloadJson, kept.payloadJson)
            assertEquals(1, device.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId).size)
        }
    }

    @Test
    fun prepareRefusesAV3UpdateOrAnOriginThatIsNotTheLocalCreation() = runBlocking<Unit> {
        val owner = device()
        val op = create(owner)
        val userId = owner.account.state.value.userId!!
        val identity = DispatchIdentity(userId.toString(), "fixture-project", owner.links.subject(userId), workspaceId,
            ownerVerifiedWorkspaceId = workspaceId)
        val tampered = op.payloadJson!!.replace(Regex("\"originCreatedAt\":\"[^\"]+\""), "\"originCreatedAt\":\"2026-01-01T00:00:00Z\"")
        owner.db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET payload_json = ? WHERE id = ?", arrayOf(tampered, op.id.toString()))
        lease().use { lease ->
            assertEquals(PrepareOutcome.REJECTED, owner.db.prepareDeliveryDispatch(lease, op.id, identity, owner.links.state.value.ledger, now))
        }
        owner.db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET payload_json = ?, operation = 'UPDATE', base_remote_version = 1 WHERE id = ?",
            arrayOf(op.payloadJson!!, op.id.toString()))
        lease().use { lease ->
            assertEquals(PrepareOutcome.REJECTED, owner.db.prepareDeliveryDispatch(lease, op.id, identity, owner.links.state.value.ledger, now))
            // Without the owner proof the same CREATE is not authorized.
            owner.db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET operation = 'CREATE', base_remote_version = NULL WHERE id = ?",
                arrayOf(op.id.toString()))
            assertEquals(PrepareOutcome.NOT_AUTHORIZED, owner.db.prepareDeliveryDispatch(lease, op.id,
                identity.copy(ownerVerifiedWorkspaceId = null), owner.links.state.value.ledger, now))
            assertEquals(PrepareOutcome.PREPARED, owner.db.prepareDeliveryDispatch(lease, op.id, identity, owner.links.state.value.ledger, now))
        }
        assertNull(owner.db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledAt)
    }

    @Test
    fun aMatchingV3ReceiptIsHeldAwaitingHeadAndNeverConfirmed() = runBlocking<Unit> {
        val owner = device()
        val op = create(owner)
        val rpc = Rpc(onV3 = { written(op) }, onCheck = { receipt(op) })
        lease().use { lease ->
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(owner, rpc).dispatch(op.id, lease))
            // Held: a second dispatch neither claims nor sends anything.
            assertEquals(DispatchOutcome.REJECTED, worker(owner, rpc).dispatch(op.id, lease))
        }
        assertEquals("Ni HEAD v2 ni reenvio", listOf("sync_own_delivery_v3", "check_sync_operations"), rpc.names)
        val held = owner.db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.BLOCKED, held.status)
        assertEquals(DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD, held.lastErrorCode)
        assertEquals(op.payloadJson, held.payloadJson)
        assertEquals(3, held.payloadVersion)
        assertNull("El PREPARE sigue abierto para confirmar mas tarde", owner.db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledAt)
        val delivery = owner.db.deliveryDao().findById(op.entityId)!!
        assertEquals(SyncStatus.PENDING, delivery.metadata.syncStatus)
        assertNull(delivery.metadata.remoteVersion)
    }

    @Test
    fun aReceiptThatIsNotExactlyThisOperationIsAConflictAndConfirmsNothing() = runBlocking<Unit> {
        val owner = device()
        lease().use { lease ->
            val variants: List<(SyncOutboxEntity) -> DeliveryRpcResponse> = listOf(
                { receipt(it, version = 2) },
                { receipt(it, hash = "0".repeat(64)) },
                { receipt(it, entity = "EXPENSE") },
                { receipt(it, deliveryId = UUID.randomUUID()) },
            )
            for (variant in variants) {
                val op = create(owner, draft(net = 1_000_000L + variants.indexOf(variant)))
                val rpc = Rpc(onV3 = { written(op) }, onCheck = { variant(op) })
                assertEquals(DispatchOutcome.CONFLICT, worker(owner, rpc).dispatch(op.id, lease))
                val kept = owner.db.syncOutboxDao().findById(op.id)!!
                assertTrue(kept.status != OutboxStatus.CONFIRMED && kept.status != OutboxStatus.BLOCKED)
                assertEquals(op.payloadJson, kept.payloadJson)
                assertEquals(SyncStatus.PENDING, owner.db.deliveryDao().findById(op.entityId)!!.metadata.syncStatus)
            }
        }
    }

    @Test
    fun reviewRejectionsAreHeldWithTheirCauseAndTheBytesUntouched() = runBlocking<Unit> {
        val owner = device()
        val causes = listOf("22023" to "FUTURE_DATE", "22023" to "CAMPAIGN_PERIOD", "22023" to "DELIVERY_TIME_AMBIGUOUS",
            "22023" to "DELIVERY_TIME_NONEXISTENT", "22023" to "ORIGIN_IN_FUTURE", "42501" to "AFTER_ARCHIVE",
            "42501" to "NOT_MEMBER_ON_DATE", "42501" to "ORIGIN_NOT_BEFORE_CLOSURE", "42501" to "ORIGIN_CLOCK_UNCERTAIN",
            "42501" to "PARCEL_INCOHERENT")
        lease().use { lease ->
            causes.forEachIndexed { index, (code, cause) ->
                val op = create(owner, draft(net = 1_000_000L + index))
                val rpc = Rpc(onV3 = { reviewAnswer(code, cause) }, onCheck = { fail("Una revision no lee recibos"); error("") })
                assertEquals(cause, DispatchOutcome.REVIEW_HELD, worker(owner, rpc).dispatch(op.id, lease))
                val held = owner.db.syncOutboxDao().findById(op.id)!!
                assertEquals(OutboxStatus.BLOCKED, held.status)
                assertEquals(DeliveryRetentionCodes.REVIEW_PREFIX + cause, held.lastErrorCode)
                assertEquals(op.payloadJson, held.payloadJson)
                assertEquals(op.id, held.id)
                assertEquals(DeliveryDispatchPrepareEntity.SETTLED_REVIEW,
                    owner.db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledOutcome)
                assertEquals(SyncStatus.PENDING, owner.db.deliveryDao().findById(op.entityId)!!.metadata.syncStatus)
            }
            // Authority (not review) keeps the old behaviour: not authorized, nothing held or deleted.
            val denied = create(owner, draft(net = 999_999))
            val rpc = Rpc(onV3 = { DeliveryRpcResponse(403, """{"code":"42501","message":"sync denied: SCOPE"}""") })
            assertEquals(DispatchOutcome.NOT_AUTHORIZED, worker(owner, rpc).dispatch(denied.id, lease))
            val kept = owner.db.syncOutboxDao().findById(denied.id)!!
            assertTrue(kept.status != OutboxStatus.BLOCKED)
            assertEquals(denied.payloadJson, kept.payloadJson)
        }
    }

    @Test
    fun aLostV3ResponseIsRecoveredByTheSameUuidAndIsStillNotConfirmed() = runBlocking<Unit> {
        val owner = device()
        val op = create(owner)
        val directory = File(context.noBackupFilesDir, "g808-lost-${UUID.randomUUID()}")
        cleanup.add { directory.deleteRecursively() }
        val cut = Rpc(onV3 = { throw java.io.IOException("respuesta perdida") })
        leaseIn(directory).use { lease -> assertEquals(DispatchOutcome.OUTCOME_UNKNOWN, worker(owner, cut).dispatch(op.id, lease)) }
        assertEquals(OutboxStatus.PROCESSING, owner.db.syncOutboxDao().findById(op.id)!!.status)
        val serving = Rpc(onV3 = { fail("La recuperacion no reenvia"); error("") }, onCheck = { receipt(op) })
        leaseIn(directory).use { lease ->
            val worker = worker(owner, serving)
            assertEquals(1, worker.settleInterrupted(farmId, lease))
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker.recoverLostResponse(op.id, lease))
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker.recoverLostResponse(op.id, lease))
        }
        assertEquals(listOf("check_sync_operations", "check_sync_operations"), serving.names)
        val held = owner.db.syncOutboxDao().findById(op.id)!!
        assertEquals(DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD, held.lastErrorCode)
        assertEquals(op.payloadJson, held.payloadJson)
        assertEquals(1, owner.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId).size)
    }

    @Test
    fun aRestartKeepsHeldOperationsBlockedUnsentAndNotReopened() = runBlocking<Unit> {
        val owner = device()
        val op = create(owner)
        lease().use { lease -> assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD,
            worker(owner, Rpc(onV3 = { written(op) }, onCheck = { receipt(op) })).dispatch(op.id, lease)) }
        // Process death: the database is reopened and a new sender takes the lock.
        databases.removeAll { it.first == owner.db }
        owner.db.close()
        val reopened = MaginaOlivoDatabase.create(context, "delivery-v3-a")
        databases += reopened to "delivery-v3-a"
        val links = LocalFarmLinkRepository(context, reopened, owner.account, "fixture-project",
            AndroidFarmLinkLedger(owner.ledger), WorkspaceOwnerVerifier { _, _ -> true })
        val silent = Rpc(onV3 = { fail("Nada se reenvia"); error("") }, onCheck = { fail("Nada se lee"); error("") })
        lease().use { lease ->
            val worker = DeliveryDispatchWorker(reopened, links, DeliveryRpcAdapter(silent)) { now }
            // The consent ledger is the same one, so this really is the recovery path: nothing to settle.
            assertTrue(links.state.value.ledger != null)
            assertEquals(0, worker.settleInterrupted(farmId, lease))
            assertEquals(DispatchOutcome.REJECTED, worker.dispatch(op.id, lease))
        }
        val userId = owner.account.state.value.userId!!.toString()
        assertEquals(0, reopened.syncOutboxDao().reopenBlockedDeliveryWithoutReceipt(op.id, userId, farmId, now.toEpochMilli()))
        val held = reopened.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.BLOCKED, held.status)
        assertEquals(DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD, held.lastErrorCode)
        assertTrue(silent.names.isEmpty())
    }

    @Test
    fun aRevocationDuringTheRequestKeepsTheV3EvidenceBlockedAndUnconfirmed() = runBlocking<Unit> {
        val owner = device()
        val op = create(owner)
        val rpc = Rpc(onV3 = { written(op) }, onCheck = {
            runBlocking { owner.links.revokeConsentForFarm(farmId) }
            receipt(op)
        })
        lease().use { lease -> assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(owner, rpc).dispatch(op.id, lease)) }
        val held = owner.db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.BLOCKED, held.status)
        assertEquals(DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD, held.lastErrorCode)
        assertEquals(SyncStatus.PENDING, owner.db.deliveryDao().findById(op.entityId)!!.metadata.syncStatus)
    }

    @Test
    fun twoDevicesOnlyTheServerConfirmedOwnerPreparesV3() = runBlocking<Unit> {
        val base = "https://fixture.test"
        fun contract(subject: UUID, role: String) = """{"contract_version":1,"issuer":"$base/auth/v1","subject":"$subject",
            "verified_at":"${Instant.now()}","token_expires_at":"${Instant.now().plusSeconds(3600)}","workspaces":[{"id":"$workspaceId",
            "name":"Olivar","role":"$role","permissions":["read","write","manage_members"],"grant_version":1,
            "authorization_expires_at":"${Instant.now().plusSeconds(900)}"}]}"""
        // Device A: the owner. Device B: a member with every permission whose stale hint chose v3.
        val a = device(verifier = ServerWorkspaceOwnerVerifier(WorkspaceAuthorizationRemote { contract(it.userId, "OWNER") }, base))
        val b = device(database = open("delivery-v3-b"), email = "member@example.test",
            verifier = ServerWorkspaceOwnerVerifier(WorkspaceAuthorizationRemote { contract(it.userId, "MANAGER") }, base))
        val opA = create(a)
        val opB = create(b)
        assertEquals(3, opA.payloadVersion); assertEquals(3, opB.payloadVersion)
        val rpcB = Rpc()
        lease().use { lease ->
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD,
                worker(a, Rpc(onV3 = { written(opA) }, onCheck = { receipt(opA) })).dispatch(opA.id, lease))
        }
        lease().use { lease -> assertEquals(DispatchOutcome.NOT_AUTHORIZED, worker(b, rpcB).dispatch(opB.id, lease)) }
        assertTrue(rpcB.names.isEmpty())
        val keptB = b.db.syncOutboxDao().findById(opB.id)!!
        assertEquals(OutboxStatus.PENDING, keptB.status)
        assertEquals(3, keptB.payloadVersion)
        assertEquals(opB.payloadJson, keptB.payloadJson)
    }

    private val activeWorkspace = object : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
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
