package com.isivoltpro.maginaolivo.data.repository

import android.content.Context
import android.util.AtomicFile
import com.isivoltpro.maginaolivo.feature.accountlink.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Private device metadata; no credentials. Missing != corrupt, and logout never clears it. */
class AndroidFarmLinkLedger(private val file: File) : FarmLinkLedgerStore {
    private val atomic = AtomicFile(file)
    override fun read(): FarmLinkLedger? = synchronized(lock) { guarded {
        if (!file.exists() && !File(file.path+".bak").exists()) return@guarded null
        val raw = atomic.openRead().use { it.readBytesLimited() }.toString(Charsets.UTF_8)
        val parser = org.json.JSONTokener(raw)
        val envelope = parser.nextValue() as JSONObject
        check(parser.nextClean() == '\u0000')
        val payload = envelope.getString("payload")
        check(hash(payload.toByteArray()) == envelope.getString("sha256"))
        val json = JSONObject(payload); check(json.getInt("format") == 1)
        val records = json.getJSONArray("consents")
        val entries = (0 until records.length()).map { index ->
            val value = records.getJSONObject(index)
            val farms = value.getJSONArray("farms")
            FarmLinkConsent(UUID.fromString(value.getString("operation")), value.getString("subject"),
                (0 until farms.length()).map { i -> farms.getJSONObject(i).let { f ->
                    FarmChoice(UUID.fromString(f.getString("id")), UUID.fromString(f.getString("workspace")), f.getString("name"), f.getLong("version"))
                } }, UUID.fromString(value.getString("backup")), value.getString("manifestHash"),
                Instant.parse(value.getString("acceptedAt")), value.getInt("textVersion"), value.getString("state"))
        }
        check(entries.isNotEmpty()); entries.forEach(::validate)
        check(entries.map { it.subject }.distinct().size == 1)
        check(entries.map { it.operationId }.distinct().size == entries.size)
        check(entries.flatMap { it.farms }.map { it.id }.distinct().size == entries.sumOf { it.farms.size })
        FarmLinkLedger(entries)
    } }

    override fun append(consent: FarmLinkConsent): FarmLinkLedger = synchronized(lock) { guarded {
        validate(consent)
        val previous = read()
        previous?.let { old ->
            check(old.subject == consent.subject)
            old.consents.find { it.operationId == consent.operationId }?.let { existing ->
                check(existing == consent); return@guarded old
            }
            check(consent.farms.none { it.id in old.farmIds })
        }
        val next = FarmLinkLedger(previous?.consents.orEmpty() + consent)
        val rows = JSONArray()
        next.consents.forEach { value ->
            val farms = JSONArray()
            value.farms.forEach { f -> farms.put(JSONObject().put("id",f.id.toString()).put("workspace",f.workspaceId.toString()).put("name",f.name).put("version",f.version)) }
            rows.put(JSONObject().put("operation",value.operationId.toString()).put("subject",value.subject)
                .put("farms",farms).put("backup",value.backupId.toString()).put("manifestHash",value.backupManifestHash)
                .put("acceptedAt",value.acceptedAt.toString()).put("textVersion",value.textVersion).put("state",value.state))
        }
        val payload = JSONObject().put("format",1).put("consents",rows).toString()
        val bytes = JSONObject().put("payload",payload).put("sha256",hash(payload.toByteArray())).toString().toByteArray()
        check(bytes.size <= MAX_BYTES)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
        next
    } }

    private fun validate(value: FarmLinkConsent) {
        check(value.subject.isNotBlank() && value.subject.length <= 512 && '|' in value.subject)
        check(value.farms.isNotEmpty() && value.farms.size <= 1000 && value.farms.map { it.id }.distinct().size == value.farms.size)
        check(value.farms.all { it.name.isNotBlank() && it.name.length <= 4096 && it.version >= 1 })
        check(value.textVersion == 1 && value.state == "PENDING_REMOTE_RESERVATION")
        check(Regex("[a-f0-9]{64}").matches(value.backupManifestHash))
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val bytes = ByteArray(4096)
        while (true) { val count=read(bytes); if(count<0)break;check(buffer.size()+count<=MAX_BYTES);buffer.write(bytes,0,count) }
        return buffer.toByteArray()
    }
    private inline fun <T> guarded(action: () -> T): T = try { action() }
        catch (error: Exception) { throw IOException("local_link_ledger_unavailable",error) }

    companion object {
        private val lock = Any()
        private const val MAX_BYTES = 1_048_576
        fun forContext(context: Context) = AndroidFarmLinkLedger(File(context.noBackupFilesDir,"farm-link-consents-v1.json"))
        internal fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
