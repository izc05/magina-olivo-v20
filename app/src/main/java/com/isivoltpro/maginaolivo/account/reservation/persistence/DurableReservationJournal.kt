package com.isivoltpro.maginaolivo.account.reservation.persistence

import com.isivoltpro.maginaolivo.account.reservation.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Explicit private-file adapter only; no Context factory, credentials, app consumer or access grant. */
class DurableReservationJournal(private val file: File, private val beforeCommit: () -> Unit = {}) {
    fun prepare(scope: ReservationScope, request: ReservationRequest): ReservationRecovery = locked {
        val records=read()
        records[scope]?.let { require(it.request==request) { "Immutable reservation operation" };return@locked it }
        val pending=ReservationRecovery(scope,request)
        require(records.size<MAX_RECORDS)
        records[scope]=pending
        commit(records.values)
        pending
    }

    fun load(scope: ReservationScope): ReservationRecovery? = locked { read()[scope] }

    fun attempt(scope: ReservationScope, actor: () -> ReservationActor?, transport: ReservationTransport): ReservationRecovery {
        val initial=load(scope) ?: throw IOException("reservation_not_prepared")
        // Transport is outside the filesystem lock. A lost response leaves the committed request intact.
        val attempted=initial.attempt(actor,transport)
        if(attempted==initial) return initial
        return locked {
            val records=read()
            val current=records[scope] ?: throw IOException("reservation_missing")
            if(current!=initial && current!=attempted) throw IOException("reservation_changed_during_request")
            // Recheck after reacquiring the commit lock; a late receipt never grants the visible account access.
            val next=attempted.receipt?.let { attempted.accept(it,actor()) } ?: attempted
            if(current!=next) { records[scope]=next;commit(records.values) }
            next
        }
    }

    private fun <T> locked(action: () -> T): T {
        val path=file.canonicalPath
        return synchronized(locks.computeIfAbsent(path) { Any() }) {
            val parent=file.absoluteFile.parentFile ?: throw IOException("journal_directory_missing")
            if(!parent.mkdirs() && !parent.isDirectory) throw IOException("journal_directory_unavailable")
            RandomAccessFile(File(parent,file.name+".lock"),"rw").use { lock ->
                lock.channel.lock().use { action() }
            }
        }
    }

    private fun read(): LinkedHashMap<ReservationScope,ReservationRecovery> {
        if(!file.exists()) return linkedMapOf()
        try {
            if(file.length() !in 1..MAX_BYTES.toLong()) throw IOException("journal_size_invalid")
            val data=file.readBytes()
            return DataInputStream(ByteArrayInputStream(data)).use { input ->
                check(input.readInt()==MAGIC && input.readInt()==1)
                val size=input.readInt();check(size in 1..MAX_BYTES && size==data.size-44)
                val expected=ByteArray(32).also(input::readFully)
                val body=ByteArray(size).also(input::readFully)
                check(MessageDigest.isEqual(expected,digest(body)) && input.read()==-1)
                DataInputStream(ByteArrayInputStream(body)).use { content ->
                    val count=content.readInt();check(count in 1..MAX_RECORDS)
                    val result=linkedMapOf<ReservationScope,ReservationRecovery>()
                    repeat(count) {
                        val scope=content.scope()
                        val request=ReservationRequest(scope.operationId,content.uuid(),content.uuid(),content.uuid(),
                            content.readInt(),content.text(),content.readLong())
                        val status=ReservationStatus.valueOf(content.text())
                        val receipt=if(content.readBoolean()) ReservationReceipt(content.scope(),content.uuid(),content.text(),content.uuid(),content.uuid()) else null
                        val recovery=ReservationRecovery(scope,request,status,receipt)
                        check(result.put(scope,recovery)==null)
                    }
                    check(content.read()==-1)
                    result
                }
            }
        } catch(error:IOException) { throw error }
        catch(error:Exception) { throw IOException("journal_invalid",error) }
    }

    private fun commit(records: Collection<ReservationRecovery>) {
        val body=ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { output ->
            output.writeInt(records.size)
            records.forEach { recovery ->
                output.scope(recovery.scope)
                recovery.request.let { r -> output.uuid(r.farmId);output.uuid(r.localWorkspaceId);output.uuid(r.consentId)
                    output.writeInt(r.consentTextVersion);output.text(r.manifestSha256);output.writeLong(r.aggregateVersion) }
                output.text(recovery.status.name)
                output.writeBoolean(recovery.receipt!=null)
                recovery.receipt?.let { r -> output.scope(r.scope);output.uuid(r.farmId);output.text(r.payloadHash)
                    output.uuid(r.remoteWorkspaceId);output.uuid(r.receiptId) }
            }
        } }.toByteArray()
        if(body.size>MAX_BYTES-44) throw IOException("journal_too_large")
        val temporary=File(file.absoluteFile.parentFile,file.name+".tmp-"+UUID.randomUUID())
        try {
            FileOutputStream(temporary).use { stream ->
                val output=DataOutputStream(stream)
                output.writeInt(MAGIC);output.writeInt(1);output.writeInt(body.size);output.write(digest(body));output.write(body)
                output.flush();stream.fd.sync()
            }
            beforeCommit()
            // Fail closed if the filesystem cannot atomically replace; never use a non-atomic fallback.
            Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { if(temporary.exists()) temporary.delete() }
    }

    private fun DataInputStream.uuid()=UUID(readLong(),readLong())
    private fun DataOutputStream.uuid(value:UUID) { writeLong(value.mostSignificantBits);writeLong(value.leastSignificantBits) }
    private fun DataInputStream.scope()=ReservationScope(ReservationActor(text(),uuid()),uuid())
    private fun DataOutputStream.scope(value:ReservationScope) { text(value.actor.issuer);uuid(value.actor.userId);uuid(value.operationId) }
    private fun DataInputStream.text():String {
        val count=readInt();check(count in 0..4096)
        val bytes=ByteArray(count).also(::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun DataOutputStream.text(value:String) {
        val bytes=value.toByteArray(Charsets.UTF_8);require(bytes.size<=4096);writeInt(bytes.size);write(bytes)
    }
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private companion object {
        const val MAGIC=0x4d475231
        const val MAX_BYTES=1_048_576
        const val MAX_RECORDS=1000
        val locks=ConcurrentHashMap<String,Any>()
    }
}
