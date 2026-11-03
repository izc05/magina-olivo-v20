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
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.buildDeliveryPayloadJson
import com.isivoltpro.maginaolivo.data.repository.buildDeliveryPayloadJsonV3
import com.isivoltpro.maginaolivo.data.repository.deliveryOriginCreatedAt
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestReceiveVerifier
import com.isivoltpro.maginaolivo.data.repository.sync.ReceiveCauses
import com.isivoltpro.maginaolivo.data.repository.sync.ReceiveHoldCauses
import com.isivoltpro.maginaolivo.data.repository.sync.deliveryDeleteHash
import com.isivoltpro.maginaolivo.data.repository.sync.harvestOwnFingerprint
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.harvest.AutoDaySplit
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #808 R3-c, design #834 A1-A13, on Room (API 35) against an in-memory server that follows the R2 contract with its exact
 * key sets. Heads and fingerprints are produced with the functions R3-b verified against REAL server bytes, so the
 * receiver's R3-b verification runs unchanged. Proves all-or-nothing per Jornada, zero outbox by reception, protection of
 * every open local state, every hold cause, AUTO_DAY derivation without outbox, MANUAL typed kilos, D10 duplicates, account
 * isolation and safe recovery. Not wired in shipped code.
 */
@RunWith(AndroidJUnit4::class)
class HarvestReceiveRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000008f1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000008f1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000008f1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000008f1")
    private val south = UUID.fromString("30000000-0000-0000-0000-0000000008f2")
    private val now = Instant.parse("2026-12-02T08:00:00.123Z")
    private val d1 = LocalDate.parse("2026-11-18")
    private val d2 = LocalDate.parse("2026-11-19")
    private val d3 = LocalDate.parse("2026-11-20")
    private val name = "g808-r3c-${UUID.randomUUID()}"
    private val ledgerFile = File(context.noBackupFilesDir, "g808-r3c-${UUID.randomUUID()}.json")
    private var owner = true
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private lateinit var links: LocalFarmLinkRepository
    private lateinit var userId: UUID

    @After fun after() {
        runCatching { db.close() }; context.deleteDatabase(name)
        listOf(ledgerFile, File(ledgerFile.path + ".bak"), File(ledgerFile.path + ".new")).forEach { it.delete() }
    }

    private fun setup(openDb: Boolean = true) = runBlocking {
        if (openDb) db = MaginaOlivoDatabase.create(context, name)
        val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
        account = AccountRepository(api, MemoryAccountSessionStore())
        account.login("owner@example.test", "password42", true)
        userId = account.state.value.userId!!
        links = LocalFarmLinkRepository(context, db, account, ISSUER, AndroidFarmLinkLedger(ledgerFile), WorkspaceOwnerVerifier { _, ws -> owner && ws == workspaceId })
        val meta = LocalMetadata(now, now, syncStatus = SyncStatus.SYNCED, remoteVersion = 1L)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", userId, "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
        for ((id, label) in listOf(north to "Norte", south to "Sur")) db.parcelDao().upsert(ParcelEntity(id, workspaceId, label, source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta))
        db.campaignDao().upsertSnapshots(listOf(
            CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, north, farmId, "La Solana", "Norte", metadata = meta),
            CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, south, farmId, "La Solana", "Sur", metadata = meta)))
        links.consent(userId, links.choices().filter { it.id == farmId }, true)
    }

    private fun receiver(server: FakeR2, issuer: String = ISSUER, accountRepo: AccountRepository = account) =
        HarvestReceiveCoordinator(db, accountRepo, issuer, server, { links.ledgerFor(it) }, WorkspaceOwnerVerifier { _, ws -> owner && ws == workspaceId },
            RandomIds) { now }

    // -------------------------------------------------------------------------------------------- the fake R2 server

    class SParcel(val id: UUID, val parcelId: UUID, val weight: Long?, val mode: String, val name: String)
    class SPesada(val id: UUID, var harvestId: UUID?, val date: LocalDate, var net: Long, var parcels: List<SParcel>, var version: Long = 1,
        var deletedAt: Instant? = null, var recordedBy: UUID? = null, val origin: Instant = Instant.parse("2026-11-18T07:00:01.250Z"),
        var headOverride: ((JSONObject?) -> JSONObject?)? = null)
    class SHarvest(val id: UUID, val date: LocalDate, val dayOrigin: String, var weight: Long = 0, var notes: String? = null, var version: Long = 1,
        var deletedAt: Instant? = null, var recordedBy: UUID? = null, var headOp: UUID? = UUID.randomUUID(), var ownOverride: String? = null)

    internal inner class FakeR2 : DeliveryRpcTransport {
        val harvests = mutableListOf<SHarvest>()
        val pesadas = mutableListOf<SPesada>()
        val names = mutableListOf<String>()
        var failOn: ((String, Int) -> Boolean)? = null
        var manifestLimit = Int.MAX_VALUE
        var issuerOverride: String? = null
        private var calls = 0
        fun bundles() = names.count { it == "get_own_harvest_bundle_v1" }

        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            names += name; calls++
            if (failOn?.invoke(name, calls) == true) throw java.io.IOException("cut")
            val body = JSONObject(bodyJson)
            return when (name) {
                "get_own_harvest_manifest_v1" -> manifest(body.optStringOrNull("p_from")?.let(LocalDate::parse), body.optStringOrNull("p_to")?.let(LocalDate::parse))
                "get_own_harvest_bundle_v1" -> DeliveryRpcResponse(200, bundle(harvests.single { it.id.toString() == body.getString("p_harvest_id") }).toString())
                else -> error("RPC no esperada: $name")
            }
        }

        private fun JSONObject.optStringOrNull(key: String) = if (isNull(key)) null else getString(key)
        private fun inRange(d: LocalDate, from: LocalDate?, to: LocalDate?) = (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to))
        private fun partitionDate(p: SPesada) = p.harvestId?.let { h -> harvests.single { it.id == h }.date } ?: p.date

        private fun manifest(from: LocalDate?, to: LocalDate?): DeliveryRpcResponse {
            val hs = harvests.filter { inRange(it.date, from, to) }.sortedBy { it.id.toString() }
            val ps = pesadas.filter { inRange(partitionDate(it), from, to) }.sortedBy { it.id.toString() }
            if (hs.size > manifestLimit) return DeliveryRpcResponse(400,
                """{"code":"54000","details":"harvests=${hs.size}","hint":null,"message":"sync limit: MANIFEST_HARVESTS"}""")
            return DeliveryRpcResponse(200, JSONObject().put("manifest_contract_version", 1).put("issuer", issuerOverride ?: ISSUER)
                .put("account_id", userId.toString()).put("workspace_id", "$workspaceId").put("farm_id", "$farmId").put("campaign_id", "$campaignId")
                .put("from", from?.toString() ?: JSONObject.NULL).put("to", to?.toString() ?: JSONObject.NULL)
                .put("harvests", JSONArray(hs.map { JSONObject().put("id", "${it.id}").put("version", it.version).put("deleted", it.deletedAt != null)
                    .put("day_origin", it.dayOrigin).put("harvest_date", "${it.date}").put("recorded_by_self", (it.recordedBy ?: userId) == userId) }))
                .put("deliveries", JSONArray(ps.map { JSONObject().put("id", "${it.id}").put("harvest_id", it.harvestId?.toString() ?: JSONObject.NULL)
                    .put("version", it.version).put("deleted", it.deletedAt != null).put("partition_date", "${partitionDate(it)}")
                    .put("recorded_by_self", (it.recordedBy ?: userId) == userId) })).toString())
        }

        fun entity(p: SPesada) = DeliveryEntity(id = p.id, workspaceId = workspaceId, farmId = farmId, campaignId = campaignId, deliveryDate = p.date,
            destinationName = "Coop", netGrams = p.net, source = "MANUAL", harvestId = p.harvestId, origin = "TREE",
            metadata = LocalMetadata(p.origin, p.origin, p.deletedAt, p.version))
        fun parcelEntities(p: SPesada) = p.parcels.map { DeliveryParcelEntity(id = it.id, workspaceId = workspaceId, deliveryId = p.id,
            parcelId = it.parcelId, parcelNameAtDelivery = it.name, weightGrams = it.weight, allocationMode = it.mode, metadata = LocalMetadata(p.origin, p.origin)) }

        /** Head of the ONE applied receipt at the row's version, as the R1-a/R2 server records it. */
        private fun head(p: SPesada): JSONObject? {
            val (payloadVersion, hash) = when {
                p.deletedAt != null -> 3 to deliveryDeleteHash(p.id, p.version - 1)
                p.version == 1L -> 3 to deliveryPayloadHash(buildDeliveryPayloadJsonV3(entity(p), parcelEntities(p)), 3)!!
                else -> 2 to deliveryPayloadHash(buildDeliveryPayloadJson(entity(p), p.version - 1, parcelEntities(p)), 2)!!
            }
            val head = JSONObject().put("operation_id", "${UUID.nameUUIDFromBytes("${p.id}:${p.version}".toByteArray())}").put("version", p.version)
                .put("payload_version", payloadVersion).put("payload_hash", hash).put("kind", if (p.deletedAt == null) "WRITE" else "DELETE")
            return p.headOverride?.invoke(head) ?: head.takeUnless { p.headOverride != null }
        }

        private fun ts(i: Instant) = i.toString()

        private fun bundle(h: SHarvest): JSONObject {
            val linked = pesadas.filter { it.harvestId == h.id }.sortedBy { it.id.toString() }
            val live = linked.filter { it.deletedAt == null }
            val auto = h.dayOrigin == "AUTO_DAY"
            val split = if (auto && h.deletedAt == null) AutoDaySplit.derive(live.flatMap { p -> p.parcels.map {
                AutoDaySplit.Share(it.parcelId, HarvestAllocation.valueOf(it.mode), it.weight) } }) else emptyList()
            val weight = if (auto) (if (h.deletedAt == null) live.sumOf { it.net } else 0L) else h.weight
            val entity = HarvestEntity(id = h.id, workspaceId = workspaceId, campaignId = campaignId, farmId = farmId, harvestDate = h.date,
                weightGrams = weight, notes = h.notes, metadata = LocalMetadata(now, now, h.deletedAt, h.version), dayOrigin = h.dayOrigin)
            val derived = if (auto) HarvestReceiveVerifier.derivedFingerprint(HarvestReceiveVerifier.Derived(weight,
                split.map { HarvestReceiveVerifier.DerivedParcel(it.parcelId, it.weightGrams, it.allocation.name) })) else null
            val row = JSONObject().put("id", "${h.id}").put("workspace_id", "$workspaceId").put("farm_id", "$farmId").put("campaign_id", "$campaignId")
                .put("harvest_date", "${h.date}").put("weight_grams", weight).put("destination", JSONObject.NULL).put("notes", h.notes ?: JSONObject.NULL)
                .put("collection_method", JSONObject.NULL).put("worker_count", JSONObject.NULL).put("machinery_text", JSONObject.NULL)
                .put("day_origin", h.dayOrigin).put("recorded_by_user_id", "${h.recordedBy ?: userId}").put("created_at", ts(now)).put("updated_at", ts(now))
                .put("deleted_at", h.deletedAt?.let(::ts) ?: JSONObject.NULL).put("version", h.version)
            val head = JSONObject().put("contract_version", 2).put("fingerprint_version", 1).put("harvest_id", "${h.id}").put("version", h.version)
                .put("day_origin", h.dayOrigin).put("deleted", h.deletedAt != null).put("weight_grams", weight)
                .put("head_operation_id", h.headOp?.toString() ?: JSONObject.NULL).put("head_payload_version", if (h.headOp == null) JSONObject.NULL else 1)
                .put("head_payload_hash", if (h.headOp == null) JSONObject.NULL else "e".repeat(64))
                .put("own_fingerprint", h.ownOverride ?: harvestOwnFingerprint(entity)).put("derived_fingerprint", derived ?: JSONObject.NULL)
            return JSONObject().put("bundle_contract_version", 1).put("issuer", issuerOverride ?: ISSUER).put("account_id", "$userId").put("harvest", row)
                .put("harvest_head", head).put("server_weight_grams", weight)
                .put("server_harvest_parcels", JSONArray(split.map { JSONObject().put("parcel_id", "${it.parcelId}")
                    .put("weight_grams", it.weightGrams ?: JSONObject.NULL).put("allocation_mode", it.allocation.name) }))
                .put("deliveries", JSONArray(linked.map { p ->
                    JSONObject().put("delivery", JSONObject().put("id", "${p.id}").put("workspace_id", "$workspaceId").put("farm_id", "$farmId")
                        .put("campaign_id", "$campaignId").put("delivery_date", "${p.date}").put("destination_organization_id", JSONObject.NULL)
                        .put("destination_name", "Coop").put("net_grams", p.net).put("gross_grams", JSONObject.NULL).put("tare_grams", JSONObject.NULL)
                        .put("delivery_number", JSONObject.NULL).put("ticket_number", JSONObject.NULL).put("source", "MANUAL").put("notes", JSONObject.NULL)
                        .put("harvest_id", p.harvestId?.toString() ?: JSONObject.NULL).put("delivery_time", JSONObject.NULL).put("harvest_origin", "TREE")
                        .put("recorded_by_user_id", "${p.recordedBy ?: userId}").put("created_at", ts(p.origin)).put("updated_at", ts(p.origin))
                        .put("deleted_at", p.deletedAt?.let(::ts) ?: JSONObject.NULL).put("version", p.version).put("snapshot_format", 1))
                        .put("parcels", JSONArray(p.parcels.sortedBy { it.parcelId.toString() }.map { s -> JSONObject().put("id", "${s.id}")
                            .put("workspace_id", "$workspaceId").put("delivery_id", "${p.id}").put("parcel_id", "${s.parcelId}").put("campaign_parcel_id", JSONObject.NULL)
                            .put("parcel_name_at_delivery", s.name).put("weight_grams", s.weight ?: JSONObject.NULL).put("allocation_mode", s.mode)
                            .put("origin_version", JSONObject.NULL).put("origin_created_at", JSONObject.NULL).put("origin_updated_at", JSONObject.NULL)
                            .put("origin_evidence_sha256", JSONObject.NULL) }))
                        .put("origin_created_at_exact", deliveryOriginCreatedAt(p.origin)).put("recorded_by_self", (p.recordedBy ?: userId) == userId)
                        .put("head", head(p) ?: JSONObject.NULL)
                }))
        }
    }

    private fun exact(parcel: UUID, w: Long, name: String = if (parcel == north) "Norte" else "Sur") = SParcel(UUID.randomUUID(), parcel, w, "EXACT", name)
    private fun loose(parcel: UUID) = SParcel(UUID.randomUUID(), parcel, null, "UNALLOCATED", if (parcel == north) "Norte" else "Sur")

    /** S1-like server: 2 AUTO_DAY + 1 MANUAL, 7 Pesadas (v3 creates, a v2 edit, an R1-a delete). */
    private fun standard(): FakeR2 = FakeR2().apply {
        val a = SHarvest(UUID.randomUUID(), d1, "AUTO_DAY"); val b = SHarvest(UUID.randomUUID(), d2, "AUTO_DAY")
        val m = SHarvest(UUID.randomUUID(), d3, "MANUAL", weight = 4_242_000)
        harvests += listOf(a, b, m)
        pesadas += SPesada(UUID.randomUUID(), a.id, d1, 1_000_000, listOf(exact(north, 600_000), exact(south, 400_000)))
        pesadas += SPesada(UUID.randomUUID(), a.id, d1, 500_000, listOf(exact(north, 500_000)), version = 2)
        pesadas += SPesada(UUID.randomUUID(), a.id, d1, 300_000, listOf(loose(south)), version = 2, deletedAt = now)
        pesadas += SPesada(UUID.randomUUID(), b.id, d2, 700_000, listOf(exact(north, 700_000)))
        pesadas += SPesada(UUID.randomUUID(), b.id, d2, 200_000, listOf(loose(south)))
        pesadas += SPesada(UUID.randomUUID(), m.id, d3, 999_000, listOf(exact(north, 999_000)))
        pesadas += SPesada(UUID.randomUUID(), m.id, d3, 1_000, listOf(exact(south, 1_000)))
    }

    // ------------------------------------------------------------------------------------------------------ helpers

    private fun dump(vararg tables: String) = tables.joinToString("\n") { t ->
        db.openHelper.writableDatabase.query("SELECT * FROM $t ORDER BY rowid").use { c ->
            "$t:" + buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { if (c.isNull(it)) "∅" else c.getString(it) }) }.joinToString(";") }
    }
    private val data = arrayOf("harvests", "harvest_parcels", "deliveries", "delivery_parcels", "sync_outbox", "sync_receive_receipts", "delivery_dispatch_prepare")
    private fun outboxCount() = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }
    private suspend fun holds() = db.syncReceiveHoldDao().listOpen(ISSUER, userId.toString())
    private fun receive(server: FakeR2) = runBlocking { receiver(server).receive(workspaceId, farmId, campaignId) }

    private val deliveries get() = OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers,
        workspaceRepository = object : WorkspaceRepository { override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId) },
        writeScope = links) { ZoneOffset.UTC }

    // ------------------------------------------------------------------------------------------------------- tests

    @Test fun a1CleanReceiveUsesRemoteIdsVersionsAndCreatesNoOutbox() = runBlocking<Unit> {
        setup(); val server = standard()
        val report = receive(server)
        assertEquals(HarvestReceiveOutcome.IMPORTED, report.outcome)
        assertEquals(3, report.applied.size); assertTrue(report.held.isEmpty())
        assertEquals(0, outboxCount())
        for (p in server.pesadas) {
            val local = db.deliveryDao().findById(p.id)!!
            assertEquals(listOf(p.version, p.version), listOf(local.metadata.version, local.metadata.remoteVersion))
            assertEquals(SyncStatus.SYNCED, local.metadata.syncStatus); assertEquals(p.harvestId, local.harvestId)
            assertEquals(p.deletedAt != null, local.metadata.deletedAt != null)
            assertEquals(p.parcels.map { it.id }.toSet(), db.deliveryDao().listParcels(p.id).map { it.id }.toSet())
        }
        val (a, b, m) = server.harvests
        // AUTO_DAY derived locally (never imported) and equal to the server rule; MANUAL keeps its typed kilos (not the sum).
        assertEquals(1_500_000L, db.harvestDao().findById(a.id)!!.weightGrams)
        assertEquals(listOf(Triple(north, 1_100_000L, "EXACT"), Triple(south, 400_000L, "EXACT")),
            db.harvestDao().listParcels(a.id).sortedBy { it.parcelId.toString() }.map { Triple(it.parcelId, it.weightGrams, it.allocationMode) })
        assertEquals(900_000L, db.harvestDao().findById(b.id)!!.weightGrams)
        assertEquals(4_242_000L, db.harvestDao().findById(m.id)!!.weightGrams)
        assertEquals(10, db.syncReceiveReceiptDao().count())   // 3 Jornadas + 7 Pesadas
    }

    @Test fun a2ARepeatedReceiveWritesNothing() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val before = dump(*data); val holdsBefore = dump("sync_receive_holds"); val bundles = server.bundles()
        val again = receive(server)
        assertEquals(HarvestReceiveOutcome.ALREADY_CURRENT, again.outcome)
        assertEquals(bundles, server.bundles())
        assertEquals(before, dump(*data)); assertEquals(holdsBefore, dump("sync_receive_holds"))
    }

    @Test fun a3AndA4AnInterruptedReadWritesNothingAndARestartReceivesTheSame() = runBlocking<Unit> {
        setup(); val server = standard()
        val empty = dump(*data)
        // Airplane mode at the manifest, then at the second bundle: OFFLINE, nothing written (reads precede the commit).
        server.failOn = { name, _ -> name == "get_own_harvest_manifest_v1" }
        assertEquals(HarvestReceiveOutcome.OFFLINE, receive(server).outcome)
        var bundles = 0
        server.failOn = { name, _ -> name == "get_own_harvest_bundle_v1" && ++bundles == 2 }
        assertEquals(HarvestReceiveOutcome.OFFLINE, receive(server).outcome)
        assertEquals(empty, dump(*data)); assertTrue(holds().isEmpty())
        // The process dies and restarts: a new database handle and account; the same call receives everything once.
        db.close(); db = MaginaOlivoDatabase.create(context, name); setup(openDb = false)
        server.failOn = null
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        assertEquals(10, db.syncReceiveReceiptDao().count()); assertEquals(0, outboxCount())
    }

    @Test fun a5AndA6IdentityOwnerIssuerConsentAndAnotherAccount() = runBlocking<Unit> {
        setup(); val server = standard(); val empty = dump(*data)
        owner = false
        assertEquals(HarvestReceiveOutcome.NOT_AUTHORIZED, receive(server).outcome)
        owner = true
        assertTrue("Sin lecturas de datos", server.names.isEmpty())
        // A response for another issuer: out of contract, nothing written.
        server.issuerOverride = "https://otro.example/auth/v1"
        assertEquals(HarvestReceiveOutcome.REJECTED, receive(server).outcome)
        server.issuerOverride = null
        // Another account on the device: not the owner of this workspace, zero RPC, nothing visible or written.
        val stranger = AccountRepository(object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }, MemoryAccountSessionStore())
        stranger.login("otra@example.test", "password42", true)
        val calls = server.names.size
        assertEquals(HarvestReceiveOutcome.NOT_AUTHORIZED, receiver(server, accountRepo = stranger).receive(workspaceId, farmId, campaignId).outcome)
        assertEquals(calls, server.names.size)
        // A foreign issuer configured on the device, or the farm consent revoked: refused before any request.
        assertEquals(HarvestReceiveOutcome.CONSENT_REQUIRED, receiver(server, issuer = "https://otro.example/auth/v1").receive(workspaceId, farmId, campaignId).outcome)
        links.revokeConsentForFarm(farmId)
        assertEquals(HarvestReceiveOutcome.CONSENT_REQUIRED, receive(server).outcome)
        assertEquals(calls, server.names.size)
        assertEquals(empty, dump(*data))
    }

    @Test fun a7EveryOpenLocalStateIsProtectedAndTheJornadaIsNotApplied() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (a) = server.harvests
        val pesada = server.pesadas.first { it.harvestId == a.id && it.deletedAt == null }
        // The server moves on (a newer version of one Pesada) while the device has that Pesada in each open state.
        pesada.version = 2; pesada.net = 1_050_000; pesada.parcels = listOf(exact(north, 650_000), exact(south, 400_000))
        for ((status, code) in listOf("PENDING" to null, "PROCESSING" to "lease:1", "FAILED" to "OUTCOME_UNKNOWN", "BLOCKED" to "V3_ACK_AWAITING_HEAD",
            "BLOCKED" to "SYNC_REVIEW:X", "BLOCKED" to "NEEDS_REBASE")) {
            val op = UUID.randomUUID()
            db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,created_at,updated_at) VALUES (?, 'DELIVERY', ?, 'UPDATE', 2, ?, 0, ?, 1000, 1000)",
                arrayOf<Any?>(op.toString(), pesada.id.toString(), status, code))
            val before = dump(*data)
            val report = receive(server)
            assertEquals("$status/$code", HarvestReceiveOutcome.HELD, report.outcome)
            assertTrue(report.held.any { it.entityId == pesada.id && it.cause == ReceiveCauses.REMOTE_AHEAD })
            assertEquals(before, dump(*data))
            db.openHelper.writableDatabase.execSQL("DELETE FROM sync_outbox WHERE id = ?", arrayOf<Any>(op.toString()))
        }
        // A local CONFLICT status is protected too.
        db.deliveryDao().findById(pesada.id)!!.let { db.deliveryDao().upsert(it.copy(metadata = it.metadata.copy(syncStatus = SyncStatus.CONFLICT))) }
        assertEquals(HarvestReceiveOutcome.HELD, receive(server).outcome)
    }

    @Test fun a8UnverifiedPesadasOrJornadasAreHeldAndNothingIsWritten() = runBlocking<Unit> {
        setup(); val server = standard(); val empty = dump(*data)
        val (a, b, m) = server.harvests
        server.pesadas.first { it.harvestId == a.id }.headOverride = { null }                                       // no head (out of protocol)
        server.pesadas.first { it.harvestId == b.id }.headOverride = { it!!.put("payload_hash", "0".repeat(64)) }     // tampered hash
        m.ownOverride = "1".repeat(64)                                                                                // own fingerprint altered
        val report = receive(server)
        assertEquals(HarvestReceiveOutcome.HELD, report.outcome)
        assertEquals(setOf(a.id, b.id, m.id), report.held.filter { it.cause == ReceiveHoldCauses.UNVERIFIED }.map { it.entityId }.toSet())
        assertEquals(empty, dump(*data))
    }

    @Test fun a9ADerivedDayThatWouldDivergeIsRolledBackEntirely() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (a) = server.harvests
        // A local Pesada linked to the day that the server does not have (an old row, no outbox).
        val extra = db.deliveryDao().findById(server.pesadas.first { it.harvestId == a.id && it.deletedAt == null }.id)!!.copy(id = UUID.randomUUID())
        db.deliveryDao().upsert(extra)
        val changed = server.pesadas.first { it.harvestId == a.id && it.version == 1L }
        changed.version = 2; changed.net = 1_100_000; changed.parcels = listOf(exact(north, 700_000), exact(south, 400_000))
        val before = dump(*data)
        val report = receive(server)
        assertTrue("${report.outcome} ${report.held.map { it.cause + ":" + it.entityId + ":" + it.detail }}", report.held.any { it.entityId == a.id && it.cause == ReceiveHoldCauses.WEIGHT_DIVERGENT })
        // The Pesada update had been written inside the transaction and was rolled back with it: nothing partial remains.
        assertEquals(before, dump(*data))
        assertEquals(1L, db.deliveryDao().findById(changed.id)!!.metadata.remoteVersion)
    }

    @Test fun a10EveryOtherHoldCauseRetainsWithoutLosingAnything() = runBlocking<Unit> {
        setup(); val server = standard()
        val (a, b, m) = server.harvests
        // FOREIGN_RECORDER, NO_JORNADA, PARENT_MISSING (an unknown Parcel).
        server.pesadas.first { it.harvestId == b.id }.recordedBy = UUID.randomUUID()
        val loose = SPesada(UUID.randomUUID(), null, d2, 50_000, listOf(exact(north, 50_000))); server.pesadas += loose
        server.pesadas.first { it.harvestId == m.id }.parcels = listOf(exact(UUID.randomUUID(), 999_000, "Desconocida"))
        val first = receive(server)
        assertEquals(setOf(a.id), first.applied.toSet())
        val causes = first.held.map { it.cause to it.entityId }.toSet()
        assertTrue(causes.contains(ReceiveHoldCauses.FOREIGN_RECORDER to b.id))
        assertTrue(causes.contains(ReceiveCauses.NO_JORNADA to loose.id)); assertNull(db.deliveryDao().findById(loose.id))
        assertTrue(causes.contains(ReceiveCauses.PARENT_MISSING to m.id)); assertNull(db.harvestDao().findById(m.id))
        // REMOTE_REWRITTEN: the same version with other content never overwrites. (The manifest only carries versions,
        // so the bundle is read because one of its Pesadas moved on; the whole Jornada is then retained.)
        a.notes = "reescrita"
        val moved = server.pesadas.first { it.harvestId == a.id && it.version == 1L }
        moved.version = 2; moved.net += 1; moved.parcels = moved.parcels.map { if (it.parcelId == north) exact(north, it.weight!! + 1) else it }
        val before = dump(*data)
        assertTrue(receive(server).held.any { it.cause == ReceiveCauses.REMOTE_REWRITTEN && it.entityId == a.id })
        assertEquals(before, dump(*data)); a.notes = null
        // REMOTE_MISSING: a confirmed Jornada absent from the manifest is never deleted.
        server.harvests.remove(a); server.pesadas.removeAll { it.harvestId == a.id }
        val missing = receive(server).held.filter { it.cause == ReceiveCauses.REMOTE_MISSING }.map { it.entityId }.toSet()
        assertTrue(missing.contains(a.id)); assertNotNull(db.harvestDao().findById(a.id))
        // LOCAL_DEPENDENTS: a remote delete of a day that still has a live local Pesada is retained.
        server.harvests += a
        val local = db.harvestDao().findById(a.id)!!
        val keep = DeliveryEntity(id = UUID.randomUUID(), workspaceId = workspaceId, farmId = farmId, campaignId = campaignId, deliveryDate = d1,
            destinationName = "Coop", netGrams = 1, source = "MANUAL", harvestId = a.id, metadata = LocalMetadata(now, now))
        db.deliveryDao().upsert(keep)
        a.version = local.metadata.remoteVersion!! + 1; a.deletedAt = now; a.headOp = UUID.randomUUID()
        assertTrue(receive(server).held.any { it.cause == ReceiveCauses.LOCAL_DEPENDENTS && it.entityId == a.id })
        assertNull(db.harvestDao().findById(a.id)!!.metadata.deletedAt)
        // Holds are never resolved by the receiver.
        assertTrue(holds().all { it.resolvedAt == null })
    }

    @Test fun a11ATwinAutoDayIsInsertedTheLocalOneIsUntouchedAndNewPesadasGoToTheCanonical() = runBlocking<Unit> {
        setup()
        // The device recorded a Pesada offline: a local AUTO_DAY X for d1 with its PENDING operations.
        val local = (deliveries.create(DeliveryDraft(farmId = farmId, deliveryDate = d1, destinationOrganizationId = null, destinationName = "Coop",
            netGrams = 100_000, shares = listOf(DeliveryShareInput(north, 100_000)), origin = PesadaOrigin.TREE)) as AppResult.Success).value
        val x = db.deliveryDao().findById(local)!!.harvestId!!
        // With a single AUTO_DAY the choice is unchanged: the next Pesada goes to X.
        assertEquals(x, db.harvestDao().findAutoDay(farmId, campaignId, d1)!!.id)
        val xBefore = listOf(db.harvestDao().findById(x), db.harvestDao().listParcels(x), db.syncOutboxDao().listForEntity(
            com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.HARVEST, x), db.deliveryDao().findById(local))
        val server = standard()
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        val y = server.harvests[0].id
        assertNotNull(db.harvestDao().findById(y))
        assertEquals(xBefore, listOf(db.harvestDao().findById(x), db.harvestDao().listParcels(x), db.syncOutboxDao().listForEntity(
            com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.HARVEST, x), db.deliveryDao().findById(local)))
        // Two AUTO_DAY for d1: the server-linked one (Y) is preferred for new Pesadas; X and its operations stay as they were.
        assertEquals(y, db.harvestDao().findAutoDay(farmId, campaignId, d1)!!.id)
    }

    @Test fun a12RemoteDeletesOfACleanPesadaAndAnEmptyDayApplyWithoutOutbox() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (_, b) = server.harvests
        for (p in server.pesadas.filter { it.harvestId == b.id }) { p.version += 1; p.deletedAt = now }
        b.version += 1; b.deletedAt = now; b.headOp = UUID.randomUUID()
        val outbox = outboxCount()
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        assertNotNull(db.harvestDao().findById(b.id)!!.metadata.deletedAt)
        assertTrue(server.pesadas.filter { it.harvestId == b.id }.all { db.deliveryDao().findById(it.id)!!.metadata.deletedAt != null })
        assertTrue(db.harvestDao().listParcels(b.id).isEmpty())
        assertEquals(outbox, outboxCount())
    }

    @Test fun a13AnOlderServerStateNeverGoesBackAndALargeManifestIsPartitioned() = runBlocking<Unit> {
        setup(); val server = standard()
        // 54000 on the whole manifest: the receiver partitions by dates and receives the exact union.
        server.manifestLimit = 1
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        assertEquals(3, server.harvests.count { db.harvestDao().findById(it.id) != null })
        server.manifestLimit = Int.MAX_VALUE
        // An older state (version below what was received) is REMOTE_BEHIND: nothing goes back.
        val p = server.pesadas.first { it.version == 2L && it.deletedAt == null }
        val before = dump(*data)
        p.version = 1
        val report = receive(server)
        assertTrue(report.held.any { it.entityId == p.id && it.cause == ReceiveCauses.REMOTE_BEHIND })
        assertEquals(before, dump(*data))
        assertEquals(2L, db.syncReceiveReceiptDao().find(ISSUER, userId.toString(), "DELIVERY", p.id)!!.remoteVersion)
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }
    private object RandomIds : IdGenerator { override fun newId(): UUID = UUID.randomUUID() }
    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }
    private companion object { const val ISSUER = "fixture-project" }
}
