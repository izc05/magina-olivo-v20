package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveV2Coordinator
import com.isivoltpro.maginaolivo.data.repository.sync.instantText
import com.isivoltpro.maginaolivo.data.repository.sync.parcelFieldsPayload
import com.isivoltpro.maginaolivo.data.repository.sync.parcelMutationHash
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.accountlink.FarmChoice
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkConsent
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2.6 2c-3: convergence of two independent disposable Room databases (devices A and B of one account)
 * through one shared fake server modelling #805 (CAS, exact receipts, typed refusals) and
 * `get_own_workspace_parents_v2` (#812), with the real 2b producers and senders and the 2c-2 receiver.
 *
 * Every scene ends with a content comparison of A, B and the server (ids, fields, status, deletedAt,
 * versions, farm periods) and logs their hashes and outbox states under tag G26CONV. Instants are
 * compared at Room's millisecond precision (see [precisionIsMillisecondFloorOnAndroid]).
 *
 * Expected blocks are asserted as such, never as convergence: C5/C6 (NEEDS_REBASE / remote_ahead until
 * an explicit resolution), C10 (the Pesada stays pending for #808). C14 belongs to 2c-4.
 */
@RunWith(AndroidJUnit4::class)
class ParcelConvergenceRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = listOf("g26-convergence-a", "g26-convergence-b")
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val north = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val south = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val now = Instant.now()
    private val server = Server()
    private val ledger = FarmLinkLedger(listOf(FarmLinkConsent(UUID.randomUUID(), "$issuer|$a",
        listOf(FarmChoice(north, workspace, "Finca Norte", 1), FarmChoice(south, workspace, "Finca Sur", 1)),
        UUID.randomUUID(), "b".repeat(64), Instant.now())))
    private lateinit var dbA: MaginaOlivoDatabase
    private lateinit var dbB: MaginaOlivoDatabase
    private lateinit var accountA: AccountRepository
    private lateinit var accountB: AccountRepository
    private val storeB = MemoryAccountSessionStore()
    private lateinit var lease: DeliveryDispatchLease

    @Before fun setup() = runBlocking {
        names.forEach { context.deleteDatabase(it) }
        dbA = MaginaOlivoDatabase.create(context, names[0])
        dbB = MaginaOlivoDatabase.create(context, names[1])
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-convergence-room"))!!
        accountA = device(MemoryAccountSessionStore(), a)
        accountB = device(storeB, a)
        // A already confirmed its WORKSPACE (#798) and FARMs (#799); B starts empty and receives them.
        val sql = dbA.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar A','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        for ((farm, farmName) in listOf(north to "Finca Norte", south to "Finca Sur")) {
            sql.execSQL("INSERT INTO farms(id,workspace_id,name,municipality,province,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$farm','$workspace','$farmName','Bedmar','Jaén','ACTIVE',1000,1000,1,'SYNCED',1)")
            server.farms[farm] = farmName
        }
        for (db in listOf(dbA, dbB)) {
            db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
            grant(db, listOf("read", "write"))
        }
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
    }

    @After fun close() {
        if (::lease.isInitialized) lease.close()
        if (::dbA.isInitialized) dbA.close()
        if (::dbB.isInitialized) dbB.close()
        names.forEach { context.deleteDatabase(it) }
    }

    @Test fun c1OfflineEditSentByAIsReceivedByBAndARepeatedReceiveWritesNothing() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertTrue(parcels(dbA).update(p, ParcelChanges("Parcela 1 renombrada", managedAreaM2 = 2500.0)) is AppResult.Success)
        assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(dbA, accountA, p))
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        converged("C1", p)
        assertEquals("Parcela 1 renombrada", dbB.parcelDao().findById(p)?.displayName)
        val before = rows(dbB)
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, receive(dbB, accountB).outcome)
        assertEquals(before, rows(dbB))
    }

    @Test fun c2ArchiveByAReachesBWithItsInstantAndClosedPeriod() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(dbA, accountA, p))
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        converged("C2", p)
        val archived = dbB.parcelDao().findById(p)!!
        assertEquals(RecordStatus.ARCHIVED, archived.status)
        assertEquals(dbA.parcelDao().findById(p)?.metadata?.deletedAt, archived.metadata.deletedAt)
        assertNull(dbB.parcelDao().findCurrentMembership(p))
    }

    @Test fun c3ArchiveAndRestoreInAnotherFarmReceivedOnceAtTheEnd() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        Thread.sleep(5)
        assertEquals(AppResult.Success(Unit), parcels(dbA).restore(p, south))
        assertEquals(listOf(ParcelMutationOutcome.CONFIRMED_LOCAL_PENDING, ParcelMutationOutcome.SYNCED), push(dbA, accountA, p))
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        converged("C3", p)
        assertEquals(2, dbB.parcelDao().listMemberships(p).size)
        assertEquals(south, dbB.parcelDao().findCurrentMembership(p)?.farmId)
        assertEquals(3L, dbB.parcelDao().findById(p)?.metadata?.remoteVersion)
    }

    @Test fun c4RestoreIsAppliedOnBsCleanArchivedCopy() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        push(dbA, accountA, p)
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        assertEquals(RecordStatus.ARCHIVED, dbB.parcelDao().findById(p)?.status)
        assertEquals(AppResult.Success(Unit), parcels(dbA).restore(p, south))
        assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(dbA, accountA, p))
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        converged("C4", p)
        assertEquals(RecordStatus.ACTIVE, dbB.parcelDao().findById(p)?.status)
    }

    /** C5: expected block. B's offline edit is kept and refused by CAS; nothing converges by itself. */
    @Test fun c5OfflineEditOnBAgainstAnArchiveFromAIsAnExpectedBlock() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertTrue(parcels(dbB).update(p, ParcelChanges("Edición de B sin conexión")) is AppResult.Success)
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(dbA, accountA, p))
        val report = receive(dbB, accountB)
        assertEquals(listOf(ParentConflict(ParentKind.PARCEL, p, "remote_ahead")), report.conflicts)
        assertEquals("Edición de B sin conexión", dbB.parcelDao().findById(p)?.displayName)
        assertEquals(listOf(ParcelMutationOutcome.NEEDS_REBASE), push(dbB, accountB, p))
        val blocked = open(dbB, p).single()
        assertEquals(OutboxStatus.BLOCKED, blocked.status)
        assertEquals("NEEDS_REBASE", blocked.lastErrorCode)
        assertEquals("Edición de B sin conexión", dbB.parcelDao().findById(p)?.displayName)
        assertEquals(RecordStatus.ARCHIVED, server.parcels.getValue(p).status.let(RecordStatus::valueOf))
        assertNotEquals(canonical(dbB, p), serverCanonical(p))
        log("C5", p, "BLOCKED_EXPECTED explicit resolution required (NEEDS_REBASE, remote_ahead)")
    }

    /** C6: expected block. Both archive offline; A wins the CAS, B is held for an explicit decision. */
    @Test fun c6BothArchiveOfflineAndOnlyTheFirstWins() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        assertEquals(AppResult.Success(Unit), parcels(dbB).archive(p))
        assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(dbA, accountA, p))
        assertEquals(listOf(ParcelMutationOutcome.NEEDS_REBASE), push(dbB, accountB, p))
        // After NEEDS_REBASE the sender marks B's parcel CONFLICT, which the receiver reports first.
        assertEquals(listOf(ParentConflict(ParentKind.PARCEL, p, "local_conflict")), receive(dbB, accountB).conflicts)
        assertEquals(1, server.applied.count { it.second == p && it.first == ParcelMutationKind.ARCHIVE })
        log("C6", p, "BLOCKED_EXPECTED explicit resolution required (concurrent ARCHIVE)")
    }

    @Test fun c7LostResponseOnAAndBReceivesBeforeARecoversTheReceipt() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        val op = open(dbA, p).single().id
        assertEquals(ParcelMutationOutcome.STAGED, sender(dbA, accountA).stage(op))
        server.lostAfterApply = true
        assertEquals(ParcelMutationOutcome.OUTCOME_UNKNOWN, sender(dbA, accountA).send(op, lease))
        server.lostAfterApply = false
        assertEquals(OutboxStatus.FAILED, dbA.syncOutboxDao().findById(op)?.status)
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        assertEquals(serverCanonical(p), canonical(dbB, p))
        assertEquals(ParcelMutationOutcome.SYNCED, sender(dbA, accountA).send(op, lease))
        assertEquals(1, server.applied.count { it.second == p && it.first == ParcelMutationKind.ARCHIVE })
        converged("C7", p)
    }

    @Test fun c8ALostReadOnBWritesNothingAndTheNextReadApplies() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertTrue(parcels(dbA).update(p, ParcelChanges("Tras lectura perdida")) is AppResult.Success)
        push(dbA, accountA, p)
        val before = rows(dbB)
        server.readLost = true
        assertEquals(ParentReceiveOutcome.OFFLINE, receive(dbB, accountB).outcome)
        server.readLost = false
        assertEquals(before, rows(dbB))
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        converged("C8", p)
    }

    @Test fun c9IdentityAndGrantChangesBetweenFetchAndCommitLeaveBByteIdentical() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        push(dbA, accountA, p)
        val before = rows(dbB)
        server.duringRead = { accountB.logout(false); storeB.write(AccountRecord(session = AuthSession(c, "c@example.test", "x", "r", now.plusSeconds(3600)))) }
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receive(dbB, accountB).outcome)
        accountB = device(storeB, a)
        server.duringRead = { grant(dbB, emptyList()) }
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receive(dbB, accountB).outcome)
        server.duringRead = null
        grant(dbB, listOf("read", "write"))
        server.subjectOverride = c
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receive(dbB, accountB).outcome)
        server.subjectOverride = null
        accountB.logout(false)
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receive(dbB, accountB).outcome)
        assertEquals(before, rows(dbB))
        accountB = device(storeB, a)
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        converged("C9", p)
    }

    /** C10: P converges; B's offline Pesada over P is untouched and still pending: its send is #808. */
    @Test fun c10OfflinePesadaOnBSurvivesTheArchiveAndStaysPendingForIssue808() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        val delivery = UUID.randomUUID()
        val meta = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)
        dbB.deliveryDao().upsert(DeliveryEntity(id = delivery, workspaceId = workspace, farmId = north,
            campaignId = UUID.randomUUID(), deliveryDate = LocalDate.parse("2026-02-01"), destinationName = "Cooperativa",
            netGrams = 1_540_000, source = "MANUAL", metadata = meta))
        dbB.deliveryDao().upsertParcels(listOf(DeliveryParcelEntity(id = UUID.randomUUID(), workspaceId = workspace,
            deliveryId = delivery, parcelId = p, parcelNameAtDelivery = "Parcela 1", allocationMode = "FULL", metadata = meta)))
        dbB.syncOutboxDao().insert(SyncOutboxEntity(id = UUID.randomUUID(), entityType = SyncEntityType.DELIVERY, entityId = delivery,
            operation = OutboxOperation.CREATE, payloadVersion = 1, createdAt = now, updatedAt = now))
        val pesada = rows(dbB, "deliveries") + rows(dbB, "delivery_parcels") + rows(dbB, "sync_outbox")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        push(dbA, accountA, p)
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        converged("C10", p)
        assertEquals(pesada, rows(dbB, "deliveries") + rows(dbB, "delivery_parcels") + rows(dbB, "sync_outbox"))
        log("C10", p, "PARCEL_CONVERGED pesada=PENDING BLOCKED_BY_808 (sending a pre-archive Pesada needs the historical writer)")
    }

    @Test fun c11ACorruptPeriodOnTheServerHoldsOnlyItsParcelWhileTheOthersAdvance() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        val q = confirmedParcel("Parcela 2")
        val pUnit = unit(dbB, p)
        // Server-side corruption of p: same period id, another farm, newer versions.
        server.parcels.getValue(p).version = 2
        server.memberships.values.single { it.parcel == p }.apply { farm = south; version = 2 }
        assertTrue(parcels(dbA).update(q, ParcelChanges("Parcela 2 avanza")) is AppResult.Success)
        push(dbA, accountA, q)
        val report = receive(dbB, accountB)
        assertEquals(listOf(ParentConflict(ParentKind.MEMBERSHIP, server.memberships.values.single { it.parcel == p }.id,
            "immutable_field_changed")), report.conflicts)
        assertEquals(pUnit, unit(dbB, p))
        converged("C11", q)
    }

    @Test fun c12SeveralEditsBothWaysKeepVersionEqualToRemoteVersionOnBothDevices() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        for (i in 1..3) {
            assertTrue(parcels(dbA).update(p, ParcelChanges("Edición $i de A")) is AppResult.Success)
            assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(dbA, accountA, p))
        }
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        assertTrue(parcels(dbB).update(p, ParcelChanges("Edición de B")) is AppResult.Success)
        assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(dbB, accountB, p))
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbA, accountA).outcome)
        converged("C12", p)
        for (db in listOf(dbA, dbB)) db.parcelDao().findById(p)!!.metadata.let {
            assertEquals(5L, it.remoteVersion); assertEquals(it.version, it.remoteVersion)
        }
    }

    @Test fun c13RoomRestartsBetweenStagingSendingAndReceivingChangeNothing() = runBlocking {
        val p = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels(dbA).archive(p))
        val op = open(dbA, p).single().id
        assertEquals(ParcelMutationOutcome.STAGED, sender(dbA, accountA).stage(op))
        dbA.close(); dbA = MaginaOlivoDatabase.create(context, names[0])
        assertEquals(ParcelMutationOutcome.SYNCED, sender(dbA, accountA).send(op, lease))
        dbB.close(); dbB = MaginaOlivoDatabase.create(context, names[1])
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        dbB.close(); dbB = MaginaOlivoDatabase.create(context, names[1])
        converged("C13", p)
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, receive(dbB, accountB).outcome)
    }

    /**
     * #813 condition 5. The server may carry nanoseconds (deleted_at_exact, *_exact); Room keeps
     * milliseconds. Criterion: Android stores the floor to the millisecond and compares at that
     * precision; the server text is never rewritten by the receiver. No exact equality is claimed.
     */
    @Test fun precisionIsMillisecondFloorOnAndroid() = runBlocking {
        val p = UUID.randomUUID(); val m = UUID.randomUUID()
        server.seed(p, "Nanosegundos", status = "ARCHIVED", archivedAt = "2026-03-01T10:00:00.123999999Z", version = 2)
        server.memberships[m] = SMember(m, north, p, "2026-01-01T00:00:00.000999999Z", "2026-03-01T10:00:00.123999999Z", 2)
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        val row = dbB.parcelDao().findById(p)!!
        assertEquals(Instant.parse("2026-03-01T10:00:00.123Z"), row.metadata.deletedAt)
        val period = dbB.parcelDao().listMemberships(p).single()
        assertEquals(Instant.parse("2026-01-01T00:00:00.000Z"), period.validFrom)
        assertEquals(Instant.parse("2026-03-01T10:00:00.123Z"), period.validUntil)
        assertEquals("2026-03-01T10:00:00.123999999Z", server.parcels.getValue(p).archivedAt)
        assertEquals(serverCanonical(p), canonical(dbB, p))
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, receive(dbB, accountB).outcome)
    }

    // ---- devices, producers, senders and receivers ----------------------------------------------

    private fun device(store: MemoryAccountSessionStore, user: UUID): AccountRepository {
        store.write(AccountRecord(session = AuthSession(user, "$user@example.test", "access", "refresh", now.plusSeconds(3600))))
        return AccountRepository(FakeAuth, store) { Instant.now() }
    }
    private suspend fun grant(db: MaginaOlivoDatabase, permissions: List<String>) = db.workspaceAuthorizationCacheDao().upsert(
        WorkspaceAuthorizationCacheEntity(issuer, a.toString(), """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[${if (permissions.isEmpty()) "" else """{"id":"$workspace","name":"Olivar A","role":"OWNER","permissions":[${permissions.joinToString(",") { "\"$it\"" }}],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}"""}]}"""))
    private fun parcels(db: MaginaOlivoDatabase) = OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), workspaceRepository = FixtureWorkspace { db })
    private fun sender(db: MaginaOlivoDatabase, account: AccountRepository) =
        ParcelMutationSendCoordinator(db, account, issuer, server, { ledger }, { Instant.now() })
    private suspend fun receive(db: MaginaOlivoDatabase, account: AccountRepository) =
        ParentReceiveV2Coordinator(db, account, issuer, server) { Instant.now() }.receive(workspace)
    private suspend fun open(db: MaginaOlivoDatabase, p: UUID) =
        db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, p).filter { it.status != OutboxStatus.CONFIRMED }
    /** Stages and sends every open marker of the parcel in creation order, as the sender requires. */
    private suspend fun push(db: MaginaOlivoDatabase, account: AccountRepository, p: UUID): List<ParcelMutationOutcome> =
        open(db, p).map { op ->
            assertEquals(ParcelMutationOutcome.STAGED, sender(db, account).stage(op.id))
            sender(db, account).send(op.id, lease)
        }

    /** The app's producer on A, then the #800 CREATE confirmed; the server gets the same row; B receives it. */
    private suspend fun confirmedParcel(name: String): UUID {
        val p = (parcels(dbA).create(NewParcel(north, name, municipality = "Bedmar", province = "Jaén",
            managedAreaM2 = 1234.5)) as AppResult.Success).value
        val sql = dbA.openHelper.writableDatabase
        sql.execSQL("UPDATE sync_outbox SET status='CONFIRMED', account_id='$a', farm_id='$north' WHERE entity_type='PARCEL' AND entity_id='$p'")
        sql.execSQL("UPDATE parcels SET remote_version=1, sync_status='SYNCED' WHERE id='$p'")
        sql.execSQL("UPDATE farm_parcel_memberships SET remote_version=1, sync_status='SYNCED' WHERE parcel_id='$p'")
        val row = dbA.parcelDao().findById(p)!!
        val memberships = dbA.parcelDao().listMemberships(p)
        server.seed(p, fields = parcelFieldsPayload(row, memberships))
        memberships.single().let { server.memberships[it.id] = SMember(it.id, it.farmId, p, instantText(it.validFrom), null, 1) }
        assertEquals(ParentReceiveOutcome.IMPORTED, receive(dbB, accountB).outcome)
        return p
    }

    // ---- convergence evidence -------------------------------------------------------------------

    private fun floor(text: String?) = text?.let { OffsetDateTime.parse(it).toInstant().truncatedTo(ChronoUnit.MILLIS).toEpochMilli() }

    /** Content of one parcel unit at Room precision: fields, status, deletedAt, versions and periods. */
    private suspend fun canonical(db: MaginaOlivoDatabase, p: UUID): String {
        val row = db.parcelDao().findById(p) ?: return "absent"
        val periods = db.parcelDao().listMemberships(p).filter { it.metadata.deletedAt == null }.sortedBy { it.id.toString() }
        return listOf(sortedJson(parcelFieldsPayload(row, periods.ifEmpty { listOf(placeholder(p)) })), row.status.name,
            row.metadata.deletedAt?.toEpochMilli(), row.metadata.remoteVersion,
            periods.joinToString(";") { "${it.id}|${it.farmId}|${it.validFrom.toEpochMilli()}|${it.validUntil?.toEpochMilli()}|${it.metadata.remoteVersion}" },
        ).joinToString("#")
    }
    /** Field content independent of key order: the producer and the fake may list keys differently. */
    private fun sortedJson(fields: JsonObject) = JsonObject(fields.toSortedMap()).toString()
    private fun placeholder(p: UUID) = com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity(
        UUID(0, 0), workspace, north, p, Instant.EPOCH, null, LocalMetadata(Instant.EPOCH, Instant.EPOCH))
    private fun serverCanonical(p: UUID): String {
        val s = server.parcels.getValue(p)
        val periods = server.memberships.values.filter { it.parcel == p }.sortedBy { it.id.toString() }
        return listOf(sortedJson(s.fields), s.status, floor(s.archivedAt), s.version,
            periods.joinToString(";") { "${it.id}|${it.farm}|${floor(it.from)}|${floor(it.until)}|${it.version}" },
        ).joinToString("#")
    }
    private suspend fun converged(scene: String, p: UUID) {
        val (ca, cb, cs) = Triple(canonical(dbA, p), canonical(dbB, p), serverCanonical(p))
        assertEquals("$scene A vs server", cs, ca)
        assertEquals("$scene B vs server", cs, cb)
        for (db in listOf(dbA, dbB)) {
            assertTrue("$scene outbox", open(db, p).isEmpty())
            db.parcelDao().findById(p)!!.metadata.let { assertEquals("$scene version", it.remoteVersion, it.version) }
        }
        log(scene, p, "CONVERGED")
    }
    private suspend fun log(scene: String, p: UUID, verdict: String) {
        fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
        suspend fun outbox(db: MaginaOlivoDatabase) = run {
            db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, p).joinToString(",") { "${it.operation}:${it.status}${it.lastErrorCode?.let { c -> "/$c" } ?: ""}" }
        }
        Log.i("G26CONV", "G26-CONV $scene $verdict hashA=${hash(canonical(dbA, p))} hashB=${hash(canonical(dbB, p))} " +
            "hashServer=${hash(serverCanonical(p))} outboxA=[${outbox(dbA)}] outboxB=[${outbox(dbB)}]")
    }
    private fun rows(db: MaginaOlivoDatabase, vararg tables: String): List<String> =
        (tables.ifEmpty { arrayOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox") }).flatMap { table ->
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
            }
        }
    private fun unit(db: MaginaOlivoDatabase, p: UUID): List<String> =
        listOf("SELECT * FROM parcels WHERE id='$p'", "SELECT * FROM farm_parcel_memberships WHERE parcel_id='$p' ORDER BY id").flatMap { q ->
            db.openHelper.readableDatabase.query(q).use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
            }
        }

    // ---- shared fake server ---------------------------------------------------------------------

    private class SParcel(val id: UUID, var fields: JsonObject, var status: String, var archivedAt: String?, var version: Long,
        val createdAt: String, var updatedAt: String)
    private class SMember(val id: UUID, var farm: UUID, val parcel: UUID, val from: String, var until: String?, var version: Long,
        var updatedAt: String = "2026-10-01T00:00:00Z")

    /** #805 (update/archive/restore with CAS and receipts), check_sync_operations and parents v2. */
    private inner class Server : DeliveryRpcTransport {
        val farms = linkedMapOf<UUID, String>()
        val parcels = linkedMapOf<UUID, SParcel>()
        val memberships = linkedMapOf<UUID, SMember>()
        val applied = mutableListOf<Pair<ParcelMutationKind, UUID>>()
        var lostAfterApply = false
        var readLost = false
        var subjectOverride: UUID? = null
        var duringRead: (suspend () -> Unit)? = null
        private val receipts = mutableMapOf<UUID, String>()
        private val replays = mutableMapOf<UUID, Triple<String, Long, String>>()
        private var clock = 0L

        private fun tick() = Instant.parse("2026-10-20T00:00:00Z").plusSeconds(++clock).toString()
        fun seed(p: UUID, name: String = "P", fields: JsonObject? = null, status: String = "ACTIVE", archivedAt: String? = null, version: Long = 1) {
            val base = fields ?: JsonObject(mapOf("displayName" to JsonPrimitive(name), "cadastralReference" to JsonNull,
                "cadastralPolygon" to JsonNull, "cadastralParcel" to JsonNull, "municipality" to JsonNull, "province" to JsonNull,
                "source" to JsonPrimitive("MANUAL"), "geometryGeoJson" to JsonNull, "sourceProvider" to JsonNull,
                "sourceImportedAt" to JsonNull, "cadastralAreaM2" to JsonNull, "managedAreaM2" to JsonNull, "notes" to JsonNull,
                "oliveTreeCount" to JsonNull, "variety" to JsonNull, "irrigationSystem" to JsonNull, "irrigationNetwork" to JsonNull,
                "irrigationSector" to JsonNull, "irrigationDays" to JsonNull))
            val at = tick()
            parcels[p] = SParcel(p, JsonObject(base.filterKeys { it in FIELDS }), status, archivedAt, version, at, at)
        }

        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            if (name == "get_own_workspace_parents_v2") {
                duringRead?.invoke()
                if (readLost) throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
                return DeliveryRpcResponse(200, graph())
            }
            if (name == "check_sync_operations") {
                val op = UUID.fromString((body.getValue("p_operation_ids") as JsonArray).single().jsonPrimitive.content)
                return DeliveryRpcResponse(200, """{"success":true,"confirmed_operations":[],"confirmed_receipts":[${receipts[op] ?: ""}]}""")
            }
            val kind = ParcelMutationKind.entries.single { it.rpc == name }
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val id = UUID.fromString(body.getValue("p_parcel_id").jsonPrimitive.content)
            val expected = body.getValue("p_expected_version").jsonPrimitive.long
            val mutation = body.getValue(kind.argument) as JsonObject
            val parcel = parcels[id] ?: return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val hash = parcelMutationHash(kind, id, expected, mutation)
            replays[op]?.let { (saved, version, touched) ->
                if (saved != hash) return conflict("RECEIPT_CONFLICT")
                return respond(ack(op, id, kind, version, touched, true))
            }
            if (parcel.version != expected) return conflict("PARCEL_VERSION_CONFLICT")
            val touched = when (kind) {
                ParcelMutationKind.UPDATE -> { parcel.fields = mutation; "[]" }
                ParcelMutationKind.ARCHIVE -> {
                    val closing = UUID.fromString(mutation.getValue("closeMembershipId").jsonPrimitive.content)
                    val row = memberships[closing]?.takeIf { it.until == null && parcel.status == "ACTIVE" }
                        ?: return conflict("MEMBERSHIP_NOT_CURRENT")
                    val at = mutation.getValue("archivedAt").jsonPrimitive.content
                    row.until = at; row.version += 1; row.updatedAt = tick()
                    parcel.status = "ARCHIVED"; parcel.archivedAt = at
                    """[{"id":"$closing","version":${row.version}}]"""
                }
                ParcelMutationKind.RESTORE -> {
                    val open = mutation.getValue("openMembership") as JsonObject
                    val openId = UUID.fromString(open.getValue("id").jsonPrimitive.content)
                    if (parcel.status != "ARCHIVED") return conflict("PARCEL_NOT_ARCHIVED")
                    memberships[openId] = SMember(openId, UUID.fromString(open.getValue("farmId").jsonPrimitive.content), id,
                        open.getValue("validFrom").jsonPrimitive.content, null, 1, tick())
                    parcel.status = "ACTIVE"; parcel.archivedAt = null
                    """[{"id":"$openId","version":1}]"""
                }
            }
            parcel.version += 1; parcel.updatedAt = tick()
            applied += kind to id
            replays[op] = Triple(hash, parcel.version, touched)
            receipts[op] = """{"operation_id":"$op","entity_id":"$id","entity_type":"PARCEL_${kind.name}","remote_version":${parcel.version},"payload_version":1,"payload_hash":"$hash"}"""
            return respond(ack(op, id, kind, parcel.version, touched, false))
        }
        private fun respond(answer: DeliveryRpcResponse) =
            if (lostAfterApply) throw DeliveryRpcFailure(DeliveryRpcError.NETWORK) else answer
        private fun conflict(reason: String) = DeliveryRpcResponse(409, """{"code":"23505","message":"parcel conflict: $reason"}""")
        private fun ack(op: UUID, id: UUID, kind: ParcelMutationKind, version: Long, touched: String, replay: Boolean) =
            DeliveryRpcResponse(200, """{"success":true,"operation_id":"$op","entity_id":"$id","kind":"${kind.name}","status":"APPLIED","remote_version":$version,"payload_version":1,"memberships":$touched,"idempotent":$replay}""")
        private fun text(value: String?) = value?.let { "\"$it\"" } ?: "null"
        fun graph(): String {
            val at = "2026-10-01T00:00:00Z"
            val farmsJson = farms.entries.joinToString(",") { (id, n) -> """{"id":"$id","workspaceId":"$workspace","name":"$n","description":null,"municipality":"Bedmar","province":"Jaén","notes":null,"status":"ACTIVE","archivedAt":null,"createdAt":"$at","updatedAt":"$at","version":1}""" }
            val parcelsJson = parcels.values.joinToString(",") { p ->
                JsonObject(p.fields + mapOf("id" to JsonPrimitive(p.id.toString()), "workspaceId" to JsonPrimitive(workspace.toString()),
                    "status" to JsonPrimitive(p.status), "archivedAt" to (p.archivedAt?.let(::JsonPrimitive) ?: JsonNull),
                    "createdAt" to JsonPrimitive(p.createdAt), "updatedAt" to JsonPrimitive(p.updatedAt),
                    "version" to JsonPrimitive(p.version))).toString()
            }
            val periods = memberships.values.joinToString(",") { m -> """{"id":"${m.id}","workspaceId":"$workspace","farmId":"${m.farm}","parcelId":"${m.parcel}","validFrom":"${m.from}","validUntil":${text(m.until)},"createdAt":"$at","updatedAt":"${m.updatedAt}","version":${m.version}}""" }
            return """{"contract_version":2,"issuer":"$issuer/auth/v1","subject":"${subjectOverride ?: a}","workspace":{"id":"$workspace","name":"Olivar A","ownerUserId":"$a","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"$at","updatedAt":"$at","version":1},"farms":[$farmsJson],"parcels":[$parcelsJson],"memberships":[$periods]}"""
        }
    }

    private object FakeAuth : AccountAuthApi {
        override val redirectUri = "app://callback"
        override suspend fun signIn(email: String, password: String): AuthSession = error("no login")
        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = error("no signup")
        override suspend fun recover(email: String, challenge: String) = error("no recovery")
        override suspend fun exchange(code: String, verifier: String): AuthSession = error("no exchange")
        override suspend fun refresh(session: AuthSession): AuthSession = error("no refresh")
        override suspend fun user(session: AuthSession): Pair<UUID, String> = session.userId to session.email
        override suspend fun changePassword(session: AuthSession, password: String) = error("no password")
        override suspend fun signOut(session: AuthSession) = Unit
    }

    private companion object {
        val FIELDS = setOf("displayName", "cadastralReference", "cadastralPolygon", "cadastralParcel", "municipality",
            "province", "source", "geometryGeoJson", "sourceProvider", "sourceImportedAt", "cadastralAreaM2", "managedAreaM2",
            "notes", "oliveTreeCount", "variety", "irrigationSystem", "irrigationNetwork", "irrigationSector", "irrigationDays")
    }
}
