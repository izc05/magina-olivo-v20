package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
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
import com.isivoltpro.maginaolivo.data.repository.DeliveryPayloadRoute
import com.isivoltpro.maginaolivo.data.repository.DeliveryRetentionCodes
import com.isivoltpro.maginaolivo.data.repository.HistoricalDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.HttpsWorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.LegacyDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.ServerWorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.V3RecoveryCauses
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.deliveryOriginCreatedAt
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDeleteCodes
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDeleteCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDeleteOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchWorker
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.DispatchOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestCodes
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestSendOutcome
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.HttpsAuthTransport
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SupabaseAuthApi
import java.io.File
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit DEV-only probe for #808: the Pesada v3 writer (`sync_own_delivery_v3`, #818 proposal)
 * against a disposable local Supabase with a pinned test certificate. Real Room, real producer,
 * real PREPARE with a fresh server owner check, real HTTPS. No receiver, no scheduler, no campaign
 * sender. The server parents are placed by the fixture with the ids the device already holds; the
 * archive goes through the real #805 RPC. The Jornada is NEVER placed by the fixture: Android sends it
 * (#824, `sync_own_harvest_v1`), and a held v3 Pesada is recovered through `get_own_delivery_head_v3`
 * (#822). Cross gate #822 + #824: Jornada -> Pesada v3 -> HEAD v3 recovery -> SYNCED, on one server.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryV3HttpsEphemeralTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://zubpicdibqbmewxeurhm.supabase.co"
    private val endpoint = "https://10.0.2.2:54443"
    private val zone = ZoneId.of("Europe/Madrid")
    private val names = List(3) {"g808-v3-https-$it-${UUID.randomUUID()}" }
    private val ledgers = mutableListOf<File>()
    private val opened = mutableListOf<MaginaOlivoDatabase>()
    private lateinit var originalTls: SSLSocketFactory
    private lateinit var fixture: JSONObject
    private lateinit var config: AuthConfiguration
    private lateinit var leaseDirectory: File
    private lateinit var lease: DeliveryDispatchLease
    private lateinit var transport: HttpsDeliveryRpcTransport
    private lateinit var authorizations: HttpsWorkspaceAuthorizationRemote

    @Before fun setup() {
        val encoded = InstrumentationRegistry.getArguments().getString("g26_test_cert")
            ?: error("Disposable TLS certificate required")
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(Base64.getDecoder().decode(encoded).inputStream())
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null); setCertificateEntry("g808-ephemeral", certificate)
        }
        val manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val tls = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, manager.trustManagers, null) }
        originalTls = HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
        fixture = JSONObject(fetch("/__g26_fixture", "GET"))
        config = AuthConfiguration(endpoint, fixture.getString("anonKey"), "qa://auth/callback")
        leaseDirectory = File(context.noBackupFilesDir, "g808-v3-https-${UUID.randomUUID()}")
        lease = DeliveryDispatchLease.tryAcquire(leaseDirectory)!!
        transport = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.DELIVERY)
        authorizations = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey"))
    }

    @After fun cleanup() {
        if (::lease.isInitialized) lease.close()
        if (::leaseDirectory.isInitialized) leaseDirectory.deleteRecursively()
        opened.forEach { runCatching { it.close() } }
        names.forEach { context.deleteDatabase(it) }
        ledgers.forEach { File(it.path).delete(); File(it.path + ".bak").delete(); File(it.path + ".new").delete() }
        if (::originalTls.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)
    }

    private class Graph(val workspace: UUID = UUID.randomUUID(), val farm: UUID = UUID.randomUUID(),
        val campaign: UUID = UUID.randomUUID(), val archived: UUID = UUID.randomUUID(), val active: UUID = UUID.randomUUID(),
        val archivedSnapshot: UUID = UUID.randomUUID(), val activeSnapshot: UUID = UUID.randomUUID())

    /** The same graph on the server (owner's) and in this device's Room. */
    private suspend fun seed(graph: Graph, db: MaginaOlivoDatabase, server: Boolean, owner: UUID) {
        if (server) {
            fetch("/__g26_admin/seed", "POST", JSONObject().put("workspace", "${graph.workspace}").put("farm", "${graph.farm}")
                .put("campaign", "${graph.campaign}").put("parcels", JSONArray()
                    .put(JSONObject().put("id", "${graph.archived}").put("name", "Norte").put("snapshot", "${graph.archivedSnapshot}"))
                    .put(JSONObject().put("id", "${graph.active}").put("name", "Sur").put("snapshot", "${graph.activeSnapshot}"))).toString())
        }
        // Parents already confirmed by their own senders (#798-#800 and the campaign): workspace, farm, campaign.
        val meta = LocalMetadata(Instant.now(), Instant.now(), syncStatus = SyncStatus.SYNCED, remoteVersion = 1L)
        db.workspaceDao().upsert(WorkspaceEntity(graph.workspace, "Olivar QA", owner, "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(graph.farm, graph.workspace, "La Solana", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(graph.archived, graph.workspace, "Norte", source = "MANUAL", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(graph.active, graph.workspace, "Sur", source = "MANUAL", metadata = meta))
        // Device B is stale: locally the campaign is still running and the parcel still active.
        db.campaignDao().upsert(CampaignEntity(graph.campaign, graph.workspace, graph.farm, "2026/27", LocalDate.parse("2025-10-01"),
            null, CampaignStatus.HARVEST, metadata = meta))
        db.campaignDao().upsertSnapshots(listOf(
            CampaignParcelSnapshotEntity(graph.archivedSnapshot, graph.workspace, graph.campaign, graph.archived, graph.farm, "La Solana", "Norte", metadata = meta),
            CampaignParcelSnapshotEntity(graph.activeSnapshot, graph.workspace, graph.campaign, graph.active, graph.farm, "La Solana", "Sur", metadata = meta)))
    }

    private class Device(val db: MaginaOlivoDatabase, val account: AccountRepository, val links: LocalFarmLinkRepository,
        val workspace: WorkspaceRepository, val actor: UUID, val ledger: File)

    private suspend fun device(name: String, actorKey: String, tokenKey: String, graph: Graph, server: Boolean): Device {
        val db = MaginaOlivoDatabase.create(context, name).also { opened += it }
        val actor = UUID.fromString(fixture.getString(actorKey))
        seed(graph, db, server, UUID.fromString(fixture.getString("actor")))
        val store = MemoryAccountSessionStore().apply { write(AccountRecord(session = session(actor, tokenKey))) }
        val account = AccountRepository(SupabaseAuthApi(config, HttpsAuthTransport(config)), store)
        // Publishes the stored session after /auth/v1/user validates it, so field writes are attributed.
        account.resume(true)
        assertEquals(actor, account.state.value.userId)
        val ledger = File(context.noBackupFilesDir, "g808-v3-ledger-${UUID.randomUUID()}.json").also { ledgers += it }
        val links = LocalFarmLinkRepository(context, db, account, issuer, AndroidFarmLinkLedger(ledger),
            ServerWorkspaceOwnerVerifier(authorizations, issuer))
        links.consent(actor, links.choices().filter { it.id == graph.farm }, accepted = true)
        val workspace = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(graph.workspace)
        }
        return Device(db, account, links, workspace, actor, ledger)
    }

    private fun repository(device: Device, at: Instant, route: DeliveryPayloadRoute) = OfflineFirstDeliveryRepository(
        device.db, FixedClock(at), RandomIds, TestDispatchers, workspaceRepository = device.workspace,
        writeScope = device.links, payloadRoute = route) { zone }

    private suspend fun create(device: Device, graph: Graph, at: Instant, parcel: UUID, day: LocalDate, net: Long,
        route: DeliveryPayloadRoute = HistoricalDeliveryRoute { _, _ -> true }, time: LocalTime? = null): SyncOutboxEntity {
        val draft = DeliveryDraft(farmId = graph.farm, deliveryDate = day, destinationOrganizationId = null,
            destinationName = "Cooperativa San Isidro", netGrams = net, shares = listOf(DeliveryShareInput(parcel, null)),
            origin = PesadaOrigin.TREE, deliveryTime = time)
        val id = when (val result = repository(device, at, route).create(draft)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> error("create failed: ${result.error}")
        }
        return device.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
    }

    /** The Jornada sender (#808, #823 RPCs) from this device: nothing is placed by the fixture. */
    private fun harvests(device: Device) = HarvestSendCoordinator(device.db, device.account, issuer,
        HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.HARVEST_SEND),
        { device.links.ledgerFor(it) }, ServerWorkspaceOwnerVerifier(authorizations, issuer), RandomIds)

    private suspend fun jornadaOf(device: Device, op: SyncOutboxEntity): UUID = device.db.deliveryDao().findById(op.entityId)!!.harvestId!!

    private suspend fun stageJornada(device: Device, op: SyncOutboxEntity): Pair<UUID, HarvestSendOutcome> {
        val jornada = jornadaOf(device, op)
        val marker = device.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, jornada)
            .single { it.status == OutboxStatus.PENDING && it.payloadJson == null }
        return marker.id to harvests(device).stage(marker.id)
    }

    private suspend fun sendJornada(device: Device, op: SyncOutboxEntity): UUID {
        val (marker, staged) = stageJornada(device, op)
        assertEquals(HarvestSendOutcome.STAGED, staged)
        val sent = harvests(device).send(marker, lease)
        assertTrue("$sent", sent == HarvestSendOutcome.SYNCED || sent == HarvestSendOutcome.CONFIRMED_LOCAL_PENDING)
        val jornada = jornadaOf(device, op)
        assertEquals(1L, device.db.harvestDao().findById(jornada)!!.metadata.remoteVersion)
        return jornada
    }

    private fun remoteJornada(id: UUID) = JSONObject(fetch("/__g26_admin/harvest_state?id=$id", "GET"))

    private fun worker(device: Device) = DeliveryDispatchWorker(device.db, device.links, DeliveryRpcAdapter(transport))
    private fun remote(id: UUID) = JSONObject(fetch("/__g26_admin/delivery?id=$id", "GET"))

    /**
     * Cross gate: the SAME operation ended CONFIRMED with its frozen bytes, the Pesada is SYNCED at the
     * receipt's version under THIS Jornada, and nothing was duplicated: one outbox operation, one server
     * row, one receipt.
     */
    private suspend fun assertRecovered(device: Device, op: SyncOutboxEntity, jornada: UUID) {
        val local = device.db.deliveryDao().findById(op.entityId)!!
        assertEquals(SyncStatus.SYNCED, local.metadata.syncStatus)
        assertEquals(1L, local.metadata.remoteVersion)
        assertEquals(jornada, local.harvestId)
        val row = device.db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.CONFIRMED, row.status)
        assertEquals(op.id, row.id)
        assertEquals(op.payloadJson, row.payloadJson)
        assertEquals(1, device.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId).size)
        val server = remote(op.entityId)
        assertEquals(1, server.getInt("rows")); assertEquals(1, server.getInt("receipts"))
        assertEquals(jornada.toString(), server.getString("harvestId"))
        assertEquals(1, device.db.harvestDao().findById(jornada)!!.metadata.remoteVersion?.toInt())
    }

    @Test fun ownerSendsHistoricalPesadasOverHttpsAndNothingIsConfirmedWithoutAV3Head() = runBlocking<Unit> {
        val graph = Graph()
        val owner = device(names[0], "actor", "token", graph, server = true)
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val day = LocalDate.now(zone).minusDays(3)
        val before = now.minus(Duration.ofDays(2))

        // B, offline: Pesadas recorded two days ago and one recorded now; a legacy v2 one.
        val historical = create(owner, graph, before, graph.archived, day, 2_800_000)
        val late = create(owner, graph, now, graph.archived, day, 1_100_000)
        val lost = create(owner, graph, before, graph.active, day, 900_000)
        val legacy = create(owner, graph, before, graph.active, day, 700_000, route = LegacyDeliveryRoute)
        val ambiguous = create(owner, graph, now, graph.active, LocalDate.parse("2026-10-25"), 600_000, time = LocalTime.of(2, 30))
        assertEquals(listOf(3, 3, 3, 2, 3), listOf(historical, late, lost, legacy, ambiguous).map { it.payloadVersion })
        // Causal order: no Pesada goes before its Jornada is confirmed by receipt and HEAD.
        assertEquals(DispatchOutcome.PARENT_NOT_SYNCED, worker(owner).dispatch(historical.id, lease))
        val jornada = sendJornada(owner, historical)
        sendJornada(owner, ambiguous)

        // A archives the parcel yesterday (real #805 RPC); B still sees it active.
        val archivedAt = now.minus(Duration.ofDays(1))
        fetch("/__g26_admin/archive?parcel=${graph.archived}&at=$archivedAt", "POST")

        // 1. History before the archive: applied by the server, receipt equal to OUR bytes, held, not confirmed.
        assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(owner).dispatch(historical.id, lease))
        val prepared = owner.db.deliveryDispatchPrepareDao().findByOperation(historical.id)!!
        val server = remote(historical.entityId)
        assertEquals(1, server.getInt("rows")); assertEquals(1, server.getInt("receipts")); assertEquals(3, server.getInt("payloadVersion"))
        assertEquals("Hash del servidor = hash Android de los mismos bytes", prepared.payloadHash, server.getString("hash"))
        val created = owner.db.deliveryDao().findById(historical.entityId)!!.metadata.createdAt
        assertEquals(deliveryOriginCreatedAt(created), server.getString("origin"))
        val held = owner.db.syncOutboxDao().findById(historical.id)!!
        assertEquals(OutboxStatus.BLOCKED, held.status)
        assertEquals(DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD, held.lastErrorCode)
        assertEquals(historical.payloadJson, held.payloadJson)
        assertEquals(SyncStatus.PENDING, owner.db.deliveryDao().findById(historical.entityId)!!.metadata.syncStatus)
        Log.i("G808", "G808-HISTORICAL held=${held.lastErrorCode} serverRows=${server.getInt("rows")} hashMatch=true")

        // 2. Recorded after the archive: typed review, nothing on the server, bytes kept.
        assertEquals(DispatchOutcome.REVIEW_HELD, worker(owner).dispatch(late.id, lease))
        assertEquals("${DeliveryRetentionCodes.REVIEW_PREFIX}ORIGIN_NOT_BEFORE_CLOSURE", owner.db.syncOutboxDao().findById(late.id)!!.lastErrorCode)
        assertEquals(late.payloadJson, owner.db.syncOutboxDao().findById(late.id)!!.payloadJson)
        assertEquals(0, remote(late.entityId).getInt("rows"))

        // 3. DST: 02:30 on 25 Oct 2026 happens twice in Madrid. Never guessed: review.
        assertEquals(DispatchOutcome.REVIEW_HELD, worker(owner).dispatch(ambiguous.id, lease))
        assertEquals("${DeliveryRetentionCodes.REVIEW_PREFIX}DELIVERY_TIME_AMBIGUOUS", owner.db.syncOutboxDao().findById(ambiguous.id)!!.lastErrorCode)
        assertEquals(0, remote(ambiguous.entityId).getInt("rows"))

        // 4. Lost response after commit, then a restart: recovered by the SAME UUID, still not confirmed.
        fetch("/__g26_admin/v3_mode?mode=drop", "POST")
        assertEquals(DispatchOutcome.OUTCOME_UNKNOWN, worker(owner).dispatch(lost.id, lease))
        fetch("/__g26_admin/v3_mode?mode=normal", "POST")
        assertEquals(1, remote(lost.entityId).getInt("rows"))
        lease.close()
        lease = DeliveryDispatchLease.tryAcquire(leaseDirectory)!!
        val restarted = worker(owner)
        assertEquals(1, restarted.settleInterrupted(graph.farm, lease))
        assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, restarted.recoverLostResponse(lost.id, lease))
        assertEquals(1, remote(lost.entityId).getInt("receipts"))
        assertEquals(lost.payloadJson, owner.db.syncOutboxDao().findById(lost.id)!!.payloadJson)
        assertEquals(1, owner.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, lost.entityId).size)

        // 5. The legacy v2 route is unchanged and still confirms through its own strict HEAD.
        assertEquals(DispatchOutcome.SYNCED, worker(owner).dispatch(legacy.id, lease))
        assertEquals(2, remote(legacy.entityId).getInt("payloadVersion"))
        assertEquals(SyncStatus.SYNCED, owner.db.deliveryDao().findById(legacy.entityId)!!.metadata.syncStatus)
        // The server derives the AUTO_DAY kilos from every live Pesada it holds (the late one never arrived).
        assertEquals(2_800_000L + 900_000L + 700_000L, remoteJornada(jornada).getLong("weight"))
        Log.i("G808", "G808-SUMMARY historical=AWAITING_HEAD late=REVIEW dst=REVIEW lost=AWAITING_HEAD legacy=SYNCED")

        // 6. Cross #822 + #824. A held v3 is never re-sent by dispatch: not claimable, nothing on the wire.
        assertEquals(DispatchOutcome.REJECTED, restarted.dispatch(historical.id, lease))
        assertEquals(1, remote(historical.entityId).getInt("receipts"))
        assertEquals(historical.payloadJson, owner.db.syncOutboxDao().findById(historical.id)!!.payloadJson)
        // After the restart (between the send and the HEAD read), both held Pesadas of the Jornada Android
        // created are recovered by their SAME operations through get_own_delivery_head_v3.
        for (op in listOf(historical, lost)) {
            assertEquals(DispatchOutcome.SYNCED, restarted.resolveAwaitingHead(op.id, lease))
            assertEquals(op.payloadJson, owner.db.syncOutboxDao().findById(op.id)!!.payloadJson)
            assertRecovered(owner, op, jornada)
        }
        // Idempotent: a second resolution reads nothing new and confirms nothing twice.
        assertEquals(DispatchOutcome.SYNCED, restarted.resolveAwaitingHead(historical.id, lease))
        assertEquals(1, remote(historical.entityId).getInt("receipts"))
        val serverDay = remoteJornada(jornada)
        assertEquals(1, serverDay.getInt("rows")); assertEquals(1, serverDay.getInt("version"))
        assertEquals(2_800_000L + 900_000L + 700_000L, serverDay.getLong("weight"))
        Log.i("G808", "G808-CROSS-FLOW jornada=ANDROID historical=SYNCED lost=SYNCED legacy=SYNCED " +
            "jornadaRows=${serverDay.getInt("rows")} weight=${serverDay.getLong("weight")}")
    }

    @Test fun aMemberWithWritePermissionNeverPreparesV3AndIsNotRedirected() = runBlocking<Unit> {
        val graph = Graph()
        // The owner's graph exists on the server; the member's device holds the same rows.
        val member = device(names[1], "member", "memberToken", graph, server = true)
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        // A stale hint chose v3 on the member's device: the fresh server check refuses it.
        val op = create(member, graph, now, graph.active, LocalDate.now(zone).minusDays(1), 500_000)
        assertEquals(3, op.payloadVersion)
        // The member cannot send the Jornada either: it does not own the workspace.
        assertEquals(HarvestSendOutcome.NOT_AUTHORIZED, stageJornada(member, op).second)
        // Even with its Jornada taken as confirmed, the v3 Pesada is refused before any request, never redirected.
        member.db.harvestDao().findById(jornadaOf(member, op))!!.let {
            member.db.harvestDao().upsert(it.copy(metadata = it.metadata.copy(remoteVersion = 1L)))
        }
        assertEquals(DispatchOutcome.NOT_AUTHORIZED, worker(member).dispatch(op.id, lease))
        assertNull(member.db.deliveryDispatchPrepareDao().findByOperation(op.id))
        val kept = member.db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.PENDING, kept.status)
        assertEquals(3, kept.payloadVersion)
        assertEquals(op.payloadJson, kept.payloadJson)
        assertEquals(0, remote(op.entityId).getInt("rows"))
        assertTrue(member.links.state.value.ledger != null)
        Log.i("G808", "G808-MEMBER outcome=NOT_AUTHORIZED serverRows=0 version=${kept.payloadVersion}")
    }

    @Test fun twoPhonesShareOneAutoDayAndSendPesadasWithoutAFixtureJornada() = runBlocking<Unit> {
        val graph = Graph()
        val a = device(names[0], "actor", "token", graph, server = true)
        val b = device(names[1], "actor", "token", graph, server = false)
        val before = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofDays(1))
        val day = LocalDate.now(zone).minusDays(2)
        // Both phones, offline, record Pesadas of the same day: two local AUTO_DAY rows with different ids.
        val a2 = create(a, graph, before, graph.active, day, 700_000, route = LegacyDeliveryRoute)
        val a3 = create(a, graph, before, graph.active, day, 800_000)
        val b3 = create(b, graph, before, graph.active, day, 900_000)
        val canonical = jornadaOf(a, a2)
        val losing = jornadaOf(b, b3)
        assertTrue(canonical != losing)
        assertEquals(DispatchOutcome.PARENT_NOT_SYNCED, worker(b).dispatch(b3.id, lease))

        // A: real Jornada through its RPC, confirmed by receipt and HEAD; then its v2 and v3 Pesadas.
        assertEquals(canonical, sendJornada(a, a2))
        assertEquals(DispatchOutcome.SYNCED, worker(a).dispatch(a2.id, lease))
        assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(a).dispatch(a3.id, lease))
        assertEquals(1_500_000L, remoteJornada(canonical).getLong("weight"))

        // B: the server already holds the canonical AUTO_DAY. Never a second one; B rebases its unsent Pesada.
        val (marker, staged) = stageJornada(b, b3)
        assertEquals(HarvestSendOutcome.STAGED, staged)
        assertEquals(HarvestSendOutcome.AUTO_DAY_EXISTS, harvests(b).send(marker, lease))
        assertEquals("harvest_id=$canonical;version=1", b.db.syncOutboxDao().findById(marker)!!.lastErrorMessage)
        assertEquals(0, remoteJornada(losing).getInt("rows"))
        val bRefused = b.db.syncOutboxDao().findById(marker)!!
        assertEquals(HarvestSendOutcome.REBASED, harvests(b).rebaseOntoCanonical(marker))
        assertEquals(canonical, jornadaOf(b, b3))
        // The rebase rebuilt only the Jornada id of the never-prepared Pesada: same operation, same kilos.
        val rebasedB3 = b.db.syncOutboxDao().findById(b3.id)!!
        assertEquals(b3.id, rebasedB3.id)
        assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(b).dispatch(b3.id, lease))
        assertEquals(canonical.toString(), remote(b3.entityId).getString("harvestId"))
        val server = remoteJornada(canonical)
        assertEquals(2_400_000L, server.getLong("weight"))
        assertEquals(1, server.getInt("rows")); assertEquals(1, server.getInt("version"))
        assertEquals(0, remoteJornada(losing).getInt("rows"))
        Log.i("G808", "G808-JORNADA canonical=1 losingOnServer=0 weight=${server.getLong("weight")} rebased=true")

        // Phone C, offline the same day. Its Jornada CREATE reaches the server, is refused (AUTO_DAY_EXISTS)
        // and the answer is lost. While the outcome is UNKNOWN nothing is rebased: marker UUID and bytes, its
        // day and its unsent Pesada stay exactly as they were, and the Pesada is never prepared.
        val c = device(names[2], "actor", "token", graph, server = false)
        val c3 = create(c, graph, before, graph.active, day, 600_000)
        val losingC = jornadaOf(c, c3)
        val (cMarker, cStaged) = stageJornada(c, c3)
        assertEquals(HarvestSendOutcome.STAGED, cStaged)
        val frozen = c.db.syncOutboxDao().findById(cMarker)!!
        fetch("/__g26_admin/v3_mode?mode=harvest_drop", "POST")
        assertEquals(HarvestSendOutcome.OUTCOME_UNKNOWN, harvests(c).send(cMarker, lease))
        fetch("/__g26_admin/v3_mode?mode=normal", "POST")
        assertEquals(0, remoteJornada(losingC).getInt("rows"))
        assertEquals(HarvestSendOutcome.REJECTED, harvests(c).rebaseOntoCanonical(cMarker))
        val unknown = c.db.syncOutboxDao().findById(cMarker)!!
        assertEquals(frozen.id, unknown.id); assertEquals(frozen.payloadJson, unknown.payloadJson)
        assertEquals("OUTCOME_UNKNOWN", unknown.lastErrorCode)
        assertEquals(losingC, jornadaOf(c, c3))
        assertEquals(c3.payloadJson, c.db.syncOutboxDao().findById(c3.id)!!.payloadJson)
        assertEquals(DispatchOutcome.PARENT_NOT_SYNCED, worker(c).dispatch(c3.id, lease))
        assertNull(c.db.deliveryDispatchPrepareDao().findByOperation(c3.id))
        // The retry with the SAME UUID and bytes learns the refusal; only then is the never-prepared Pesada rebased.
        assertEquals(HarvestSendOutcome.AUTO_DAY_EXISTS, harvests(c).send(cMarker, lease))
        val cRefused = c.db.syncOutboxDao().findById(cMarker)!!
        assertEquals(frozen.payloadJson, cRefused.payloadJson)
        assertEquals(HarvestSendOutcome.REBASED, harvests(c).rebaseOntoCanonical(cMarker))
        val rebasedC3 = c.db.syncOutboxDao().findById(c3.id)!!
        assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(c).dispatch(c3.id, lease))

        // Cross #822 + #824: every held v3 Pesada of the ONE canonical day is recovered through
        // get_own_delivery_head_v3, by its SAME operation, on its own phone.
        assertEquals(DispatchOutcome.SYNCED, worker(a).resolveAwaitingHead(a3.id, lease))
        assertEquals(DispatchOutcome.SYNCED, worker(b).resolveAwaitingHead(b3.id, lease))
        assertEquals(DispatchOutcome.SYNCED, worker(c).resolveAwaitingHead(c3.id, lease))
        assertRecovered(a, a2, canonical)
        assertRecovered(a, a3, canonical)
        assertRecovered(b, rebasedB3, canonical)
        assertRecovered(c, rebasedC3, canonical)
        // The losing days never reached the server; their CREATEs are kept, audited, with their bytes.
        for ((device, refused) in listOf(b to bRefused, c to cRefused)) {
            val kept = device.db.syncOutboxDao().findById(refused.id)!!
            assertEquals(OutboxStatus.BLOCKED, kept.status)
            assertEquals(HarvestCodes.AUTO_DAY_REBASED, kept.lastErrorCode)
            assertEquals(refused.payloadJson, kept.payloadJson)
            assertEquals(0, remoteJornada(kept.entityId).getInt("rows"))
        }
        val total = remoteJornada(canonical)
        assertEquals(1, total.getInt("rows")); assertEquals(1, total.getInt("version"))
        assertEquals(700_000L + 800_000L + 900_000L + 600_000L, total.getLong("weight"))
        Log.i("G808", "G808-CROSS-PHONES phones=3 jornadaRows=${total.getInt("rows")} weight=${total.getLong("weight")} " +
            "unknownRebased=false retriedRebased=true v3Recovered=3")
    }

    @Test fun aHeldPesadaIsConfirmedOnlyByAStrictV3HeadAfterASessionRotation() = runBlocking<Unit> {
        val graph = Graph()
        val owner = device(names[0], "actor", "token", graph, server = true)
        val before = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofDays(2))
        val day = LocalDate.now(zone).minusDays(3)
        val kept = create(owner, graph, before, graph.active, day, 1_300_000)
        val edited = create(owner, graph, before, graph.active, day, 1_400_000)
        val rebase = create(owner, graph, before, graph.active, day, 1_500_000)
        // #822 placed this Jornada through the fixture; in the cross gate Android sends it (#824).
        val jornada = sendJornada(owner, kept)
        assertEquals(jornada, jornadaOf(owner, edited)); assertEquals(jornada, jornadaOf(owner, rebase))
        for (op in listOf(kept, edited, rebase)) {
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(owner).dispatch(op.id, lease))
        }

        // Someone writes the second Pesada outside the receipt protocol: its receipt is no longer the head.
        fetch("/__g26_admin/touch?id=${edited.entityId}", "POST")
        assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(owner).resolveAwaitingHead(edited.id, lease))
        val stillHeld = owner.db.syncOutboxDao().findById(edited.id)!!
        assertEquals(DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD, stillHeld.lastErrorCode)
        assertEquals(V3RecoveryCauses.HEAD_SUPERSEDED, stillHeld.lastErrorMessage)
        assertEquals(edited.payloadJson, stillHeld.payloadJson)
        assertEquals(SyncStatus.PENDING, owner.db.deliveryDao().findById(edited.entityId)!!.metadata.syncStatus)

        // The third Pesada is edited locally after the hold: the strict head confirms it as NEEDS_REBASE, never SYNCED.
        val later = before.plus(Duration.ofHours(1))
        val draft = DeliveryDraft(farmId = graph.farm, deliveryDate = day, destinationOrganizationId = null,
            destinationName = "Cooperativa San Isidro", netGrams = 1_550_000, shares = listOf(DeliveryShareInput(graph.active, null)),
            origin = PesadaOrigin.TREE)
        assertTrue(repository(owner, later, HistoricalDeliveryRoute { _, _ -> true }).update(rebase.entityId, draft) is AppResult.Success)
        assertEquals(DispatchOutcome.NEEDS_REBASE, worker(owner).resolveAwaitingHead(rebase.id, lease))
        assertEquals(SyncStatus.CONFLICT, owner.db.deliveryDao().findById(rebase.entityId)!!.metadata.syncStatus)
        assertEquals(OutboxStatus.CONFIRMED, owner.db.syncOutboxDao().findById(rebase.id)!!.status)
        assertEquals(1, remote(rebase.entityId).getInt("receipts"))

        // The session rotates (a new login of the same user); the SAME operation is confirmed by its strict head.
        val actor = UUID.fromString(fixture.getString("actor"))
        val rotatedStore = MemoryAccountSessionStore().apply { write(AccountRecord(session = session(actor, "rotatedToken"))) }
        val rotated = AccountRepository(SupabaseAuthApi(config, HttpsAuthTransport(config)), rotatedStore)
        rotated.resume(true)
        assertEquals(actor, rotated.state.value.userId)
        val links = LocalFarmLinkRepository(context, owner.db, rotated, issuer, AndroidFarmLinkLedger(owner.ledger),
            ServerWorkspaceOwnerVerifier(authorizations, issuer))
        val recovering = DeliveryDispatchWorker(owner.db, links, DeliveryRpcAdapter(transport))
        assertEquals(DispatchOutcome.SYNCED, recovering.resolveAwaitingHead(kept.id, lease))
        assertEquals(DispatchOutcome.SYNCED, recovering.resolveAwaitingHead(kept.id, lease))
        assertRecovered(owner, kept, jornada)
        // A session of ANOTHER project (issuer) is refused explicitly, locally, before any network call: exactly
        // REJECTED, zero RPC, zero owner reads, outbox/PREPARE/Pesada identical and no remote ACK or version change.
        val rpcCalls = mutableListOf<String>()
        val counting = DeliveryRpcTransport { name, body, token -> rpcCalls += name; transport.rpc(name, body, token) }
        var ownerReads = 0
        val realOwner = ServerWorkspaceOwnerVerifier(authorizations, issuer)
        val countingOwner = WorkspaceOwnerVerifier { session, ws -> ownerReads++; realOwner.isCurrentOwner(session, ws) }
        val foreign = LocalFarmLinkRepository(context, owner.db, rotated, "https://otro-proyecto.supabase.co",
            AndroidFarmLinkLedger(owner.ledger), countingOwner)
        val outboxBefore = owner.db.syncOutboxDao().findById(edited.id)
        val prepareBefore = owner.db.deliveryDispatchPrepareDao().findByOperation(edited.id)
        val pesadaBefore = owner.db.deliveryDao().findById(edited.entityId)
        val serverBefore = remote(edited.entityId)
        val foreignOutcome = DeliveryDispatchWorker(owner.db, foreign, DeliveryRpcAdapter(counting)).resolveAwaitingHead(edited.id, lease)
        assertEquals(DispatchOutcome.REJECTED, foreignOutcome)
        assertTrue("Cero RPC: $rpcCalls", rpcCalls.isEmpty())
        assertEquals("Cero lecturas de propietario", 0, ownerReads)
        assertEquals(outboxBefore, owner.db.syncOutboxDao().findById(edited.id))
        assertEquals(prepareBefore, owner.db.deliveryDispatchPrepareDao().findByOperation(edited.id))
        assertNull(prepareBefore!!.settledAt)
        assertEquals(pesadaBefore, owner.db.deliveryDao().findById(edited.entityId))
        val serverAfter = remote(edited.entityId)
        assertEquals(serverBefore.getInt("receipts"), serverAfter.getInt("receipts"))
        assertEquals(serverBefore.getInt("version"), serverAfter.getInt("version"))
        assertEquals(serverBefore.getString("hash"), serverAfter.getString("hash"))
        assertEquals(1, remoteJornada(jornada).getInt("rows"))
        Log.i("G808", "G808-RECOVERY kept=SYNCED edited=${stillHeld.lastErrorMessage} localEdit=NEEDS_REBASE rotated=true " +
            "foreignIssuer=$foreignOutcome")
    }

    /**
     * #808 R1-a Android: the logical delete of a Pesada against the real `delete_own_delivery_v1` (R1-a SQL
     * proposal). Confirmed only by this actor's receipt and `get_own_delivery_delete_head_v1`; the server
     * recomputes the AUTO_DAY; a lost response is resolved by the SAME operation without a second delete; a stale
     * version is retained with its cause and nothing changes on the server.
     */
    @Test fun ownerDeletesPesadasOverHttpsWithReceiptHeadRecoveryAndCas() = runBlocking<Unit> {
        val graph = Graph()
        val owner = device(names[0], "actor", "token", graph, server = true)
        val before = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofDays(1))
        val day = LocalDate.now(zone).minusDays(2)
        val a = create(owner, graph, before, graph.active, day, 1_000_000)
        val b = create(owner, graph, before, graph.active, day, 600_000)
        val c = create(owner, graph, before, graph.active, day, 400_000)
        val jornada = sendJornada(owner, a)
        for (op in listOf(a, b, c)) {
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(owner).dispatch(op.id, lease))
            assertEquals(DispatchOutcome.SYNCED, worker(owner).resolveAwaitingHead(op.id, lease))
        }
        assertEquals(2_000_000L, remoteJornada(jornada).getLong("weight"))
        val deletes = DeliveryDeleteCoordinator(owner.db, owner.account, issuer,
            HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.DELIVERY_DELETE),
            { owner.links.ledgerFor(it) }, ServerWorkspaceOwnerVerifier(authorizations, issuer))
        val repo = repository(owner, before.plus(Duration.ofHours(1)), HistoricalDeliveryRoute { _, _ -> true })
        suspend fun marker(op: SyncOutboxEntity): SyncOutboxEntity {
            assertTrue(repo.delete(op.entityId) is AppResult.Success)
            return owner.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId)
                .single { it.operation == com.isivoltpro.maginaolivo.data.local.model.OutboxOperation.DELETE }
        }

        // 1. A delete confirmed by the real receipt and delete HEAD; the server recomputes the AUTO_DAY.
        val mb = marker(b)
        assertEquals(DeliveryDeleteOutcome.STAGED, deletes.stage(mb.id))
        val frozen = owner.db.syncOutboxDao().findById(mb.id)!!
        assertEquals(DeliveryDeleteOutcome.SYNCED, deletes.send(mb.id, lease))
        val serverB = remote(b.entityId)
        assertEquals(1, serverB.getInt("rows")); assertEquals(1, serverB.getInt("deleted"))
        assertEquals(2, serverB.getInt("version")); assertEquals(2, serverB.getInt("receipts"))
        assertEquals(1, serverB.getInt("parcels"))
        val local = owner.db.deliveryDao().findById(b.entityId)!!
        assertEquals(2L, local.metadata.remoteVersion); assertEquals(SyncStatus.SYNCED, local.metadata.syncStatus)
        assertEquals(OutboxStatus.CONFIRMED, owner.db.syncOutboxDao().findById(mb.id)!!.status)
        assertEquals(frozen.payloadJson, owner.db.syncOutboxDao().findById(mb.id)!!.payloadJson)
        assertEquals(1_400_000L, remoteJornada(jornada).getLong("weight"))
        assertEquals(1_400_000L, owner.db.harvestDao().findById(jornada)!!.weightGrams)
        assertEquals(1, remoteJornada(jornada).getInt("version"))

        // 2. The answer is lost after the commit: OUTCOME_UNKNOWN, then the SAME operation is confirmed from its
        //    receipt and delete HEAD, with no second delete (still two receipts for this Pesada).
        val mc = marker(c)
        assertEquals(DeliveryDeleteOutcome.STAGED, deletes.stage(mc.id))
        fetch("/__g26_admin/v3_mode?mode=delete_drop", "POST")
        assertEquals(DeliveryDeleteOutcome.OUTCOME_UNKNOWN, deletes.send(mc.id, lease))
        fetch("/__g26_admin/v3_mode?mode=normal", "POST")
        assertEquals(1, remote(c.entityId).getInt("deleted"))
        assertEquals("OUTCOME_UNKNOWN", owner.db.syncOutboxDao().findById(mc.id)!!.lastErrorCode)
        assertEquals(DeliveryDeleteOutcome.SYNCED, deletes.send(mc.id, lease))
        assertEquals(2, remote(c.entityId).getInt("receipts"))
        assertEquals(1, owner.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, c.entityId)
            .count { it.operation == com.isivoltpro.maginaolivo.data.local.model.OutboxOperation.DELETE })
        assertEquals(1_000_000L, remoteJornada(jornada).getLong("weight"))

        // 3. Someone wrote the last Pesada outside this device (version 2): STALE_VERSION, retained, nothing deleted.
        fetch("/__g26_admin/touch?id=${a.entityId}", "POST")
        val ma = marker(a)
        assertEquals(DeliveryDeleteOutcome.STAGED, deletes.stage(ma.id))
        assertEquals(DeliveryDeleteOutcome.CONFLICT, deletes.send(ma.id, lease))
        val held = owner.db.syncOutboxDao().findById(ma.id)!!
        assertEquals(OutboxStatus.BLOCKED, held.status)
        assertEquals("${DeliveryDeleteCodes.CONFLICT_PREFIX}STALE_VERSION", held.lastErrorCode)
        assertEquals("remote_version=2", held.lastErrorMessage)
        val serverA = remote(a.entityId)
        assertEquals(0, serverA.getInt("deleted")); assertEquals(2, serverA.getInt("version"))
        assertEquals(1_000_000L, remoteJornada(jornada).getLong("weight"))
        Log.i("G808", "G808-DELETE synced=2 lostRecovered=true receiptsC=${remote(c.entityId).getInt("receipts")} " +
            "stale=${held.lastErrorCode} weight=${remoteJornada(jornada).getLong("weight")}")
    }

    /**
     * #808 gate J against the real server: the device's AUTO_DAY split (CR-010 amendment J) equals the server's derived
     * split after creates and deletes; the Jornada delete waits for the deletes of its Pesadas, then applies once.
     */
    @Test fun gateJTheDeviceSplitEqualsTheServerAndTheJornadaDeleteWaitsForItsPesadas() = runBlocking<Unit> {
        val graph = Graph()
        val owner = device(names[0], "actor", "token", graph, server = true)
        val before = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofDays(1))
        val day = LocalDate.now(zone).minusDays(2)
        val repo = repository(owner, before, HistoricalDeliveryRoute { _, _ -> true })
        suspend fun exact(net: Long, vararg shares: Pair<UUID, Long?>): SyncOutboxEntity {
            val draft = DeliveryDraft(farmId = graph.farm, deliveryDate = day, destinationOrganizationId = null,
                destinationName = "Cooperativa San Isidro", netGrams = net, shares = shares.map { DeliveryShareInput(it.first, it.second) },
                origin = PesadaOrigin.TREE)
            val id = (repo.create(draft) as AppResult.Success).value
            return owner.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        }
        val a = exact(1_000_000, graph.active to 600_000L, graph.archived to 400_000L)
        val b = exact(500_000, graph.archived to null)
        val jornada = sendJornada(owner, a)
        for (op in listOf(a, b)) {
            assertEquals(DispatchOutcome.V3_ACK_AWAITING_HEAD, worker(owner).dispatch(op.id, lease))
            assertEquals(DispatchOutcome.SYNCED, worker(owner).resolveAwaitingHead(op.id, lease))
        }
        fun localSplit() = runBlocking { owner.db.harvestDao().listParcels(jornada).sortedBy { it.parcelId.toString() }
            .map { Triple(it.parcelId.toString(), it.allocationMode, it.weightGrams) } }
        fun serverSplit() = remoteJornada(jornada).getJSONArray("split").let { arr -> (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); Triple(o.getString("parcelId"), o.getString("mode"), if (o.isNull("weight")) null else o.getLong("weight")) } }
        // EXACT for the Parcel with only exact shares, UNALLOCATED for the one with a share without kilos: same on both.
        assertEquals(serverSplit(), localSplit())
        assertEquals(1_500_000L, remoteJornada(jornada).getLong("weight"))
        assertEquals(1_500_000L, owner.db.harvestDao().findById(jornada)!!.weightGrams)
        val deletes = DeliveryDeleteCoordinator(owner.db, owner.account, issuer,
            HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.DELIVERY_DELETE),
            { owner.links.ledgerFor(it) }, ServerWorkspaceOwnerVerifier(authorizations, issuer))
        suspend fun deleteMarker(op: SyncOutboxEntity): SyncOutboxEntity {
            assertTrue(repo.delete(op.entityId) is AppResult.Success)
            return owner.db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId)
                .single { it.operation == com.isivoltpro.maginaolivo.data.local.model.OutboxOperation.DELETE }
        }
        // Delete the mixed Pesada: both splits follow, the remaining Parcel back to EXACT.
        val mb = deleteMarker(b)
        assertEquals(DeliveryDeleteOutcome.STAGED, deletes.stage(mb.id))
        assertEquals(DeliveryDeleteOutcome.SYNCED, deletes.send(mb.id, lease))
        assertEquals(serverSplit(), localSplit())
        assertEquals(1_000_000L, remoteJornada(jornada).getLong("weight"))
        // The last Pesada goes locally: the empty AUTO_DAY is retired with a DELETE marker that must wait.
        val ma = deleteMarker(a)
        val dayMarker = owner.db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, jornada)
            .single { it.status == OutboxStatus.PENDING && it.payloadJson == null }
        assertEquals(HarvestSendOutcome.WAITING_DEPENDENTS, harvests(owner).stage(dayMarker.id))
        assertEquals(0, remoteJornada(jornada).getInt("deleted"))
        assertEquals(DeliveryDeleteOutcome.STAGED, deletes.stage(ma.id))
        assertEquals(DeliveryDeleteOutcome.SYNCED, deletes.send(ma.id, lease))
        // Server: kilos 0, no split, Jornada alive. Only now the Jornada delete is frozen and applied, once.
        val empty = remoteJornada(jornada)
        assertEquals(0L, empty.getLong("weight")); assertEquals(0, empty.getInt("parcels")); assertEquals(0, empty.getInt("deleted"))
        assertEquals(HarvestSendOutcome.STAGED, harvests(owner).stage(dayMarker.id))
        assertEquals(HarvestSendOutcome.SYNCED, harvests(owner).send(dayMarker.id, lease))
        val gone = remoteJornada(jornada)
        assertEquals(listOf(1, 2), listOf(gone.getInt("deleted"), gone.getInt("version")))
        assertEquals(2, remote(a.entityId).getInt("receipts")); assertEquals(2, remote(b.entityId).getInt("receipts"))
        Log.i("G808", "G808-JORNADA-J splitEqual=true deleteWaited=true weight=0 jornadaDeleted=${gone.getInt("deleted")}")
    }

    private fun session(actor: UUID, key: String): AuthSession {
        val token = fixture.getString(key)
        val expiration = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]))).getLong("exp")
        return AuthSession(actor, "qa@example.test", token, "unused", Instant.ofEpochSecond(expiration))
    }

    private fun fetch(path: String, method: String, body: String? = null): String {
        val connection = URL("$endpoint$path").openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = method
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode in 200..299) { "$path -> ${connection.responseCode}" }
            if (connection.responseCode == 204) "" else connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }
}
