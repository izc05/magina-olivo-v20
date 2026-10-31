package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.AckCorrelationOutcome
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.AuthenticatedDeliveryAck
import com.isivoltpro.maginaolivo.data.repository.DispatchIdentity
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.correlateAuthenticatedDeliveryAck
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.settleAbandonedPreparedDispatches
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchDeathProbeService
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B1 gate — REAL process death, not a simulated one.
 *
 * Every in-process recovery test can only model what a crash does to the lease epoch. The
 * journal test that killed an actual process was retired with `DeliveryReceiptJournal`, so this
 * class restores that axis against the canonical Room table.
 *
 * It runs in THREE separate instrumentation invocations, driven from the host, because only a
 * new invocation is genuinely a new process:
 *
 * ```
 * am instrument -w -e class ...#phase1SeedTheScopeForTheDeathProbe -e g24DeathPhase prepare <runner>
 * am startservice ... DeliveryDispatchDeathProbeService   # takes the lease, writes the PREPARE
 * kill -9 <pid of :deathprobe>                            # a REAL kill of a LIVE owner
 * am instrument -w -e class ...#phase2RecoverTheSameOperation -e g24DeathPhase recover <runner>
 * am instrument -w -e class ...#phase3Cleanup -e g24DeathPhase cleanup <runner>
 * ```
 *
 * The kill must land on a live process. Phase 2 fails if the probe's report says it timed out
 * instead of being killed, so a run that merely exited cleanly cannot pass as process death.
 *
 * Without the `g24DeathPhase` argument every test is skipped, so CI stays green and this never
 * runs by accident. It uses its own database name, its own lock directory and its own ledger
 * file: no production or QA data is read or written. It issues no HTTP request.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryDispatchProcessDeathTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val lockDirectory = File(context.noBackupFilesDir, LOCK_DIRECTORY)
    private val ledgerFile = File(context.noBackupFilesDir, LEDGER_FILE)
    private val stateFile = File(context.noBackupFilesDir, STATE_FILE)
    private val reportFile = File(context.noBackupFilesDir, REPORT_FILE)

    private fun phase(): String? = InstrumentationRegistry.getArguments().getString(ARGUMENT)

    private fun requirePhase(expected: String) =
        assumeTrue("Requiere -e $ARGUMENT $expected; sin ese argumento no se ejecuta", phase() == expected)

    // ----------------------------------------------------------------------------- phase 1

    /**
     * Seeds the scope and records it on disk. It deliberately does NOT take the lease and does
     * NOT write the PREPARE: an instrumentation process has already exited by the time the host
     * could kill it, so a kill aimed here would land on nothing and the scenario would degenerate
     * into a clean exit. The PREPARE is written by [DeliveryDispatchDeathProbeService] in its own
     * process, which the host then kills for real while it still owns everything.
     */
    @Test
    fun phase1SeedTheScopeForTheDeathProbe() = runBlocking {
        requirePhase("prepare")
        listOf(ledgerFile, File(ledgerFile.path + ".bak"), File(ledgerFile.path + ".new"), stateFile, reportFile)
            .forEach { it.delete() }
        lockDirectory.deleteRecursively()
        context.deleteDatabase(DB)

        val db = MaginaOlivoDatabase.create(context, DB)
        seed(db)
        val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
        val account = AccountRepository(api, MemoryAccountSessionStore())
        account.login("b1@example.test", "password42", true)
        val links = LocalFarmLinkRepository(context, db, account, ISSUER, AndroidFarmLinkLedger(ledgerFile))
        val userId = checkNotNull(account.state.value.userId)
        links.consent(userId, links.choices().filter { it.id == FARM }, true)

        val deliveries = OfflineFirstDeliveryRepository(
            db, FixedClock(NOW), RandomIds, TestDispatchers, writeScope = links,
        ) { ZoneOffset.UTC }
        val created = deliveries.create(
            DeliveryDraft(
                farmId = FARM,
                deliveryDate = LocalDate.parse("2026-11-18"),
                destinationOrganizationId = null,
                destinationName = "Cooperativa San Isidro",
                netGrams = 2_800_000,
                shares = listOf(DeliveryShareInput(NORTH, null)),
                origin = PesadaOrigin.TREE,
            ),
        )
        val deliveryId = (created as AppResult.Success).value
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, deliveryId).single()

        assertNull(
            "Nada ha preparado todavia: eso lo hace el proceso que va a morir",
            db.deliveryDispatchPrepareDao().findByOperation(op.id),
        )
        // The lease must be free for the death probe, and this process must not keep it.
        val free = DeliveryDispatchLease.tryAcquire(lockDirectory)
        assertNotNull("El lease debe estar libre para la sonda", free)
        free!!.close()

        stateFile.writeText(
            JSONObject()
                .put("operationId", op.id.toString())
                .put("deliveryId", deliveryId.toString())
                .put("accountId", userId.toString())
                .put("subject", links.subject(userId))
                .put("issuer", ISSUER)
                .put("localWorkspaceId", WORKSPACE.toString())
                .put("database", DB)
                .put("ledger", ledgerFile.absolutePath)
                .put("lockDirectory", lockDirectory.absolutePath)
                .put("at", NOW.toString())
                .put("payloadJson", op.payloadJson)
                .put("payloadVersion", op.payloadVersion)
                .put("payloadHash", deliveryPayloadHash(op.payloadJson!!, op.payloadVersion))
                .put("baseRemoteVersion", op.baseRemoteVersion ?: JSONObject.NULL)
                .toString(),
        )

        // Closed on purpose: the death probe process opens this same database next.
        db.close()
        assertTrue("El ambito queda registrado en disco", stateFile.exists())

        // Start the victim from inside the app's own UID: a non exported service cannot be
        // started from an adb shell. It runs in :deathprobe, takes the lease, writes the
        // PREPARE and then holds everything until the host kills it.
        val intent = Intent(context, DeliveryDispatchDeathProbeService::class.java).apply {
            putExtra(DeliveryDispatchDeathProbeService.EXTRA_STATE, stateFile.absolutePath)
            putExtra(DeliveryDispatchDeathProbeService.EXTRA_REPORT, reportFile.absolutePath)
        }
        assertNotNull("El servicio sonda debe existir en el flavor dev", context.startService(intent))

        val deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline &&
            !(reportFile.exists() && reportFile.readText().contains("HOLDING"))
        ) {
            Thread.sleep(100L)
        }
        val armed = if (reportFile.exists()) reportFile.readLines().filter { it.isNotBlank() } else emptyList()
        assertTrue("La sonda debia preparar y quedarse sosteniendo el lease: $armed", armed.contains("HOLDING"))
        assertTrue("La sonda debe informar su PID para poder matarla: $armed", armed.any { it.startsWith("PID:") })

        // The PREPARE now exists on disk, written by the process that is about to be killed.
        val check = MaginaOlivoDatabase.create(context, DB)
        val prepared = checkNotNull(check.deliveryDispatchPrepareDao().findByOperation(op.id)) {
            "La sonda debio escribir el PREPARE duradero"
        }
        assertNull("Sin liquidar: nadie ha muerto todavia", prepared.settledAt)
        assertEquals(OutboxStatus.PROCESSING, check.syncOutboxDao().findById(op.id)!!.status)
        check.close()

        // This test is the WITNESS of the kill. It stays alive so the package is not torn down,
        // observes the victim alive in /proc, and only returns once the victim is gone. Without
        // this, the probe would die with the instrumentation teardown and a `kill -9` from the
        // host would land on nothing: a clean exit dressed up as a crash.
        val victim = armed.first { it.startsWith("PID:") }.removePrefix("PID:").trim()
        val victimProc = File("/proc/$victim")
        assertTrue("La victima $victim debe estar VIVA tras armarse", victimProc.exists())

        val killDeadline = System.currentTimeMillis() + KILL_WAIT_MILLIS
        while (System.currentTimeMillis() < killDeadline && victimProc.exists()) {
            Thread.sleep(100L)
        }
        assertTrue(
            "El host debia matar el proceso $victim mientras sostenia el lease; sigue vivo",
            !victimProc.exists(),
        )
        val epitaph = reportFile.readLines().filter { it.isNotBlank() }
        assertTrue("La sonda murio sin liberar nada: $epitaph", !epitaph.contains("RELEASED"))
        assertTrue(
            "La sonda expiro sola en vez de morir: $epitaph",
            !epitaph.contains("TIMEOUT_WITHOUT_BEING_KILLED"),
        )
        assertEquals("Su ultimo acto fue sostener el lease", "HOLDING", epitaph.last())
    }

    // ----------------------------------------------------------------------------- phase 2

    /**
     * A genuinely new process. Nothing in this JVM has ever held the lock file, so acquiring it
     * proves the previous owner's death released the OS lock, and recovery must restore exactly
     * the same operation UUID, payload and base version, as a NON terminal unknown outcome that
     * still accepts its late receipt.
     */
    @Test
    fun phase2RecoverTheSameOperation() = runBlocking {
        requirePhase("recover")
        assertTrue("Falta el estado de la fase 1: ejecutala antes", stateFile.exists())
        val state = JSONObject(stateFile.readText())
        val operationId = UUID.fromString(state.getString("operationId"))
        val deliveryId = UUID.fromString(state.getString("deliveryId"))
        val accountId = state.getString("accountId")

        // The marker comes from the process that actually died, not from this one.
        assertTrue("Falta el informe de la sonda que debia morir", reportFile.exists())
        val probe = reportFile.readLines().filter { it.isNotBlank() }
        assertTrue("La sonda debio escribir el PREPARE: $probe", probe.any { it.startsWith("PREPARED:") })
        assertTrue("La sonda debio quedarse sosteniendo el lease: $probe", probe.contains("HOLDING"))
        assertTrue(
            "La sonda expiro sola en vez de ser matada; esto NO prueba muerte real: $probe",
            !probe.contains("TIMEOUT_WITHOUT_BEING_KILLED"),
        )
        assertTrue("La sonda no debio liberar nada: $probe", !probe.contains("RELEASED"))
        val markerBeforeDeath = probe.first { it.startsWith("PREPARED:") }.removePrefix("PREPARED:")
        val payloadJson = state.getString("payloadJson")
        val payloadHash = state.getString("payloadHash")
        val payloadVersion = state.getInt("payloadVersion")

        val db = MaginaOlivoDatabase.create(context, DB)

        // The durable PREPARE survived the kill, untouched and unsettled.
        val survived = checkNotNull(db.deliveryDispatchPrepareDao().findByOperation(operationId)) {
            "El PREPARE duradero debe sobrevivir a la muerte del proceso"
        }
        assertNull("Nadie liquido nada: el proceso murio", survived.settledAt)
        assertEquals("El marcador es el del emisor difunto", markerBeforeDeath, survived.leaseMarker)
        assertEquals(deliveryId, survived.deliveryId)
        assertEquals(accountId, survived.accountId)
        assertEquals(payloadHash, survived.payloadHash)
        assertEquals(OutboxStatus.PROCESSING, db.syncOutboxDao().findById(operationId)!!.status)

        // The OS released the lock when the owner died. File age never grants ownership.
        val lease = DeliveryDispatchLease.tryAcquire(lockDirectory)
        assertNotNull("La muerte del proceso debe haber liberado el lock del sistema", lease)
        assertTrue(
            "La nueva epoca debe poder recuperar la del emisor difunto",
            lease!!.canRecover(markerBeforeDeath),
        )

        assertEquals(
            "Se recupera exactamente una operacion interrumpida",
            1,
            db.settleAbandonedPreparedDispatches(lease, accountId, FARM, NOW.plusSeconds(30)),
        )

        val recovered = db.syncOutboxDao().findById(operationId)!!
        assertEquals(operationId, recovered.id)
        assertEquals("El mismo UUID de pesada", deliveryId, recovered.entityId)
        assertEquals("Los MISMOS bytes de payload", payloadJson, recovered.payloadJson)
        assertEquals(payloadVersion, recovered.payloadVersion)
        assertEquals(OutboxStatus.FAILED, recovered.status)
        assertEquals("OUTCOME_UNKNOWN", recovered.lastErrorCode)
        assertEquals(
            DeliveryDispatchPrepareEntity.SETTLED_UNKNOWN,
            db.deliveryDispatchPrepareDao().findByOperation(operationId)!!.settledOutcome,
        )

        // An unknown outcome is NOT terminal: the server's receipt may still arrive.
        val ledger = AndroidFarmLinkLedger(ledgerFile).read()
        assertNotNull("El ledger de consentimiento debe sobrevivir tambien", ledger)
        val identity = DispatchIdentity(
            accountId = survived.accountId, issuer = survived.issuer, subject = survived.subject,
            localWorkspaceId = survived.localWorkspaceId, remoteWorkspaceId = survived.remoteWorkspaceId,
        )
        val ack = AuthenticatedDeliveryAck(
            operationId, deliveryId, FARM, accountId, accountId, 7L, payloadHash, payloadVersion,
        )
        assertEquals(
            "El ACK tardio del mismo intento se aplica",
            AckCorrelationOutcome.APPLIED_SYNCED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, ledger, NOW.plusSeconds(60)),
        )
        assertEquals(
            "Y solo una vez",
            AckCorrelationOutcome.ALREADY_SETTLED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, ledger, NOW.plusSeconds(90)),
        )
        assertEquals(
            "Un solo PREPARE para esta pesada, de principio a fin",
            1,
            db.deliveryDispatchPrepareDao().listForDelivery(deliveryId).size,
        )

        lease.close()
        db.close()
    }

    // ----------------------------------------------------------------------------- phase 3

    @Test
    fun phase3Cleanup() {
        requirePhase("cleanup")
        context.deleteDatabase(DB)
        lockDirectory.deleteRecursively()
        AndroidFarmLinkLedger(ledgerFile).read()?.consents?.forEach {
            File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively()
        }
        listOf(ledgerFile, File(ledgerFile.path + ".bak"), File(ledgerFile.path + ".new"), stateFile, reportFile)
            .forEach { it.delete() }
        assertTrue("Nada queda del escenario B1", !stateFile.exists() && !lockDirectory.exists())
    }

    // ----------------------------------------------------------------------------- fixture

    private suspend fun seed(db: MaginaOlivoDatabase) {
        val meta = LocalMetadata(NOW, NOW)
        db.workspaceDao().upsert(
            WorkspaceEntity(WORKSPACE, "Olivar B1", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(FARM, WORKSPACE, "La Solana B1", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(NORTH, WORKSPACE, "Norte", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(
            CampaignEntity(
                CAMPAIGN, WORKSPACE, FARM, "2026/27", LocalDate.parse("2026-10-01"), null,
                CampaignStatus.HARVEST, metadata = meta,
            ),
        )
        db.campaignDao().upsertSnapshots(
            listOf(
                CampaignParcelSnapshotEntity(
                    UUID.randomUUID(), WORKSPACE, CAMPAIGN, NORTH, FARM, "La Solana B1", "Norte", metadata = meta,
                ),
            ),
        )
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
        const val ARGUMENT = "g24DeathPhase"
        const val DB = "g24-process-death.db"
        const val LOCK_DIRECTORY = "g24-process-death-lock"
        const val LEDGER_FILE = "g24-process-death-ledger.json"
        const val STATE_FILE = "g24-process-death-state.json"
        const val REPORT_FILE = "g24-process-death-probe.txt"
        const val ISSUER = "fixture-project"

        /** How long phase 1 waits for the host's kill before declaring the gate not executed. */
        const val KILL_WAIT_MILLIS = 120_000L

        val NOW: Instant = Instant.parse("2026-12-02T08:00:00Z")
        val WORKSPACE: UUID = UUID.fromString("10000000-0000-0000-0000-0000000000b1")
        val FARM: UUID = UUID.fromString("20000000-0000-0000-0000-0000000000b1")
        val NORTH: UUID = UUID.fromString("30000000-0000-0000-0000-0000000000b1")
        val CAMPAIGN: UUID = UUID.fromString("40000000-0000-0000-0000-0000000000b1")
    }
}
