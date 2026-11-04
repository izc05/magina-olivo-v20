package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
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
import com.isivoltpro.maginaolivo.data.repository.sync.parcelMutationHash
import com.isivoltpro.maginaolivo.data.repository.sync.parseParcelMutationEnvelope
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.accountlink.FarmChoice
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkConsent
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkRevocation
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-only G2.6 PARCEL UPDATE/ARCHIVE/RESTORE checks against a fake modelling the reviewed #805 RPCs. */
@RunWith(AndroidJUnit4::class)
class ParcelMutationSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-parcel-mutation-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val north = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val south = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private var now = Instant.now()
    private val store = MemoryAccountSessionStore()
    private val auth = FakeAuth()
    private val server = Server()
    private var ledger: FarmLinkLedger? = null
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private lateinit var lease: DeliveryDispatchLease

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-parcel-mutation-room"))!!
        login(a)
        // WORKSPACE (#798) and both FARMs (#799) already confirmed: SYNCED, remote v1, bound CREATEs CONFIRMED.
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar A','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id) VALUES ('${UUID.randomUUID()}','WORKSPACE','$workspace','CREATE',1,'CONFIRMED',0,1000,1000,'$a')")
        for ((farm, farmName) in listOf(north to "Finca Norte", south to "Finca Sur")) {
            sql.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$farm','$workspace','$farmName','ACTIVE',1000,1000,1,'SYNCED',1)")
            sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,farm_id) VALUES ('${UUID.randomUUID()}','FARM','$farm','CREATE',1,'CONFIRMED',0,1000,1000,'$a','$farm')")
            server.farmOwners[farm] = a
        }
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        grant(listOf("read", "write", "manage_members"))
        server.workspaceOwners[workspace] = a
        consent(north, south)
    }

    @After fun close() {
        if (::lease.isInitialized) lease.close()
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun archiveThenRestoreInAnotherFarmInStrictOrderKeepingUuidsAndDates() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        val first = db.parcelDao().listMemberships(parcel).single()
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        Thread.sleep(5)
        assertEquals(AppResult.Success(Unit), parcels().restore(parcel, south))
        val (archive, restore) = open(parcel)
        assertEquals(listOf(OutboxOperation.ARCHIVE, OutboxOperation.RESTORE), listOf(archive.operation, restore.operation))
        // The producer writes the marker and the membership change with one instant: the proof used to classify it.
        val closed = db.parcelDao().listMemberships(parcel).single { it.id == first.id }
        val opened = db.parcelDao().listMemberships(parcel).single { it.id != first.id }
        assertEquals(archive.createdAt, closed.validUntil)
        assertEquals(restore.createdAt, opened.validFrom)

        assertEquals(ParcelMutationOutcome.WAITING_PREVIOUS, sender().stage(restore.id))
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(1L, db.syncOutboxDao().findById(archive.id)?.baseRemoteVersion)
        assertEquals(north, db.syncOutboxDao().findById(archive.id)?.farmId)
        // The RESTORE marker is still local: the ARCHIVE is confirmed, the parcel stays pending.
        assertEquals(ParcelMutationOutcome.CONFIRMED_LOCAL_PENDING, sender().send(archive.id, lease))
        assertEquals(2L, db.parcelDao().findById(parcel)?.metadata?.remoteVersion)
        assertEquals(2L, db.parcelDao().listMemberships(parcel).single { it.id == first.id }.metadata.remoteVersion)

        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(restore.id))
        assertEquals(south, db.syncOutboxDao().findById(restore.id)?.farmId)
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(restore.id, lease))
        val row = db.parcelDao().findById(parcel)!!
        assertEquals(RecordStatus.ACTIVE, row.status)
        assertEquals(3L, row.metadata.remoteVersion)
        assertEquals(SyncStatus.SYNCED, row.metadata.syncStatus)
        assertEquals(1L, db.parcelDao().listMemberships(parcel).single { it.id == opened.id }.metadata.remoteVersion)

        // The server holds the same UUIDs, instants and farms; the closed period is kept.
        val remote = server.parcels.getValue(parcel)
        assertEquals(3L, remote.version)
        assertEquals(closed.validUntil.toString(), remote.memberships.getValue(first.id).until)
        assertEquals(north, remote.memberships.getValue(first.id).farm)
        assertEquals(south, remote.memberships.getValue(opened.id).farm)
        assertEquals(opened.validFrom.toString(), remote.memberships.getValue(opened.id).from)
        assertNull(remote.memberships.getValue(opened.id).until)
        assertEquals(listOf("archive_own_parcel_v1", "check_sync_operations", "restore_own_parcel_v1", "check_sync_operations"),
            server.calls.map { it.first })
        assertEquals(ParcelMutationOutcome.ALREADY_CONFIRMED, sender().send(restore.id, lease))
    }

    @Test fun archiveWaitsForEveryUnconfirmedDependentWithoutConsumingAttempts() = runBlocking {
        val parcel = confirmedParcel("Parcela con gasto")
        val expense = UUID.randomUUID()
        db.expenseDao().upsert(ExpenseEntity(expense, workspace, farmId = north, parcelId = parcel,
            expenseDate = LocalDate.parse("2026-02-01"), concept = "Poda", category = "LABOUR", amountMinor = 12_000,
            currency = "EUR", metadata = LocalMetadata(now, now)))
        val dependent = SyncOutboxEntity(id = UUID.randomUUID(), entityType = SyncEntityType.EXPENSE, entityId = expense,
            operation = OutboxOperation.CREATE, payloadVersion = 1, createdAt = now, updatedAt = now)
        db.syncOutboxDao().insert(dependent)
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = open(parcel).single()
        for ((status, cause) in listOf(OutboxStatus.PENDING to "EXPENSE:PENDING=1",
            OutboxStatus.FAILED to "EXPENSE:FAILED=1", OutboxStatus.BLOCKED to "EXPENSE:BLOCKED=1")) {
            // FAILED is OUTCOME_UNKNOWN: never taken as confirmed without its receipt.
            db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='${status.name}' WHERE id='${dependent.id}'")
            assertEquals(status.name, ParcelMutationOutcome.WAITING_DEPENDENCIES, sender().stage(archive.id))
            val waiting = db.syncOutboxDao().findById(archive.id)!!
            assertEquals(OutboxStatus.PENDING, waiting.status)
            assertEquals(0, waiting.attemptCount)
            assertNull(waiting.accountId)
            assertNull(waiting.payloadJson)
            assertEquals("WAITING_DEPENDENCIES", waiting.lastErrorCode)
            assertEquals(cause, waiting.lastErrorMessage)
            assertEquals(listOf(dependent.id), sender().archiveDependencies(parcel).map { it.id })
        }
        assertTrue(server.calls.isEmpty())
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='CONFIRMED' WHERE id='${dependent.id}'")
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertNull(db.syncOutboxDao().findById(archive.id)?.lastErrorCode)
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(archive.id, lease))
        // Archiving is logical: the expense keeps its parcel.
        assertEquals(parcel, db.expenseDao().findById(expense)?.parcelId)
    }

    @Test fun lostResponseAndRestartReplayTheSameOperationAndPayloadOnce() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        assertTrue(parcels().update(parcel, ParcelChanges("Parcela renombrada", managedAreaM2 = 2500.0)) is AppResult.Success)
        val op = open(parcel).single().id
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(op))
        val payload = db.syncOutboxDao().findById(op)?.payloadJson
        assertEquals(ParcelMutationKind.UPDATE, parseParcelMutationEnvelope(payload!!)?.kind)
        for ((mode, attempts) in listOf(Mode.LOST_AFTER_APPLY to 1, Mode.ERROR_AFTER_APPLY to 2)) {
            server.mode = mode
            assertEquals(mode.name, ParcelMutationOutcome.OUTCOME_UNKNOWN, sender().send(op, lease))
            val current = db.syncOutboxDao().findById(op)!!
            assertEquals(OutboxStatus.FAILED, current.status)
            assertEquals(attempts, current.attemptCount)
            assertEquals(payload, current.payloadJson)
        }
        db.close(); db = MaginaOlivoDatabase.create(context, name)
        server.mode = Mode.NORMAL
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(op, lease))
        assertEquals(2L, server.parcels.getValue(parcel).version)
        assertEquals("Parcela renombrada", server.parcels.getValue(parcel).name)
        assertEquals(1, server.receipts.size)
        assertEquals(1, server.calls.filter { it.first == "update_own_parcel_v1" }.map { it.second }.distinct().size)
    }

    @Test fun versionConflictNeedsRebaseAndTypedServerCausesAreBlockedWithoutOverwriting() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        assertTrue(parcels().update(parcel, ParcelChanges("Edición sin conexión")) is AppResult.Success)
        val op = open(parcel).single().id
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(op))
        server.parcels.getValue(parcel).version = 2 // another device archived/edited first
        assertEquals(ParcelMutationOutcome.NEEDS_REBASE, sender().send(op, lease))
        val blocked = db.syncOutboxDao().findById(op)!!
        assertEquals(OutboxStatus.BLOCKED, blocked.status)
        assertEquals("NEEDS_REBASE", blocked.lastErrorCode)
        assertEquals(0, blocked.attemptCount)
        assertTrue(blocked.payloadJson != null)
        assertEquals("Edición sin conexión", db.parcelDao().findById(parcel)?.displayName)
        assertEquals(SyncStatus.CONFLICT, db.parcelDao().findById(parcel)?.metadata?.syncStatus)
        val calls = server.calls.size
        assertEquals(ParcelMutationOutcome.REJECTED, sender().send(op, lease))
        assertEquals(calls, server.calls.size)

        val other = confirmedParcel("En campaña")
        assertEquals(AppResult.Success(Unit), parcels().archive(other))
        val archive = open(other).single().id
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive))
        server.mode = Mode.CAMPAIGN_RUNNING
        assertEquals(ParcelMutationOutcome.CONFLICT, sender().send(archive, lease))
        assertEquals("ARCHIVE_BLOCKED_CAMPAIGN", db.syncOutboxDao().findById(archive)?.lastErrorCode)
        assertEquals(RecordStatus.ACTIVE, server.parcels.getValue(other).status)
    }

    @Test fun consentRevocationLogoutAndAccountSwitchKeepTheOutboxAndSendNothing() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        Thread.sleep(5)
        assertEquals(AppResult.Success(Unit), parcels().restore(parcel, south))
        val (archive, restore) = open(parcel)
        // Origin farm without consent: the ARCHIVE is not even staged.
        consent(south)
        assertEquals(ParcelMutationOutcome.CONSENT_REQUIRED, sender().stage(archive.id))
        consent(north, south)
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        val staged = db.syncOutboxDao().findById(archive.id)
        // Revoked after staging: same effect as LocalFarmLinkRepository.revokeConsentForFarm.
        ledger = ledger!!.copy(revocations = listOf(FarmLinkRevocation(UUID.randomUUID(), "$issuer|$a", north, now, 1)))
        db.syncOutboxDao().updateStatusForFarm(north, OutboxStatus.BLOCKED, now.toEpochMilli())
        assertEquals(ParcelMutationOutcome.CONSENT_REQUIRED, sender().send(archive.id, lease))
        assertEquals(staged?.payloadJson, db.syncOutboxDao().findById(archive.id)?.payloadJson)
        consent(north, south)
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='PENDING' WHERE id='${archive.id}'")
        account.logout(false)
        assertEquals(ParcelMutationOutcome.NOT_AUTHORIZED, sender().send(archive.id, lease))
        login(c)
        assertEquals(ParcelMutationOutcome.NOT_AUTHORIZED, sender().send(archive.id, lease))
        // Another account cannot bind the RESTORE either: it waits behind the unconfirmed ARCHIVE, unbound.
        assertEquals(ParcelMutationOutcome.WAITING_PREVIOUS, sender().stage(restore.id))
        auth.offline = true
        login(a)
        assertEquals(ParcelMutationOutcome.OFFLINE, sender().send(archive.id, lease))
        auth.offline = false
        assertTrue(server.calls.isEmpty())
        assertEquals(a.toString(), db.syncOutboxDao().findById(archive.id)?.accountId)
        assertNull(db.syncOutboxDao().findById(restore.id)?.accountId)
        // Target farm without consent: the RESTORE is held after the ARCHIVE is confirmed.
        assertEquals(ParcelMutationOutcome.CONFIRMED_LOCAL_PENDING, sender().send(archive.id, lease))
        consent(north)
        assertEquals(ParcelMutationOutcome.CONSENT_REQUIRED, sender().stage(restore.id))
        assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(restore.id)?.status)
    }

    @Test fun legacyMarkersAreClassifiedExactlyOrHeldForAnExplicitDecision() = runBlocking {
        // Before G2.6 «Archivar» wrote DELETE and «Restaurar» wrote UPDATE: same rows, same instants.
        val parcel = confirmedParcel("Parcela heredada")
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        Thread.sleep(5)
        assertEquals(AppResult.Success(Unit), parcels().restore(parcel, south))
        val (archive, restore) = open(parcel)
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET operation='DELETE' WHERE id='${archive.id}'")
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET operation='UPDATE' WHERE id='${restore.id}'")
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(ParcelMutationKind.ARCHIVE, parseParcelMutationEnvelope(db.syncOutboxDao().findById(archive.id)!!.payloadJson!!)?.kind)
        assertEquals(ParcelMutationOutcome.CONFIRMED_LOCAL_PENDING, sender().send(archive.id, lease))
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(restore.id))
        assertEquals(ParcelMutationKind.RESTORE, parseParcelMutationEnvelope(db.syncOutboxDao().findById(restore.id)!!.payloadJson!!)?.kind)
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(restore.id, lease))
        // The stored operation stays as written (audit); the envelope carries the typed kind.
        assertEquals(OutboxOperation.DELETE, db.syncOutboxDao().findById(archive.id)?.operation)

        // A legacy DELETE whose instant closes no membership proves nothing: held, unbound, never sent.
        val orphan = SyncOutboxEntity(id = UUID.randomUUID(), entityType = SyncEntityType.PARCEL, entityId = parcel,
            operation = OutboxOperation.DELETE, payloadVersion = 1, createdAt = Instant.now(), updatedAt = Instant.now())
        db.syncOutboxDao().insert(orphan)
        assertEquals(ParcelMutationOutcome.REVIEW_REQUIRED, sender().stage(orphan.id))
        val held = db.syncOutboxDao().findById(orphan.id)!!
        assertEquals(OutboxStatus.BLOCKED, held.status)
        assertEquals("REVIEW_REQUIRED", held.lastErrorCode)
        assertNull(held.accountId)
        assertNull(held.payloadJson)
        assertEquals(ParcelMutationOutcome.REJECTED, sender().stage(orphan.id))
        // ARCHIVE cannot be chosen without proof; an explicit UPDATE decision can, and is then sent.
        assertFalse(sender().resolveReview(orphan.id, ParcelMutationKind.ARCHIVE))
        assertTrue(sender().resolveReview(orphan.id, ParcelMutationKind.UPDATE))
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(orphan.id))
        assertEquals("RESOLVED_REVIEW", db.syncOutboxDao().findById(orphan.id)?.lastErrorCode)
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(orphan.id, lease))
        assertEquals(4L, server.parcels.getValue(parcel).version)
    }

    @Test fun offlineEditAfterStagingIsKeptPendingAndNeverOverwritten() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        assertTrue(parcels().update(parcel, ParcelChanges("Primera edición")) is AppResult.Success)
        val op = open(parcel).single().id
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(op))
        assertTrue(parcels().update(parcel, ParcelChanges("Segunda edición sin conexión")) is AppResult.Success)
        assertEquals(ParcelMutationOutcome.CONFIRMED_LOCAL_PENDING, sender().send(op, lease))
        val row = db.parcelDao().findById(parcel)!!
        assertEquals("Segunda edición sin conexión", row.displayName)
        assertEquals(SyncStatus.PENDING, row.metadata.syncStatus)
        assertEquals(2L, row.metadata.remoteVersion)
        assertEquals("Primera edición", server.parcels.getValue(parcel).name)
        val next = open(parcel).single()
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(next.id))
        assertEquals(2L, db.syncOutboxDao().findById(next.id)?.baseRemoteVersion)
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(next.id, lease))
        assertEquals("Segunda edición sin conexión", server.parcels.getValue(parcel).name)
    }

    private fun sender() = ParcelMutationSendCoordinator(db, account, issuer, server, { ledger }, { now })
    private fun parcels() = OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), workspaceRepository = FixtureWorkspace { db })

    /** The app's producer, then the #800 CREATE confirmed: parcel and membership at remote v1 on both sides. */
    private suspend fun confirmedParcel(parcelName: String): UUID {
        val parcel = (parcels().create(NewParcel(north, parcelName, municipality = "Bedmar", province = "Jaén",
            managedAreaM2 = 1234.5)) as AppResult.Success).value
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE sync_outbox SET status='CONFIRMED', account_id='$a', farm_id='$north' WHERE entity_type='PARCEL' AND entity_id='$parcel'")
        sql.execSQL("UPDATE parcels SET remote_version=1, sync_status='SYNCED' WHERE id='$parcel'")
        sql.execSQL("UPDATE farm_parcel_memberships SET remote_version=1, sync_status='SYNCED' WHERE parcel_id='$parcel'")
        val membership = db.parcelDao().listMemberships(parcel).single()
        server.parcels[parcel] = RemoteParcel(workspace, parcelName, 1, RecordStatus.ACTIVE,
            mutableMapOf(membership.id to RemoteMembership(north, membership.validFrom.toString(), null, 1)))
        return parcel
    }

    private suspend fun open(parcel: UUID) =
        db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).filter { it.status != OutboxStatus.CONFIRMED }

    private fun consent(vararg farms: UUID) {
        ledger = FarmLinkLedger(listOf(FarmLinkConsent(UUID.randomUUID(), "$issuer|$a",
            farms.map { FarmChoice(it, workspace, if (it == north) "Finca Norte" else "Finca Sur", 1) },
            UUID.randomUUID(), "b".repeat(64), now)))
    }
    private suspend fun grant(permissions: List<String>) = db.workspaceAuthorizationCacheDao().upsert(
        WorkspaceAuthorizationCacheEntity(issuer, a.toString(),
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"Olivar A","role":"OWNER","permissions":[${permissions.joinToString(",") { "\"$it\"" }}],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""))
    private fun login(id: UUID) {
        store.write(AccountRecord(session = AuthSession(id, "$id@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(auth, store) { now }
    }

    private enum class Mode { NORMAL, LOST_AFTER_APPLY, ERROR_AFTER_APPLY, CAMPAIGN_RUNNING }
    private data class RemoteMembership(val farm: UUID, val from: String, var until: String?, var version: Long)
    private data class RemoteParcel(val workspace: UUID, var name: String, var version: Long, var status: RecordStatus,
        val memberships: MutableMap<UUID, RemoteMembership>)
    private data class Receipt(val kind: ParcelMutationKind, val parcel: UUID, val hash: String, val version: Long,
        val touched: String)

    /** The three #805 RPCs + check_sync_operations: owner-only, owned farms, CAS, exact replays, typed causes. */
    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        val calls = mutableListOf<Pair<String, String>>()
        val workspaceOwners = mutableMapOf<UUID, UUID>()
        val farmOwners = mutableMapOf<UUID, UUID>()
        val parcels = mutableMapOf<UUID, RemoteParcel>()
        val receipts = mutableMapOf<Pair<UUID, UUID>, Receipt>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += name to bodyJson
            val actor = (store.read().session ?: return DeliveryRpcResponse(401, "{}")).userId
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            if (name == "check_sync_operations") {
                val op = UUID.fromString((body.getValue("p_operation_ids") as JsonArray).single().jsonPrimitive.content)
                val rows = receipts[actor to op]?.let { r ->
                    """[{"operation_id":"$op","entity_id":"${r.parcel}","entity_type":"PARCEL_${r.kind.name}","remote_version":${r.version},"payload_version":1,"payload_hash":"${r.hash}"}]"""
                } ?: "[]"
                return DeliveryRpcResponse(200, """{"success":true,"confirmed_operations":[],"confirmed_receipts":$rows}""")
            }
            val kind = ParcelMutationKind.entries.single { it.rpc == name }
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val id = UUID.fromString(body.getValue("p_parcel_id").jsonPrimitive.content)
            val expected = body.getValue("p_expected_version").jsonPrimitive.long
            val mutation = body.getValue(kind.argument) as JsonObject
            val parcel = parcels[id] ?: return DeliveryRpcResponse(403, """{"code":"42501"}""")
            if (workspaceOwners[parcel.workspace] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val hash = parcelMutationHash(kind, id, expected, mutation)
            val saved = receipts[actor to op]
            if (saved != null) {
                if (saved.kind != kind || saved.parcel != id || saved.hash != hash) return conflict("RECEIPT_CONFLICT")
                return respond(ack(op, id, kind, saved.version, saved.touched, true))
            }
            if (parcel.version != expected) return conflict("PARCEL_VERSION_CONFLICT")
            val touched = when (kind) {
                ParcelMutationKind.UPDATE -> {
                    parcel.name = mutation.getValue("displayName").jsonPrimitive.content
                    "[]"
                }
                ParcelMutationKind.ARCHIVE -> {
                    if (mode == Mode.CAMPAIGN_RUNNING) return conflict("ARCHIVE_BLOCKED_CAMPAIGN")
                    val closing = UUID.fromString(mutation.getValue("closeMembershipId").jsonPrimitive.content)
                    val row = parcel.memberships[closing]?.takeIf { it.until == null && parcel.status == RecordStatus.ACTIVE }
                        ?: return conflict("MEMBERSHIP_NOT_CURRENT")
                    if (farmOwners[row.farm] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
                    row.until = mutation.getValue("archivedAt").jsonPrimitive.content; row.version += 1
                    parcel.status = RecordStatus.ARCHIVED
                    """[{"id":"$closing","version":${row.version}}]"""
                }
                ParcelMutationKind.RESTORE -> {
                    val open = mutation.getValue("openMembership") as JsonObject
                    val openId = UUID.fromString(open.getValue("id").jsonPrimitive.content)
                    val farm = UUID.fromString(open.getValue("farmId").jsonPrimitive.content)
                    if (parcel.status != RecordStatus.ARCHIVED) return conflict("PARCEL_NOT_ARCHIVED")
                    if (farmOwners[farm] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
                    parcel.memberships[openId] = RemoteMembership(farm, open.getValue("validFrom").jsonPrimitive.content, null, 1)
                    parcel.status = RecordStatus.ACTIVE
                    """[{"id":"$openId","version":1}]"""
                }
            }
            parcel.version += 1
            receipts[actor to op] = Receipt(kind, id, hash, parcel.version, touched)
            return respond(ack(op, id, kind, parcel.version, touched, false))
        }
        /** The server committed (or had committed) but the answer never arrives intact. */
        private fun respond(answer: DeliveryRpcResponse): DeliveryRpcResponse = when (mode) {
            Mode.LOST_AFTER_APPLY -> throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
            Mode.ERROR_AFTER_APPLY -> DeliveryRpcResponse(503, "{}")
            else -> answer
        }
        private fun conflict(reason: String) =
            DeliveryRpcResponse(409, """{"code":"23505","message":"parcel conflict: $reason"}""")
        private fun ack(op: UUID, id: UUID, kind: ParcelMutationKind, version: Long, touched: String, replay: Boolean) =
            DeliveryRpcResponse(200, """{"success":true,"operation_id":"$op","entity_id":"$id","kind":"${kind.name}","status":"APPLIED","remote_version":$version,"payload_version":1,"memberships":$touched,"idempotent":$replay}""")
    }

    private inner class FakeAuth : AccountAuthApi {
        var offline = false
        override val redirectUri = "app://callback"
        override suspend fun signIn(email: String, password: String): AuthSession = error("no login")
        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = error("no signup")
        override suspend fun recover(email: String, challenge: String) = error("no recovery")
        override suspend fun exchange(code: String, verifier: String): AuthSession = error("no exchange")
        override suspend fun refresh(session: AuthSession): AuthSession = error("no refresh")
        override suspend fun user(session: AuthSession): Pair<UUID, String> {
            if (offline) throw AuthFailure(AuthFailure.Kind.NETWORK)
            return session.userId to session.email
        }
        override suspend fun changePassword(session: AuthSession, password: String) = error("no password")
        override suspend fun signOut(session: AuthSession) = Unit
    }
}
