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
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
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
import com.isivoltpro.maginaolivo.data.repository.enqueueCollapsed
import com.isivoltpro.maginaolivo.data.repository.prepareDeliveryDispatch
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchWorker
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.DispatchOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.HARVEST_KEYS
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestCodes
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestKind
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.harvestDeleteHash
import com.isivoltpro.maginaolivo.data.repository.sync.harvestPayloadHash
import com.isivoltpro.maginaolivo.data.repository.sync.parseHarvestEnvelope
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
 * #808 Jornada (HARVEST) sender, Room only (API 35), against an in-memory server that follows the #823
 * contract (canonical AUTO_DAY, CAS, idempotent receipts, strict HEAD, dependents). Proves staging once,
 * confirmation only by receipt + HEAD, the causal order Jornada -> Pesada without a wait cycle, the safe
 * rebase after AUTO_DAY_EXISTS and the retention of anything that was prepared. Not wired in shipped code.
 */
@RunWith(AndroidJUnit4::class)
class HarvestSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000008c1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000008c1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000008c1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000008c1")
    private val now = Instant.parse("2026-12-02T08:00:00.123Z")
    private val day = LocalDate.parse("2026-11-18")
    private val cleanup = mutableListOf<() -> Unit>()
    private val opened = mutableListOf<Pair<MaginaOlivoDatabase, String>>()
    private var owner = true

    @After fun after() {
        cleanup.forEach { it() }
        opened.forEach { (db, name) -> runCatching { db.close() }; context.deleteDatabase(name) }
    }

    /** One phone: its own Room, account, consent and producer; parents already confirmed (#798-#800 senders). */
    private inner class Phone(val name: String, parentsConfirmed: Boolean = true) {
        val db: MaginaOlivoDatabase
        val account: AccountRepository
        val links: LocalFarmLinkRepository
        val ledger: File = File(context.noBackupFilesDir, "g808-hv-${UUID.randomUUID()}.json")
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
                val remote = if (parentsConfirmed) 1L else null
                val meta = LocalMetadata(now, now, syncStatus = if (parentsConfirmed) SyncStatus.SYNCED else SyncStatus.PENDING, remoteVersion = remote)
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

        fun sender(server: FakeServer) = HarvestSendCoordinator(db, account, ISSUER, server, { links.ledgerFor(it) },
            WorkspaceOwnerVerifier { _, ws -> owner && ws == workspaceId }, RandomIds) { now }

        suspend fun pesada(net: Long = 2_800_000): SyncOutboxEntity {
            val id = when (val r = deliveries.create(DeliveryDraft(farmId = farmId, deliveryDate = day, destinationOrganizationId = null,
                destinationName = "Cooperativa San Isidro", netGrams = net, shares = listOf(DeliveryShareInput(north, net)),
                origin = PesadaOrigin.TREE))) { is AppResult.Success -> r.value; is AppResult.Failure -> error("create: ${r.error}") }
            return db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single { it.status == OutboxStatus.PENDING }
        }

        suspend fun jornadaOf(op: SyncOutboxEntity): HarvestEntity = db.harvestDao().findById(db.deliveryDao().findById(op.entityId)!!.harvestId!!)!!
        suspend fun unboundMarker(harvest: UUID): SyncOutboxEntity =
            db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, harvest).single { it.status == OutboxStatus.PENDING && it.payloadJson == null }
    }

    /** In-memory #823 server, shared by phones of one account. The client-side hash functions mirror the server's. */
    internal class FakeServer : DeliveryRpcTransport {
        data class Day(val id: UUID, val key: String?, var version: Long, var deleted: Boolean = false)
        data class Receipt(val entity: UUID, val hash: String, val version: Long)
        val days = mutableMapOf<UUID, Day>()
        val receipts = mutableMapOf<UUID, Receipt>()
        val withPesadas = mutableSetOf<UUID>()
        val names = mutableListOf<String>()
        var dropNext = false
        var reviewNext: String? = null
        var headOverride: ((JsonObject) -> JsonObject)? = null

        private fun error(status: Int, code: String, message: String, details: String? = null) =
            DeliveryRpcResponse(status, """{"code":"$code","details":${details?.let { "\"$it\"" } ?: "null"},"hint":null,"message":"$message"}""")
        private fun ack(op: UUID, entity: UUID, version: Long, idem: Boolean) = DeliveryRpcResponse(200,
            """{"success":true,"operation_id":"$op","entity_id":"$entity","status":"APPLIED","remote_version":$version,"payload_version":1,"idempotent":$idem}""")

        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            names += name
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            val response = when (name) {
                "sync_own_harvest_v1" -> write(body)
                "delete_own_harvest_v1" -> delete(body)
                "check_sync_operations" -> {
                    val ids = (body.getValue("p_operation_ids") as kotlinx.serialization.json.JsonArray).map { UUID.fromString(it.jsonPrimitive.content) }
                    DeliveryRpcResponse(200, """{"confirmed_operations":[],"confirmed_receipts":[${ids.mapNotNull { id -> receipts[id]?.let { r ->
                        """{"operation_id":"$id","entity_id":"${r.entity}","entity_type":"HARVEST","remote_version":${r.version},"payload_version":1,"payload_hash":"${r.hash}"}"""
                    } }.joinToString(",")}]}""")
                }
                "get_own_harvest_head_v1" -> {
                    val id = UUID.fromString(body.getValue("p_harvest_id").jsonPrimitive.content)
                    val day = days[id] ?: return error(403, "42501", "sync denied: SCOPE")
                    val heads = receipts.filter { it.value.entity == id && it.value.version == day.version }
                    val head = heads.entries.singleOrNull()
                    var json = Json.parseToJsonElement("""{"contract_version":1,"harvest_id":"$id","version":${day.version},"day_origin":"AUTO_DAY",
                        "deleted":${day.deleted},"weight_grams":0,"head_operation_id":${head?.let { "\"${it.key}\"" } ?: "null"},
                        "head_payload_version":${if (head == null) "null" else "1"},"head_payload_hash":${head?.let { "\"${it.value.hash}\"" } ?: "null"}}""") as JsonObject
                    headOverride?.let { json = it(json) }
                    DeliveryRpcResponse(200, json.toString())
                }
                else -> error("RPC no esperada: $name")
            }
            if (dropNext && name != "check_sync_operations" && name != "get_own_harvest_head_v1") {
                dropNext = false
                throw java.io.IOException("respuesta perdida tras el commit")
            }
            return response
        }

        private fun write(body: JsonObject): DeliveryRpcResponse {
            reviewNext?.let { reviewNext = null; return error(400, "22023", "sync review: $it") }
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val payload = body.getValue("p_harvest") as JsonObject
            val hash = harvestPayloadHash(payload)
            val id = UUID.fromString(payload.getValue("id").jsonPrimitive.content)
            receipts[op]?.let { r -> return if (r.hash == hash && r.entity == id) ack(op, id, r.version, true) else error(409, "23505", "sync conflict: RECEIPT_CONFLICT") }
            val base = payload["baseVersion"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.long
            val existing = days[id]
            val version = if (existing == null) {
                val auto = payload.getValue("dayOrigin").jsonPrimitive.content == "AUTO_DAY"
                val key = if (auto) "${payload.getValue("farmId").jsonPrimitive.content}|${payload.getValue("campaignId").jsonPrimitive.content}|${payload.getValue("harvestDate").jsonPrimitive.content}" else null
                days.values.firstOrNull { key != null && it.key == key && !it.deleted }?.let {
                    return error(409, "23505", "sync conflict: AUTO_DAY_EXISTS", "harvest_id=${it.id}")
                }
                days[id] = Day(id, key, 1); 1L
            } else {
                if (base != existing.version) return error(409, "23505", "sync conflict: STALE_VERSION")
                existing.version += 1; existing.version
            }
            receipts[op] = Receipt(id, hash, version)
            return ack(op, id, version, false)
        }

        private fun delete(body: JsonObject): DeliveryRpcResponse {
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val id = UUID.fromString(body.getValue("p_harvest_id").jsonPrimitive.content)
            val expected = body.getValue("p_expected_version").jsonPrimitive.long
            val hash = harvestDeleteHash(id, expected)
            receipts[op]?.let { r -> return if (r.hash == hash) ack(op, id, r.version, true) else error(409, "23505", "sync conflict: RECEIPT_CONFLICT") }
            val day = days[id] ?: return error(403, "42501", "sync denied: SCOPE")
            if (day.version != expected) return error(409, "23505", "sync conflict: STALE_VERSION")
            if (id in withPesadas) return error(409, "23505", "sync conflict: HARVEST_HAS_DEPENDENTS")
            day.deleted = true; day.version += 1
            receipts[op] = Receipt(id, hash, day.version)
            return ack(op, id, day.version, false)
        }
    }

    private fun lease(): DeliveryDispatchLease {
        val directory = File(context.noBackupFilesDir, "g808-hv-lease-${UUID.randomUUID()}")
        cleanup.add { directory.deleteRecursively() }
        return checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
    }

    private suspend fun confirmJornada(phone: Phone, server: FakeServer, op: SyncOutboxEntity, lease: DeliveryDispatchLease): HarvestEntity {
        val jornada = phone.jornadaOf(op)
        val marker = phone.unboundMarker(jornada.id)
        assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(marker.id))
        val outcome = phone.sender(server).send(marker.id, lease)
        assertTrue("$outcome", outcome == HarvestSendOutcome.SYNCED || outcome == HarvestSendOutcome.CONFIRMED_LOCAL_PENDING)
        return phone.db.harvestDao().findById(jornada.id)!!
    }

    @Test
    fun stageFreezesTheClosedPayloadOnceAndAReconciliationNeverReplacesIt() = runBlocking<Unit> {
        val phone = Phone("hv-stage")
        val first = phone.pesada()
        val jornada = phone.jornadaOf(first)
        assertEquals(AUTO_DAY, jornada.dayOrigin)
        val marker = phone.unboundMarker(jornada.id)
        assertEquals(HarvestSendOutcome.STAGED, phone.sender(FakeServer()).stage(marker.id))
        val staged = phone.db.syncOutboxDao().findById(marker.id)!!
        val envelope = parseHarvestEnvelope(staged.payloadJson!!)!!
        assertEquals(HarvestKind.CREATE, envelope.kind)
        assertEquals(HARVEST_KEYS, envelope.harvest!!.keys)
        assertEquals("AUTO_DAY", envelope.harvest!!["dayOrigin"]!!.jsonPrimitive.content)
        assertEquals("Un AUTO_DAY nunca envia kilos", JsonNull, envelope.harvest!!["weightGrams"])
        assertEquals(phone.userId.toString(), staged.accountId)
        // A second Pesada of the day reconciles the Jornada: the staged operation keeps its UUID and bytes.
        phone.pesada(net = 1_000_000)
        val after = phone.db.syncOutboxDao().findById(marker.id)!!
        assertEquals(staged.payloadJson, after.payloadJson)
        assertEquals(OutboxStatus.PENDING, after.status)
        assertEquals(2_800_000L + 1_000_000L, phone.db.harvestDao().findById(jornada.id)!!.weightGrams)
        // Staging twice is refused: frozen once.
        assertEquals(HarvestSendOutcome.REJECTED, phone.sender(FakeServer()).stage(marker.id))
    }

    @Test
    fun aManualJornadaCarriesItsTypedKilos() = runBlocking<Unit> {
        val phone = Phone("hv-manual")
        val id = UUID.randomUUID()
        phone.db.harvestDao().upsert(HarvestEntity(id = id, workspaceId = workspaceId, campaignId = campaignId, farmId = farmId,
            harvestDate = day, weightGrams = 4_200_000, metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING), dayOrigin = null))
        phone.db.enqueueCollapsed(RandomIds, SyncEntityType.HARVEST, id, OutboxOperation.CREATE, now)
        val marker = phone.unboundMarker(id)
        val server = FakeServer()
        assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(marker.id))
        val envelope = parseHarvestEnvelope(phone.db.syncOutboxDao().findById(marker.id)!!.payloadJson!!)!!
        assertEquals("MANUAL", envelope.harvest!!["dayOrigin"]!!.jsonPrimitive.content)
        assertEquals(4_200_000L, envelope.harvest!!["weightGrams"]!!.jsonPrimitive.long)
        lease().use { assertEquals(HarvestSendOutcome.SYNCED, phone.sender(server).send(marker.id, it)) }
    }

    @Test
    fun onlyAnExactReceiptAndHeadConfirmTheJornada() = runBlocking<Unit> {
        val phone = Phone("hv-confirm")
        val pesada = phone.pesada()
        val server = FakeServer()
        lease().use { lease ->
            val confirmed = confirmJornada(phone, server, pesada, lease)
            assertEquals(1L, confirmed.metadata.remoteVersion)
            assertEquals(listOf("sync_own_harvest_v1", "check_sync_operations", "get_own_harvest_head_v1"), server.names)
            val marker = phone.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, confirmed.id).single { it.payloadJson != null }
            assertEquals(OutboxStatus.CONFIRMED, marker.status)
            assertEquals(HarvestSendOutcome.ALREADY_CONFIRMED, phone.sender(server).send(marker.id, lease))
        }
        // A HEAD that names another operation never confirms.
        val other = Phone("hv-head-mismatch")
        val pesada2 = other.pesada()
        val bad = FakeServer().apply { headOverride = { head -> JsonObject(head + ("head_operation_id" to kotlinx.serialization.json.JsonPrimitive(UUID.randomUUID().toString()))) } }
        val marker2 = other.unboundMarker(other.jornadaOf(pesada2).id)
        assertEquals(HarvestSendOutcome.STAGED, other.sender(bad).stage(marker2.id))
        lease().use { assertEquals(HarvestSendOutcome.CONFLICT, other.sender(bad).send(marker2.id, it)) }
        val held = other.db.syncOutboxDao().findById(marker2.id)!!
        assertEquals(OutboxStatus.BLOCKED, held.status)
        assertEquals(HarvestCodes.HEAD_MISMATCH, held.lastErrorCode)
        assertNull(other.jornadaOf(pesada2).metadata.remoteVersion)
    }

    @Test
    fun aLostResponseIsRetriedWithTheSameOperationAndBytes() = runBlocking<Unit> {
        val phone = Phone("hv-lost")
        val pesada = phone.pesada()
        val server = FakeServer().apply { dropNext = true }
        val marker = phone.unboundMarker(phone.jornadaOf(pesada).id)
        assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(marker.id))
        val staged = phone.db.syncOutboxDao().findById(marker.id)!!
        lease().use { lease ->
            assertEquals(HarvestSendOutcome.OUTCOME_UNKNOWN, phone.sender(server).send(marker.id, lease))
            val unknown = phone.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(OutboxStatus.FAILED, unknown.status)
            assertEquals(staged.payloadJson, unknown.payloadJson)
            assertEquals(1, server.days.size)
            assertEquals(HarvestSendOutcome.SYNCED, phone.sender(server).send(marker.id, lease))
        }
        assertEquals("Una sola Jornada en el servidor", 1, server.days.size)
        assertEquals(1, server.receipts.size)
    }

    @Test
    fun pesadasWaitForTheirJornadaAndLaterReconciliationsNeverBlockThem() = runBlocking<Unit> {
        val phone = Phone("hv-causal")
        val pesada = phone.pesada()
        val writes = object : DeliveryRpcTransport {
            val names = mutableListOf<String>()
            override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
                names += name
                val opId = Regex("\"p_operation_id\":\"([^\"]+)\"").find(bodyJson)?.groupValues?.get(1)
                return when (name) {
                    "sync_own_delivery_v3" -> {
                        val op = phone.db.syncOutboxDao().findById(UUID.fromString(opId))!!
                        DeliveryRpcResponse(200, """{"success":true,"operation_id":"${op.id}","entity_id":"${op.entityId}","status":"APPLIED","remote_version":1,"payload_version":3,"idempotent":false}""")
                    }
                    else -> {
                        val id = Regex("\"([0-9a-f-]{36})\"").find(bodyJson)!!.groupValues[1]
                        val op = phone.db.syncOutboxDao().findById(UUID.fromString(id))!!
                        DeliveryRpcResponse(200, """{"confirmed_receipts":[{"operation_id":"${op.id}","entity_id":"${op.entityId}","entity_type":"DELIVERY","remote_version":1,"payload_version":3,"payload_hash":"${deliveryPayloadHash(op.payloadJson!!, 3)}"}]}""")
                    }
                }
            }
        }
        val worker = DeliveryDispatchWorker(phone.db, phone.links, DeliveryRpcAdapter(writes)) { now }
        lease().use { lease ->
            assertEquals(DispatchOutcome.PARENT_NOT_SYNCED, worker.dispatch(pesada.id, lease))
            assertTrue("Nada enviado antes de su Jornada", writes.names.isEmpty())
            assertNull(phone.db.deliveryDispatchPrepareDao().findByOperation(pesada.id))
            val server = FakeServer()
            confirmJornada(phone, server, pesada, lease)
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker.dispatch(pesada.id, lease))
            // Another Pesada of the same day: the reconciliation marker never blocks it (no wait cycle)...
            val second = phone.pesada(net = 1_000_000)
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker.dispatch(second.id, lease))
            // ...and staging that marker sends nothing: the server derives the kilos.
            val jornada = phone.jornadaOf(second)
            val marker = phone.unboundMarker(jornada.id)
            val before = server.names.size
            assertEquals(HarvestSendOutcome.RESOLVED_NOOP, phone.sender(server).stage(marker.id))
            assertEquals(HarvestCodes.RESOLVED_DERIVED_ONLY, phone.db.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
            assertEquals(before, server.names.size)
        }
    }

    @Test
    fun autoDayExistsRebasesOnlyUnsentPesadasKeepingTheirKilos() = runBlocking<Unit> {
        val server = FakeServer()
        val a = Phone("hv-phone-a")
        val b = Phone("hv-phone-b")
        // Two phones offline record a Pesada of the same day: two local AUTO_DAY rows.
        val pa = a.pesada(net = 1_000_000)
        val pb = b.pesada(net = 2_000_000)
        val canonical = a.jornadaOf(pa).id
        val losing = b.jornadaOf(pb).id
        assertTrue(canonical != losing)
        lease().use { lease ->
            confirmJornada(a, server, pa, lease)
            val marker = b.unboundMarker(losing)
            assertEquals(HarvestSendOutcome.STAGED, b.sender(server).stage(marker.id))
            assertEquals(HarvestSendOutcome.AUTO_DAY_EXISTS, b.sender(server).send(marker.id, lease))
            val held = b.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(HarvestCodes.AUTO_DAY_EXISTS, held.lastErrorCode)
            assertEquals("harvest_id=$canonical;version=1", held.lastErrorMessage)
            assertEquals("Nunca dos AUTO_DAY", 1, server.days.size)
            val pesadaBefore = b.db.deliveryDao().findById(pb.entityId)!!
            val sharesBefore = b.db.deliveryDao().listParcels(pb.entityId)
            assertEquals(HarvestSendOutcome.REBASED, b.sender(server).rebaseOntoCanonical(marker.id))
            val moved = b.db.deliveryDao().findById(pb.entityId)!!
            assertEquals(canonical, moved.harvestId)
            assertEquals("Los kilos no cambian", pesadaBefore.netGrams, moved.netGrams)
            assertEquals(sharesBefore.map { it.id to it.weightGrams }, b.db.deliveryDao().listParcels(pb.entityId).map { it.id to it.weightGrams })
            val rebuilt = b.db.syncOutboxDao().findById(pb.id)!!
            assertEquals("Mismo UUID de operacion", pb.id, rebuilt.id)
            assertEquals(pb.payloadJson!!.replace(losing.toString(), canonical.toString()), rebuilt.payloadJson)
            assertEquals(1L, b.db.harvestDao().findById(canonical)!!.metadata.remoteVersion)
            assertNotNull(b.db.harvestDao().findById(losing)!!.metadata.deletedAt)
            val audit = b.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(HarvestCodes.AUTO_DAY_REBASED, audit.lastErrorCode)
            assertEquals("rebased_to=$canonical", audit.lastErrorMessage)
            assertEquals(held.payloadJson, audit.payloadJson)
        }
    }

    /** Everything the rebase could touch on phone B, captured to prove "nothing mutated". */
    private suspend fun snapshot(phone: Phone, marker: UUID, losing: UUID, pesadas: List<SyncOutboxEntity>): List<Any?> =
        listOf(phone.db.syncOutboxDao().findById(marker), phone.db.harvestDao().findById(losing),
            phone.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, losing)) +
            pesadas.flatMap { listOf(phone.db.deliveryDao().findById(it.entityId), phone.db.deliveryDao().listParcels(it.entityId),
                phone.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, it.entityId)) }

    @Test
    fun aRevokedOwnerNeverRebasesAndOnlyAFreshOwnerDoes() = runBlocking<Unit> {
        val server = FakeServer()
        val a = Phone("hv-owner-a")
        val b = Phone("hv-owner-b")
        val pa = a.pesada(net = 1_000_000)
        val pb = b.pesada(net = 2_000_000)
        val canonical = a.jornadaOf(pa).id
        val losing = b.jornadaOf(pb).id
        lease().use { lease ->
            confirmJornada(a, server, pa, lease)
            val marker = b.unboundMarker(losing)
            assertEquals(HarvestSendOutcome.STAGED, b.sender(server).stage(marker.id))
            assertEquals(HarvestSendOutcome.AUTO_DAY_EXISTS, b.sender(server).send(marker.id, lease))
            val before = snapshot(b, marker.id, losing, listOf(pb))
            val calls = server.names.size
            // The ownership was revoked on the server: the fresh check refuses, nothing is rewritten.
            owner = false
            assertEquals(HarvestSendOutcome.NOT_AUTHORIZED, b.sender(server).rebaseOntoCanonical(marker.id))
            assertEquals(before, snapshot(b, marker.id, losing, listOf(pb)))
            // No owner verifier at all (the shipped configuration): refused before any session check.
            owner = true
            val unwired = HarvestSendCoordinator(b.db, b.account, ISSUER, server, { b.links.ledgerFor(it) }, null, RandomIds) { now }
            assertEquals(HarvestSendOutcome.NOT_AUTHORIZED, unwired.rebaseOntoCanonical(marker.id))
            assertEquals(before, snapshot(b, marker.id, losing, listOf(pb)))
            // Another project's coordinator cannot rebase an envelope frozen for this issuer.
            val foreign = HarvestSendCoordinator(b.db, b.account, "https://otro-proyecto.supabase.co", server, { b.links.ledgerFor(it) },
                WorkspaceOwnerVerifier { _, ws -> ws == workspaceId }, RandomIds) { now }
            assertEquals(HarvestSendOutcome.REJECTED, foreign.rebaseOntoCanonical(marker.id))
            assertEquals(before, snapshot(b, marker.id, losing, listOf(pb)))
            assertEquals("El rebase nunca llama a RPC de escritura", calls, server.names.size)
            // The server confirms ownership again: the SAME refused operation is rebased.
            assertEquals(HarvestSendOutcome.REBASED, b.sender(server).rebaseOntoCanonical(marker.id))
            assertEquals(canonical, b.db.deliveryDao().findById(pb.entityId)!!.harvestId)
            assertEquals(HarvestCodes.AUTO_DAY_REBASED, b.db.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
        }
    }

    @Test
    fun aPesadaWithoutAnOutboxOperationIsNeverRebased() = runBlocking<Unit> {
        val server = FakeServer()
        val a = Phone("hv-orphan-a")
        val b = Phone("hv-orphan-b")
        val pa = a.pesada(net = 1_000_000)
        val pb = b.pesada(net = 2_000_000)
        val orphan = b.pesada(net = 500_000)
        val losing = b.jornadaOf(pb).id
        assertEquals(losing, b.jornadaOf(orphan).id)
        lease().use { lease ->
            confirmJornada(a, server, pa, lease)
            val marker = b.unboundMarker(losing)
            assertEquals(HarvestSendOutcome.STAGED, b.sender(server).stage(marker.id))
            assertEquals(HarvestSendOutcome.AUTO_DAY_EXISTS, b.sender(server).send(marker.id, lease))
            // The second Pesada lost its outbox operation (e.g. an older client cleared it): no guaranteed way out.
            b.db.syncOutboxDao().deleteOnlyPendingForEntity(SyncEntityType.DELIVERY, orphan.entityId)
            assertTrue(b.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, orphan.entityId).isEmpty())
            val before = snapshot(b, marker.id, losing, listOf(pb, orphan))
            assertEquals(HarvestSendOutcome.RETAINED, b.sender(server).rebaseOntoCanonical(marker.id))
            assertEquals(before, snapshot(b, marker.id, losing, listOf(pb, orphan)))
            // The Pesada that kept its operation keeps it PENDING, with its UUID, bytes and Jornada.
            val kept = b.db.syncOutboxDao().findById(pb.id)!!
            assertEquals(OutboxStatus.PENDING, kept.status)
            assertEquals(pb.payloadJson, kept.payloadJson)
            assertEquals(losing, b.db.deliveryDao().findById(pb.entityId)!!.harvestId)
            assertEquals(losing, b.db.deliveryDao().findById(orphan.entityId)!!.harvestId)
            assertNull(b.db.harvestDao().findById(losing)!!.metadata.deletedAt)
            assertEquals(HarvestCodes.AUTO_DAY_EXISTS, b.db.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
        }
    }

    @Test
    fun aPreparedPesadaIsNeverRebasedAndEverythingIsRetained() = runBlocking<Unit> {
        val server = FakeServer()
        val a = Phone("hv-retain-a")
        val b = Phone("hv-retain-b")
        val pa = a.pesada(); val pb = b.pesada(net = 2_000_000)
        val losing = b.jornadaOf(pb).id
        // A Pesada prepared before (an older sender): it may already be on the wire with its Jornada id.
        lease().use { lease ->
            val identity = DispatchIdentity(b.userId.toString(), ISSUER, b.links.subject(b.userId), workspaceId, ownerVerifiedWorkspaceId = workspaceId)
            assertEquals(PrepareOutcome.PREPARED, b.db.prepareDeliveryDispatch(lease, pb.id, identity, b.links.state.value.ledger, now))
            confirmJornada(a, server, pa, lease)
            val marker = b.unboundMarker(losing)
            assertEquals(HarvestSendOutcome.STAGED, b.sender(server).stage(marker.id))
            assertEquals(HarvestSendOutcome.AUTO_DAY_EXISTS, b.sender(server).send(marker.id, lease))
            val before = b.db.syncOutboxDao().findById(pb.id)!!
            assertEquals(HarvestSendOutcome.RETAINED, b.sender(server).rebaseOntoCanonical(marker.id))
            assertEquals(losing, b.db.deliveryDao().findById(pb.entityId)!!.harvestId)
            assertEquals(before, b.db.syncOutboxDao().findById(pb.id))
            assertNull(b.db.harvestDao().findById(losing)!!.metadata.deletedAt)
            assertEquals(HarvestCodes.AUTO_DAY_EXISTS, b.db.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
        }
    }

    /** Settles the local delete of a Pesada as the R1-a sender would for one that never reached the server. */
    private fun settlePesadaDelete(phone: Phone, pesadaId: UUID, status: String = "BLOCKED", code: String? = "DELETE_RESOLVED_NEVER_SENT") =
        phone.db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET status = ?, last_error_code = ? WHERE entity_type = 'DELIVERY' AND entity_id = ? AND operation = 'DELETE'",
            arrayOf<Any?>(status, code, pesadaId.toString()))

    @Test
    fun aDeleteWaitsForItsPesadasIsRetainedWhileTheServerHasThemAndIsReevaluatedExplicitly() = runBlocking<Unit> {
        val phone = Phone("hv-delete")
        val pesada = phone.pesada()
        val server = FakeServer()
        lease().use { lease ->
            val jornada = confirmJornada(phone, server, pesada, lease)
            // The Pesada goes locally: the empty AUTO_DAY is retired and a DELETE marker produced.
            assertTrue(phone.deliveries.delete(pesada.entityId) is AppResult.Success)
            assertNotNull(phone.db.harvestDao().findById(jornada.id)!!.metadata.deletedAt)
            val marker = phone.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, jornada.id)
                .single { it.status == OutboxStatus.PENDING && it.payloadJson == null }
            // Gate J: Pesadas before Jornada. While the Pesada delete is unsettled nothing is frozen or sent.
            val calls = server.names.size
            assertEquals(HarvestSendOutcome.WAITING_DEPENDENTS, phone.sender(server).stage(marker.id))
            assertEquals(marker, phone.db.syncOutboxDao().findById(marker.id))
            assertEquals(calls, server.names.size)
            settlePesadaDelete(phone, pesada.entityId)
            assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(marker.id))
            val staged = phone.db.syncOutboxDao().findById(marker.id)!!
            val envelope = parseHarvestEnvelope(staged.payloadJson!!)!!
            assertEquals(HarvestKind.DELETE, envelope.kind); assertEquals(1L, envelope.expectedVersion)
            // The server still holds a Pesada (another device): refused, retained, never deleted.
            server.withPesadas += jornada.id
            assertEquals(HarvestSendOutcome.CONFLICT, phone.sender(server).send(marker.id, lease))
            assertEquals(HarvestCodes.HAS_DEPENDENTS, phone.db.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
            assertTrue("Retenida, no se borra", server.days.getValue(jornada.id).deleted.not())
            // A local dependent unsettled again: the re-evaluation waits, nothing moves, zero RPC.
            settlePesadaDelete(phone, pesada.entityId, status = "PENDING", code = null)
            val held = phone.db.syncOutboxDao().findById(marker.id)!!
            val before = server.names.size
            assertEquals(HarvestSendOutcome.WAITING_DEPENDENTS, phone.sender(server).reevaluateDependents(marker.id, lease))
            assertEquals(held, phone.db.syncOutboxDao().findById(marker.id))
            assertEquals(before, server.names.size)
            // Settled locally but still refused by the server: retained again with the same UUID and bytes.
            settlePesadaDelete(phone, pesada.entityId)
            assertEquals(HarvestSendOutcome.CONFLICT, phone.sender(server).reevaluateDependents(marker.id, lease))
            assertEquals(staged.payloadJson, phone.db.syncOutboxDao().findById(marker.id)!!.payloadJson)
            // The server's Pesada is gone: the SAME operation applies once; nothing is re-frozen.
            server.withPesadas -= jornada.id
            assertEquals(HarvestSendOutcome.SYNCED, phone.sender(server).reevaluateDependents(marker.id, lease))
            val done = phone.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(OutboxStatus.CONFIRMED, done.status); assertEquals(staged.payloadJson, done.payloadJson)
            assertTrue(server.days.getValue(jornada.id).deleted)
            assertEquals(1, server.receipts.keys.count { it == marker.id })
            // Only a delete retained as HAS_DEPENDENTS can be re-evaluated.
            assertEquals(HarvestSendOutcome.REJECTED, phone.sender(server).reevaluateDependents(marker.id, lease))
        }
    }

    /** Links a Pesada to a hand-recorded Jornada as a pre-CR-010 device did, and optionally rewrites its kilos. */
    private suspend fun legacyLink(phone: Phone, manual: UUID, pesada: SyncOutboxEntity, kilos: Long? = null) {
        phone.db.deliveryDao().findById(pesada.entityId)!!.let { phone.db.deliveryDao().upsert(it.copy(harvestId = manual)) }
        if (kilos != null) phone.db.harvestDao().findById(manual)!!.let { phone.db.harvestDao().upsert(it.copy(weightGrams = kilos)) }
    }

    @Test
    fun aManualJornadaWhoseKilosMayHaveBeenRewrittenIsRetainedForReviewWithZeroRpc() = runBlocking<Unit> {
        val phone = Phone("hv-j-review")
        val server = FakeServer()
        lease().use { lease ->
            // Kilos equal to its linked Pesadas and nothing confirmed to compare: ambiguous, retained.
            val ambiguous = manualDay(phone, split = false)
            legacyLink(phone, ambiguous, phone.pesada(net = 2_800_000), kilos = 2_800_000)
            val m1 = phone.unboundMarker(ambiguous)
            val day1 = phone.db.harvestDao().findById(ambiguous)
            assertEquals(HarvestSendOutcome.MANUAL_KILOS_REVIEW, phone.sender(server).stage(m1.id))
            val held = phone.db.syncOutboxDao().findById(m1.id)!!
            assertEquals(OutboxStatus.BLOCKED, held.status); assertEquals(HarvestCodes.MANUAL_KILOS_REVIEW, held.lastErrorCode)
            assertEquals(day1, phone.db.harvestDao().findById(ambiguous))
            assertTrue("Cero RPC", server.names.none { it == "sync_own_harvest_v1" })
            // Typed kilos different from the linked Pesadas: not suspect, sent as usual.
            val typed = manualDay(phone, split = false)
            legacyLink(phone, typed, phone.pesada(net = 1_000_000))
            assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(phone.unboundMarker(typed).id))
            // A value confirmed earlier and still equal: not suspect either.
            val confirmed = manualDay(phone, split = false)
            phone.db.harvestDao().findById(confirmed)!!.let { phone.db.harvestDao().upsert(it.copy(weightGrams = 3_000_000)) }
            val first = phone.unboundMarker(confirmed)
            assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(first.id))
            assertEquals(HarvestSendOutcome.SYNCED, phone.sender(server).send(first.id, lease))
            legacyLink(phone, confirmed, phone.pesada(net = 3_000_000))
            phone.db.enqueueCollapsed(RandomIds, SyncEntityType.HARVEST, confirmed, OutboxOperation.UPDATE, now)
            assertEquals(HarvestSendOutcome.RESOLVED_NOOP, phone.sender(server).stage(phone.unboundMarker(confirmed).id))
            // An operation frozen before the value became suspect is retained by send, before any request.
            val late = manualDay(phone, split = false)
            val lateMarker = phone.unboundMarker(late)
            assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(lateMarker.id))
            legacyLink(phone, late, phone.pesada(net = 4_200_000))
            val calls = server.names.size
            assertEquals(HarvestSendOutcome.MANUAL_KILOS_REVIEW, phone.sender(server).send(lateMarker.id, lease))
            assertEquals(calls, server.names.size)
            assertEquals(HarvestCodes.MANUAL_KILOS_REVIEW, phone.db.syncOutboxDao().findById(lateMarker.id)!!.lastErrorCode)
        }
    }

    @Test
    fun reviewOwnerConsentAndParentsAreRespected() = runBlocking<Unit> {
        val server = FakeServer().apply { reviewNext = "FUTURE_DATE" }
        val phone = Phone("hv-review")
        val pesada = phone.pesada()
        val marker = phone.unboundMarker(phone.jornadaOf(pesada).id)
        assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(marker.id))
        lease().use { lease ->
            owner = false
            assertEquals(HarvestSendOutcome.NOT_AUTHORIZED, phone.sender(server).send(marker.id, lease))
            assertTrue("Sin propietario no se escribe", server.names.isEmpty())
            assertEquals(OutboxStatus.PENDING, phone.db.syncOutboxDao().findById(marker.id)!!.status)
            owner = true
            assertEquals(HarvestSendOutcome.REVIEW_HELD, phone.sender(server).send(marker.id, lease))
            assertEquals(HarvestCodes.REVIEW_PREFIX + "FUTURE_DATE", phone.db.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
        }
        val unconfirmed = Phone("hv-parents", parentsConfirmed = false)
        val p2 = unconfirmed.pesada()
        assertEquals(HarvestSendOutcome.PARENT_NOT_SYNCED, unconfirmed.sender(server).stage(unconfirmed.unboundMarker(unconfirmed.jornadaOf(p2).id).id))
        val revoked = Phone("hv-revoked")
        val p3 = revoked.pesada()
        revoked.links.revokeConsentForFarm(farmId)
        val m3 = revoked.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, revoked.jornadaOf(p3).id).single()
        // Without consent for the farm nothing is attributed or staged; the marker stays as it was.
        assertEquals(HarvestSendOutcome.CONSENT_REQUIRED, revoked.sender(server).stage(m3.id))
        val untouched = revoked.db.syncOutboxDao().findById(m3.id)!!
        assertNull(untouched.payloadJson); assertNull(untouched.accountId)
    }

    /** R1-b: a hand-recorded day; [origin] null is a legacy day without origin, also sent as MANUAL. */
    private suspend fun manualDay(phone: Phone, split: Boolean, origin: String? = "MANUAL"): UUID {
        val id = UUID.randomUUID()
        phone.db.harvestDao().upsert(HarvestEntity(id = id, workspaceId = workspaceId, campaignId = campaignId, farmId = farmId,
            harvestDate = day, weightGrams = 4_200_000, metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING), dayOrigin = origin))
        if (split) addSplit(phone, id)
        phone.db.enqueueCollapsed(RandomIds, SyncEntityType.HARVEST, id, OutboxOperation.CREATE, now)
        return id
    }

    private suspend fun addSplit(phone: Phone, id: UUID) = phone.db.harvestDao().upsertParcels(listOf(
        HarvestParcelEntity(UUID.randomUUID(), workspaceId, id, north, null, "Norte", 4_200_000, "EXACT", LocalMetadata(now, now))))

    /** Everything R1-b must keep: the day, its split, every outbox operation of it. */
    private suspend fun keep(phone: Phone, id: UUID): List<Any?> = listOf(phone.db.harvestDao().findById(id),
        phone.db.harvestDao().listParcels(id), phone.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, id))

    @Test
    fun aManualJornadaWithAParcelSplitIsRetainedWithoutAnyRpcAcrossSessionsAndRestarts() = runBlocking<Unit> {
        val phone = Phone("hv-r1b-stage")
        val server = FakeServer()
        for (origin in listOf("MANUAL", null)) {
            val id = manualDay(phone, split = true, origin = origin)
            val marker = phone.unboundMarker(id)
            val dayBefore = phone.db.harvestDao().findById(id)
            val splitBefore = phone.db.harvestDao().listParcels(id)
            assertEquals(HarvestSendOutcome.MANUAL_PARCELS_UNSUPPORTED, phone.sender(server).stage(marker.id))
            val held = phone.db.syncOutboxDao().findById(marker.id)!!
            assertEquals(marker.id, held.id)
            assertEquals(OutboxStatus.BLOCKED, held.status)
            assertEquals(HarvestCodes.MANUAL_PARCELS_UNSUPPORTED, held.lastErrorCode)
            assertNull("Nunca se congela un sobre incompleto", held.payloadJson)
            assertEquals(0, held.attemptCount)
            assertEquals(dayBefore, phone.db.harvestDao().findById(id))
            assertEquals(splitBefore, phone.db.harvestDao().listParcels(id))
            // A later edit of the same day: a new marker, retained too; the first one is never deleted.
            phone.db.enqueueCollapsed(RandomIds, SyncEntityType.HARVEST, id, OutboxOperation.UPDATE, now)
            val second = phone.unboundMarker(id)
            assertEquals(HarvestSendOutcome.MANUAL_PARCELS_UNSUPPORTED, phone.sender(server).stage(second.id))
            assertEquals(2, phone.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, id).size)
            assertEquals(held, phone.db.syncOutboxDao().findById(marker.id))
        }
        assertTrue("Cero RPC", server.names.isEmpty())

        // Another session of another account is refused BEFORE the barrier: it never touches the marker.
        val id = manualDay(phone, split = true)
        val marker = phone.unboundMarker(id)
        val before = keep(phone, id)
        val stranger = AccountRepository(object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false },
            MemoryAccountSessionStore())
        stranger.login("otra@example.test", "password42", true)
        assertTrue(stranger.state.value.userId != phone.userId)
        val foreign = HarvestSendCoordinator(phone.db, stranger, ISSUER, server, { phone.links.ledgerFor(it) },
            WorkspaceOwnerVerifier { _, ws -> ws == workspaceId }, RandomIds) { now }
        assertEquals(HarvestSendOutcome.NOT_AUTHORIZED, foreign.stage(marker.id))
        assertEquals(before, keep(phone, id))

        // Restart, then a new login of the same account: still retained, still zero RPC.
        phone.db.close()
        val reopened = MaginaOlivoDatabase.create(context, phone.name).also { opened += it to phone.name }
        phone.account.logout(false)
        phone.account.login("owner@example.test", "password42", true)
        assertEquals(phone.userId, phone.account.state.value.userId)
        val links = LocalFarmLinkRepository(context, reopened, phone.account, ISSUER, AndroidFarmLinkLedger(phone.ledger))
        val restarted = HarvestSendCoordinator(reopened, phone.account, ISSUER, server, { links.ledgerFor(it) },
            WorkspaceOwnerVerifier { _, ws -> ws == workspaceId }, RandomIds) { now }
        assertEquals(HarvestSendOutcome.MANUAL_PARCELS_UNSUPPORTED, restarted.stage(marker.id))
        assertEquals(HarvestCodes.MANUAL_PARCELS_UNSUPPORTED, reopened.syncOutboxDao().findById(marker.id)!!.lastErrorCode)
        assertEquals(before[0], reopened.harvestDao().findById(id))
        assertEquals(before[1], reopened.harvestDao().listParcels(id))
        assertTrue("Cero RPC", server.names.isEmpty())
    }

    @Test
    fun aStagedOrUnknownManualOperationIsRetainedAsItIsOnceTheDayHasASplit() = runBlocking<Unit> {
        val phone = Phone("hv-r1b-send")
        val server = FakeServer()
        lease().use { lease ->
            // Staged before the split existed (or by an older client): frozen bytes, never re-frozen.
            val staged = manualDay(phone, split = false)
            val m1 = phone.unboundMarker(staged)
            assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(m1.id))
            val frozen = phone.db.syncOutboxDao().findById(m1.id)!!
            addSplit(phone, staged)
            assertEquals(HarvestSendOutcome.MANUAL_PARCELS_UNSUPPORTED, phone.sender(server).send(m1.id, lease))
            assertTrue("Cero RPC", server.names.isEmpty())
            val held = phone.db.syncOutboxDao().findById(m1.id)!!
            assertEquals(OutboxStatus.BLOCKED, held.status)
            assertEquals(HarvestCodes.MANUAL_PARCELS_UNSUPPORTED, held.lastErrorCode)
            assertNull(held.lastErrorMessage)
            assertEquals(frozen.id, held.id); assertEquals(frozen.payloadJson, held.payloadJson)
            assertEquals(frozen.accountId, held.accountId); assertEquals(frozen.attemptCount, held.attemptCount)
            assertEquals(1, phone.db.harvestDao().listParcels(staged).size)
            // Retained means never re-sent: another send refuses without a request.
            assertEquals(HarvestSendOutcome.REJECTED, phone.sender(server).send(m1.id, lease))
            assertTrue(server.names.isEmpty())

            // UNKNOWN: the first answer was lost. Once the day has a split nothing more goes out; the
            // unknown outcome and the attempt count stay recorded, the bytes are the same.
            val unknownDay = manualDay(phone, split = false)
            val m2 = phone.unboundMarker(unknownDay)
            assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(m2.id))
            server.dropNext = true
            assertEquals(HarvestSendOutcome.OUTCOME_UNKNOWN, phone.sender(server).send(m2.id, lease))
            val unknown = phone.db.syncOutboxDao().findById(m2.id)!!
            val calls = server.names.size
            addSplit(phone, unknownDay)
            assertEquals(HarvestSendOutcome.MANUAL_PARCELS_UNSUPPORTED, phone.sender(server).send(m2.id, lease))
            assertEquals("Cero RPC", calls, server.names.size)
            val keptUnknown = phone.db.syncOutboxDao().findById(m2.id)!!
            assertEquals(OutboxStatus.BLOCKED, keptUnknown.status)
            assertEquals("previous=OUTCOME_UNKNOWN;attempts=${unknown.attemptCount}", keptUnknown.lastErrorMessage)
            assertEquals(unknown.payloadJson, keptUnknown.payloadJson); assertEquals(unknown.attemptCount, keptUnknown.attemptCount)
        }
    }

    @Test
    fun aManualWithoutSplitStillSyncsAndAnAutoDayIsNeverRetained() = runBlocking<Unit> {
        val phone = Phone("hv-r1b-allowed")
        val server = FakeServer()
        lease().use { lease ->
            val manual = manualDay(phone, split = false)
            val m = phone.unboundMarker(manual)
            assertEquals(HarvestSendOutcome.STAGED, phone.sender(server).stage(m.id))
            assertEquals(HarvestSendOutcome.SYNCED, phone.sender(server).send(m.id, lease))
            assertEquals(1L, phone.db.harvestDao().findById(manual)!!.metadata.remoteVersion)
            // An AUTO_DAY has a split derived from its Pesadas: the server derives its own, never retained.
            val pesada = phone.pesada()
            val auto = phone.jornadaOf(pesada)
            assertTrue(phone.db.harvestDao().listParcels(auto.id).isNotEmpty())
            val confirmed = confirmJornada(phone, server, pesada, lease)
            assertEquals(1L, confirmed.metadata.remoteVersion)
            assertTrue(phone.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, auto.id)
                .none { it.lastErrorCode == HarvestCodes.MANUAL_PARCELS_UNSUPPORTED })
        }
    }

    @Test
    fun aStagedJornadaSurvivesARestartAndIsSentAfterwards() = runBlocking<Unit> {
        val phone = Phone("hv-restart")
        val pesada = phone.pesada()
        val marker = phone.unboundMarker(phone.jornadaOf(pesada).id)
        assertEquals(HarvestSendOutcome.STAGED, phone.sender(FakeServer()).stage(marker.id))
        val staged = phone.db.syncOutboxDao().findById(marker.id)!!
        phone.db.close()
        val reopened = MaginaOlivoDatabase.create(context, phone.name).also { opened += it to phone.name }
        assertEquals(staged, reopened.syncOutboxDao().findById(marker.id))
        val links = LocalFarmLinkRepository(context, reopened, phone.account, ISSUER, AndroidFarmLinkLedger(phone.ledger))
        val sender = HarvestSendCoordinator(reopened, phone.account, ISSUER, FakeServer(), { links.ledgerFor(it) },
            WorkspaceOwnerVerifier { _, ws -> ws == workspaceId }, RandomIds) { now }
        lease().use { assertEquals(HarvestSendOutcome.SYNCED, sender.send(marker.id, it)) }
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
