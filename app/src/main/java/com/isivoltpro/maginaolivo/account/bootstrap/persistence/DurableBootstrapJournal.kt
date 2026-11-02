package com.isivoltpro.maginaolivo.account.bootstrap.persistence

import com.isivoltpro.maginaolivo.account.bootstrap.*
import com.isivoltpro.maginaolivo.account.reservation.ReservationActor
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BootstrapSnapshot internal constructor(val plan: BootstrapPlan, receipts: List<BootstrapReceipt>) {
    val receipts: List<BootstrapReceipt> = Collections.unmodifiableList(receipts.toList())
    init { require(plan.validReceipts(this.receipts)) }

    fun ready(actor: ReservationActor?, consentId: UUID?, consentedFarmIds: Set<UUID>): List<BootstrapStep> =
        plan.ready(actor, consentId, consentedFarmIds, receipts)
}

/**
 * One immutable plan per private file. No app factory, transport, tokens or Room state writes.
 * Integrity checksum detects corruption, not a malicious writer. Caller must use private no-backup storage.
 * Atomic rename + fsync protect process interruption; directory fsync/power-loss guarantees are not claimed.
 */
class DurableBootstrapJournal(private val file: File, private val beforeCommit: () -> Unit = {}) {
    fun prepare(plan: BootstrapPlan): BootstrapSnapshot = locked {
        val requested = BootstrapSnapshot(plan, emptyList())
        read()?.let { existing ->
            require(encode(BootstrapSnapshot(existing.plan, emptyList())).contentEquals(encode(requested))) {
                "Immutable bootstrap plan"
            }
            return@locked existing
        }
        commit(requested)
        requested
    }

    /** Private recovery data; this is not authorization to display or send another account's payload. */
    fun load(): BootstrapSnapshot? = locked { read() }

    /** Accept only a previously authenticated, operation-bound server receipt; no credential inference. */
    fun record(receipt: BootstrapReceipt): BootstrapSnapshot = locked {
        val current = read() ?: throw IOException("bootstrap_not_prepared")
        current.receipts.find { it.operationId == receipt.operationId }?.let {
            require(it == receipt) { "Immutable bootstrap receipt" }
            return@locked current
        }
        val next = BootstrapSnapshot(current.plan, current.receipts + receipt)
        commit(next)
        next
    }

    private fun <T> locked(action: () -> T): T {
        val path = file.canonicalPath
        return synchronized(locks.computeIfAbsent(path) { Any() }) {
            val parent = file.absoluteFile.parentFile ?: throw IOException("bootstrap_directory_missing")
            if (!parent.mkdirs() && !parent.isDirectory) throw IOException("bootstrap_directory_unavailable")
            RandomAccessFile(File(parent, file.name + ".lock"), "rw").use { lock ->
                lock.channel.lock().use {
                    parent.listFiles()?.filter { it.isFile && it.name.startsWith(file.name + ".tmp-") &&
                        UUID_REGEX.matches(it.name.removePrefix(file.name + ".tmp-")) }?.forEach { it.delete() }
                    action()
                }
            }
        }
    }

    private fun read(): BootstrapSnapshot? {
        if (!file.exists()) return null
        try {
            if (file.length() !in 45..MAX_BYTES.toLong()) throw IOException("bootstrap_size_invalid")
            // Bound the read even if an external writer changes the file after the length check.
            val data = FileInputStream(file).use { it.readBytesBounded() }
            return DataInputStream(ByteArrayInputStream(data)).use { input ->
                check(input.readInt() == MAGIC && input.readInt() == 1)
                val size = input.readInt(); check(size == data.size - 44)
                val checksum = ByteArray(32).also(input::readFully)
                val body = ByteArray(size).also(input::readFully)
                check(MessageDigest.isEqual(checksum, digest(body)) && input.read() == -1)
                decode(body)
            }
        } catch (error: IOException) { throw error }
        catch (error: Exception) { throw IOException("bootstrap_invalid", error) }
    }

    private fun commit(snapshot: BootstrapSnapshot) {
        val body = encode(snapshot)
        val temporary = File(file.absoluteFile.parentFile, file.name + ".tmp-" + UUID.randomUUID())
        try {
            FileOutputStream(temporary).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(MAGIC); out.writeInt(1); out.writeInt(body.size); out.write(digest(body)); out.write(body)
                out.flush(); stream.fd.sync()
            }
            beforeCommit()
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { if (temporary.exists()) temporary.delete() }
    }

