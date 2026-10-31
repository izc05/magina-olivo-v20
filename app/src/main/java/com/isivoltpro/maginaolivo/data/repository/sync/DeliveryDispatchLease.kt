package com.isivoltpro.maginaolivo.data.repository.sync

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.util.UUID

/**
 * Nonblocking, process-lifetime ownership for the future Delivery sender. Every sender
 * and recovery participant must use the SAME private no-backup directory and hold the
 * lease across claim, HTTP completion/cancellation and receipt persistence.
 *
 * The lock file must never be deleted/renamed to recover it. OS process death releases
 * the lock; file age does not. This does not authorize HTTP or reset PROCESSING rows.
 *
 * Every request runs inside [withActiveRequest]. [close] while one is still running (HTTP,
 * its cancellation, or the persistence of its result) only stops new work: the OS lock is
 * released when the last request has finished, so no new epoch can recover an operation
 * whose previous request is still executing.
 */
internal class DeliveryDispatchLease private constructor(
    private val path: String,
    private val file: RandomAccessFile,
    private val lock: FileLock,
) : Closeable {
    private var closed = false
    private var closing = false
    private var activeRequests = 0
    private val acquisitionId = UUID.randomUUID()

    /** Still the OS owner; true while a closing lease finishes its running requests. */
    fun requireHeld() = synchronized(guard) { requireHeldLocked() }

    /** Owner that may start NEW work: claims, recoveries and requests. */
    fun requireOpen() = synchronized(guard) {
        check(!closing) { "Delivery dispatch ownership is closing" }
        requireHeldLocked()
    }

    /**
     * Runs one request and everything that belongs to it (cancellation handling, receipt
     * persistence). Ownership cannot be released before [block] has returned or thrown.
     */
    suspend fun <T> withActiveRequest(block: suspend () -> T): T {
        synchronized(guard) {
            check(!closing) { "Delivery dispatch ownership is closing" }
            requireHeldLocked()
            activeRequests++
        }
        try {
            return block()
        } finally {
            synchronized(guard) {
                activeRequests--
                if (closing && activeRequests == 0) releaseLocked()
            }
        }
    }

    private fun requireHeldLocked() =
        check(!closed && lock.isValid && file.channel.isOpen) { "Delivery dispatch ownership was released" }

    /** Each acquisition has a new epoch, even when it uses the same canonical directory. */
    fun claimMarker(): String {
        return recoveryNamespace() + acquisitionId
    }

    fun recoveryNamespace(): String {
        requireHeld()
        return "DELIVERY_LEASE_V2:" + MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) } + ":"
    }

    fun canRecover(previousMarker: String?): Boolean {
        val namespace = recoveryNamespace()
        return previousMarker != null && previousMarker.startsWith(namespace) &&
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .matches(previousMarker.removePrefix(namespace)) && previousMarker != claimMarker()
    }

    /** Stops new work now; releases the OS lock now or when the last running request ends. */
    override fun close() = synchronized(guard) {
        if (!closing) {
            closing = true
            if (activeRequests == 0) releaseLocked()
        }
    }

    private fun releaseLocked() {
        if (!closed) {
            closed = true
            try {
                if (lock.isValid) lock.release()
            } finally {
                try { file.close() } finally { ownedPaths.remove(path) }
            }
        }
    }

    companion object {
        private val guard = Any()
        // Do not open/close a second channel for a file already locked by this JVM.
        // On some systems that close would release the first channel's OS lock too.
        private val ownedPaths = mutableSetOf<String>()

        fun tryAcquire(directory: File): DeliveryDispatchLease? = synchronized(guard) {
            val root = directory.canonicalFile
            if (!root.mkdirs() && !root.isDirectory) throw IOException("Delivery lock directory unavailable")
            val target = File(root, "delivery-dispatch.lock")
            val path = target.canonicalPath
            if (path in ownedPaths) return@synchronized null
            val file = RandomAccessFile(target, "rw")
            try {
                val lock = file.channel.tryLock()
                if (lock == null) {
                    file.close()
                    return@synchronized null
                }
                ownedPaths.add(path)
                DeliveryDispatchLease(path, file, lock)
            } catch (error: Exception) {
                try { file.close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
                throw error
            }
        }
    }
}
