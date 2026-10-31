package com.isivoltpro.maginaolivo.data.repository.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.DispatchIdentity
import com.isivoltpro.maginaolivo.data.repository.PrepareOutcome
import com.isivoltpro.maginaolivo.data.repository.prepareDeliveryDispatch
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * DEV-ONLY test aid for the B1 gate: the process that gets KILLED.
 *
 * An instrumentation run cannot be the victim, because the runner's own process has already
 * exited by the time the host could kill it — a clean exit, not a crash. So this service runs in
 * its own process (`android:process=":deathprobe"`), takes the dispatch lease, writes the durable
 * PREPARE, and then holds both the OS lock and the open database indefinitely. The host sends it
 * a real `kill -9` while it is alive and mid-flight; nothing here ever releases the lease, closes
 * the database or settles the operation.
 *
 * It reads the scope from the state file the test wrote, so no fixture logic is duplicated here.
 * It issues no HTTP request and touches no database other than the test one named in that state.
 *
 * Report file tokens, one per line: `PID:<pid>`, then `PREPARED:<leaseMarker>` (or
 * `OUTCOME:<name>` / `ERROR:<type>`), then `HOLDING`. `HOLDING` with no later token is the
 * expected final state: it means the process was killed while still holding everything.
 */
internal class DeliveryDispatchDeathProbeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val statePath = intent?.getStringExtra(EXTRA_STATE)
        val reportPath = intent?.getStringExtra(EXTRA_REPORT)
        if (statePath == null || reportPath == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val report = File(reportPath)

        thread(name = "death-probe", isDaemon = false) {
            fun say(token: String) = synchronized(report) { report.appendText(token + "\n") }
            say("PID:" + Process.myPid())
            try {
                val state = JSONObject(File(statePath).readText())
                val lease = DeliveryDispatchLease.tryAcquire(File(state.getString("lockDirectory")))
                if (lease == null) {
                    say("BLOCKED")
                    stopSelf(startId)
                    return@thread
                }
                val marker = lease.claimMarker()
                // Deliberately NOT in a use/try-finally block: this process must die owning it.
                val db = MaginaOlivoDatabase.create(this, state.getString("database"))
                val ledger = AndroidFarmLinkLedger(File(state.getString("ledger"))).read()
                val identity = DispatchIdentity(
                    accountId = state.getString("accountId"),
                    issuer = state.getString("issuer"),
                    subject = state.getString("subject"),
                    localWorkspaceId = UUID.fromString(state.getString("localWorkspaceId")),
                )
                val outcome = runBlocking {
                    db.prepareDeliveryDispatch(
                        lease,
                        UUID.fromString(state.getString("operationId")),
                        identity,
                        ledger,
                        Instant.parse(state.getString("at")),
                    )
                }
                if (outcome != PrepareOutcome.PREPARED) {
                    say("OUTCOME:" + outcome.name)
                    stopSelf(startId)
                    return@thread
                }
                say("PREPARED:" + marker)
                say("HOLDING")
                // Hold the lease, the open database and this process until the host kills us.
                // The timeout only prevents a stuck probe from outliving the test session; a
                // normal B1 run never reaches it, because kill -9 arrives first.
                val deadline = System.currentTimeMillis() + HOLD_TIMEOUT_MILLIS
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(200L)
                }
                say("TIMEOUT_WITHOUT_BEING_KILLED")
            } catch (error: Throwable) {
                say("ERROR:" + error.javaClass.simpleName + ":" + (error.message ?: ""))
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_STATE = "state"
        const val EXTRA_REPORT = "report"

        private const val HOLD_TIMEOUT_MILLIS = 300_000L
    }
}