    private fun encode(snapshot: BootstrapSnapshot): ByteArray {
        val buffer = ByteArrayOutputStream()
        // Enforce a whole-plan bound during serialization, not after an unbounded allocation.
        val bounded = object : OutputStream() {
            override fun write(b: Int) { checkCapacity(1); buffer.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { checkCapacity(len); buffer.write(b, off, len) }
            private fun checkCapacity(count: Int) {
                if (count > MAX_BYTES - 44 - buffer.size()) throw IOException("bootstrap_too_large")
            }
        }
        DataOutputStream(bounded).use { out ->
            val plan = snapshot.plan
            out.actor(plan.actor); out.uuid(plan.remoteWorkspaceId); out.uuid(plan.consentId); out.ids(plan.consentedFarmIds)
            out.writeInt(plan.steps.size)
            plan.steps.forEach { step ->
                out.uuid(step.operationId); out.uuid(step.entityId); out.text(step.entity.name)
                out.text(step.payloadJson); out.text(step.payloadHash); out.writeInt(step.payloadVersion)
                out.ids(step.dependencies); out.ids(step.requiredFarmIds); out.ids(step.requiredParcelIds)
            }
            out.writeInt(snapshot.receipts.size)
            snapshot.receipts.forEach { receipt ->
                out.actor(receipt.actor); out.uuid(receipt.operationId); out.uuid(receipt.entityId); out.text(receipt.entity.name)
                out.text(receipt.payloadHash); out.writeInt(receipt.payloadVersion); out.writeLong(receipt.remoteVersion)
            }
        }
        return buffer.toByteArray()
    }

    private fun decode(bytes: ByteArray): BootstrapSnapshot = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val actor = input.actor(); val workspace = input.uuid(); val consent = input.uuid(); val farms = input.ids()
        val steps = List(input.count()) {
            BootstrapStep(input.uuid(), input.uuid(), BootstrapEntity.valueOf(input.text()), input.text(), input.text(),
                input.readInt(), input.ids(), input.ids(), input.ids())
        }
        val receipts = List(input.count()) {
            BootstrapReceipt(input.actor(), input.uuid(), input.uuid(), BootstrapEntity.valueOf(input.text()),
                input.text(), input.readInt(), input.readLong())
        }
        check(input.read() == -1)
        BootstrapSnapshot(BootstrapPlan(actor, workspace, consent, farms, steps), receipts)
    }

    private fun InputStream.readBytesBounded(): ByteArray {
        val result = ByteArrayOutputStream(); val chunk = ByteArray(8192)
        while (true) {
            val count = read(chunk); if (count < 0) return result.toByteArray()
            if (count > MAX_BYTES - result.size()) throw IOException("bootstrap_too_large")
            result.write(chunk, 0, count)
        }
    }
    private fun DataInputStream.count(): Int = readInt().also { check(it in 0..10000) }
    private fun DataInputStream.uuid() = UUID(readLong(), readLong())
    private fun DataOutputStream.uuid(id: UUID) { writeLong(id.mostSignificantBits); writeLong(id.leastSignificantBits) }
    private fun DataInputStream.actor() = ReservationActor(text(), uuid())
    private fun DataOutputStream.actor(actor: ReservationActor) { text(actor.issuer); uuid(actor.userId) }
    private fun DataInputStream.ids(): Set<UUID> {
        val ids = List(count()) { uuid() }; check(ids.size == ids.toSet().size); return ids.toSet()
    }
    private fun DataOutputStream.ids(ids: Set<UUID>) { require(ids.size <= 10000); writeInt(ids.size); ids.sortedBy(UUID::toString).forEach { uuid(it) } }
    private fun DataInputStream.text(): String {
        val size = readInt(); check(size in 0..MAX_TEXT_BYTES && size <= available())
        val bytes = ByteArray(size).also(::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun DataOutputStream.text(text: String) {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
        require(encoded.remaining() <= MAX_TEXT_BYTES)
        val bytes = ByteArray(encoded.remaining()).also(encoded::get)
        writeInt(bytes.size); write(bytes)
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private companion object {
        const val MAGIC = 0x4d474231
        const val MAX_BYTES = 16 * 1024 * 1024
        const val MAX_TEXT_BYTES = 4 * 1024 * 1024
        val locks = ConcurrentHashMap<String, Any>()
        val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
