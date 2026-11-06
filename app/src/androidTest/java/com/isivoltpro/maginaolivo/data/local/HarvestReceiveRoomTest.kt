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
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryHeldReceiptEntity
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
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestReceiveReport
import com.isivoltpro.maginaolivo.data.repository.sync.ReceiveCauses
import com.isivoltpro.maginaolivo.data.repository.sync.ReceiveCursor
import com.isivoltpro.maginaolivo.data.repository.sync.ReceiveLimit
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
    class SPesada(val id: UUID, var harvestId: UUID?, var date: LocalDate, var net: Long, var parcels: List<SParcel>, var version: Long = 1,
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
        // R3-d: a Jornada dated beyond the local campaign dates is never left out of every partition.
        server.harvests += SHarvest(UUID.randomUUID(), LocalDate.parse("2031-01-01"), "MANUAL", weight = 1_000)
        server.manifestLimit = 1
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        assertEquals(4, server.harvests.count { db.harvestDao().findById(it.id) != null })
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

    // ------------------------------------------------------------------------------- R3-e-fix: a Pesada changes Jornada

    /** The derived kilos and split of [harvest] exactly as the server rule (and its bundle) computes them. */
    private fun serverDerived(server: FakeR2, harvest: UUID): Pair<Long, List<Triple<UUID, Long?, String>>> {
        val live = server.pesadas.filter { it.harvestId == harvest && it.deletedAt == null }
        return live.sumOf { it.net } to AutoDaySplit.derive(live.flatMap { p -> p.parcels.map {
            AutoDaySplit.Share(it.parcelId, HarvestAllocation.valueOf(it.mode), it.weight) } })
            .sortedBy { it.parcelId.toString() }.map { Triple(it.parcelId, it.weightGrams, it.allocation.name) }
    }
    private suspend fun localDerived(harvest: UUID): Pair<Long, List<Triple<UUID, Long?, String>>> =
        db.harvestDao().findById(harvest)!!.weightGrams!! to db.harvestDao().listParcels(harvest).sortedBy { it.parcelId.toString() }
            .map { Triple(it.parcelId, it.weightGrams, it.allocationMode) }

    /** The sender changes a Pesada's date: it moves X -> Y. On the server X keeps its version (only its derived fingerprint moves). */
    private fun moveToY(server: FakeR2): Triple<SHarvest, SHarvest, SPesada> {
        val (x, y) = server.harvests
        val p = server.pesadas.first { it.harvestId == x.id && it.deletedAt == null && it.version == 2L }
        p.harvestId = y.id; p.date = y.date; p.version += 1
        return Triple(x, y, p)
    }

    @Test fun m1APesadaMovedToAnotherJornadaLeavesBothDaysEqualToTheServer() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (x, y, p) = moveToY(server)
        assertEquals(1_500_000L, localDerived(x.id).first)
        val outbox = outboxCount()
        val report = receive(server)
        assertEquals("${report.outcome} ${report.held.map { it.cause + ":" + it.entityId }}", HarvestReceiveOutcome.IMPORTED, report.outcome)
        assertTrue(report.held.isEmpty())
        assertEquals(y.id, db.deliveryDao().findById(p.id)!!.harvestId)
        assertEquals(p.version, db.deliveryDao().findById(p.id)!!.metadata.remoteVersion)
        // Both days end with the server's kilos and split: the source no longer counts the Pesada that left it.
        assertEquals(serverDerived(server, y.id), localDerived(y.id))
        assertEquals(serverDerived(server, x.id), localDerived(x.id))
        assertEquals(1_000_000L, localDerived(x.id).first)
        assertEquals(outbox, outboxCount())
    }

    @Test fun m2APendingChangeConflictOrHeldReceiptOnTheMovedPesadaOrItsSourceAppliesNothingOfEither() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (x, y, p) = moveToY(server)
        val sql = db.openHelper.writableDatabase
        suspend fun check(label: String, cause: String, on: UUID) {
            val before = dump(*data)
            val report = receive(server)
            assertEquals(label, HarvestReceiveOutcome.HELD, report.outcome)
            assertTrue("$label ${report.held.map { it.cause + ":" + it.entityId }}", report.held.any { it.cause == cause && it.entityId == on })
            assertEquals(label, before, dump(*data))
            assertEquals(label, x.id, db.deliveryDao().findById(p.id)!!.harvestId)       // the Pesada never left X
            assertEquals(label, 1_500_000L, localDerived(x.id).first)   // X untouched
            assertEquals(label, 900_000L, localDerived(y.id).first)     // Y untouched
        }
        // A pending local change of the moved Pesada, or of its source Jornada.
        for ((type, id, cause) in listOf(Triple("DELIVERY", p.id, ReceiveCauses.REMOTE_AHEAD), Triple("HARVEST", x.id, ReceiveCauses.LOCAL_PENDING))) {
            val op = UUID.randomUUID()
            sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,created_at,updated_at) VALUES (?, ?, ?, 'UPDATE', 2, 'PENDING', 0, NULL, 1000, 1000)",
                arrayOf<Any?>(op.toString(), type, id.toString()))
            check("pending $type", cause, id)
            sql.execSQL("DELETE FROM sync_outbox WHERE id = ?", arrayOf<Any>(op.toString()))
        }
        // A local CONFLICT on the moved Pesada.
        val row = db.deliveryDao().findById(p.id)!!
        db.deliveryDao().upsert(row.copy(metadata = row.metadata.copy(syncStatus = SyncStatus.CONFLICT)))
        check("conflict", ReceiveCauses.REMOTE_AHEAD, p.id)
        db.deliveryDao().upsert(row)
        // A held (unreconciled) receipt of the moved Pesada.
        val held = UUID.randomUUID()
        db.deliveryHeldReceiptDao().insert(DeliveryHeldReceiptEntity(held, p.id, userId.toString(), farmId, 2, "a".repeat(64), 2, UUID.randomUUID(),
            receivedAt = now))
        check("held receipt", ReceiveCauses.REMOTE_AHEAD, p.id)
        sql.execSQL("DELETE FROM delivery_held_receipts WHERE operation_id = ?", arrayOf<Any>(held.toString()))
        // Once clean, the same call moves it: both days equal to the server, no outbox.
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        assertEquals(serverDerived(server, x.id), localDerived(x.id)); assertEquals(serverDerived(server, y.id), localDerived(y.id))
        assertEquals(0, outboxCount())
        assertTrue(holds().all { it.resolvedAt == null })
    }

    @Test fun m3AnInterruptionOrADivergenceBetweenXAndYWritesNeitherAndARestartConverges() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (x, y, p) = moveToY(server)
        val before = dump(*data)
        // Cut on the second bundle (X and Y are both read): nothing written.
        var bundles = 0
        server.failOn = { name, _ -> name == "get_own_harvest_bundle_v1" && ++bundles == 2 }
        assertEquals(HarvestReceiveOutcome.OFFLINE, receive(server).outcome)
        assertEquals(before, dump(*data))
        server.failOn = null
        // A failure AFTER Y's writes, while deriving X (an extra local Pesada makes X diverge): the whole group rolls back.
        val extra = db.deliveryDao().findById(server.pesadas.first { it.harvestId == x.id && it.deletedAt == null }.id)!!.copy(id = UUID.randomUUID())
        db.deliveryDao().upsert(extra)
        val withExtra = dump(*data)
        val report = receive(server)
        assertTrue("${report.held.map { it.cause + ":" + it.entityId }}", report.held.any { it.cause == ReceiveHoldCauses.WEIGHT_DIVERGENT && it.entityId == x.id })
        assertTrue(report.applied.isEmpty())
        assertEquals(withExtra, dump(*data))
        assertEquals(x.id, db.deliveryDao().findById(p.id)!!.harvestId)
        db.openHelper.writableDatabase.execSQL("DELETE FROM deliveries WHERE id = ?", arrayOf<Any>(extra.id.toString()))
        assertEquals(before, dump(*data))
        // The process dies and restarts: a new database handle; the same call converges once.
        db.close(); db = MaginaOlivoDatabase.create(context, name); setup(openDb = false)
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        assertEquals(y.id, db.deliveryDao().findById(p.id)!!.harvestId)
        assertEquals(serverDerived(server, x.id), localDerived(x.id)); assertEquals(serverDerived(server, y.id), localDerived(y.id))
        assertEquals(0, outboxCount())
    }

    // ------------------------------------------------------------------------------- R3-e-limit: receive in passes

    /** [n] MANUAL Jornadas, one Pesada each; ids from [prefix] (24 chars + 12 hex) to place them in the order, or random. */
    private fun FakeR2.bulk(n: Int, prefix: String? = null, unverified: Boolean = false): List<SHarvest> = (0 until n).map { i ->
        val id = prefix?.let { UUID.fromString(it + "%012x".format(i)) } ?: UUID.randomUUID()
        val h = SHarvest(id, d3, "MANUAL", weight = 10_000L + i, ownOverride = if (unverified) "1".repeat(64) else null)
        harvests += h; pesadas += SPesada(UUID.randomUUID(), h.id, d3, 1_000L + i, listOf(exact(north, 1_000L + i))); h
    }

    /** A whole series: passes until the end (or a non-PARTIAL outcome); never more passes than the guard. */
    private fun series(server: FakeR2, limit: ReceiveLimit, start: ReceiveCursor? = null, guard: Int = 1_000): List<HarvestReceiveReport> = runBlocking {
        val out = mutableListOf<HarvestReceiveReport>(); var cursor = start
        do {
            val r = receiver(server).receive(workspaceId, farmId, campaignId, limit, cursor); out += r; cursor = r.next
            check(out.size <= guard) { "series did not end" }
        } while (r.outcome == HarvestReceiveOutcome.PARTIAL)
        out
    }

    /** Order-independent content (rows sorted; derived parcel ids replaced by their identity) with the account normalised. */
    private fun content(): String = (data.filter { it != "harvest_parcels" }.map { "SELECT * FROM $it" } +
        "SELECT harvest_id, parcel_id, weight_grams, allocation_mode FROM harvest_parcels").joinToString("\n") { q ->
        db.openHelper.writableDatabase.query(q).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { if (c.isNull(it)) "∅" else c.getString(it) }) }
                .sorted().joinToString(";")
        }
    }.replace(userId.toString(), "U")

    private val low = "00000000-0000-0000-0000-"
    private val high = "ffffffff-ffff-ffff-ffff-"

    @Test fun l1ALargeCampaignArrivesInPassesEqualToASinglePass() = runBlocking<Unit> {
        setup(); val server = standard().apply { bulk(247) }
        val passes = series(server, ReceiveLimit(maxBundles = 100))
        assertEquals(listOf(HarvestReceiveOutcome.PARTIAL, HarvestReceiveOutcome.PARTIAL, HarvestReceiveOutcome.IMPORTED), passes.map { it.outcome })
        assertEquals(listOf(100, 100, 50), passes.map { it.bundlesRead })
        assertNull(passes.last().next); assertTrue(passes.all { it.held.isEmpty() && it.oversizedGroups == 0 })
        assertEquals(250 + 254, db.syncReceiveReceiptDao().count()); assertEquals(0, outboxCount())
        val batched = content()
        // The same server received in ONE unbounded pass on a fresh device gives exactly the same content.
        db.close(); context.deleteDatabase(name)
        listOf(ledgerFile, File(ledgerFile.path + ".bak"), File(ledgerFile.path + ".new")).forEach { it.delete() }
        setup()
        val single = receiver(server).receive(workspaceId, farmId, campaignId, ReceiveLimit.UNBOUNDED)
        assertEquals(HarvestReceiveOutcome.IMPORTED, single.outcome); assertEquals(250, single.bundlesRead); assertNull(single.next)
        assertEquals(batched, content())
    }

    @Test fun l2ACutInTheSecondPassKeepsTheFirstAndResumesWithoutDuplicates() = runBlocking<Unit> {
        setup(); val server = standard().apply { bulk(247) }
        val limit = ReceiveLimit(maxBundles = 100)
        val first = receiver(server).receive(workspaceId, farmId, campaignId, limit)
        assertEquals(HarvestReceiveOutcome.PARTIAL, first.outcome); val cursor = first.next!!
        val afterFirst = dump(*data)
        assertEquals(100, first.applied.size)
        // Airplane mode at the 2nd bundle of the 2nd pass: nothing of that pass written, the first pass stays.
        var bundles = 0
        server.failOn = { name, _ -> name == "get_own_harvest_bundle_v1" && ++bundles == 2 }
        assertEquals(HarvestReceiveOutcome.OFFLINE, receiver(server).receive(workspaceId, farmId, campaignId, limit, cursor).outcome)
        assertEquals(afterFirst, dump(*data))
        server.failOn = null
        // Resume with the same cursor: the series ends with everything once.
        val rest = series(server, limit, cursor)
        assertEquals(listOf(HarvestReceiveOutcome.PARTIAL, HarvestReceiveOutcome.IMPORTED), rest.map { it.outcome })
        assertEquals(504, db.syncReceiveReceiptDao().count()); assertEquals(0, outboxCount())
        // A cursor lost (null): the new series reads nothing and writes nothing.
        val state = dump(*data); val read = server.bundles()
        val again = series(server, limit)
        assertEquals(listOf(HarvestReceiveOutcome.ALREADY_CURRENT), again.map { it.outcome })
        assertEquals(read, server.bundles()); assertEquals(state, dump(*data))
    }

    @Test fun l3RetainedJornadasNeverStallTheSeries() = runBlocking<Unit> {
        setup(); val server = FakeR2()
        server.bulk(150, low, unverified = true); val good = server.bulk(100, high)
        val passes = series(server, ReceiveLimit(maxBundles = 100))
        assertEquals(listOf(HarvestReceiveOutcome.PARTIAL, HarvestReceiveOutcome.PARTIAL, HarvestReceiveOutcome.IMPORTED), passes.map { it.outcome })
        assertEquals(listOf(0, 50, 50), passes.map { it.applied.size })
        assertTrue(good.all { db.harvestDao().findById(it.id) != null })
        assertEquals(150, holds().count { it.cause == ReceiveHoldCauses.UNVERIFIED })
        // A new series re-reads only what is retained, writes nothing and duplicates no hold.
        val state = dump(*data); val held = dump("sync_receive_holds")
        val again = series(server, ReceiveLimit(maxBundles = 100))
        assertEquals(listOf(HarvestReceiveOutcome.PARTIAL, HarvestReceiveOutcome.HELD), again.map { it.outcome })
        assertEquals(listOf(100, 50), again.map { it.bundlesRead })
        assertEquals(state, dump(*data)); assertEquals(held, dump("sync_receive_holds")); assertEquals(0, outboxCount())
    }

    @Test fun l4ACursorOfAnotherScopeOrAccountIsDiscardedOrRefused() = runBlocking<Unit> {
        setup(); val server = standard().apply { bulk(247) }
        val limit = ReceiveLimit(maxBundles = 100)
        val end = ReceiveCursor(ISSUER, userId, workspaceId, farmId, campaignId, UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
        // Control: the device's own cursor past the last group is honoured (nothing left in this series, nothing read).
        val honoured = receiver(server).receive(workspaceId, farmId, campaignId, limit, end)
        assertEquals(0, honoured.bundlesRead); assertNull(honoured.next)
        // The same cursor of another issuer, account, workspace, farm or campaign is discarded: the series starts over.
        for (foreign in listOf(end.copy(issuer = "https://otro.example"), end.copy(accountId = UUID.randomUUID()),
            end.copy(workspaceId = UUID.randomUUID()), end.copy(farmId = UUID.randomUUID()), end.copy(campaignId = UUID.randomUUID()))) {
            val r = receiver(server).receive(workspaceId, farmId, campaignId, ReceiveLimit(maxBundles = 1), foreign)
            assertEquals("$foreign", HarvestReceiveOutcome.PARTIAL, r.outcome)
            assertEquals("$foreign", end.copy(after = r.next!!.after), r.next)   // the new cursor is the device's own
        }
        // Another account on the device, even with a valid-looking cursor: refused before any request.
        val stranger = AccountRepository(object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }, MemoryAccountSessionStore())
        stranger.login("otra@example.test", "password42", true)
        val calls = server.names.size
        assertEquals(HarvestReceiveOutcome.NOT_AUTHORIZED, receiver(server, accountRepo = stranger).receive(workspaceId, farmId, campaignId, limit, end).outcome)
        assertEquals(calls, server.names.size)
    }

    @Test fun l5TheByteLimitStillAdvancesOneWholeGroupPerPass() = runBlocking<Unit> {
        setup(); val server = standard().apply { bulk(2) }
        val passes = series(server, ReceiveLimit(maxBytes = 1))
        assertEquals(5, passes.size)
        assertTrue(passes.all { it.bundlesRead == 1 && it.oversizedGroups == 1 && it.bundleBytes > 1 })
        assertEquals(HarvestReceiveOutcome.IMPORTED, passes.last().outcome)
        assertEquals(5, server.harvests.count { db.harvestDao().findById(it.id) != null }); assertEquals(0, outboxCount())
    }

    @Test fun l6APendingChangeOrConflictInALaterPassIsRetainedAndTheRestApplies() = runBlocking<Unit> {
        setup(); val server = FakeR2(); val days = server.bulk(150, low)
        series(server, ReceiveLimit.UNBOUNDED)
        // The server moves on for 120 Pesadas; the device has a pending change on #110 and a CONFLICT on #115 (2nd pass).
        val moved = days.take(120).map { d -> server.pesadas.single { it.harvestId == d.id } }
        for (p in moved) { p.version = 2; p.net += 7; p.parcels = listOf(exact(north, p.net)) }
        val pending = moved[110]; val conflict = moved[115]
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,created_at,updated_at) VALUES (?, 'DELIVERY', ?, 'UPDATE', 2, 'PENDING', 0, NULL, 1000, 1000)",
            arrayOf<Any?>(UUID.randomUUID().toString(), pending.id.toString()))
        db.deliveryDao().findById(conflict.id)!!.let { db.deliveryDao().upsert(it.copy(metadata = it.metadata.copy(syncStatus = SyncStatus.CONFLICT))) }
        val protectedBefore = listOf(pending.id, conflict.id).map { db.deliveryDao().findById(it) to db.deliveryDao().listParcels(it) }
        val passes = series(server, ReceiveLimit(maxBundles = 100))
        assertEquals(listOf(HarvestReceiveOutcome.PARTIAL, HarvestReceiveOutcome.IMPORTED), passes.map { it.outcome })
        assertEquals(setOf(pending.id, conflict.id), passes.flatMap { it.held }.filter { it.cause == ReceiveCauses.REMOTE_AHEAD }.map { it.entityId }.toSet())
        assertEquals(protectedBefore, listOf(pending.id, conflict.id).map { db.deliveryDao().findById(it) to db.deliveryDao().listParcels(it) })
        assertEquals(118, moved.count { db.deliveryDao().findById(it.id)!!.metadata.remoteVersion == 2L })
        assertEquals(1, outboxCount())   // only the device's own pending operation
        // MANUAL Jornadas keep their typed kilos (J-D2).
        assertTrue(days.all { db.harvestDao().findById(it.id)!!.weightGrams == it.weight })
    }

    @Test fun l7RemoteMissingAndNoJornadaAreDecidedOnTheWholeManifestEveryPassWithoutDuplicates() = runBlocking<Unit> {
        setup(); val server = standard()
        val loose = SPesada(UUID.randomUUID(), null, d2, 50_000, listOf(exact(north, 50_000))); server.pesadas += loose
        series(server, ReceiveLimit.UNBOUNDED)
        val gone = server.harvests[2]; server.harvests.remove(gone); server.pesadas.removeAll { it.harvestId == gone.id }
        server.bulk(150)
        val passes = series(server, ReceiveLimit(maxBundles = 100))
        assertEquals(2, passes.size)
        for (r in passes) {
            assertTrue(r.held.any { it.cause == ReceiveCauses.REMOTE_MISSING && it.entityId == gone.id })
            assertTrue(r.held.any { it.cause == ReceiveCauses.NO_JORNADA && it.entityId == loose.id })
        }
        assertEquals(1, holds().count { it.cause == ReceiveCauses.REMOTE_MISSING && it.entityId == gone.id })
        assertEquals(1, holds().count { it.cause == ReceiveCauses.NO_JORNADA && it.entityId == loose.id })
        assertNotNull(db.harvestDao().findById(gone.id)); assertNull(db.deliveryDao().findById(loose.id))
    }

    @Test fun l8AMovedPesadaKeepsXAndYInOneGroupOverThePassLimitAndTheSeriesEnds() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (x, y, p) = moveToY(server)
        val m = server.harvests[2]; m.version = 2; m.weight = 4_000_000; m.headOp = UUID.randomUUID()
        val passes = series(server, ReceiveLimit(maxBundles = 1))
        // Two groups: {X, Y} read whole although over the limit (oversized, never split) and {M}; one pass each, no loop.
        assertEquals(2, passes.size); assertEquals(1, passes.sumOf { it.oversizedGroups })
        val xy = passes.single { x.id in it.applied }
        assertEquals(setOf(x.id, y.id), xy.applied.toSet()); assertEquals(2, xy.bundlesRead)
        assertEquals(y.id, db.deliveryDao().findById(p.id)!!.harvestId)
        assertEquals(serverDerived(server, x.id), localDerived(x.id)); assertEquals(serverDerived(server, y.id), localDerived(y.id))
        assertEquals(4_000_000L, db.harvestDao().findById(m.id)!!.weightGrams)
        assertEquals(HarvestReceiveOutcome.IMPORTED, passes.last().outcome); assertEquals(0, outboxCount())
    }

    @Test fun m4TheRepeatIsIdempotentAndAMoveThatEmptiesAndDeletesTheSourceApplies() = runBlocking<Unit> {
        setup(); val server = standard(); receive(server)
        val (x, y, _) = moveToY(server)
        val fpBefore = db.syncReceiveReceiptDao().find(ISSUER, userId.toString(), "HARVEST", x.id)!!
        assertEquals(HarvestReceiveOutcome.IMPORTED, receive(server).outcome)
        val fpAfter = db.syncReceiveReceiptDao().find(ISSUER, userId.toString(), "HARVEST", x.id)!!
        assertEquals(fpBefore.remoteVersion, fpAfter.remoteVersion)              // X kept its version ...
        assertTrue(fpBefore.derivedFingerprint != fpAfter.derivedFingerprint)    // ... only its derived fingerprint moved
        // The repeat reads nothing and writes nothing.
        val state = dump(*data); val holdState = dump("sync_receive_holds"); val read = server.bundles()
        assertEquals(HarvestReceiveOutcome.ALREADY_CURRENT, receive(server).outcome)
        assertEquals(read, server.bundles()); assertEquals(state, dump(*data)); assertEquals(holdState, dump("sync_receive_holds"))
        // The last live Pesada of X moves too and the server deletes the empty X: applied, never LOCAL_DEPENDENTS.
        val last = server.pesadas.single { it.harvestId == x.id && it.deletedAt == null }
        last.harvestId = y.id; last.date = y.date; last.version += 1
        x.version += 1; x.deletedAt = now; x.headOp = UUID.randomUUID()
        val report = receive(server)
        assertEquals("${report.held.map { it.cause + ":" + it.entityId }}", HarvestReceiveOutcome.IMPORTED, report.outcome)
        assertNotNull(db.harvestDao().findById(x.id)!!.metadata.deletedAt)
        assertTrue(db.harvestDao().listParcels(x.id).isEmpty())
        assertEquals(y.id, db.deliveryDao().findById(last.id)!!.harvestId)
        assertEquals(serverDerived(server, y.id), localDerived(y.id))
        assertEquals(0, outboxCount())
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
