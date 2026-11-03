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
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
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
import com.isivoltpro.maginaolivo.data.repository.DispatchIdentity
import com.isivoltpro.maginaolivo.data.repository.HistoricalDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.PrepareOutcome
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.prepareDeliveryDispatch
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDeleteCodes
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDeleteCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDeleteOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchWorker
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.DispatchOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.deliveryDeleteHash
import com.isivoltpro.maginaolivo.data.repository.sync.parseDeliveryDeleteEnvelope
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.harvest.AUTO_DAY
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #808 R1-a Android: the sender of a Pesada's logical delete, Room only (API 35), against an in-memory server
 * that follows the R1-a SQL contract (`delete_own_delivery_v1`: CAS, idempotent receipt with the canonical DELETE
 * hash, payload version 3; `get_own_delivery_delete_head_v1`). Proves: staging once with a frozen UUID and bytes,
 * confirmation only by receipt + delete HEAD, lost responses resolved by the SAME operation without a second
 * delete, a v3 WRITE receipt never confirming a delete, unsettled siblings protected, AUTO_DAY kilos and split
 * kept by the local recomputation and never touched by the sender, and owner/identity/consent checks before any
 * request. Not wired in shipped code.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryDeleteSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000008d1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000008d1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000008d1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000008d1")
    private val now = Instant.parse("2026-12-02T08:00:00.123Z")
    private val day = LocalDate.parse("2026-11-18")
    private val cleanup = mutableListOf<() -> Unit>()
    private val opened = mutableListOf<Pair<MaginaOlivoDatabase, String>>()
    private var owner = true

    @After fun after() {
        cleanup.forEach { it() }
        opened.forEach { (db, name) -> runCatching { db.close() }; context.deleteDatabase(name) }
    }

    private inner class Phone(val name: String) {
        val db: MaginaOlivoDatabase
        val account: AccountRepository
        val links: LocalFarmLinkRepository
        val ledger: File = File(context.noBackupFilesDir, "g808-dd-${UUID.randomUUID()}.json")
        val userId: UUID

        init {
            context.deleteDatabase(name)
            db = MaginaOlivoDatabase.create(context, name).also { opened += it to name }
            val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
            account = AccountRepository(api, MemoryAccountSessionStore())
            runBlocking { account.login("owner@example.test", "password42", true) }
            userId = account.state.value.userId!!
            val store = AndroidFarmLinkLedger(ledger)
            links = LocalFarmLinkRepository(context, db, account, ISSUER, store, WorkspaceOwnerVerifier { _, ws -> owner && ws == workspaceId })
            cleanup.add {
                store.read()?.consents?.forEach { File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively() }
                listOf(ledger, File(ledger.path + ".bak"), File(ledger.path + ".new")).forEach { it.delete() }
            }
            runBlocking {
                val meta = LocalMetadata(now, now, syncStatus = SyncStatus.SYNCED, remoteVersion = 1L)
                db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", userId, "ES", "Europe/Madrid", "es-ES", "EUR", meta))
                db.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
                db.parcelDao().upsert(ParcelEntity(north, workspaceId, "Norte", source = "MANUAL", metadata = meta))
                db.campaignDao().upsert(CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"),
                    null, CampaignStatus.HARVEST, metadata = meta))
                db.campaignDao().upsertSnapshots(listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId,
                    north, farmId, "La Solana", "Norte", metadata = meta)))
                links.consent(userId, links.choices().filter { it.id == farmId }, true)
            }
        }

        val deliveries get() = OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers,
            workspaceRepository = object : WorkspaceRepository {
                override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
            }, writeScope = links, payloadRoute = HistoricalDeliveryRoute { _, _ -> true }) { ZoneOffset.UTC }

        fun sender(server: DeliveryRpcTransport, issuer: String = ISSUER) = DeliveryDeleteCoordinator(db, account, issuer, server,
            { links.ledgerFor(it) }, WorkspaceOwnerVerifier { _, ws -> owner && ws == workspaceId }) { now }

        /** A Pesada created by this phone, never sent: its v3 CREATE is PENDING and it has no remote version. */
        suspend fun pesada(net: Long): SyncOutboxEntity {
            val id = when (val r = deliveries.create(DeliveryDraft(farmId = farmId, deliveryDate = day, destinationOrganizationId = null,
                destinationName = "Cooperativa San Isidro", netGrams = net, shares = listOf(DeliveryShareInput(north, net)),
                origin = PesadaOrigin.TREE))) { is AppResult.Success -> r.value; is AppResult.Failure -> error("create: ${r.error}") }
            return db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single { it.status == OutboxStatus.PENDING }
        }

        /** A Pesada whose v3 CREATE the server applied and this phone confirmed (version 1 on both sides). */
        suspend fun confirmed(server: FakeServer, net: Long): SyncOutboxEntity {
            val op = pesada(net)
            db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status = 'CONFIRMED' WHERE id = ?", arrayOf<Any>(op.id.toString()))
            val row = db.deliveryDao().findById(op.entityId)!!
            db.deliveryDao().upsert(row.copy(metadata = row.metadata.copy(remoteVersion = 1L, syncStatus = SyncStatus.SYNCED)))
            server.deliveries[op.entityId] = FakeServer.Pesada(1L)
            server.receipts[op.id] = FakeServer.Receipt(op.entityId, deliveryPayloadHash(op.payloadJson!!, 3)!!, 1L)
            return op
        }

        suspend fun delete(op: SyncOutboxEntity): SyncOutboxEntity {
            assertTrue(deliveries.delete(op.entityId) is AppResult.Success)
            return deleteMarker(op.entityId)
        }

        suspend fun deleteMarker(id: UUID) =
            db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single { it.operation == OutboxOperation.DELETE }

        suspend fun jornadaOf(op: SyncOutboxEntity): HarvestEntity =
            db.harvestDao().findById(db.deliveryDao().findById(op.entityId)!!.harvestId!!)!!

        /** Everything a refusal must leave untouched: every operation, the Pesada, its parcels, its Jornada and split. */
        suspend fun snapshot(op: SyncOutboxEntity): List<Any?> {
            val pesada = db.deliveryDao().findById(op.entityId)!!
            return listOf(db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId), pesada,
                db.deliveryDao().listParcels(op.entityId), pesada.harvestId?.let { db.harvestDao().findById(it) },
                pesada.harvestId?.let { db.harvestDao().listParcels(it) })
        }
    }

    /** In-memory R1-a server: one account, Pesadas with version and tombstone, receipts by operation. */
    internal class FakeServer : DeliveryRpcTransport {
        data class Pesada(var version: Long, var deleted: Boolean = false)
        data class Receipt(val entity: UUID, val hash: String, val version: Long, val type: String = "DELIVERY")
        val deliveries = mutableMapOf<UUID, Pesada>()
        val receipts = mutableMapOf<UUID, Receipt>()
        val names = mutableListOf<String>()
        var dropAfterCommit = false
        var dropBeforeCommit = false
        var deny = false
        var headOverride: ((JsonObject) -> JsonObject)? = null
        var receiptOverride: ((JsonObject) -> JsonObject)? = null
        val deletes get() = names.count { it == "delete_own_delivery_v1" }

        private fun error(status: Int, code: String, message: String, details: String? = null) =
            DeliveryRpcResponse(status, """{"code":"$code","details":${details?.let { "\"$it\"" } ?: "null"},"hint":null,"message":"$message"}""")

        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            names += name
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            return when (name) {
                "delete_own_delivery_v1" -> delete(body)
                "check_sync_operations" -> {
                    val ids = (body.getValue("p_operation_ids") as JsonArray).map { UUID.fromString(it.jsonPrimitive.content) }
                    val rows = ids.mapNotNull { id -> receipts[id]?.let { r ->
                        var row = Json.parseToJsonElement("""{"operation_id":"$id","entity_id":"${r.entity}","entity_type":"${r.type}",
                            "remote_version":${r.version},"payload_version":3,"payload_hash":"${r.hash}"}""") as JsonObject
                        receiptOverride?.let { row = it(row) }
                        row
                    } }
                    DeliveryRpcResponse(200, """{"confirmed_operations":[],"confirmed_receipts":${JsonArray(rows)}}""")
                }
                "get_own_delivery_delete_head_v1" -> {
                    val id = UUID.fromString(body.getValue("p_delivery_id").jsonPrimitive.content)
                    val pesada = deliveries[id] ?: return error(403, "42501", "sync denied: SCOPE")
                    val head = if (!pesada.deleted) null
                        else receipts.entries.filter { it.value.entity == id && it.value.version == pesada.version }.singleOrNull()
                    var json = Json.parseToJsonElement("""{"contract_version":1,"delivery_id":"$id","version":${pesada.version},
                        "deleted":${pesada.deleted},"head_operation_id":${head?.let { "\"${it.key}\"" } ?: "null"},
                        "head_payload_version":${if (head == null) "null" else "3"},"head_payload_hash":${head?.let { "\"${it.value.hash}\"" } ?: "null"}}""") as JsonObject
                    headOverride?.let { json = it(json) }
                    DeliveryRpcResponse(200, json.toString())
                }
                else -> error("RPC no esperada: $name")
            }
        }

        private fun delete(body: JsonObject): DeliveryRpcResponse {
            if (deny) return error(403, "42501", "sync denied: SCOPE")
            if (dropBeforeCommit) { dropBeforeCommit = false; throw java.io.IOException("corte antes del commit") }
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val id = UUID.fromString(body.getValue("p_delivery_id").jsonPrimitive.content)
            val expected = body.getValue("p_expected_version").jsonPrimitive.long
            val hash = deliveryDeleteHash(id, expected)
            receipts[op]?.let { r ->
                return if (r.type == "DELIVERY" && r.entity == id && r.hash == hash) ack(op, id, r.version, true)
                else error(409, "23505", "sync conflict: RECEIPT_CONFLICT")
            }
            val pesada = deliveries[id] ?: return error(403, "42501", "sync denied: SCOPE")
            if (pesada.deleted) return error(409, "23505", "sync conflict: DELETED")
            if (pesada.version != expected) return error(409, "23505", "sync conflict: STALE_VERSION", "remote_version=${pesada.version}")
            pesada.version += 1; pesada.deleted = true
            receipts[op] = Receipt(id, hash, pesada.version)
            if (dropAfterCommit) { dropAfterCommit = false; throw java.io.IOException("respuesta perdida tras el commit") }
            return ack(op, id, pesada.version, false)
        }

        private fun ack(op: UUID, entity: UUID, version: Long, idem: Boolean) = DeliveryRpcResponse(200,
            """{"success":true,"operation_id":"$op","entity_id":"$entity","status":"APPLIED","remote_version":$version,"payload_version":3,"deleted":true,"idempotent":$idem}""")
    }

    private fun lease(): DeliveryDispatchLease {
        val directory = File(context.noBackupFilesDir, "g808-dd-lease-${UUID.randomUUID()}")
        cleanup.add { directory.deleteRecursively() }
        return checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
    }

    @Test
    fun aConfirmedPesadaDeleteIsFrozenOnceAndConfirmedOnlyByItsReceiptAndDeleteHead() = runBlocking<Unit> {
        val phone = Phone("dd-happy")
        val server = FakeServer()
        val first = phone.confirmed(server, 1_000_000)
        val second = phone.confirmed(server, 600_000)
        val third = phone.confirmed(server, 400_000)
        val jornada = phone.jornadaOf(first)
        assertEquals(AUTO_DAY, jornada.dayOrigin)
        assertEquals(2_000_000L, jornada.weightGrams)

        // The local delete: tombstone, AUTO_DAY kilos and split recomputed from the live Pesadas, one unbound marker.
        val marker = phone.delete(second)
        assertNull(marker.accountId); assertNull(marker.payloadJson)
        val recomputed = phone.db.harvestDao().findById(jornada.id)!!
        assertEquals(1_400_000L, recomputed.weightGrams)
        // A local AUTO_DAY split records its origin Parcels without kilos (UNALLOCATED); the server derives its own.
        assertEquals(listOf(north), phone.db.harvestDao().listParcels(jornada.id).map { it.parcelId })

        assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(marker.id))
        val staged = phone.db.syncOutboxDao().findById(marker.id)!!
        assertEquals(marker.id, staged.id)
        assertEquals(phone.userId.toString(), staged.accountId)
        assertEquals(farmId, staged.farmId)
        assertEquals(3, staged.payloadVersion)
        val envelope = parseDeliveryDeleteEnvelope(staged.payloadJson!!)!!
        assertEquals(1L, envelope.expectedVersion)
        assertEquals(second.entityId, envelope.deliveryId)
        // Frozen once: a second stage changes nothing.
        assertEquals(DeliveryDeleteOutcome.REJECTED, phone.sender(server).stage(marker.id))
        assertEquals(staged, phone.db.syncOutboxDao().findById(marker.id))
        assertTrue("Sin RPC al congelar", server.names.isEmpty())

        val dayBefore = phone.db.harvestDao().findById(jornada.id)
        val splitBefore = phone.db.harvestDao().listParcels(jornada.id)
        lease().use { lease ->
            assertEquals(DeliveryDeleteOutcome.SYNCED, phone.sender(server).send(marker.id, lease))
            assertEquals(listOf("delete_own_delivery_v1", "check_sync_operations", "get_own_delivery_delete_head_v1"), server.names)
            val done = phone.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(OutboxStatus.CONFIRMED, done.status)
            assertEquals(staged.payloadJson, done.payloadJson)
            val pesada = phone.db.deliveryDao().findById(second.entityId)!!
            assertNotNull(pesada.metadata.deletedAt)
            assertEquals(2L, pesada.metadata.remoteVersion)
            assertEquals(SyncStatus.SYNCED, pesada.metadata.syncStatus)
            assertEquals(1, phone.db.deliveryDao().listParcels(second.entityId).size)
            // The sender never writes the Jornada: kilos and split are those of the local recomputation.
            assertEquals(dayBefore, phone.db.harvestDao().findById(jornada.id))
            assertEquals(splitBefore, phone.db.harvestDao().listParcels(jornada.id))
            // Idempotent locally: nothing is read or sent twice.
            server.names.clear()
            assertEquals(DeliveryDeleteOutcome.ALREADY_CONFIRMED, phone.sender(server).send(marker.id, lease))
            assertTrue(server.names.isEmpty())
            assertEquals(1, server.receipts.values.count { it.entity == second.entityId && it.version == 2L })
            assertEquals(listOf(first.entityId, third.entityId).sorted(),
                server.deliveries.filter { !it.value.deleted }.keys.sorted())
        }
    }

    @Test
    fun aLostResponseIsResolvedByTheSameOperationWithoutASecondDelete() = runBlocking<Unit> {
        val phone = Phone("dd-lost")
        val server = FakeServer()
        val kept = phone.confirmed(server, 900_000)
        val lostAfter = phone.confirmed(server, 700_000)
        val lostBefore = phone.confirmed(server, 300_000)
        lease().use { lease ->
            // 1. Applied, answer lost: OUTCOME_UNKNOWN, same UUID and bytes; the retry reads the receipt FIRST.
            val marker = phone.delete(lostAfter)
            assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(marker.id))
            val frozen = phone.db.syncOutboxDao().findById(marker.id)!!
            server.dropAfterCommit = true
            assertEquals(DeliveryDeleteOutcome.OUTCOME_UNKNOWN, phone.sender(server).send(marker.id, lease))
            val unknown = phone.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(OutboxStatus.FAILED, unknown.status)
            assertEquals(DeliveryDeleteCodes.OUTCOME_UNKNOWN, unknown.lastErrorCode)
            assertEquals(frozen.payloadJson, unknown.payloadJson)
            assertEquals(1L, phone.db.deliveryDao().findById(lostAfter.entityId)!!.metadata.remoteVersion)
            server.names.clear()
            // A new coordinator (a restart) resolves it with zero writes.
            assertEquals(DeliveryDeleteOutcome.SYNCED, phone.sender(server).send(marker.id, lease))
            assertEquals(listOf("check_sync_operations", "get_own_delivery_delete_head_v1"), server.names)
            assertEquals(1, server.receipts.keys.count { it == marker.id })
            assertEquals(frozen.payloadJson, phone.db.syncOutboxDao().findById(marker.id)!!.payloadJson)
            assertEquals(2L, phone.db.deliveryDao().findById(lostAfter.entityId)!!.metadata.remoteVersion)

            // 2. Cut BEFORE the commit: no receipt, so the SAME operation is sent again, once.
            val second = phone.delete(lostBefore)
            assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(second.id))
            server.dropBeforeCommit = true
            assertEquals(DeliveryDeleteOutcome.OUTCOME_UNKNOWN, phone.sender(server).send(second.id, lease))
            assertEquals(false, server.deliveries.getValue(lostBefore.entityId).deleted)
            server.names.clear()
            assertEquals(DeliveryDeleteOutcome.SYNCED, phone.sender(server).send(second.id, lease))
            assertEquals(listOf("check_sync_operations", "delete_own_delivery_v1", "check_sync_operations",
                "get_own_delivery_delete_head_v1"), server.names)
            assertEquals(1, server.receipts.keys.count { it == second.id })
            assertEquals(false, server.deliveries.getValue(kept.entityId).deleted)
        }
    }

    @Test
    fun aV3WriteReceiptNeverConfirmsADeleteAndTheWriteWorkerNeverTakesIt() = runBlocking<Unit> {
        val phone = Phone("dd-write-receipt")
        val server = FakeServer()
        val write = phone.confirmed(server, 800_000)
        phone.confirmed(server, 200_000)
        val marker = phone.delete(write)
        assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(marker.id))
        lease().use { lease ->
            // The write worker refuses a DELETE marker before anything: no PREPARE, no RPC, no state change.
            val worker = DeliveryDispatchWorker(phone.db, phone.links, DeliveryRpcAdapter(server))
            val before = phone.snapshot(write)
            assertEquals(DispatchOutcome.REJECTED, worker.dispatch(marker.id, lease))
            assertEquals(DispatchOutcome.REJECTED, worker.recoverLostResponse(marker.id, lease))
            assertEquals(DispatchOutcome.REJECTED, worker.resolveAwaitingHead(marker.id, lease))
            assertTrue(server.names.isEmpty())
            assertNull(phone.db.deliveryDispatchPrepareDao().findByOperation(marker.id))
            assertEquals(before, phone.snapshot(write))

            // The server answers with the v3 WRITE receipt and head of the Pesada instead: never a delete ACK.
            val writeHash = server.receipts.getValue(write.id).hash
            server.receiptOverride = { row -> JsonObject(row + ("payload_hash" to JsonPrimitive(writeHash))) }
            assertEquals(DeliveryDeleteOutcome.CONFLICT, phone.sender(server).send(marker.id, lease))
            val held = phone.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(OutboxStatus.BLOCKED, held.status)
            assertEquals(DeliveryDeleteCodes.RECEIPT_CONFLICT, held.lastErrorCode)
            assertEquals(1L, phone.db.deliveryDao().findById(write.entityId)!!.metadata.remoteVersion)
            assertEquals(SyncStatus.PENDING, phone.db.deliveryDao().findById(write.entityId)!!.metadata.syncStatus)
        }
        // Same with a HEAD that names the write operation (or reports the Pesada alive): HEAD_MISMATCH, held.
        val other = Phone("dd-write-head")
        val server2 = FakeServer()
        val w2 = other.confirmed(server2, 500_000)
        val m2 = other.delete(w2)
        assertEquals(DeliveryDeleteOutcome.STAGED, other.sender(server2).stage(m2.id))
        server2.headOverride = { head -> JsonObject(head + mapOf("head_operation_id" to JsonPrimitive(w2.id.toString()),
            "head_payload_hash" to JsonPrimitive(server2.receipts.getValue(w2.id).hash))) }
        lease().use { assertEquals(DeliveryDeleteOutcome.CONFLICT, other.sender(server2).send(m2.id, it)) }
        assertEquals(DeliveryDeleteCodes.HEAD_MISMATCH, other.db.syncOutboxDao().findById(m2.id)!!.lastErrorCode)
        assertEquals(SyncStatus.PENDING, other.db.deliveryDao().findById(w2.entityId)!!.metadata.syncStatus)
    }

    @Test
    fun staleVersionAndAnAlreadyDeletedPesadaAreRetainedWithTheirCauseAndNeverResent() = runBlocking<Unit> {
        val phone = Phone("dd-stale")
        val server = FakeServer()
        val stale = phone.confirmed(server, 800_000)
        val gone = phone.confirmed(server, 300_000)
        phone.confirmed(server, 100_000)
        // Another device edited the first Pesada (version 2) and deleted the second one.
        server.deliveries.getValue(stale.entityId).version = 2L
        server.deliveries.getValue(gone.entityId).apply { version = 2L; deleted = true }
        val m1 = phone.delete(stale)
        val m2 = phone.delete(gone)
        val dayAfterLocalDelete = phone.db.harvestDao().findById(phone.jornadaOf(stale).id)
        lease().use { lease ->
            for (m in listOf(m1, m2)) assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(m.id))
            val frozen = phone.db.syncOutboxDao().findById(m1.id)!!
            assertEquals(DeliveryDeleteOutcome.CONFLICT, phone.sender(server).send(m1.id, lease))
            val held = phone.db.syncOutboxDao().findById(m1.id)!!
            assertEquals(OutboxStatus.BLOCKED, held.status)
            assertEquals("${DeliveryDeleteCodes.CONFLICT_PREFIX}STALE_VERSION", held.lastErrorCode)
            assertEquals("remote_version=2", held.lastErrorMessage)
            assertEquals(frozen.payloadJson, held.payloadJson)
            assertEquals(DeliveryDeleteOutcome.CONFLICT, phone.sender(server).send(m2.id, lease))
            assertEquals("${DeliveryDeleteCodes.CONFLICT_PREFIX}DELETED", phone.db.syncOutboxDao().findById(m2.id)!!.lastErrorCode)
            // The local tombstones and the Jornada stay; nothing is confirmed, rebased or retried by itself.
            for (op in listOf(stale, gone)) {
                val pesada = phone.db.deliveryDao().findById(op.entityId)!!
                assertNotNull(pesada.metadata.deletedAt)
                assertEquals(1L, pesada.metadata.remoteVersion)
            }
            assertEquals(dayAfterLocalDelete, phone.db.harvestDao().findById(phone.jornadaOf(stale).id))
            server.names.clear()
            assertEquals(DeliveryDeleteOutcome.REJECTED, phone.sender(server).send(m1.id, lease))
            assertTrue(server.names.isEmpty())
        }
    }

    @Test
    fun unsettledOperationsAndPreparesOfThePesadaAreProtectedAndNeverErased() = runBlocking<Unit> {
        val phone = Phone("dd-siblings")
        val server = FakeServer()
        phone.confirmed(server, 100_000)
        // A v3 CREATE already prepared (PROCESSING, unsettled PREPARE): it may be on the wire.
        val prepared = phone.pesada(500_000)
        lease().use { lease ->
            val identity = DispatchIdentity(phone.userId.toString(), ISSUER, phone.links.subject(phone.userId), workspaceId,
                ownerVerifiedWorkspaceId = workspaceId)
            assertEquals(PrepareOutcome.PREPARED, phone.db.prepareDeliveryDispatch(lease, prepared.id, identity, phone.links.state.value.ledger, now))
        }
        val claimed = phone.db.syncOutboxDao().findById(prepared.id)!!
        val marker = phone.delete(prepared)
        // The local delete never erases it: same row, same state, same bytes.
        assertEquals(claimed, phone.db.syncOutboxDao().findById(prepared.id))
        val before = phone.snapshot(prepared)
        assertEquals(DeliveryDeleteOutcome.WAITING_SIBLING, phone.sender(server).stage(marker.id))
        assertEquals(before, phone.snapshot(prepared))

        // FAILED/OUTCOME_UNKNOWN, BLOCKED (awaiting a v3 HEAD) and PENDING bound siblings: the same.
        for ((status, code) in listOf("FAILED" to "OUTCOME_UNKNOWN", "BLOCKED" to "V3_ACK_AWAITING_HEAD", "PENDING" to null)) {
            val op = phone.confirmed(server, 200_000)
            phone.db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status = ?, last_error_code = ?, account_id = ? WHERE id = ?",
                arrayOf<Any?>(status, code, phone.userId.toString(), op.id.toString()))
            val sibling = phone.db.syncOutboxDao().findById(op.id)!!
            val m = if (status == "PENDING") {
                // A PENDING write is replaced by a local delete (as before R1-a), so it is added after it.
                val d = phone.delete(op)
                phone.db.syncOutboxDao().insert(sibling.copy(id = UUID.randomUUID()))
                d
            } else phone.delete(op).also { assertEquals(sibling, phone.db.syncOutboxDao().findById(op.id)) }
            val snapshot = phone.snapshot(op)
            assertEquals("$status", DeliveryDeleteOutcome.WAITING_SIBLING, phone.sender(server).stage(m.id))
            assertEquals(snapshot, phone.snapshot(op))
        }
        assertTrue("Cero RPC con hermanos sin liquidar", server.names.isEmpty())
    }

    @Test
    fun onlyTheFreshOwnerWithConsentAndItsOwnIssuerSendsADelete() = runBlocking<Unit> {
        val phone = Phone("dd-auth")
        val server = FakeServer()
        val op = phone.confirmed(server, 700_000)
        phone.confirmed(server, 300_000)
        val marker = phone.delete(op)
        assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(marker.id))
        val before = phone.snapshot(op)
        lease().use { lease ->
            // A foreign issuer: refused locally, zero RPC.
            assertEquals(DeliveryDeleteOutcome.REJECTED, phone.sender(server, issuer = "otro-proyecto").send(marker.id, lease))
            // The owner is revoked on the server: refused before the delete, nothing written.
            owner = false
            assertEquals(DeliveryDeleteOutcome.NOT_AUTHORIZED, phone.sender(server).send(marker.id, lease))
            owner = true
            assertTrue(server.names.isEmpty())
            assertEquals(before, phone.snapshot(op))
            // The server itself refuses (SCOPE): NOT_AUTHORIZED, the marker stays PENDING with its bytes.
            server.deny = true
            assertEquals(DeliveryDeleteOutcome.NOT_AUTHORIZED, phone.sender(server).send(marker.id, lease))
            server.deny = false
            assertEquals(before, phone.snapshot(op))
            server.names.clear()
            // Another account on the device: never sends what the first one froze.
            val stranger = AccountRepository(object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false },
                MemoryAccountSessionStore())
            stranger.login("otra@example.test", "password42", true)
            assertTrue(stranger.state.value.userId != phone.userId)
            val foreign = DeliveryDeleteCoordinator(phone.db, stranger, ISSUER, server, { phone.links.ledgerFor(it) },
                WorkspaceOwnerVerifier { _, ws -> owner && ws == workspaceId }) { now }
            assertEquals(DeliveryDeleteOutcome.NOT_AUTHORIZED, foreign.send(marker.id, lease))
            assertEquals(DeliveryDeleteOutcome.REJECTED, foreign.stage(marker.id))
            // The session ends: refused, nothing written.
            phone.account.logout(false)
            assertEquals(DeliveryDeleteOutcome.NOT_AUTHORIZED, phone.sender(server).send(marker.id, lease))
            assertTrue(server.names.isEmpty())
            assertEquals(before, phone.snapshot(op))
            // The rightful owner again: the SAME operation is applied once.
            phone.account.login("owner@example.test", "password42", true)
            assertEquals(DeliveryDeleteOutcome.SYNCED, phone.sender(server).send(marker.id, lease))
            assertEquals(marker.id, phone.deleteMarker(op.entityId).id)
            assertEquals(1, server.deletes)
            // A staged delete whose farm consent is then revoked: the revocation BLOCKS the farm's pending operations,
            // so it is refused before any request, with its UUID and frozen bytes kept, and the tombstone untouched.
            val next = phone.confirmed(server, 100_000)
            val nextMarker = phone.delete(next)
            assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(nextMarker.id))
            val staged = phone.db.syncOutboxDao().findById(nextMarker.id)!!
            val tombstone = phone.db.deliveryDao().findById(next.entityId)
            server.names.clear()
            phone.links.revokeConsentForFarm(farmId)
            assertEquals(DeliveryDeleteOutcome.REJECTED, phone.sender(server).send(nextMarker.id, lease))
            assertTrue(server.names.isEmpty())
            val revoked = phone.db.syncOutboxDao().findById(nextMarker.id)!!
            assertEquals(OutboxStatus.BLOCKED, revoked.status)
            assertEquals(staged.id, revoked.id); assertEquals(staged.payloadJson, revoked.payloadJson)
            assertEquals(staged.accountId, revoked.accountId)
            assertEquals(tombstone, phone.db.deliveryDao().findById(next.entityId))
            assertTrue(server.deliveries.getValue(next.entityId).deleted.not())
        }
    }

    @Test
    fun aNeverSentPesadaIsResolvedWithoutARequestAndAManualDayIsNeverWrittenByTheSender() = runBlocking<Unit> {
        val phone = Phone("dd-never")
        val server = FakeServer()
        val kept = phone.pesada(600_000)
        val unsent = phone.pesada(400_000)
        val marker = phone.delete(unsent)
        // Its unsent CREATE was replaced by the delete; nothing was ever prepared: resolved, zero RPC.
        assertEquals(DeliveryDeleteOutcome.RESOLVED_NEVER_SENT, phone.sender(server).stage(marker.id))
        assertEquals(DeliveryDeleteCodes.RESOLVED_NEVER_SENT, phone.db.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
        assertTrue(server.names.isEmpty())
        assertEquals(600_000L, phone.jornadaOf(kept).weightGrams)

        // A quarantined marker (a write receipt applied while it waited) is staged against the current version.
        val confirmed = phone.confirmed(server, 500_000)
        val quarantined = phone.delete(confirmed)
        phone.db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status = 'BLOCKED', last_error_code = 'NEEDS_REBASE' WHERE id = ?",
            arrayOf<Any>(quarantined.id.toString()))
        assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(quarantined.id))
        assertEquals(1L, parseDeliveryDeleteEnvelope(phone.db.syncOutboxDao().findById(quarantined.id)!!.payloadJson!!)!!.expectedVersion)

        // A Pesada of a MANUAL Jornada: the sender confirms the delete and never writes the Jornada row.
        val manual = UUID.randomUUID()
        phone.db.harvestDao().upsert(HarvestEntity(id = manual, workspaceId = workspaceId, campaignId = campaignId, farmId = farmId,
            harvestDate = day, weightGrams = 4_242_000, metadata = LocalMetadata(now, now, syncStatus = SyncStatus.SYNCED, remoteVersion = 1L),
            dayOrigin = "MANUAL"))
        val inManual = phone.confirmed(server, 250_000)
        phone.db.deliveryDao().findById(inManual.entityId)!!.let { phone.db.deliveryDao().upsert(it.copy(harvestId = manual)) }
        val manualMarker = phone.delete(inManual)
        val manualAfterLocalDelete = phone.db.harvestDao().findById(manual)
        assertEquals(DeliveryDeleteOutcome.STAGED, phone.sender(server).stage(manualMarker.id))
        lease().use { assertEquals(DeliveryDeleteOutcome.SYNCED, phone.sender(server).send(manualMarker.id, it)) }
        assertEquals(manualAfterLocalDelete, phone.db.harvestDao().findById(manual))
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

    private companion object {
        const val ISSUER = "fixture-project"
    }
}
