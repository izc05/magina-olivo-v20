package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDrill
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DisabledDeliveryRpcTransport
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SimulatedAccountAuthApi
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The drill is assembled and inert. This test is the proof of the second half: it exercises the
 * composition point on the device and shows that nothing can be sent.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryDrillDisabledTest {

    private val session = com.isivoltpro.maginaolivo.feature.account.AuthSession(
        UUID.randomUUID(), "drill@example.test", "token", "refresh",
        Instant.now().plusSeconds(3_600),
    )

    @Test
    fun theDrillIsOffAndReportsEveryBlockerRatherThanFailingQuietly() {
        assertFalse("El interruptor de compilacion debe estar en false", DeliveryDrill.ENABLED)

        // Even pretending the credentials are there, the drill is unavailable.
        assertFalse(DeliveryDrill.isAvailable(hasDevCredentials = true))
        assertFalse(DeliveryDrill.isAvailable(hasDevCredentials = false))

        assertEquals(
            "Sin credenciales se reportan los tres bloqueos, no solo el primero",
            listOf(
                DeliveryDrill.Blocker.NOT_AUTHORIZED,
                DeliveryDrill.Blocker.NO_DEV_CREDENTIALS,
                DeliveryDrill.Blocker.TRANSPORT_DISABLED,
            ),
            DeliveryDrill.blockers(hasDevCredentials = false),
        )
        assertEquals(
            listOf(DeliveryDrill.Blocker.NOT_AUTHORIZED, DeliveryDrill.Blocker.TRANSPORT_DISABLED),
            DeliveryDrill.blockers(hasDevCredentials = true),
        )
    }

    @Test
    fun theTransportTheDrillCanBuildRefusesEveryContractedCallWithoutASocket() = runBlocking {
        assertTrue(
            "El unico transporte disponible es el que rechaza",
            DeliveryDrill.transportForDrill() === DisabledDeliveryRpcTransport,
        )
        val adapter = DeliveryRpcAdapter(DeliveryDrill.transportForDrill())
        val deliveryId = UUID.randomUUID()
        val operationId = UUID.randomUUID()

        val calls = listOf<suspend () -> Any?>(
            { adapter.syncOwnDelivery(session, operationId, """{"id":"$deliveryId"}""") },
            { adapter.checkSyncOperations(session, listOf(operationId)) },
            { adapter.getOwnDeliveryV2(session, deliveryId) },
            { adapter.readOwnDeliveryAggregate(session, deliveryId) },
        )
        for (call in calls) {
            try {
                call()
                fail("Ninguna llamada puede completarse mientras el ensayo esta desactivado")
            } catch (failure: DeliveryRpcFailure) {
                // FORBIDDEN, not NETWORK: nothing left the device, so the outcome is NOT unknown.
                assertEquals(DeliveryRpcError.FORBIDDEN, failure.error)
                assertEquals("DRILL_DISABLED", failure.code)
                assertFalse(
                    "Un rechazo local no puede presentarse como resultado desconocido",
                    failure.outcomeUnknown,
                )
            }
        }
    }

    @Test
    fun theSingleDispatchEntryPointRefusesWithRealArgumentsAndWritesNothing() = runBlocking {
        // Real database, real repository, real lease: the arguments are usable, so a refusal here
        // is the guard doing its job and not an artefact of an unusable call site.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "drill-guard-${UUID.randomUUID()}.db"
        val ledger = File(context.noBackupFilesDir, "drill-guard-${UUID.randomUUID()}.json")
        val directory = File(context.noBackupFilesDir, "drill-guard-${UUID.randomUUID()}")
        val database = MaginaOlivoDatabase.create(context, dbName)
        try {
            val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
            val account = AccountRepository(api, MemoryAccountSessionStore())
            account.login("drill@example.test", "password42", true)
            val links = LocalFarmLinkRepository(
                context, database, account, "fixture-project", AndroidFarmLinkLedger(ledger),
            )
            val lease = checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
            try {
                val failure = runCatching {
                    DeliveryDrill.runSingleDispatch(
                        database, links, UUID.randomUUID(), lease, hasDevCredentials = true,
                    )
                }.exceptionOrNull()
                assertTrue(
                    "El ensayo debe rechazarse, no ejecutarse: " + failure?.javaClass?.simpleName,
                    failure is IllegalStateException,
                )
                assertTrue(
                    "El motivo debe nombrarse, no ocultarse: " + failure!!.message,
                    failure.message!!.contains(DeliveryDrill.Blocker.NOT_AUTHORIZED.name),
                )
                // Nothing was written and nothing was claimed.
                assertEquals(0, database.deliveryDispatchPrepareDao().countUnsettled())
                // The lease is untouched: still held, with no request ever started.
                lease.requireOpen()
            } finally {
                lease.close()
            }
        } finally {
            database.close()
            context.deleteDatabase(dbName)
            directory.deleteRecursively()
            listOf(ledger, File(ledger.path + ".bak"), File(ledger.path + ".new")).forEach { it.delete() }
        }
    }
}
