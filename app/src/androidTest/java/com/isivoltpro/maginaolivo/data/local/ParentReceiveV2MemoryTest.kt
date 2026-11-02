package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.PARENT_GRAPH_V2_MAX_BYTES
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveReport
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveV2Coordinator
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2c-2 (#812 condition 4): memory, GC and latency of the v2 receiver with the largest graphs the
 * contract allows, on the disposable API 35 emulator. Results go to logcat tag G26MEM, which the
 * runner prints. Nothing is tuned here: no bound is raised and no pagination is added.
 *
 * The fake transport reproduces the real one's copies (bounded byte buffer, then a UTF-8 String),
 * because the HTTPS client itself may not be referenced outside its dev-flavor probes.
 */
@RunWith(AndroidJUnit4::class)
class ParentReceiveV2MemoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-parent-receive-v2-memory"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private val store = MemoryAccountSessionStore()
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository

    @Before fun setup() = runBlocking { reset() }

    @After fun close() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun contractMeasurementGraphFourKilobyteGeometries() = runBlocking {
        val bytes = graph(4116).toByteArray(Charsets.UTF_8)
        val first = measure("at-limit-4KB", bytes)
        assertEquals(ParentReceiveOutcome.IMPORTED, first.outcome)
        assertEquals(1 + 100 + 1000 + 5000, first.inserted)
        assertEquals(listOf(100, 1000, 5000), listOf(count("farms"), count("parcels"), count("farm_parcel_memberships")))
        val again = measure("at-limit-4KB-repeat", bytes)
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, again.outcome)
    }

    @Test fun largestGraphUnderTheEightMebibyteBound() = runBlocking {
        val base = graph(0).toByteArray(Charsets.UTF_8).size
        val geometry = (PARENT_GRAPH_V2_MAX_BYTES - base - 4096) / 1000
        val bytes = graph(geometry).toByteArray(Charsets.UTF_8)
        assertTrue("${bytes.size} bytes", bytes.size in (PARENT_GRAPH_V2_MAX_BYTES - 16384)..PARENT_GRAPH_V2_MAX_BYTES)
        val report = measure("max-under-8MiB", bytes)
        assertEquals(ParentReceiveOutcome.IMPORTED, report.outcome)
        assertEquals(1000, count("parcels"))
    }

    @Test fun overTheBoundIsRefusedBeforeParsingAndWritesNothing() = runBlocking {
        val base = graph(0).toByteArray(Charsets.UTF_8).size
        val geometry = (PARENT_GRAPH_V2_MAX_BYTES - base) / 1000 + 8
        val bytes = graph(geometry).toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size > PARENT_GRAPH_V2_MAX_BYTES)
        val report = measure("over-8MiB", bytes)
        assertEquals(ParentReceiveOutcome.TOO_LARGE, report.outcome)
        assertEquals(listOf(0, 0, 0, 0), listOf(count("workspaces"), count("farms"), count("parcels"), count("farm_parcel_memberships")))
    }

    private suspend fun measure(label: String, payload: ByteArray): ParentReceiveReport {
        val runtime = Runtime.getRuntime()
        repeat(2) { System.gc(); System.runFinalization() }
        SystemClock.sleep(200)
        val baseline = runtime.totalMemory() - runtime.freeMemory()
        val peak = AtomicLong(baseline)
        val running = AtomicBoolean(true)
        val sampler = Thread {
            while (running.get()) {
                val used = runtime.totalMemory() - runtime.freeMemory()
                if (used > peak.get()) peak.set(used)
                SystemClock.sleep(2)
            }
        }.apply { isDaemon = true; start() }
        val gcCount = stat("art.gc.gc-count"); val gcTime = stat("art.gc.gc-time"); val blocking = stat("art.gc.blocking-gc-count")
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val started = SystemClock.elapsedRealtime()
        val report = try {
            ParentReceiveV2Coordinator(db, account, issuer, transport(payload)) { now }.receive(workspace)
        } catch (oom: OutOfMemoryError) {
            running.set(false)
            Log.i(TAG, "G26-MEM $label OOM ${oom.message}")
            throw oom
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        running.set(false); sampler.join()
        val line = "G26-MEM $label outcome=${report.outcome} payloadBytes=${payload.size} elapsedMs=$elapsed " +
            "heapPeakDeltaMB=${mb(peak.get() - baseline)} heapBaselineMB=${mb(baseline)} heapMaxMB=${mb(runtime.maxMemory())} " +
            "gcCount=${stat("art.gc.gc-count") - gcCount} gcTimeMs=${stat("art.gc.gc-time") - gcTime} " +
            "blockingGc=${stat("art.gc.blocking-gc-count") - blocking} nativeDeltaMB=${mb(Debug.getNativeHeapAllocatedSize() - nativeBefore)} " +
            "api=${android.os.Build.VERSION.SDK_INT}"
        Log.i(TAG, line)
        return report
    }

    /** Same copies as the real transport: a bounded byte buffer, then a UTF-8 String. */
    private fun transport(payload: ByteArray) = DeliveryRpcTransport { _, _, _ ->
        val output = ByteArrayOutputStream()
        ByteArrayInputStream(payload).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                check(output.size() + read <= PARENT_GRAPH_V2_MAX_BYTES + 65536)
                output.write(buffer, 0, read)
            }
        }
        DeliveryRpcResponse(200, output.toString("UTF-8"))
    }

    /** 100 farms, 1000 ACTIVE parcels with [geometry] chars of GeoJSON, 5000 periods (4 closed + 1 open each). */
    private fun graph(geometry: Int): String {
        val farms = (0 until 100).map { UUID(0x2222L, it.toLong()) }
        val shape = if (geometry == 0) null else buildString(geometry) {
            append("{\"type\":\"Polygon\",\"coordinates\":[[")
            while (length < geometry - 4) append("[-3.4812345,37.6312345],")
            setLength(geometry - 4); append("]]}")
        }
        val at = "2026-10-11T08:00:00.123456+00:00"
        val out = StringBuilder(PARENT_GRAPH_V2_MAX_BYTES + 65536)
        out.append("""{"contract_version":2,"issuer":"$issuer/auth/v1","subject":"$a","workspace":{"id":"$workspace","name":"Olivar","ownerUserId":"$a","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"$at","updatedAt":"$at","version":1},"farms":[""")
        farms.forEachIndexed { i, id ->
            if (i > 0) out.append(',')
            out.append("""{"id":"$id","workspaceId":"$workspace","name":"Finca $i","description":null,"municipality":"Bedmar","province":"Jaén","notes":null,"status":"ACTIVE","archivedAt":null,"createdAt":"$at","updatedAt":"$at","version":1}""")
        }
        out.append("],\"parcels\":[")
        for (i in 0 until 1000) {
            if (i > 0) out.append(',')
            out.append("""{"id":"${UUID(0x3333L, i.toLong())}","workspaceId":"$workspace","displayName":"Parcela $i","cadastralReference":null,"cadastralPolygon":null,"cadastralParcel":null,"municipality":"Bedmar","province":"Jaén","source":"MANUAL","geometryGeoJson":${shape?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},"sourceProvider":null,"sourceImportedAt":null,"cadastralAreaM2":null,"managedAreaM2":${10000 + i},"notes":null,"status":"ACTIVE","oliveTreeCount":${100 + i},"variety":"Picual","irrigationSystem":null,"irrigationNetwork":null,"irrigationSector":null,"irrigationDays":null,"archivedAt":null,"createdAt":"$at","updatedAt":"$at","version":1}""")
        }
        out.append("],\"memberships\":[")
        var first = true
        for (i in 0 until 1000) for (k in 0 until 5) {
            if (!first) out.append(',')
            first = false
            val from = "${2020 + k}-01-01T00:00:00Z"
            val until = if (k < 4) "\"${2020 + k}-12-31T00:00:00Z\"" else "null"
            out.append("""{"id":"${UUID(0x4444L, (i * 5 + k).toLong())}","workspaceId":"$workspace","farmId":"${farms[(i + k) % 100]}","parcelId":"${UUID(0x3333L, i.toLong())}","validFrom":"$from","validUntil":$until,"createdAt":"$at","updatedAt":"$at","version":1}""")
        }
        out.append("]}")
        return out.toString()
    }

    private suspend fun reset() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        store.write(AccountRecord(session = AuthSession(a, "a@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth, store) { now }
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, a.toString(),
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"QA","role":"OWNER","permissions":["read"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""))
    }

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private fun stat(key: String) = Debug.getRuntimeStat(key)?.toLongOrNull() ?: -1L
    private fun mb(bytes: Long) = "%.1f".format(bytes / 1048576.0)

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

    private companion object { const val TAG = "G26MEM" }
}
