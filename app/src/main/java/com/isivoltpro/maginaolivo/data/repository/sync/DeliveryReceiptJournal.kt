package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.account.reservation.ReservationActor
import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Prepared before HTTP; correlation only, never a permission grant. */
internal data class DeliveryReceiptIntent(
    val actor: ReservationActor,
    val localWorkspaceId: UUID,
    val remoteWorkspaceId: UUID,
    val consentId: UUID,
    val farmId: UUID,
    val operationId: UUID,
    val deliveryId: UUID,
    val payloadVersion: Int,
    val payloadHash: String,
    val baseRemoteVersion: Long? = null,
    val consentTextVersion: Int = 2,
) {
    init {
        require(payloadVersion in 1..2 && Regex("[0-9a-f]{64}").matches(payloadHash))
        require(baseRemoteVersion == null || baseRemoteVersion >= 1)
        require(consentTextVersion >= 1)
    }
}

internal data class DeliveryReceiptSnapshot(val intent: DeliveryReceiptIntent, val remoteVersion: Long?) {
    init { require(remoteVersion == null || remoteVersion > (intent.baseRemoteVersion ?: 0)) }
}

/**
 * One prepared operation and its immutable authenticated ACK per private no-backup file.
 * Record the prepared scope BEFORE sending; publish the receipt BEFORE changing Room.
 * A revoked account/farm may retain evidence without becoming eligible for dispatch.
 * This class does not authenticate ACKs, inspect consent or change any Room row.
 * Checksum detects accidental corruption, not malicious private-file modification.
 */
internal class DeliveryReceiptJournal(private val file: File, private val beforeCommit: () -> Unit = {}) {
    fun prepare(intent: DeliveryReceiptIntent): DeliveryReceiptSnapshot = locked {
        read()?.let {
            require(it.intent == intent) { "Immutable delivery receipt intent" }
            return@locked it
        }
        DeliveryReceiptSnapshot(intent, null).also(::commit)
    }

    /** Caller already verified the server response for this exact prepared scope/hash. */
    fun record(intent: DeliveryReceiptIntent, remoteVersion: Long): DeliveryReceiptSnapshot = locked {
        val current = read() ?: throw IOException("delivery_receipt_not_prepared")
        require(current.intent == intent) { "Receipt scope does not match prepared operation" }
        current.remoteVersion?.let {
            require(it == remoteVersion) { "Immutable delivery receipt version" }
            return@locked current
        }
        DeliveryReceiptSnapshot(intent, remoteVersion).also(::commit)
    }

    /** Private correlation data, not authorization to show another account's evidence. */
    fun load(): DeliveryReceiptSnapshot? = locked { read() }

    private fun <T> locked(action: () -> T): T = synchronized(locks.computeIfAbsent(file.canonicalPath) { Any() }) {
        val parent = file.absoluteFile.parentFile ?: throw IOException("delivery_receipt_directory_missing")
        if (!parent.mkdirs() && !parent.isDirectory) throw IOException("delivery_receipt_directory_unavailable")
        RandomAccessFile(File(parent, file.name + ".lock"), "rw").use { lock ->
            lock.channel.lock().use {
                parent.listFiles()?.filter { it.isFile && it.name.startsWith(file.name + ".tmp-") &&
                    UUID_PATTERN.matches(it.name.removePrefix(file.name + ".tmp-")) }?.forEach {
                    if (!it.delete()) throw IOException("delivery_receipt_temporary_cleanup_failed")
                }
                action()
            }
        }
    }

    private fun read(): DeliveryReceiptSnapshot? {
        if (!file.exists()) return null
        try {
            val data = FileInputStream(file).use { input ->
                val result = ByteArrayOutputStream(); val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    if (count > MAX_BYTES - result.size()) throw IOException("delivery_receipt_too_large")
                    result.write(chunk, 0, count)
                }
                result.toByteArray()
            }
            return DataInputStream(ByteArrayInputStream(data)).use { input ->
                check(input.readInt() == MAGIC && input.readInt() == 1)
                val size = input.readInt(); check(size >= 0 && size == data.size - 44)
                val checksum = ByteArray(32).also(input::readFully)
                val body = ByteArray(size).also(input::readFully)
                check(MessageDigest.isEqual(checksum, digest(body)) && input.read() == -1)
                decode(body)
            }
        } catch (error: IOException) { throw error }
        catch (error: Exception) { throw IOException("delivery_receipt_invalid", error) }
    }

    private fun commit(snapshot: DeliveryReceiptSnapshot) {
        val body = encode(snapshot)
        check(body.size <= MAX_BYTES - 44)
        val temporary = File(file.absoluteFile.parentFile, file.name + ".tmp-" + UUID.randomUUID())
        try {
            FileOutputStream(temporary).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(MAGIC); out.writeInt(1); out.writeInt(body.size)
                out.write(digest(body)); out.write(body); out.flush(); stream.fd.sync()
            }
            beforeCommit()
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { if (temporary.exists()) temporary.delete() }
    }

    private fun encode(snapshot: DeliveryReceiptSnapshot): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            val intent = snapshot.intent
            out.text(intent.actor.issuer); out.uuid(intent.actor.userId)
            out.uuid(intent.localWorkspaceId); out.uuid(intent.remoteWorkspaceId); out.uuid(intent.consentId)
            out.uuid(intent.farmId); out.uuid(intent.operationId); out.uuid(intent.deliveryId)
            out.writeInt(intent.payloadVersion); out.text(intent.payloadHash)
            out.writeBoolean(intent.baseRemoteVersion != null); intent.baseRemoteVersion?.let(out::writeLong)
            out.writeInt(intent.consentTextVersion)
            out.writeBoolean(snapshot.remoteVersion != null); snapshot.remoteVersion?.let(out::writeLong)
        }
        return buffer.toByteArray()
    }

    private fun decode(body: ByteArray): DeliveryReceiptSnapshot = DataInputStream(ByteArrayInputStream(body)).use { input ->
        val actor = ReservationActor(input.text(8192), input.uuid())
        val local = input.uuid(); val remote = input.uuid(); val consent = input.uuid()
        val farm = input.uuid(); val operation = input.uuid(); val delivery = input.uuid()
        val payloadVersion = input.readInt(); val hash = input.text(64)
        val base = if (input.readBoolean()) input.readLong() else null
        val textVersion = input.readInt()
        val version = if (input.readBoolean()) input.readLong() else null
        check(input.read() == -1)
        DeliveryReceiptSnapshot(DeliveryReceiptIntent(actor, local, remote, consent, farm, operation, delivery,
            payloadVersion, hash, base, textVersion), version)
    }

    private fun DataInputStream.uuid() = UUID(readLong(), readLong())
    private fun DataOutputStream.uuid(id: UUID) { writeLong(id.mostSignificantBits); writeLong(id.leastSignificantBits) }
    private fun DataInputStream.text(maximum: Int): String {
        val size = readInt(); check(size in 0..maximum && size <= available())
        val bytes = ByteArray(size).also(::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun DataOutputStream.text(value: String) {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        require(encoded.remaining() <= 8192)
        val bytes = ByteArray(encoded.remaining()).also(encoded::get)
        writeInt(bytes.size); write(bytes)
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private companion object {
        const val MAGIC = 0x4d4f4452
        const val MAX_BYTES = 12 * 1024
        val locks = ConcurrentHashMap<String, Any>()
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
